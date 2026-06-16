package com.filesync.client.websocket;

import com.filesync.common.dto.FileUpdateMessage;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FileUpdateClient {
    private static final Logger log = LoggerFactory.getLogger(FileUpdateClient.class);
    private final String baseUrl;
    private final String authToken;
    private StompSession session;
    private boolean connected = false;

    private UUID folderId;
    private Consumer<FileUpdateMessage> handler;
    private final CountDownLatch subscriptionLatch = new CountDownLatch(1);

    public FileUpdateClient(String baseUrl, String authToken) {
        this.baseUrl = baseUrl;
        this.authToken = authToken;
    }

    public void connect(UUID folderId, Consumer<FileUpdateMessage> handler) throws Exception {
        this.folderId = folderId;
        this.handler = handler;
        log.info("Connecting to WebSocket at {}", baseUrl + "/ws/files");
        SockJsClient sockJsClient = new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))
        );
        String wsUrl = baseUrl + "/ws/files";
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("token", authToken);
        connectHeaders.add("Authorization", "Bearer " + authToken);

        CompletableFuture<StompSession> future = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        log.info("FileUpdateClient connected successfully. Headers: {}", connectedHeaders);
                        String destination = "/topic/file/" + folderId.toString();
                        session.subscribe(destination, new StompFrameHandler() {
                            @Override
                            public Type getPayloadType(StompHeaders headers) {
                                return FileUpdateMessage.class;
                            }

                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                log.info("Received file update frame: headers={}, payload={}", headers, payload);
                                if (payload instanceof FileUpdateMessage) {
                                    FileUpdateMessage msg = (FileUpdateMessage) payload;
                                    log.info("Received file update: {} - {}", msg.getEventType(), msg.getRelativePath());
                                    handler.accept(msg);
                                } else {
                                    log.warn("Unexpected payload type: {} (payload={})",
                                            payload != null ? payload.getClass() : "null", payload);
                                }
                            }
                        });
                        subscriptionLatch.countDown();
                    }

                    @Override
                    public void handleException(StompSession session, StompCommand command,
                                                StompHeaders headers, byte[] payload, Throwable exception) {
                        log.error("FileUpdateClient STOMP exception: command={}, headers={}, payload={}",
                                command, headers, new String(payload), exception);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        log.error("FileUpdateClient transport error: ", exception);
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.info("FileUpdateClient received a frame (session-level): headers={}, payload={}", headers, payload);
                        super.handleFrame(headers, payload);
                    }
                }
        );
        this.session = future.get();
        if (!subscriptionLatch.await(5, TimeUnit.SECONDS)) {
            log.warn("Subscription timeout - subscription might not be active");
        }
        this.connected = true;
        log.info("FileUpdateClient connected to {} (session ID: {})", wsUrl, session.getSessionId());
    }

    public void connect() throws Exception {
        throw new UnsupportedOperationException("Use connect(UUID, Consumer) instead");
    }

    public void subscribeToFolder(UUID folderId, Consumer<FileUpdateMessage> handler) {
        throw new UnsupportedOperationException("Use connect(UUID, Consumer) for both connection and subscription");
    }

    public void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            connected = false;
            log.info("FileUpdateClient disconnected");
        }
    }

    public boolean isConnected() {
        return connected && session != null && session.isConnected();
    }
}