package com.filesync.server.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.dto.SyncActionDto;
import com.filesync.common.dto.SyncRequestDto;
import com.filesync.common.enums.SyncActionType;
import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.domain.SyncTask;
import com.filesync.server.dto.SyncMessage;
import com.filesync.server.repository.FileMetadataRepository;
import com.filesync.server.repository.SyncTaskRepository;
import com.filesync.server.service.SyncTaskStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class SyncConsumer
{
    private static final Logger log = LoggerFactory.getLogger(SyncConsumer.class);
    private final FileMetadataRepository fileMetadataRepository;
    private final SyncTaskRepository syncTaskRepository;
    private final ObjectMapper objectMapper;
    private final SyncTaskStatusService syncTaskStatusService;

    public SyncConsumer(FileMetadataRepository fileMetadataRepository, SyncTaskRepository syncTaskRepository,
                        ObjectMapper objectMapper, SyncTaskStatusService syncTaskStatusService)
    {
        this.fileMetadataRepository = fileMetadataRepository;
        this.syncTaskRepository = syncTaskRepository;
        this.objectMapper = objectMapper;
        this.syncTaskStatusService = syncTaskStatusService;
    }

    @RabbitListener(queues = "${sync.queue.name:sync.queue}")
    public void processSync(SyncMessage syncMessage)
    {
        String taskId = syncMessage.getTaskId();
        SyncRequestDto syncRequestDto = syncMessage.getSyncRequestDto();
        log.info("Consumer received sync task {}", taskId);
        UUID folderId = syncRequestDto.getFolderId();

        try
        {
            // mark as processing
            SyncTask syncTask = syncTaskRepository.findById(taskId).orElseThrow();
            syncTask.setStatus("PROCESSING");
            syncTask.setUpdatedAt(LocalDateTime.now());
            syncTaskRepository.save(syncTask);
            syncTaskRepository.flush();

            // get the list of files
            List<FileMetadataEntity> serverFiles = fileMetadataRepository.findByOwnerId(syncRequestDto.getOwnerId());

            if (folderId != null) {
                // shared folder files
                serverFiles = fileMetadataRepository.findByFolderId(folderId);
            } else {
                // personal files
                serverFiles = fileMetadataRepository.findByOwnerIdAndFolderIdIsNull(syncRequestDto.getOwnerId());
            }

            // create a map to store the server files
            Map<String, FileMetadataDto> serverFileMap = new HashMap<>();
            for (FileMetadataEntity entity : serverFiles)
            {
                serverFileMap.put(entity.getRelativePath(), convertToDto(entity));
            }

            List<SyncActionDto> actionDtos = new ArrayList<>();
            for (FileMetadataDto clientFile : syncRequestDto.getClientFiles())
            {
                String path = clientFile.getRelativePath();
                FileMetadataDto serverFile = serverFileMap.get(path);

                if (serverFile == null)
                {
                    actionDtos.add(new SyncActionDto(SyncActionType.UPLOAD, clientFile, "Client's new file"));
                }
                else
                {
                    if (clientFile.getSha256Hash().equals(serverFile.getSha256Hash()))
                    {
                        actionDtos.add(new SyncActionDto(SyncActionType.NO_ACTION, clientFile, "In Sync"));
                    }
                    else
                    {
                        actionDtos.add(new SyncActionDto(SyncActionType.CONFLICT, clientFile, "Both modified"));
                    }
                    serverFileMap.remove(path);
                }
            }
            for (FileMetadataDto serverFile : serverFileMap.values())
            {
                actionDtos.add(new SyncActionDto(SyncActionType.DOWNLOAD, serverFile, "Server new file"));
            }

            log.info("Sync comparison complete for taskId={}, actions count: {}", taskId, actionDtos.size());

            // store results as json
            String actionJson = objectMapper.writeValueAsString(actionDtos);
            syncTask.setActionsJson(actionJson);
            syncTask.setStatus("COMPLETED");
            syncTask.setUpdatedAt(LocalDateTime.now());
            syncTaskRepository.save(syncTask);
            syncTaskRepository.flush();
            log.info("=== ASYNC SYNC COMPLETED for taskId={}", taskId);

        } catch (Throwable e) {
            log.error("Async sync FAILED for taskId=" + taskId, e);
            syncTaskStatusService.markFailed(taskId, e.getMessage());
        }
    }

    private FileMetadataDto convertToDto(FileMetadataEntity entity) {
        System.out.println("[DEBUG] convertToDto for path: " + entity.getRelativePath()); // debug
        Set<String> sharedCopy = new HashSet<>(entity.getSharedWith());
        System.out.println("[DEBUG] sharedWith size: " + sharedCopy.size()); // debug
        return new FileMetadataDto(
                entity.getId(),
                entity.getRelativePath(),
                entity.getSha256Hash(),
                entity.getSize(),
                entity.getLastModified(),
                entity.getVersionVectorJson(),
                entity.getOwnerId(),
                sharedCopy,
                entity.getStatus()
        );
    }
}
