package com.filesync.server.repository;

import com.filesync.server.domain.SyncTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncTaskRepository extends JpaRepository<SyncTask, String> {
}