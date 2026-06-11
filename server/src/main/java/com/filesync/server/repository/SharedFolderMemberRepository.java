package com.filesync.server.repository;

import com.filesync.common.enums.Permission;
import com.filesync.server.domain.SharedFolderMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharedFolderMemberRepository extends JpaRepository<SharedFolderMemberEntity, Long> {
    List<SharedFolderMemberEntity> findByUserId(String userId);
    List<SharedFolderMemberEntity> findByFolderId(UUID folderId);
    boolean existsByFolderIdAndUserIdAndPermissionIn(UUID folderId, String userId, Collection<Permission> permissions);
    Optional<SharedFolderMemberEntity> findByFolderIdAndUserId(UUID folderId, String userId);
    void deleteByFolderId(UUID folderId);
}