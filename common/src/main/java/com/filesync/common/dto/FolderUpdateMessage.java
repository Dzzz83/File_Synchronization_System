package com.filesync.common.dto;

import java.util.UUID;

public class FolderUpdateMessage {
    private String eventType; // "CREATED" or "DELETED"
    private UUID folderId;
    private String folderName;
    private String ownerId;

    public FolderUpdateMessage() {}

    public FolderUpdateMessage(String eventType, UUID folderId, String folderName, String ownerId) {
        this.eventType = eventType;
        this.folderId = folderId;
        this.folderName = folderName;
        this.ownerId = ownerId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
}