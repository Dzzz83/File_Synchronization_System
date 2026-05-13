package com.filesync.server.service;

import com.filesync.server.domain.SyncTask;
import com.filesync.server.repository.SyncTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SyncTaskStatusService {
    private final SyncTaskRepository syncTaskRepository;

    public SyncTaskStatusService(SyncTaskRepository syncTaskRepository) {
        this.syncTaskRepository = syncTaskRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String taskId, String errorMessage) {
        SyncTask task = syncTaskRepository.findById(taskId).orElseThrow();
        task.setStatus("FAILED");
        task.setErrorMessage(errorMessage);
        task.setUpdatedAt(LocalDateTime.now());
        syncTaskRepository.save(task);
        syncTaskRepository.flush();
    }
}
