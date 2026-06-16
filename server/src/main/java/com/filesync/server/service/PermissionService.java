package com.filesync.server.service;

import com.filesync.common.enums.Permission;
import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import com.filesync.server.repository.SharedFolderMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class PermissionService {
    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final FileMetadataRepository fileMetadataRepository;
    private final SharedFolderMemberRepository memberRepository;

    public PermissionService(FileMetadataRepository fileMetadataRepository,
                             SharedFolderMemberRepository memberRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.memberRepository = memberRepository;
    }

    private boolean hasReadAccess(UUID folderId, String userId) {
        boolean result = memberRepository.existsByFolderIdAndUserIdAndPermissionIn(
                folderId, userId, Set.of(Permission.READ, Permission.WRITE));
        log.debug("hasReadAccess(folderId={}, userId={}) = {}", folderId, userId, result);
        return result;
    }

    private boolean hasWriteAccess(UUID folderId, String userId) {
        boolean result = memberRepository.existsByFolderIdAndUserIdAndPermissionIn(
                folderId, userId, Set.of(Permission.WRITE));
        log.debug("hasWriteAccess(folderId={}, userId={}) = {}", folderId, userId, result);
        return result;
    }

    public boolean canRead(String userId, String fileId) {
        log.debug("canRead called: userId={}, fileId={}", userId, fileId);
        FileMetadataEntity file = fileMetadataRepository.findById(fileId).orElse(null);
        if (file == null) {
            log.warn("canRead: file not found for fileId={}", fileId);
            return false;
        }

        UUID folderId = file.getFolderId();
        String ownerId = file.getOwnerId();
        log.info("canRead: userId={}, fileId={}, ownerId={}, folderId={}, isDirectory={}",
                userId, fileId, ownerId, folderId, file.isDirectory());

        if (folderId == null) {
            boolean result = ownerId.equals(userId);
            log.info("  Personal file: ownerId={} equals userId={} => {}", ownerId, userId, result);
            return result;
        } else {
            boolean hasRead = hasReadAccess(folderId, userId);
            log.info("  Shared folder file: hasReadAccess for folderId={} = {}", folderId, hasRead);
            return hasRead;
        }
    }

    public boolean canWrite(String userId, String fileId) {
        log.debug("canWrite called: userId={}, fileId={}", userId, fileId);
        FileMetadataEntity file = fileMetadataRepository.findById(fileId).orElse(null);
        if (file == null) {
            log.warn("canWrite: file not found for fileId={}", fileId);
            return false;
        }

        UUID folderId = file.getFolderId();
        String ownerId = file.getOwnerId();
        log.info("canWrite: userId={}, fileId={}, ownerId={}, folderId={}",
                userId, fileId, ownerId, folderId);

        if (folderId == null) {
            boolean result = ownerId.equals(userId);
            log.info("  Personal file: ownerId={} equals userId={} => {}", ownerId, userId, result);
            return result;
        } else {
            boolean hasWrite = hasWriteAccess(folderId, userId);
            log.info("  Shared folder file: hasWriteAccess for folderId={} = {}", folderId, hasWrite);
            return hasWrite;
        }
    }

    public boolean canWriteToFolder(String userId, UUID folderId) {
        log.debug("canWriteToFolder: userId={}, folderId={}", userId, folderId);
        boolean result = hasWriteAccess(folderId, userId);
        log.info("canWriteToFolder: result={}", result);
        return result;
    }

    public boolean canReadFolder(String userId, UUID folderId) {
        log.debug("canReadFolder: userId={}, folderId={}", userId, folderId);
        boolean result = hasReadAccess(folderId, userId);
        log.info("canReadFolder: result={}", result);
        return result;
    }

    // Method called by FileMetaDataService.hasPermission
    public boolean hasPermission(FileMetadataEntity file, String username, String requiredPermission) {
        if (file == null) {
            log.warn("hasPermission: file is null");
            return false;
        }
        String permission = requiredPermission.toUpperCase();
        log.debug("hasPermission: fileId={}, username={}, requiredPermission={}", file.getId(), username, requiredPermission);

        if ("READ".equals(permission)) {
            return canRead(username, file.getId());
        } else if ("WRITE".equals(permission) || "DELETE".equals(permission)) {
            return canWrite(username, file.getId());
        }
        log.warn("hasPermission: unknown permission type: {}", permission);
        return false;
    }
}