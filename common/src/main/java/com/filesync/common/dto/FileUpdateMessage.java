package com.filesync.common.dto;

import java.util.UUID;

public class FileUpdateMessage {
    private String fileId;
    private String relativePath;
    private String eventType;
    private boolean isDirectory;
    private long size;
    private UUID folderId;      // ← new field

    public FileUpdateMessage() {}

    public FileUpdateMessage(String fileId, String relativePath, String eventType,
                             boolean isDirectory, long size, UUID folderId) {
        this.fileId = fileId;
        this.relativePath = relativePath;
        this.eventType = eventType;
        this.isDirectory = isDirectory;
        this.size = size;
        this.folderId = folderId;
    }

    // Getters and setters for all fields
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public boolean isDirectory() { return isDirectory; }
    public void setDirectory(boolean directory) { isDirectory = directory; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public UUID getFolderId() { return folderId; }
    public void setFolderId(UUID folderId) { this.folderId = folderId; }
}