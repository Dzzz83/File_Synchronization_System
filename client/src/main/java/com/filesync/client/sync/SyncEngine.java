package com.filesync.client.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.filesync.client.conflict.ConflictResolver;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.dto.SyncActionDto;
import com.filesync.common.dto.SyncRequestDto;
import com.filesync.common.dto.SyncResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SyncEngine {
    private static final Logger log = LoggerFactory.getLogger(SyncEngine.class);
    private final SyncHttpClient httpClient;
    private final String ownerId;
    private final Path syncFolder;
    private final UUID folderId;
    private final FolderScanner scanner;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public SyncEngine(SyncHttpClient httpClient, String ownerId, String syncFolderPath, UUID folderId) {
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.syncFolder = Paths.get(syncFolderPath);
        this.folderId = folderId;
        this.scanner = new FolderScanner(this.syncFolder);
    }

    public void sync() throws Exception {
        List<FileMetadataDto> localFiles = scanner.scan();
        SyncRequestDto syncRequestDto = new SyncRequestDto(ownerId, localFiles, folderId);
        String taskId = httpClient.startSync(syncRequestDto);
        log.info("Sync task started: {}", taskId);

        SyncResponseDto response = null;
        while (true) {
            Thread.sleep(2000);
            Map<String, Object> status = httpClient.getSyncStatus(taskId);
            String state = (String) status.get("status");
            if ("COMPLETED".equals(state)) {
                Object actionsObj = status.get("actions");
                if (actionsObj instanceof List) {
                    String json = objectMapper.writeValueAsString(actionsObj);
                    List<SyncActionDto> actionDtos = objectMapper.readValue(json,
                            new TypeReference<List<SyncActionDto>>() {});
                    response = new SyncResponseDto(actionDtos);
                } else {
                    response = new SyncResponseDto(List.of());
                }
                break;
            } else if ("FAILED".equals(state)) {
                String error = (String) status.get("errorMessage");
                throw new RuntimeException("Sync failed: " + error);
            }
        }

        for (SyncActionDto actionDto : response.getActions()) {
            FileMetadataDto file = actionDto.getFileMetadata();
            Path localPath = syncFolder.resolve(file.getRelativePath());

            switch (actionDto.getAction()) {
                case UPLOAD:
                    try {
                        file.setOwnerId(ownerId);
                        file.setFolderId(folderId);
                        httpClient.createMetadata(file);

                        long fileSize = Files.size(localPath);
                        long THRESHOLD = 5 * 1024 * 1024;
                        if (fileSize > THRESHOLD) {
                            httpClient.uploadLargeFile(file.getFileId(), localPath, folderId, null);
                        } else {
                            httpClient.uploadFile(file.getFileId(), localPath, folderId);
                        }
                        log.info("Uploaded {}", file.getRelativePath());
                    } catch (Exception e) {
                        // Compensating transaction: roll back the metadata
                        log.error("Upload failed for {}, deleting metadata to restore consistency", file.getRelativePath(), e);
                        try {
                            httpClient.deleteFile(file.getFileId(), folderId);
                        } catch (Exception deleteEx) {
                            log.error("Failed to delete orphaned metadata for fileId {}: {}", file.getFileId(), deleteEx.getMessage());
                        }
                        throw e;
                    }
                    break;

                case DOWNLOAD:
                    try {
                        httpClient.downloadFile(file.getFileId(), localPath);
                        log.info("Downloaded {}", file.getRelativePath());
                    } catch (Exception e) {
                        boolean is4xx = false;
                        if (e instanceof WebClientResponseException) {
                            WebClientResponseException wce = (WebClientResponseException) e;
                            if (wce.getStatusCode().is4xxClientError()) is4xx = true;
                        } else if (e.getCause() instanceof WebClientResponseException) {
                            WebClientResponseException wce = (WebClientResponseException) e.getCause();
                            if (wce.getStatusCode().is4xxClientError()) is4xx = true;
                        }
                        if (is4xx) {
                            log.warn("File not found on server ({}), skipping download", file.getRelativePath());
                            if (Files.exists(localPath)) {
                                Files.delete(localPath);
                                log.info("Deleted local copy of {}", file.getRelativePath());
                            }
                            break;
                        } else {
                            throw e;
                        }
                    }
                    break;

                case CONFLICT:
                    try {
                        ConflictResolver.resolve(file, localPath, httpClient);
                    } catch (Exception e) {
                        boolean is4xx = false;
                        if (e instanceof WebClientResponseException) {
                            WebClientResponseException wce = (WebClientResponseException) e;
                            if (wce.getStatusCode().is4xxClientError()) is4xx = true;
                        } else if (e.getCause() instanceof WebClientResponseException) {
                            WebClientResponseException wce = (WebClientResponseException) e.getCause();
                            if (wce.getStatusCode().is4xxClientError()) is4xx = true;
                        }
                        if (is4xx) {
                            log.warn("File not found on server ({}), skipping conflict resolution", file.getRelativePath());
                            if (Files.exists(localPath)) {
                                Files.delete(localPath);
                                log.info("Deleted local copy of {}", file.getRelativePath());
                            }
                            break;
                        } else {
                            throw e;
                        }
                    }
                    break;

                case NO_ACTION:
                    break;
            }
        }
    }
}