package com.filesync.client.service;

import com.filesync.client.conflict.ConflictResolver;
import com.filesync.client.file.FileHasher;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.SyncStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class FileOperationService {
    private final SyncHttpClient httpClient;
    private final String ownerId;
    private final UUID folderId;

    public FileOperationService(SyncHttpClient httpClient, String ownerId, UUID folderId) {
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.folderId = folderId;
    }

    public List<FileMetadataDto> listFiles() {
        return httpClient.getFiles(ownerId, folderId, null);
    }

    public void deleteFile(String fileId) {
        httpClient.deleteFile(fileId, folderId);
    }

    public void downloadFile(String fileId, Path destination) throws IOException {
        httpClient.downloadFile(fileId, destination);
    }

    public void uploadFile(Path localFilePath, UUID parentId) throws IOException {
        String fileName = localFilePath.getFileName().toString();
        String fileId = UUID.randomUUID().toString();
        long fileSize = Files.size(localFilePath);
        long threshold = 5 * 1024 * 1024;

        FileMetadataDto dto = new FileMetadataDto();
        dto.setFileId(fileId);
        dto.setRelativePath(fileName);
        dto.setSize(fileSize);
        dto.setSha256Hash(FileHasher.computeHash(localFilePath));
        dto.setLastModified(Files.getLastModifiedTime(localFilePath).toInstant());
        dto.setOwnerId(ownerId);
        dto.setStatus(SyncStatus.SYNCED);
        dto.setFolderId(folderId);
        dto.setParentId(parentId);

        httpClient.createMetadata(dto);

        if (fileSize > threshold) {
            httpClient.uploadLargeFile(fileId, localFilePath, folderId);
        } else {
            httpClient.uploadFile(fileId, localFilePath, folderId);
        }
    }

    public void editFile(FileMetadataDto fileDto, String newContent) throws IOException {
        Path tempFile = Files.createTempFile("edited_", ".tmp");
        try {
            Files.writeString(tempFile, newContent);
            String newHash = FileHasher.computeHash(tempFile);
            FileMetadataDto updatedDto = new FileMetadataDto();
            updatedDto.setFileId(fileDto.getFileId());
            updatedDto.setRelativePath(fileDto.getRelativePath());
            updatedDto.setSize(Files.size(tempFile));
            updatedDto.setSha256Hash(newHash);
            updatedDto.setLastModified(Instant.now());
            updatedDto.setOwnerId(ownerId);
            updatedDto.setStatus(SyncStatus.SYNCED);
            updatedDto.setFolderId(folderId);
            updatedDto.setParentId(fileDto.getParentId());

            httpClient.createMetadata(updatedDto);

            long fileSize = Files.size(tempFile);
            if (fileSize > 5 * 1024 * 1024) {
                httpClient.uploadLargeFile(fileDto.getFileId(), tempFile, folderId);
            } else {
                httpClient.uploadFile(fileDto.getFileId(), tempFile, folderId);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public void resolveConflict(FileMetadataDto fileDto, Path localPath, FileMetadataDto currentMeta) throws IOException {
        ConflictResolver.resolve(fileDto, localPath, httpClient, null);
    }

    public FileMetadataDto getMetadata(String fileId) {
        return httpClient.getFileMetadata(fileId);
    }

    public void logout() {
        httpClient.logout();
    }

    public void close() {
        httpClient.close();
    }
}