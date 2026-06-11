package com.filesync.server.dto;

import com.filesync.common.dto.SyncRequestDto;

import java.io.Serializable;
import java.util.UUID;

public class SyncMessage implements Serializable
{
    private String taskId;
    private SyncRequestDto syncRequestDto;
    private UUID folderId;

    public SyncMessage() {}

    public SyncMessage(String taskId, SyncRequestDto syncRequestDto)
    {
        this.taskId = taskId;
        this.syncRequestDto = syncRequestDto;
        this.folderId = syncRequestDto.getFolderId();
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public SyncRequestDto getSyncRequestDto() {
        return syncRequestDto;
    }

    public void setSyncRequestDto(SyncRequestDto syncRequestDto) {
        this.syncRequestDto = syncRequestDto;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
    }
}
