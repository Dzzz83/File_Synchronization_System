package com.filesync.client.service;

import com.filesync.client.conflict.ConflictResolver;
import com.filesync.client.file.FileHasher;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.SyncStatus;
import javafx.application.Platform;

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

    // FIXED: Added ProgressService integration
    public void uploadFile(Path localFilePath, UUID parentId) throws IOException {
        ProgressService ps = ProgressService.getInstance();
        String fileName = localFilePath.getFileName().toString();
        Platform.runLater(() -> ps.startOperation("Uploading " + fileName));

        try {
            String fileId = UUID.randomUUID().toString();
            long fileSize = Files.size(localFilePath);
            long threshold = 5 * 1024 * 1024;

            // Inside uploadFile method
            FileMetadataDto dto = FileMetadataDto.forUpload(
                    fileId, fileName, fileSize,
                    FileHasher.computeHash(localFilePath),
                    Files.getLastModifiedTime(localFilePath).toInstant(),
                    ownerId, folderId, parentId
            );
            httpClient.createMetadata(dto);

            if (fileSize > threshold) {
                httpClient.uploadLargeFile(fileId, localFilePath, folderId, (bytesUploaded, totalBytes) -> {
                    Platform.runLater(() -> {
                        ps.updateProgress(bytesUploaded, totalBytes);
                        ps.updateMessage(String.format("Uploading %s: %d / %d KB",
                                fileName, bytesUploaded / 1024, totalBytes / 1024));
                    });
                });
            } else {
                httpClient.uploadFile(fileId, localFilePath, folderId);
                Platform.runLater(() -> ps.updateProgress(fileSize, fileSize));
            }
        } finally {
            Platform.runLater(ps::finishOperation);
        }
    }

    // FIXED: Added ProgressService integration
    public void editFile(FileMetadataDto fileDto, String newContent) throws IOException {
        ProgressService ps = ProgressService.getInstance();
        String fileName = fileDto.getRelativePath();
        Platform.runLater(() -> ps.startOperation("Saving " + fileName));

        Path tempFile = Files.createTempFile("edited_", ".tmp");
        try {
            Files.writeString(tempFile, newContent);
            String newHash = FileHasher.computeHash(tempFile);
            FileMetadataDto updatedDto = FileMetadataDto.forUpload(
                    fileDto.getFileId(),
                    fileName,
                    Files.size(tempFile),
                    newHash,
                    Instant.now(),
                    ownerId,
                    folderId,
                    fileDto.getParentId()
            );

            httpClient.createMetadata(updatedDto);

            long fileSize = Files.size(tempFile);
            if (fileSize > 5 * 1024 * 1024) {
                httpClient.uploadLargeFile(fileDto.getFileId(), tempFile, folderId, (bytesUploaded, totalBytes) -> {
                    Platform.runLater(() -> {
                        ps.updateProgress(bytesUploaded, totalBytes);
                        ps.updateMessage(String.format("Saving %s: %d / %d KB",
                                fileName, bytesUploaded / 1024, totalBytes / 1024));
                    });
                });
            } else {
                httpClient.uploadFile(fileDto.getFileId(), tempFile, folderId);
                Platform.runLater(() -> ps.updateProgress(fileSize, fileSize));
            }
        } finally {
            Files.deleteIfExists(tempFile);
            Platform.runLater(ps::finishOperation);
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