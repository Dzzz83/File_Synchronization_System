package com.filesync.common.dto;

import java.util.Set;
import java.util.UUID;

public class UploadStatusDto {
    private String fileId;
    private Set<Integer> uploadedChunks;
    private boolean completed;

    public UploadStatusDto() {
    }

    public UploadStatusDto(String fileId, Set<Integer> uploadedChunks, boolean completed) {
        this.fileId = fileId;
        this.uploadedChunks = uploadedChunks;
        this.completed = completed;
    }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public Set<Integer> getUploadedChunks() { return uploadedChunks; }
    public void setUploadedChunks(Set<Integer> uploadedChunks) { this.uploadedChunks = uploadedChunks; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}