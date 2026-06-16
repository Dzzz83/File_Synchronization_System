package com.filesync.server.service;

import com.filesync.common.dto.FileUpdateMessage;
import com.filesync.server.domain.FileMetadataEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class FileEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(FileEventPublisher.class);
    private final SimpMessagingTemplate messagingTemplate;

    public FileEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishFileEvent(FileMetadataEntity file, String eventType) {
        if (file.getFolderId() == null) {
            log.debug("Skipping WebSocket event for personal file: {}", file.getId());
            return;
        }
        FileUpdateMessage message = new FileUpdateMessage(
                file.getId(),
                file.getRelativePath(),
                eventType,
                file.isDirectory(),
                file.getSize()
        );
        String destination = "/topic/file/" + file.getFolderId().toString();
        log.info("🔔 Publishing file event: {} to {} for fileId {}", eventType, destination, file.getId());
        messagingTemplate.convertAndSend(destination, message);
        log.info("✅ Published file event: {} to {}", eventType, destination);
    }
}