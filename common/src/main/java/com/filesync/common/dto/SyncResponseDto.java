package com.filesync.common.dto;

import java.util.ArrayList;
import java.util.List;

public class SyncResponseDto {
    private List<SyncActionDto> actions;

    public SyncResponseDto() {
        this.actions = new ArrayList<>();
    }

    public SyncResponseDto(List<SyncActionDto> actions) {
        this.actions = actions != null ? actions : new ArrayList<>();
    }

    // Getters and setters
    public List<SyncActionDto> getActions() { return actions; }
    public void setActions(List<SyncActionDto> actions) { this.actions = actions; }
}