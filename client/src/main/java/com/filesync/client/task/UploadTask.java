package com.filesync.client.task;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.service.ProgressService;
import com.filesync.common.dto.FileMetadataDto;
import javafx.application.Platform;
import javafx.concurrent.Task;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public class UploadTask extends Task<Void> {
    private final SyncHttpClient httpClient;
    private final String ownerId;
    private final UUID folderId;
    private final UUID parentId;
    private final Path localFile;
    private final String fileName;
    private final long fileSize;

    // Original constructor (uses local file's name)
    public UploadTask(SyncHttpClient httpClient, String ownerId, UUID folderId, UUID parentId, Path localFile) {
        this(httpClient, ownerId, folderId, parentId, localFile, localFile.getFileName().toString());
    }

    // New constructor that accepts a custom file name
    public UploadTask(SyncHttpClient httpClient, String ownerId, UUID folderId, UUID parentId, Path localFile, String customFileName) {
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.folderId = folderId;
        this.parentId = parentId;
        this.localFile = localFile;
        this.fileName = customFileName;
        this.fileSize = localFile.toFile().length();
    }

    @Override
    protected Void call() throws Exception {
        ProgressService ps = ProgressService.getInstance();

        Platform.runLater(() -> ps.startOperation("Uploading " + fileName + "..."));

        try {
            updateMessage("Uploading " + fileName + "...");
            updateProgress(0, fileSize);

            String fileId = UUID.randomUUID().toString();
            FileMetadataDto dto = FileMetadataDto.forUpload(
                    fileId, fileName, fileSize,
                    computeHash(localFile),
                    Files.getLastModifiedTime(localFile).toInstant(),
                    ownerId, folderId, parentId
            );
            httpClient.createMetadata(dto);

            if (fileSize > 5 * 1024 * 1024) {
                httpClient.uploadLargeFile(fileId, localFile, folderId, (bytesUploaded, totalBytes) -> {
                    updateProgress(bytesUploaded, totalBytes);
                    Platform.runLater(() -> {
                        ps.updateProgress(bytesUploaded, totalBytes);
                        ps.updateMessage(String.format("Uploading %s: %d / %d KB",
                                fileName, bytesUploaded / 1024, totalBytes / 1024));
                    });
                });
            } else {
                httpClient.uploadFile(fileId, localFile, folderId);
                updateProgress(fileSize, fileSize);
                Platform.runLater(() -> {
                    ps.updateProgress(fileSize, fileSize);
                    ps.updateMessage("Upload complete");
                });
            }

            updateProgress(fileSize, fileSize);
            updateMessage("Upload complete");
            return null;
        } finally {
            Platform.runLater(ps::finishOperation);
        }
    }

    private String computeHash(Path file) throws IOException {
        return com.filesync.client.file.FileHasher.computeHash(file);
    }
}