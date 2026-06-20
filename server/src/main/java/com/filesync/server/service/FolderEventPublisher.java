package com.filesync.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filesync.common.dto.FolderUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FolderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(FolderEventPublisher.class);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private static final String REDIS_FOLDER_CHANNEL = "folder-events";

    public FolderEventPublisher(StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                SimpMessagingTemplate messagingTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    public void publishFolderEvent(String eventType, UUID folderId, String folderName, String ownerId) {
        FolderUpdateMessage message = new FolderUpdateMessage(eventType, folderId, folderName, ownerId);
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(REDIS_FOLDER_CHANNEL, json);
            log.info(" Published folder event to Redis: {} - {}", eventType, folderId);
        } catch (Exception e) {
            log.error("Failed to publish folder event", e);
        }
    }

    public void broadcastLocally(FolderUpdateMessage message) {
        messagingTemplate.convertAndSend("/topic/folders", message);
        log.info("Broadcast folder event locally: {} - {}", message.getEventType(), message.getFolderId());
    }
}