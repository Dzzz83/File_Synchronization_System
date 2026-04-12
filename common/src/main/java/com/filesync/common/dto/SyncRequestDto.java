package com.filesync.common.dto;

import java.util.List;

public class SyncRequestDto {
    private String ownerId;
    private List<FileMetadataDto> clientFiles;

    public SyncRequestDto() {}

    public SyncRequestDto(String ownerId, List<FileMetadataDto> clientFiles) {
        this.ownerId = ownerId;
        this.clientFiles = clientFiles;
    }

    // Getters and setters
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public List<FileMetadataDto> getClientFiles() { return clientFiles; }
    public void setClientFiles(List<FileMetadataDto> clientFiles) { this.clientFiles = clientFiles; }
}