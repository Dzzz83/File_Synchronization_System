package com.filesync.client.service;

import com.filesync.client.file.FileHasher;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.SyncStatus;
import javafx.application.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class FolderUploadService {
    private final SyncHttpClient httpClient;
    private final String ownerId;
    private final UUID sharedFolderId;
    private final UUID parentId;
    private final Path localFolder;
    private final Consumer<String> statusCallback;
    private final Map<Path, UUID> folderIdCache = new HashMap<>();
    private final ProgressService progressService;
    private final int totalFiles;
    private final AtomicInteger uploadedFiles = new AtomicInteger(0);

    public FolderUploadService(SyncHttpClient httpClient, String ownerId, UUID sharedFolderId,
                               UUID parentId, Path localFolder, Consumer<String> statusCallback,
                               ProgressService progressService, int totalFiles) {
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.sharedFolderId = sharedFolderId;
        this.parentId = parentId;
        this.localFolder = localFolder;
        this.statusCallback = statusCallback;
        this.progressService = progressService;
        this.totalFiles = totalFiles;
    }

    private void createServerDirectory(Path dir) throws IOException {
        if (folderIdCache.containsKey(dir)) return;

        Path parentDir = dir.getParent();
        UUID parentServerId = folderIdCache.get(parentDir);
        if (parentServerId == null) {
            if (parentDir != null && !parentDir.equals(localFolder)) {
                createServerDirectory(parentDir);
                parentServerId = folderIdCache.get(parentDir);
            }
            if (parentServerId == null) {
                parentServerId = this.parentId;
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
                    .filter(dir -> !dir.equals(localFolder))
                    .forEach(dir -> {
                        try {
                            createServerDirectory(dir);
                        } catch (Exception e) {
                            statusCallback.accept("Failed to create directory: " + dir + " - " + e.getMessage());
                        }
                    });
        }

        // Step 3: Upload all files
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
                parentServerId = parentId;
                statusCallback.accept("Warning: parent folder not cached for " + file);
            }

            String fileName = file.getFileName().toString();
            String fileId = UUID.randomUUID().toString();
            long fileSize = Files.size(file);

            FileMetadataDto dto = new FileMetadataDto();
            dto.setFileId(fileId);
            dto.setRelativePath(fileName);
            dto.setSize(fileSize);
            dto.setSha256Hash(FileHasher.computeHash(file));
            dto.setLastModified(Files.getLastModifiedTime(file).toInstant());
            dto.setOwnerId(ownerId);
            dto.setStatus(SyncStatus.SYNCED);
            dto.setFolderId(sharedFolderId);
            dto.setParentId(parentServerId);
            httpClient.createMetadata(dto);

            if (fileSize > 5 * 1024 * 1024) {
                // Use progress callback to update both chunk progress and final file completion
                httpClient.uploadLargeFile(fileId, file, sharedFolderId, (bytesUploaded, totalBytes) -> {
                    Platform.runLater(() -> {
                        // Update progress service with chunk-level detail (optional)
                        progressService.updateMessage(String.format("Uploading %s: %d/%d KB",
                                fileName, bytesUploaded / 1024, totalBytes / 1024));
                        // Note: Do NOT update overall folder progress here; that is updated after file completion
                    });
                });
            } else {
                httpClient.uploadFile(fileId, file, sharedFolderId);
            }

            statusCallback.accept("Uploaded: " + fileName);

            // Update overall folder progress (one file completed)
            int completed = uploadedFiles.incrementAndGet();
            Platform.runLater(() -> progressService.updateProgress(completed, totalFiles));
        } catch (Exception e) {
            statusCallback.accept("Failed: " + file.getFileName() + " - " + e.getMessage());
        }
    }
}