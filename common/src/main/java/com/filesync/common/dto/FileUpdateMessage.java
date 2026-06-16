package com.filesync.common.dto;

public class FileUpdateMessage {
    private String fileId;
    private String relativePath;
    private String eventType; // "CREATED", "UPDATED", "DELETED"
    private boolean isDirectory;
    private long size;

    public FileUpdateMessage() {}

    public FileUpdateMessage(String fileId, String relativePath, String eventType, boolean isDirectory, long size) {
        this.fileId = fileId;
        this.relativePath = relativePath;
        this.eventType = eventType;
        this.isDirectory = isDirectory;
        this.size = size;
    }

    // Getters and setters
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
}