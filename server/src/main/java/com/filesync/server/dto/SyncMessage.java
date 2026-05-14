package com.filesync.server.dto;

import com.filesync.common.dto.SyncRequestDto;

import java.io.Serializable;

public class SyncMessage implements Serializable
{
    private String taskId;
    private SyncRequestDto syncRequestDto;

    public SyncMessage() {}

    public SyncMessage(String taskId, SyncRequestDto syncRequestDto)
    {
        this.taskId = taskId;
        this.syncRequestDto = syncRequestDto;
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
}
