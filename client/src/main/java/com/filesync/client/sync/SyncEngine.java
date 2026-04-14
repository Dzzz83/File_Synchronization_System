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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

public class SyncEngine {
    private String ownerId;
    private Path syncFolder;
    private FolderScanner scanner;
    private SyncHttpClient httpClient;
    private LocalMetadataRepository localMetadataRepository;

    public SyncEngine(String ownerId, String syncFolderPath, String serverBaseUrl) throws SQLException {
        this.ownerId = ownerId;
        this.syncFolder = Paths.get(syncFolderPath);
        this.localMetadataRepository = new LocalMetadataRepository();
        this.scanner = new FolderScanner(this.syncFolder, this.localMetadataRepository);
        this.httpClient = new SyncHttpClient(serverBaseUrl);
    }

    public void sync() throws IOException, SQLException
    {
        // scan local folder
        List<FileMetadataDto> localFiles = scanner.scan();
        // build request and send to server
        SyncRequestDto requestDto = new SyncRequestDto(ownerId, localFiles);
        SyncResponseDto responseDto = httpClient.sync(requestDto);
        // process each action
        for (SyncActionDto actionDto : responseDto.getActions())
        {
            // get file metadata
            FileMetadataDto file = actionDto.getFileMetadata();
            // get relative path
            Path localPath = syncFolder.resolve(file.getRelativePath());

            switch (actionDto.getAction())
            {
                case UPLOAD:
                    file.setOwnerId(ownerId);
                    httpClient.createMetadata(file);
                    // upload the file
                    httpClient.uploadFile(file.getFileId(), localPath);
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
