package com.filesync.client.service;

import com.filesync.client.file.FileHasher;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.SyncStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class FolderUploadService {
    private final SyncHttpClient httpClient;
    private final String ownerId;
    private final UUID sharedFolderId;
    private final UUID parentId;          // root parent ID (current directory where upload starts)
    private final Path localFolder;       // local folder to upload
    private final Consumer<String> statusCallback;
    private final Map<Path, UUID> folderIdCache = new HashMap<>();

    public FolderUploadService(SyncHttpClient httpClient, String ownerId, UUID sharedFolderId,
                               UUID parentId, Path localFolder, Consumer<String> statusCallback) {
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.sharedFolderId = sharedFolderId;
        this.parentId = parentId;
        this.localFolder = localFolder;
        this.statusCallback = statusCallback;
    }

    private void createServerDirectory(Path dir) throws IOException {
        if (folderIdCache.containsKey(dir)) return;

        Path parentDir = dir.getParent();
        UUID parentServerId = folderIdCache.get(parentDir);
        if (parentServerId == null) {
            // Should not happen with top‑down walk, but safe fallback
            if (parentDir != null && !parentDir.equals(localFolder)) {
                createServerDirectory(parentDir);
                parentServerId = folderIdCache.get(parentDir);
            }
            if (parentServerId == null) {
                parentServerId = this.parentId; // root parent
            }
        }

        String folderName = dir.getFileName().toString();
        UUID newFolderId = httpClient.createFolder(folderName, parentServerId, sharedFolderId);
        folderIdCache.put(dir, newFolderId);
        statusCallback.accept("Created folder: " + folderName);
    }

    public void upload() throws IOException {
        // Step 1: Create the root folder on the server
        String rootFolderName = localFolder.getFileName().toString();
        UUID rootServerId = httpClient.createFolder(rootFolderName, parentId, sharedFolderId);
        folderIdCache.put(localFolder, rootServerId);
        statusCallback.accept("Created folder: " + rootFolderName);

        // Step 2: Create all subdirectories (top‑down)
        try (Stream<Path> walk = Files.walk(localFolder)) {
            walk.filter(Files::isDirectory)
                    .filter(dir -> !dir.equals(localFolder)) // skip root, already created
                    .forEach(dir -> {
                        try {
                            createServerDirectory(dir);
                        } catch (Exception e) {
                            statusCallback.accept("Failed to create directory: " + dir + " - " + e.getMessage());
                        }
                    });
        }

        // Step 3: Upload all files (use the cached folder IDs)
        try (Stream<Path> walk = Files.walk(localFolder)) {
            walk.filter(Files::isRegularFile)
                    .forEach(this::uploadSingleFile);
        }
    }

    private void uploadSingleFile(Path file) {
        try {
            Path parentDir = file.getParent();
            UUID parentServerId = folderIdCache.get(parentDir);
            if (parentServerId == null) {
                // Fallback: use the upload root parent ID
                parentServerId = parentId;
                statusCallback.accept("Warning: parent folder not cached for " + file);
            }

            String fileName = file.getFileName().toString();
            String fileId = UUID.randomUUID().toString();
            FileMetadataDto dto = new FileMetadataDto();
            dto.setFileId(fileId);
            dto.setRelativePath(fileName);
            dto.setSize(Files.size(file));
            dto.setSha256Hash(FileHasher.computeHash(file));
            dto.setLastModified(Files.getLastModifiedTime(file).toInstant());
            dto.setOwnerId(ownerId);
            dto.setStatus(SyncStatus.SYNCED);
            dto.setFolderId(sharedFolderId);
            dto.setParentId(parentServerId);
            httpClient.createMetadata(dto);

            long fileSize = Files.size(file);
            if (fileSize > 5 * 1024 * 1024) {
                httpClient.uploadLargeFile(fileId, file, sharedFolderId);
            } else {
                httpClient.uploadFile(fileId, file, sharedFolderId);
            }
            statusCallback.accept("Uploaded: " + fileName);
        } catch (Exception e) {
            statusCallback.accept("Failed: " + file.getFileName());
        }
    }
}