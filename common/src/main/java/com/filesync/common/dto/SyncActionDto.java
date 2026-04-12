package com.filesync.common.dto;

import com.filesync.common.enums.SyncActionType;

public class SyncActionDto {
    private SyncActionType action;
    private FileMetadataDto fileMetadata;
    private String message;

    public SyncActionDto() {}

    public SyncActionDto(SyncActionType action, FileMetadataDto fileMetadata, String message) {
        this.action = action;
        this.fileMetadata = fileMetadata;
        this.message = message;
    }

    // Getters and setters
    public SyncActionType getAction() { return action; }
    public void setAction(SyncActionType action) { this.action = action; }
    public FileMetadataDto getFileMetadata() { return fileMetadata; }
    public void setFileMetadata(FileMetadataDto fileMetadata) { this.fileMetadata = fileMetadata; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}