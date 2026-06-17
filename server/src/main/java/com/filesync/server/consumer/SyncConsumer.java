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
        String taskId = syncMessage.getTaskId();
        SyncRequestDto syncRequestDto = syncMessage.getSyncRequestDto();
        log.info("Consumer received sync task {}", taskId);
        UUID folderId = syncRequestDto.getFolderId();
        String ownerId = syncRequestDto.getOwnerId();

        try {
            // Mark task as processing
            SyncTask syncTask = syncTaskRepository.findById(taskId).orElseThrow();
            syncTask.setStatus("PROCESSING");
            syncTask.setUpdatedAt(LocalDateTime.now());
            syncTaskRepository.save(syncTask);
            syncTaskRepository.flush();

            // Fetch server files (ALL files for this user/folder)
            List<FileMetadataEntity> serverFiles;
            if (folderId != null) {
                // Shared folder sync – all files under that folderId
                serverFiles = fileMetadataRepository.findByFolderId(folderId);
            } else {
                // Personal sync – ALL files owned by the user (including subfolders)
                serverFiles = fileMetadataRepository.findByOwnerId(ownerId);
            }

            // Build map: relativePath → FileMetadataDto
            Map<String, FileMetadataDto> serverFileMap = new HashMap<>();
            for (FileMetadataEntity entity : serverFiles) {
                serverFileMap.put(entity.getRelativePath(), convertToDto(entity));
            }

            List<SyncActionDto> actionDtos = new ArrayList<>();

            // Process client files
            for (FileMetadataDto clientFile : syncRequestDto.getClientFiles()) {
                String path = clientFile.getRelativePath();
                FileMetadataDto serverFile = serverFileMap.get(path);

                if (serverFile == null) {
                    // File exists locally but not on server → upload
                    actionDtos.add(new SyncActionDto(SyncActionType.UPLOAD, clientFile, "Client's new file"));
                } else {
                    // File exists on both sides – compare hashes
                    if (Objects.equals(clientFile.getSha256Hash(), serverFile.getSha256Hash())) {
                        actionDtos.add(new SyncActionDto(SyncActionType.NO_ACTION, clientFile, "In sync"));
                    } else {
                        // Different content → conflict (both sides modified)
                        actionDtos.add(new SyncActionDto(SyncActionType.CONFLICT, clientFile, "Both modified"));
                    }
                    serverFileMap.remove(path);
                }
            }

            // Remaining server files → client must download them
            for (FileMetadataDto serverFile : serverFileMap.values()) {
                actionDtos.add(new SyncActionDto(SyncActionType.DOWNLOAD, serverFile, "Server new file"));
            }

            log.info("Sync comparison complete for taskId={}, actions count: {}", taskId, actionDtos.size());

            // Serialize and store actions
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