package com.filesync.common.dto;

import java.util.List;
import java.util.UUID;

public class SyncRequestDto {
    private String ownerId;
    private List<FileMetadataDto> clientFiles;
    private UUID folderId;

    public SyncRequestDto() {}

    public SyncRequestDto(String ownerId, List<FileMetadataDto> clientFiles) {
        this.ownerId = ownerId;
        this.clientFiles = clientFiles;
        this.folderId = null;
    }

    public SyncRequestDto(String ownerId, List<FileMetadataDto> clientFiles, UUID folderId) {
        this.ownerId = ownerId;
        this.clientFiles = clientFiles;
        this.folderId = folderId;
    }

    // Getters and setters
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public List<FileMetadataDto> getClientFiles() { return clientFiles; }
    public void setClientFiles(List<FileMetadataDto> clientFiles) { this.clientFiles = clientFiles; }
    public UUID getFolderId() { return folderId; }
    public void setFolderId(UUID folderId) { this.folderId = folderId; }
}