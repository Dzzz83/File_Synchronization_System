package com.filesync.server.websocket.controller;

import com.filesync.common.dto.ChatMessage;
import com.filesync.server.domain.ChatMessageEntity;
import com.filesync.server.repository.ChatMessageRepository;
import com.filesync.server.websocket.service.ActiveUserService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Controller
public class ChatController {

    private final ActiveUserService activeUserService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final Map<String, Set<UUID>> sessionSubscriptions = new ConcurrentHashMap<>();

    public ChatController(ActiveUserService activeUserService,
                          SimpMessagingTemplate messagingTemplate,
                          ChatMessageRepository chatMessageRepository) {
        this.activeUserService = activeUserService;
        this.messagingTemplate = messagingTemplate;
        this.chatMessageRepository = chatMessageRepository;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(ChatMessage message) {
        Instant now = Instant.now();
        message.setTimestamp(now);

        // Save message to database
        ChatMessageEntity entity = new ChatMessageEntity(
                message.getFolderId(),
                message.getSender(),
                message.getContent(),
                now
        );
        chatMessageRepository.save(entity);

        // Refresh the sender's active timestamp (keeps them from being cleaned up)
        activeUserService.userJoined(message.getFolderId(), message.getSender());

        // Broadcast to all subscribers of this folder
        messagingTemplate.convertAndSend("/topic/folder/" + message.getFolderId().toString(), message);
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (destination != null && destination.startsWith("/topic/folder/")) {
            String[] parts = destination.split("/");
            if (parts.length >= 4) {
                try {
                    UUID folderId = UUID.fromString(parts[3]);
                    Principal principal = accessor.getUser();
                    String username = principal != null ? principal.getName() : null;
                    String sessionId = accessor.getSessionId();
                    if (username != null && sessionId != null) {
                        sessionSubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(folderId);
                        // Add user with current timestamp (updates if already present)
                        activeUserService.userJoined(folderId, username);
                        messagingTemplate.convertAndSend("/topic/folder/" + folderId + "/active",
                                activeUserService.getActiveUsers(folderId));

                        // Send last 100 messages as history
                        List<ChatMessageEntity> recent = chatMessageRepository.findTop100ByFolderIdOrderByTimestampDesc(folderId);
                        List<ChatMessage> history = recent.stream()
                                .map(e -> new ChatMessage(e.getSender(), e.getContent(), e.getTimestamp(), e.getFolderId()))
                                .collect(Collectors.toList());
                        messagingTemplate.convertAndSendToUser(username, "/queue/history", history);
                    }
                } catch (IllegalArgumentException e) {
                    System.err.println("Invalid UUID in subscription destination: " + destination);
                }
            }
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Principal principal = event.getUser();
        if (sessionId != null && principal != null) {
            String username = principal.getName();
            Set<UUID> folderIds = sessionSubscriptions.remove(sessionId);
            if (folderIds != null) {
                for (UUID folderId : folderIds) {
                    activeUserService.userLeft(folderId, username);
                    messagingTemplate.convertAndSend("/topic/folder/" + folderId + "/active",
                            activeUserService.getActiveUsers(folderId));
                }
            }
        }
    }
}