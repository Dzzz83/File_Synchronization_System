package com.filesync.client.task;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.SyncStatus;
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

    public UploadTask(SyncHttpClient httpClient, String ownerId, UUID folderId, UUID parentId, Path localFile) {
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.folderId = folderId;
        this.parentId = parentId;
        this.localFile = localFile;
        this.fileName = localFile.getFileName().toString();
        this.fileSize = localFile.toFile().length();
    }

    @Override
    protected Void call() throws Exception {
        updateMessage("Uploading " + fileName + "...");
        updateProgress(0, fileSize);

        String fileId = UUID.randomUUID().toString();
        FileMetadataDto dto = new FileMetadataDto();
        dto.setFileId(fileId);
        dto.setRelativePath(fileName);
        dto.setSize(fileSize);
        dto.setSha256Hash(computeHash(localFile));
        dto.setLastModified(Files.getLastModifiedTime(localFile).toInstant());
        dto.setOwnerId(ownerId);
        dto.setStatus(SyncStatus.SYNCED);
        dto.setFolderId(folderId);
        dto.setParentId(parentId);
        httpClient.createMetadata(dto);

        if (fileSize > 5 * 1024 * 1024) {
            // TODO: Enhance ChunkedUploader to report progress (optional)
            httpClient.uploadLargeFile(fileId, localFile, folderId);
        } else {
            httpClient.uploadFile(fileId, localFile, folderId);
        }

        updateProgress(fileSize, fileSize);
        updateMessage("Upload complete");
        return null;
    }

    private String computeHash(Path file) throws IOException {
        // reuse existing FileHasher.computeHash
        return com.filesync.client.file.FileHasher.computeHash(file);
    }
}