package com.filesync.server.controller;

import com.filesync.common.dto.SyncRequestDto;
import com.filesync.common.dto.SyncResponseDto;
import com.filesync.server.service.SyncService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private final SyncService syncService;

    public SyncController(SyncService syncService)
    {
        this.syncService = syncService;
    }

    @PostMapping
    public SyncResponseDto sync(@RequestBody SyncRequestDto request)
    {
        return syncService.sync(request);
    }

}
