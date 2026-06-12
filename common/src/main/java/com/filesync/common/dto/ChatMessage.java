package com.filesync.common.dto;

import java.time.Instant;
import java.util.UUID;

public class ChatMessage {
    private String sender;
    private String content;
    private Instant timestamp;
    private UUID folderId;

    public ChatMessage() {
    }

    public ChatMessage(String sender, String content, Instant timestamp, UUID folderId) {
        this.sender = sender;
        this.content = content;
        this.timestamp = timestamp;
        this.folderId = folderId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
    }
}