package com.filesync.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filesync.common.dto.FolderUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class FolderEventRedisListener implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(FolderEventRedisListener.class);
    private final FolderEventPublisher folderEventPublisher;
    private final ObjectMapper objectMapper;

    public FolderEventRedisListener(FolderEventPublisher folderEventPublisher, ObjectMapper objectMapper) {
        this.folderEventPublisher = folderEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            FolderUpdateMessage folderUpdate = objectMapper.readValue(json, FolderUpdateMessage.class);
            if (folderUpdate == null) return;
            log.info(" Received folder event from Redis: {} - {}", folderUpdate.getEventType(), folderUpdate.getFolderId());
            folderEventPublisher.broadcastLocally(folderUpdate);
        } catch (Exception e) {
            log.error("Failed to process Redis folder event", e);
        }
    }
}