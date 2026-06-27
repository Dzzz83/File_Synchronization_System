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
public class SyncConsumer {
    private static final Logger log = LoggerFactory.getLogger(SyncConsumer.class);
    private final FileMetadataRepository fileMetadataRepository;
    private final SyncTaskRepository syncTaskRepository;
    private final ObjectMapper objectMapper;
    private final SyncTaskStatusService syncTaskStatusService;

    public SyncConsumer(FileMetadataRepository fileMetadataRepository,
                        SyncTaskRepository syncTaskRepository,
                        ObjectMapper objectMapper,
                        SyncTaskStatusService syncTaskStatusService) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.syncTaskRepository = syncTaskRepository;
        this.objectMapper = objectMapper;
        this.syncTaskStatusService = syncTaskStatusService;
    }

    @RabbitListener(queues = "${sync.queue.name:sync.queue}")
    public void processSync(SyncMessage syncMessage) {
        // get the task from rabbitMQ
        String taskId = syncMessage.getTaskId();
        SyncRequestDto syncRequestDto = syncMessage.getSyncRequestDto();
        log.info("Consumer received sync task {}", taskId);
        UUID folderId = syncRequestDto.getFolderId();
        String ownerId = syncRequestDto.getOwnerId();

        try {
            // Mark as processing
            SyncTask syncTask = syncTaskRepository.findById(taskId).orElseThrow();
            syncTask.setStatus("PROCESSING");
            syncTask.setUpdatedAt(LocalDateTime.now());
            syncTaskRepository.save(syncTask);
            syncTaskRepository.flush();

            // Fetch server files (shared or personal)
            List<FileMetadataEntity> serverFiles;
            if (folderId != null) {
                serverFiles = fileMetadataRepository.findByFolderId(folderId);
            } else {
                serverFiles = fileMetadataRepository.findByOwnerId(ownerId);
            }

            // Index server files by relative path
            Map<String, FileMetadataDto> serverFileMap = new HashMap<>();
            for (FileMetadataEntity entity : serverFiles) {
                serverFileMap.put(entity.getRelativePath(), convertToDto(entity));
            }

            List<SyncActionDto> actionDtos = new ArrayList<>();

            // Compare each client file against the server state
            for (FileMetadataDto clientFile : syncRequestDto.getClientFiles()) {
                String path = clientFile.getRelativePath();
                FileMetadataDto serverFile = serverFileMap.get(path);

                if (serverFile == null) {
                    actionDtos.add(new SyncActionDto(SyncActionType.UPLOAD, clientFile, "Client's new file"));
                } else {
                    if (Objects.equals(clientFile.getSha256Hash(), serverFile.getSha256Hash())) {
                        actionDtos.add(new SyncActionDto(SyncActionType.NO_ACTION, clientFile, "In sync"));
                    } else {
                        actionDtos.add(new SyncActionDto(SyncActionType.CONFLICT, clientFile, "Both modified"));
                    }
                    serverFileMap.remove(path);
                }
            }

            // Remaining server files → download
            for (FileMetadataDto serverFile : serverFileMap.values()) {
                actionDtos.add(new SyncActionDto(SyncActionType.DOWNLOAD, serverFile, "Server new file"));
            }

            log.info("Sync comparison complete for taskId={}, actions count: {}", taskId, actionDtos.size());

            // Serialize actions and mark task completed
            String actionJson = objectMapper.writeValueAsString(actionDtos);
            syncTask.setActionsJson(actionJson);
            syncTask.setStatus("COMPLETED");
            syncTask.setUpdatedAt(LocalDateTime.now());
            syncTaskRepository.save(syncTask);
            syncTaskRepository.flush();
            log.info("Async sync COMPLETED for taskId={}", taskId);

        } catch (Throwable e) {
            log.error("Async sync FAILED for taskId={}", taskId, e);
            syncTaskStatusService.markFailed(taskId, e.getMessage());
        }
    }

    private FileMetadataDto convertToDto(FileMetadataEntity entity) {
        FileMetadataDto dto = new FileMetadataDto();
        dto.setFileId(entity.getId());
        dto.setRelativePath(entity.getRelativePath());
        dto.setSha256Hash(entity.getSha256Hash());
        dto.setSize(entity.getSize());
        dto.setLastModified(entity.getLastModified());
        dto.setOwnerId(entity.getOwnerId());
        dto.setSharedWith(new HashSet<>(entity.getSharedWith()));
        dto.setStatus(entity.getStatus());
        dto.setFolderId(entity.getFolderId());
        dto.setDirectory(entity.isDirectory());
        dto.setParentId(entity.getParentId());
        return dto;
    }
}