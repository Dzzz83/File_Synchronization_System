package com.filesync.common.dto;

import java.util.List;

public class SyncResponseDto {
    private List<SyncActionDto> actions;

    public SyncResponseDto(){};

    public SyncResponseDto(List<SyncActionDto> actions) {
        this.actions = actions;
    }

    public List<SyncActionDto> getActions() {
        return actions;
    }

    public void setActions(List<SyncActionDto> actions) {
        this.actions = actions;
    }

}
