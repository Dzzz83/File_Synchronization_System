package com.filesync.common.dto;

public class ConflictContextDto {
    private final String fileId;
    private final String serverContent;
    private final String userContent;

    public ConflictContextDto(String fileId, String serverContent, String userContent) {
        this.fileId = fileId;
        this.serverContent = serverContent;
        this.userContent = userContent;
    }

    public String getFileId() { return fileId; }
    public String getServerContent() { return serverContent; }
    public String getUserContent() { return userContent; }
}