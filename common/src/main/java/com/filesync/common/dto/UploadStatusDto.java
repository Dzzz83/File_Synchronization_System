package com.filesync.common.dto;

import java.util.Set;

public class UploadStatusDto {
    private final String fileId;
    private final Set<Integer> uploadedChunks;
    private final boolean completed;

    public UploadStatusDto(String fileId, Set<Integer> uploadedChunks, boolean completed) {
        this.fileId = fileId;
        this.uploadedChunks = Set.copyOf(uploadedChunks);
        this.completed = completed;
    }

    public String getFileId() {
        return fileId;
    }

    public Set<Integer> getUploadedChunks() {
        return uploadedChunks;
    }

    public boolean isCompleted() {
        return completed;
    }
}
