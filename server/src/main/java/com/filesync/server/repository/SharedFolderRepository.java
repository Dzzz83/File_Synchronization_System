package com.filesync.server.repository;

import com.filesync.server.domain.SharedFolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SharedFolderRepository extends JpaRepository<SharedFolderEntity, UUID> {
    List<SharedFolderEntity> findByOwnerId(String ownerId);
    List<SharedFolderEntity> findByIdIn(List<UUID> ids);
    List<SharedFolderEntity> findByNameContainingIgnoreCase(String name);
    boolean existsByOwnerIdAndName(String ownerId, String name);
}