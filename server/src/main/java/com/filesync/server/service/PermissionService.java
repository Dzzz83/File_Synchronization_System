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

    // check if the user can read the file
    public boolean canRead(String userId, String fileId) {
        // find the file
        FileMetadataEntity file = fileMetadataRepository.findById(fileId).orElse(null);
        if (file == null) return false;
        // check if the file is not in any shared folder
        if (file.getFolderId() == null) {
            return file.getOwnerId().equals(userId);
        } else {
            // return a boolean for whether the file has read access
            return hasReadAccess(file.getFolderId(), userId);
        }
    }

    public boolean canWrite(String userId, String fileId) {
        FileMetadataEntity file = fileMetadataRepository.findById(fileId).orElse(null);
        if (file == null) return false;
        if (file.getFolderId() == null) {
            return file.getOwnerId().equals(userId);
        } else {
            // return a boolean for whether the file has write access
            return hasWriteAccess(file.getFolderId(), userId);
        }
    }

    public boolean canWriteToFolder(String userId, UUID folderId)
    {
        return hasWriteAccess(folderId, userId);
    }

    public boolean canReadFolder(String userId, UUID folderId) {
        return hasReadAccess(folderId, userId);
    }
}