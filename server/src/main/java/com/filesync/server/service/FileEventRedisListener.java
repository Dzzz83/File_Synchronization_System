package com.filesync.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filesync.common.dto.FileUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class FileEventRedisListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(FileEventRedisListener.class);
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public FileEventRedisListener(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            FileUpdateMessage event = objectMapper.readValue(json, FileUpdateMessage.class);
            if (event == null) return;

            String destination = "/topic/file/" + event.getFolderId().toString();
            log.info("📨 Broadcasting file event locally: {} to {}", event.getEventType(), destination);
            log.info("📥 Received file event from Redis: {}", event);
            messagingTemplate.convertAndSend(destination, event);
        } catch (IOException e) {
            log.error("Failed to deserialize Redis file event", e);
        } catch (Exception e) {
            log.error("Failed to process Redis file event", e);
        }
    }
}