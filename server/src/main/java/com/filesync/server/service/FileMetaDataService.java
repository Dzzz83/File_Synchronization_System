package com.filesync.server.service;

import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FileMetaDataService {
    private static final Logger log = LoggerFactory.getLogger(FileMetaDataService.class);

    private final FileMetadataRepository fileMetadataRepository;
    private final QuotaService quotaService;
    private final PermissionService permissionService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FileEventPublisher fileEventPublisher;

    public FileMetaDataService(FileMetadataRepository fileMetadataRepository,
                               QuotaService quotaService,
                               FileEventPublisher fileEventPublisher,
                               PermissionService permissionService,
                               ApplicationEventPublisher eventPublisher) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.quotaService = quotaService;
        this.permissionService = permissionService;
        this.eventPublisher = eventPublisher;
        this.fileEventPublisher = fileEventPublisher;
    }

    // ========== Basic CRUD ==========

    public boolean existsById(String fileId) {
        return fileMetadataRepository.existsById(fileId);
    }

    public FileMetadataEntity getFileById(String fileId) {
        return fileMetadataRepository.findById(fileId).orElse(null);
    }

    @Transactional
    public FileMetadataEntity saveFileMetaData(FileMetadataEntity entity) {
        if (!entity.isDirectory() && entity.getId() == null) {
            quotaService.checkAndReserveQuota(entity.getOwnerId(), entity.getSize());
        }
        FileMetadataEntity saved = fileMetadataRepository.save(entity);
        eventPublisher.publishEvent(new SyncEvent(this, saved, SyncEvent.Type.CREATED_OR_UPDATED));
        log.debug("Publishing WebSocket event for saved file: {} (folder: {})", saved.getId(), saved.getFolderId());
        fileEventPublisher.publishFileEvent(saved, "CREATED_OR_UPDATED");
        return saved;
    }

    @Transactional
    public void deleteFileAndUpdateAncestors(String fileId) {
        FileMetadataEntity file = getFileById(fileId);
        if (file == null) return;
        if (file.isDirectory()) {
            throw new IllegalArgumentException("Cannot delete a folder using deleteFileAndUpdateAncestors");
        }
        log.info("🗑️ Deleting file: {} with folderId: {}", fileId, file.getFolderId());
        // Publish WebSocket event once
        fileEventPublisher.publishFileEvent(file, "DELETED");
        long size = file.getSize();
        UUID parentId = file.getParentId();
        fileMetadataRepository.deleteById(fileId);
        if (parentId != null && size > 0) {
            removeFromAncestors(parentId, size);
        }
        eventPublisher.publishEvent(new SyncEvent(this, file, SyncEvent.Type.DELETED));
        quotaService.releaseQuota(file.getOwnerId(), size);
    }

    @Transactional
    public void deleteFolderRecursively(String folderId) {
        FileMetadataEntity folder = getFileById(folderId);
        if (folder == null) return;
        if (!folder.isDirectory()) {
            deleteFileAndUpdateAncestors(folderId);
            return;
        }
        List<FileMetadataEntity> children = fileMetadataRepository.findByParentId(UUID.fromString(folderId));
        for (FileMetadataEntity child : children) {
            if (child.isDirectory()) {
                deleteFolderRecursively(child.getId());
            } else {
                log.debug("Publishing WebSocket delete event for child file: {} (folder: {})", child.getId(), child.getFolderId());
                fileEventPublisher.publishFileEvent(child, "DELETED");
                fileMetadataRepository.deleteById(child.getId());
                quotaService.releaseQuota(child.getOwnerId(), child.getSize());
                eventPublisher.publishEvent(new SyncEvent(this, child, SyncEvent.Type.DELETED));
            }
        }
        log.debug("Publishing WebSocket delete event for folder: {} (folder: {})", folderId, folder.getFolderId());
        fileEventPublisher.publishFileEvent(folder, "DELETED");
        fileMetadataRepository.deleteById(folderId);
        eventPublisher.publishEvent(new SyncEvent(this, folder, SyncEvent.Type.DELETED));
    }

    // ========== Ancestor Size Management ==========

    @Transactional
    public void addToAncestors(UUID parentId, long delta) {
        if (parentId == null || delta == 0) return;
        FileMetadataEntity parent = fileMetadataRepository.findById(parentId.toString()).orElse(null);
        while (parent != null) {
            parent.setSize(parent.getSize() + delta);
            fileMetadataRepository.save(parent);
            if (parent.getParentId() == null) break;
            parent = fileMetadataRepository.findById(parent.getParentId().toString()).orElse(null);
        }
    }

    @Transactional
    public void removeFromAncestors(UUID parentId, long delta) {
        addToAncestors(parentId, -delta);
    }

    // ========== Move Operations ==========

    @Transactional
    public void moveFolder(String folderId, UUID newParentId, UUID newFolderId) {
        FileMetadataEntity folder = getFileById(folderId);
        if (folder == null) return;
        if (!folder.isDirectory()) {
            throw new IllegalArgumentException("moveFolder can only be used on directories");
        }
        long folderSize = folder.getSize();
        if (folder.getParentId() != null && folderSize > 0) {
            removeFromAncestors(folder.getParentId(), folderSize);
        }
        folder.setParentId(newParentId);
        folder.setFolderId(newFolderId);
        fileMetadataRepository.save(folder);
        if (newParentId != null && folderSize > 0) {
            addToAncestors(newParentId, folderSize);
        }
        eventPublisher.publishEvent(new SyncEvent(this, folder, SyncEvent.Type.MOVED));
        log.debug("Publishing WebSocket update event for moved folder: {} (new folder: {})", folderId, newFolderId);
        fileEventPublisher.publishFileEvent(folder, "UPDATED");
    }

    // ========== Folder Creation ==========

    @Transactional
    public FileMetadataEntity createFolder(String name, String ownerId, UUID parentId, UUID sharedFolderId) {
        boolean exists = fileMetadataRepository.existsByParentIdAndRelativePath(parentId, name);
        if (exists) {
            throw new IllegalArgumentException("A folder or file with the same name already exists at this location");
        }
        FileMetadataEntity folder = new FileMetadataEntity();
        folder.setId(UUID.randomUUID().toString());
        folder.setRelativePath(name);
        folder.setDirectory(true);
        folder.setOwnerId(ownerId);
        folder.setParentId(parentId);
        folder.setFolderId(sharedFolderId);
        folder.setSize(0L);
        FileMetadataEntity saved = fileMetadataRepository.save(folder);
        eventPublisher.publishEvent(new SyncEvent(this, saved, SyncEvent.Type.CREATED_OR_UPDATED));
        log.debug("Publishing WebSocket event for created folder: {} (folder: {})", saved.getId(), saved.getFolderId());
        fileEventPublisher.publishFileEvent(saved, "CREATED_OR_UPDATED");
        return saved;
    }

    // ========== Query Methods ==========

    public List<FileMetadataEntity> getFilesByParent(UUID parentId) {
        return fileMetadataRepository.findByParentId(parentId);
    }

    public List<FileMetadataEntity> getSharedFolderRootFiles(UUID folderId) {
        return fileMetadataRepository.findByFolderIdAndParentIdIsNull(folderId);
    }

    public List<FileMetadataEntity> getPersonalRootFiles(String ownerId) {
        return fileMetadataRepository.findByOwnerIdAndParentIdIsNull(ownerId);
    }

    // ========== Quota Helpers ==========

    public long getTotalSizeOfFolder(String folderId) {
        FileMetadataEntity folder = getFileById(folderId);
        if (folder == null || !folder.isDirectory()) {
            return 0;
        }
        return folder.getSize();
    }

    public int getFileCountInFolder(String folderId) {
        FileMetadataEntity folder = getFileById(folderId);
        if (folder == null || !folder.isDirectory()) {
            return 0;
        }
        List<FileMetadataEntity> children = fileMetadataRepository.findByParentId(UUID.fromString(folderId));
        int count = 0;
        for (FileMetadataEntity child : children) {
            if (child.isDirectory()) {
                count += getFileCountInFolder(child.getId());
            } else {
                count++;
            }
        }
        return count;
    }

    // ========== Permission Checks ==========

    public boolean hasPermission(String fileId, String username, String requiredPermission) {
        FileMetadataEntity file = getFileById(fileId);
        if (file == null) return false;
        return permissionService.hasPermission(file, username, requiredPermission);
    }

    public enum ResolutionStrategy {
        KEEP_LOCAL, KEEP_REMOTE, MERGE
    }

    // ========== Event Class ==========

    public static class SyncEvent {
        private final Object source;
        private final FileMetadataEntity entity;
        private final Type type;

        public enum Type { CREATED_OR_UPDATED, DELETED, MOVED }

        public SyncEvent(Object source, FileMetadataEntity entity, Type type) {
            this.source = source;
            this.entity = entity;
            this.type = type;
        }

        public FileMetadataEntity getEntity() { return entity; }
        public Type getType() { return type; }
    }
}