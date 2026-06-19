package com.filesync.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filesync.common.dto.FileUpdateMessage;
import com.filesync.server.domain.FileMetadataEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class FileEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(FileEventPublisher.class);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String REDIS_FILE_EVENT_CHANNEL = "file-events";

    public FileEventPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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
                file.getSize(),
                file.getFolderId()
        );

        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(REDIS_FILE_EVENT_CHANNEL, json);
            log.info("Publishing file event to Redis: fileId={}, folderId={}, eventType={}",
                    file.getId(), file.getFolderId(), eventType);
            log.info("Published file event to Redis: {} for fileId {}", eventType, file.getId());
        } catch (Exception e) {
            log.error("Failed to serialize file event for fileId {}", file.getId(), e);
        }
    }
}