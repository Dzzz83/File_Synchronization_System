package com.filesync.server.repository;

import com.filesync.server.domain.SharedFolderRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharedFolderRequestRepository extends JpaRepository<SharedFolderRequestEntity, UUID> {
    List<SharedFolderRequestEntity> findByFolderIdAndStatus(UUID folderId, String status);
    Optional<SharedFolderRequestEntity> findByFolderIdAndRequesterId(UUID folderId, String requesterId);
    int countByFolderIdAndStatus(UUID folderId, String status);
    void deleteByFolderId(UUID folderId);
}