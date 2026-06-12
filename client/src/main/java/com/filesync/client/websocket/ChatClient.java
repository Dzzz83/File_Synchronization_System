package com.filesync.client.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.filesync.common.dto.ChatMessage;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class ChatClient {
    private WebSocketStompClient stompClient;
    private StompSession session;
    private final String baseUrl;
    private final String authToken;
    private final ObjectMapper objectMapper;

    public ChatClient(String baseUrl, String authToken) {
        this.baseUrl = baseUrl;
        this.authToken = authToken;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void connect(UUID folderId,
                        Consumer<ChatMessage> onMessage,
                        Consumer<Set> onActiveUsersUpdate) {
        String sockJsUrlRaw = baseUrl.replace("http://", "ws://").replace("https://", "wss://");
        if (!sockJsUrlRaw.endsWith("/")) {
            sockJsUrlRaw += "/";
        }
        final String sockJsUrl = sockJsUrlRaw + "ws/chat";

        final UUID finalFolderId = folderId;
        final Consumer<ChatMessage> finalOnMessage = onMessage;
        final Consumer<Set> finalOnActiveUsersUpdate = onActiveUsersUpdate;

        WebSocketClient webSocketClient = new StandardWebSocketClient();
        Transport webSocketTransport = new WebSocketTransport(webSocketClient);
        SockJsClient sockJsClient = new SockJsClient(Collections.singletonList(webSocketTransport));

        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession stompSession, StompHeaders connectedHeaders) {
                session = stompSession;
                System.out.println("Connected to STOMP WebSocket server (SockJS) at " + sockJsUrl);

                // 1. Subscribe to history FIRST (so the server can deliver history when we later subscribe to the folder)
                session.subscribe("/user/queue/history", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return byte[].class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        byte[] bytes = (byte[]) payload;
                        String raw = new String(bytes);
                        System.out.println("History raw payload: " + raw);
                        try {
                            List<ChatMessage> history = objectMapper.readValue(bytes,
                                    objectMapper.getTypeFactory().constructCollectionType(List.class, ChatMessage.class));
                            System.out.println("Received " + history.size() + " history messages");
                            for (ChatMessage msg : history) {
                                finalOnMessage.accept(msg);
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to parse history: " + raw);
                            e.printStackTrace();
                        }
                    }
                });

                // 2. Subscribe to live chat messages
                session.subscribe("/topic/folder/" + finalFolderId.toString(), new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return byte[].class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        byte[] bytes = (byte[]) payload;
                        try {
                            ChatMessage msg = objectMapper.readValue(bytes, ChatMessage.class);
                            finalOnMessage.accept(msg);
                        } catch (Exception e) {
                            System.err.println("Ignoring non‑ChatMessage payload: " + new String(bytes));
                        }
                    }
                });

                // 3. Subscribe to active users updates
                session.subscribe("/topic/folder/" + finalFolderId.toString() + "/active", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return byte[].class;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public void handleFrame(StompHeaders headers, Object payload) {
                        byte[] bytes = (byte[]) payload;
                        try {
                            Set<String> users = objectMapper.readValue(bytes, Set.class);
                            finalOnActiveUsersUpdate.accept(users);
                        } catch (Exception e) {
                            System.err.println("Ignoring non‑Set payload for active users: " + new String(bytes));
                        }
                    }
                });
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                System.err.println("STOMP Client Exception: " + exception.getMessage());
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                System.err.println("STOMP Transport Error: " + exception.getMessage());
            }
        };

        StompHeaders connectHeaders = new StompHeaders();
        if (authToken != null && !authToken.isEmpty()) {
            connectHeaders.add("Authorization", "Bearer " + authToken);
        }

        WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
        stompClient.connectAsync(sockJsUrl, httpHeaders, connectHeaders, sessionHandler);
    }

    public void sendMessage(ChatMessage message) {
        if (session != null && session.isConnected()) {
            session.send("/app/chat.send", message);
        } else {
            System.err.println("Cannot send message: STOMP session is not connected.");
        }
    }

    public void disconnect() {
        try {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            if (stompClient != null && stompClient.isRunning()) {
                stompClient.stop();
            }
            System.out.println("Disconnected STOMP WebSocket client");
        } catch (Exception e) {
            System.err.println("Error during STOMP disconnect: " + e.getMessage());
        }
    }
}