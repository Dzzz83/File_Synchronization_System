package com.filesync.server.service;

import com.filesync.common.enums.Permission;
import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import com.filesync.server.repository.SharedFolderMemberRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class PermissionService {
    private final FileMetadataRepository fileMetadataRepository;
    private final SharedFolderMemberRepository memberRepository;

    public PermissionService(FileMetadataRepository fileMetadataRepository,
                             SharedFolderMemberRepository memberRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.memberRepository = memberRepository;
    }

    private boolean hasReadAccess(UUID folderId, String userId) {
        return memberRepository.existsByFolderIdAndUserIdAndPermissionIn(
                folderId, userId, Set.of(Permission.READ, Permission.WRITE));
    }

    private boolean hasWriteAccess(UUID folderId, String userId) {
        return memberRepository.existsByFolderIdAndUserIdAndPermissionIn(
                folderId, userId, Set.of(Permission.WRITE));
    }

    public boolean canRead(String userId, String fileId) {
        FileMetadataEntity file = fileMetadataRepository.findById(fileId).orElse(null);
        if (file == null) return false;
        if (file.getFolderId() == null) {
            return file.getOwnerId().equals(userId);
        } else {
            return hasReadAccess(file.getFolderId(), userId);
        }
    }

    public boolean canWrite(String userId, String fileId) {
        FileMetadataEntity file = fileMetadataRepository.findById(fileId).orElse(null);
        if (file == null) return false;
        if (file.getFolderId() == null) {
            return file.getOwnerId().equals(userId);
        } else {
            return hasWriteAccess(file.getFolderId(), userId);
        }
    }

    public boolean canWriteToFolder(String userId, UUID folderId) {
        return hasWriteAccess(folderId, userId);
    }

    public boolean canReadFolder(String userId, UUID folderId) {
        return hasReadAccess(folderId, userId);
    }

    // Method called by FileMetaDataService.hasPermission
    public boolean hasPermission(FileMetadataEntity file, String username, String requiredPermission) {
        if (file == null) return false;
        String permission = requiredPermission.toUpperCase();
        if ("READ".equals(permission)) {
            return canRead(username, file.getId());
        } else if ("WRITE".equals(permission) || "DELETE".equals(permission)) {
            // DELETE requires write access (or owner, same as write)
            return canWrite(username, file.getId());
        }
        return false;
    }
}