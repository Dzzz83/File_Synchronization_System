package com.filesync.client.sync;

import com.filesync.client.conflict.ConflictResolver;
import com.filesync.client.db.LocalMetadataRepository;
import com.filesync.client.file.FolderScanner;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.dto.SyncActionDto;
import com.filesync.common.dto.SyncRequestDto;
import com.filesync.common.dto.SyncResponseDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SyncEngine {
    private String ownerId;
    private Path syncFolder;
    private FolderScanner scanner;
    private SyncHttpClient httpClient;
    private LocalMetadataRepository localMetadataRepository;
    private UUID folderId;

    public SyncEngine(String ownerId, String syncFolderPath, String serverBaseUrl, UUID folderId) throws SQLException {
        this.ownerId = ownerId;
        this.syncFolder = Paths.get(syncFolderPath);
        this.folderId = folderId;
        this.localMetadataRepository = new LocalMetadataRepository();
        this.scanner = new FolderScanner(this.syncFolder, this.localMetadataRepository);
        this.httpClient = new SyncHttpClient(serverBaseUrl);
    }

    public void sync() throws IOException, SQLException, InterruptedException {
        // scan local folder
        List<FileMetadataDto> localFiles = scanner.scan();

        // start async
        SyncRequestDto syncRequestDto = new SyncRequestDto(ownerId, localFiles, folderId);
        String taskId = httpClient.startSync(syncRequestDto);
        System.out.println("Sync task started: " + taskId);

        // checks until the task is completed
        SyncResponseDto response = null;
        while (true)
        {
            // wait 2 secs
            Thread.sleep(2000);
            // get the status
            Map<String, Object> status = httpClient.getSyncStatus(taskId);
            // convert to string
            String state = (String) status.get("status");

            if ("COMPLETED".equals(state))
            {
                // get the actions
                @SuppressWarnings("unchecked")
                List<SyncActionDto> actionDtos = (List<SyncActionDto>) status.get("actions");
                response = new SyncResponseDto(actionDtos);
                break;
            }
            else if ("FAILED".equals(state))
            {
                String error = (String) status.get("errorMessage");
                throw new RuntimeException("Sync failed: " + error);
            }
        }

        for (SyncActionDto actionDto : response.getActions())
        {
            // get file metadata
            FileMetadataDto file = actionDto.getFileMetadata();
            // get relative path
            Path localPath = syncFolder.resolve(file.getRelativePath());

            switch (actionDto.getAction())
            {
                case UPLOAD:
                    file.setOwnerId(ownerId);
                    file.setFolderId(folderId);
                    httpClient.createMetadata(file);

                    long fileSize = Files.size(localPath);
                    long THRESHOLD = 5 * 1024 * 1024;

                    if (fileSize > THRESHOLD)
                    {
                        // upload the large file
                        System.out.println("Large file detected, using chunked upload for: " + file.getRelativePath());
                        httpClient.uploadLargeFile(file.getFileId(), localPath, folderId, null);
                    }
                    else
                    {
                        // upload small file
                        httpClient.uploadFile(file.getFileId(), localPath, folderId);
                    }
                    // save the file
                    localMetadataRepository.saveFile(file.getRelativePath(), file.getFileId(), file.getSha256Hash());
                    System.out.println("Uploaded " + file.getRelativePath());
                    break;
                case DOWNLOAD:
                    // download the file
                    httpClient.downloadFile(file.getFileId(), localPath);
                    localMetadataRepository.saveFile(file.getRelativePath(), file.getFileId(), file.getSha256Hash());
                    System.out.println("Downloaded " + file.getRelativePath());
                    break;
                case CONFLICT:
                    ConflictResolver.resolve(file, localPath, httpClient, localMetadataRepository);
                    break;
                case NO_ACTION:
                    break;
            }

        }
    }
}