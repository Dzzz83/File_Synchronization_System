package com.filesync.common.dto;
import com.filesync.common.enums.SyncActionType;

public class SyncActionDto {
    private SyncActionType actionType;
    private FileMetadataDto fileMetadata;
    private String message;

    public SyncActionDto() {}

    public SyncActionDto(SyncActionType actionType, FileMetadataDto fileMetadata, String message) {
        this.actionType = actionType;
        this.fileMetadata = fileMetadata;
        this.message = message;
    }

    public SyncActionType getActionType() {
        return actionType;
    }

    public FileMetadataDto getFileMetadata() {
        return fileMetadata;
    }

    public String getMessage() {
        return message;
    }

    public void setActionType(SyncActionType actionType) {
        this.actionType = actionType;
    }

    public void setFileMetadata(FileMetadataDto fileMetadata) {
        this.fileMetadata = fileMetadata;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
