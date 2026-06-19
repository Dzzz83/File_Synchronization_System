package com.filesync.client.websocket;

import com.filesync.common.dto.FolderUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;

public class FolderUpdateClient {
    private static final Logger log = LoggerFactory.getLogger(FolderUpdateClient.class);
    private final String baseUrl;
    private final String authToken;
    private StompSession session;
    private boolean connected = false;

    public FolderUpdateClient(String baseUrl, String authToken) {
        this.baseUrl = baseUrl;
        this.authToken = authToken;
    }

    public void connect(Consumer<FolderUpdateMessage> handler) throws Exception {
        String wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://") + "/ws/folders";
        log.info("🔄 Connecting to folder WebSocket at: {}", wsUrl);

        SockJsClient sockJsClient = new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))
        );
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + authToken);
        log.debug("📤 Sending auth token in CONNECT headers");

        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                log.info("✅ Folder WebSocket connected, session ID: {}", session.getSessionId());
                session.subscribe("/topic/folders", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return FolderUpdateMessage.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.info("📩 Folder event received (raw): {}", payload);
                        if (payload instanceof FolderUpdateMessage) {
                            FolderUpdateMessage msg = (FolderUpdateMessage) payload;
                            log.info("📩 Parsed FolderUpdateMessage: eventType={}, folderId={}", msg.getEventType(), msg.getFolderId());
                            handler.accept(msg);
                        } else {
                            log.warn("⚠️ Unexpected payload type: {}", payload != null ? payload.getClass() : "null");
                        }
                    }
                });
                log.info("📨 Subscribed to /topic/folders");
                connected = true;
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                log.error("❌ STOMP exception: command={}, message={}", command, exception.getMessage(), exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                log.error("❌ Folder WebSocket transport error", exception);
                connected = false;
            }
        };

        this.session = stompClient.connectAsync(wsUrl, new WebSocketHttpHeaders(), connectHeaders, sessionHandler).get();
        log.info("✅ Folder WebSocket client created and connection initiated");
    }

    public void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            connected = false;
            log.info("🔌 Folder WebSocket disconnected");
        }
    }

    public boolean isConnected() {
        return connected && session != null && session.isConnected();
    }
}