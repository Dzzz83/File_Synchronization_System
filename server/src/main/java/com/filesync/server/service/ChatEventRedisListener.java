package com.filesync.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filesync.common.dto.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class ChatEventRedisListener implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(ChatEventRedisListener.class);
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public ChatEventRedisListener(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            ChatMessage chatMessage = objectMapper.readValue(json, ChatMessage.class);
            if (chatMessage == null) return;

            String destination = "/topic/folder/" + chatMessage.getFolderId().toString();
            log.info("Broadcasting chat message locally: {} to {}", chatMessage.getSender(), destination);
            messagingTemplate.convertAndSend(destination, chatMessage);
        } catch (Exception e) {
            log.error("Failed to process Redis chat event", e);
        }
    }
}