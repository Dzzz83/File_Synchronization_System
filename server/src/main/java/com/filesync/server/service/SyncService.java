package com.filesync.server.service;

import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.dto.SyncActionDto;
import com.filesync.common.dto.SyncRequestDto;
import com.filesync.common.dto.SyncResponseDto;
import com.filesync.common.enums.SyncActionType;
import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SyncService {
    private final FileMetadataRepository repository;

    public SyncService(FileMetadataRepository repository)
    {
        this.repository = repository;
    }

    private FileMetadataDto convertToDto(FileMetadataEntity entity)
    {
        return new FileMetadataDto(
                entity.getId(),
                entity.getRelativePath(),
                entity.getSha256Hash(),
                entity.getSize(),
                entity.getLastModified(),
                entity.getVersionVectorJson(),
                entity.getOwnerId(),
                entity.getSharedWith(),
                entity.getStatus()
        );
    }
    public SyncResponseDto sync(SyncRequestDto request)
    {
        List<FileMetadataEntity> serverFiles = repository.findByOwnerId(request.getOwnerId());

        // convert to map
        Map<String, FileMetadataDto> serverFileMap = new HashMap<>();
        for (FileMetadataEntity entity : serverFiles)
        {
            serverFileMap.put(entity.getRelativePath(), convertToDto(entity));
        }

        List<SyncActionDto> actions = new ArrayList<>();
        for (FileMetadataDto clientFile : request.getClientFiles())
        {
            // get file path of client file
            String filePath = clientFile.getRelativePath();
            // look up in service file map to find the server file
            FileMetadataDto serverFile = serverFileMap.get(filePath);
            if (serverFile == null)
            {
                // file is not on server, client must upload their file
                actions.add(new SyncActionDto(SyncActionType.UPLOAD, clientFile, "Client's new file"));
            }
            else
            {
                // compare hash
                boolean isHashEqual = (clientFile.getSha256Hash().equals(serverFile.getSha256Hash()));
                if (isHashEqual)
                {
                    // do nothing
                    actions.add(new SyncActionDto(SyncActionType.NO_ACTION, clientFile, "In Sync"));
                }
                else
                {
                    // hash not equal ==> conflict
                    actions.add(new SyncActionDto(SyncActionType.CONFLICT, clientFile, "Both is modified"));
                }
                serverFileMap.remove(filePath);
            }
        }

        for (FileMetadataDto serverFile : serverFileMap.values())
        {
            actions.add(new SyncActionDto(SyncActionType.DOWNLOAD, serverFile, "Server new file"));
        }
        return new SyncResponseDto(actions);
    }
}
