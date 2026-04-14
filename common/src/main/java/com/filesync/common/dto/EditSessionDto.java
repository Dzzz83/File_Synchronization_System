package com.filesync.common.dto;

public class EditSessionDto {
    private final String fileId;
    private final String originalHash;

    public EditSessionDto(String fileId, String originalHash) {
        this.fileId = fileId;
        this.originalHash = originalHash;
    }

    public String getFileId() { return fileId; }
    public String getOriginalHash() { return originalHash; }
}