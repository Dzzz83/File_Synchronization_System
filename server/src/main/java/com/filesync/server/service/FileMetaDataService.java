package com.filesync.server.service;

import com.filesync.common.enums.SyncStatus;
import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import com.filesync.server.storage.FileStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FileMetaDataService {
    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private FileStorage fileStorage;

    // ==================== Basic CRUD ====================

    public FileMetadataEntity saveFileMetaData(FileMetadataEntity entity) {
        return fileMetadataRepository.save(entity);
    }

    public List<FileMetadataEntity> getFilesByOwner(String ownerId) {
        return fileMetadataRepository.findByOwnerId(ownerId);
    }

    public FileMetadataEntity getFileById(String fileId) {
        return fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));
    }

    public void deleteFile(String fileId) {
        fileMetadataRepository.deleteById(fileId);
    }

    public boolean existsById(String fileId) {
        return fileMetadataRepository.existsById(fileId);
    }

    // ==================== Queries ====================

    public List<FileMetadataEntity> getFilesByFolder(UUID folderId) {
        return fileMetadataRepository.findByFolderId(folderId);
    }

    public List<FileMetadataEntity> getPersonalFilesByOwner(String ownerId) {
        return fileMetadataRepository.findByOwnerIdAndFolderIdIsNull(ownerId);
    }

    public FileMetadataEntity createFolder(String name, String ownerId, UUID parentId, UUID sharedFolderId) {
        FileMetadataEntity folder = new FileMetadataEntity();
        folder.setId(UUID.randomUUID().toString());
        folder.setRelativePath(name);
        folder.setDirectory(true);
        folder.setSize(0L);
        folder.setLastModified(Instant.now());
        folder.setOwnerId(ownerId);
        folder.setParentId(parentId);
        folder.setFolderId(sharedFolderId);
        folder.setStatus(SyncStatus.SYNCED);
        return saveFileMetaData(folder);
    }

    public List<FileMetadataEntity> getFilesByParent(UUID parentId) {
        return fileMetadataRepository.findByParentId(parentId);
    }

    public List<FileMetadataEntity> getSharedFolderRootFiles(UUID folderId) {
        return fileMetadataRepository.findByFolderIdAndParentIdIsNull(folderId);
    }

    public List<FileMetadataEntity> getPersonalRootFiles(String ownerId) {
        return fileMetadataRepository.findByOwnerIdAndParentIdIsNull(ownerId);
    }

    // ==================== Folder Size Management ====================

    /**
     * Adds delta to the size of the folder and all its ancestors.
     * @param parentId the folder to start from (may be null)
     * @param delta the size change (positive or negative)
     */
    public void addToAncestors(UUID parentId, long delta) {
        if (parentId == null || delta == 0) return;
        FileMetadataEntity folder = getFileById(parentId.toString());
        if (folder != null && folder.isDirectory()) {
            folder.setSize(folder.getSize() + delta);
            saveFileMetaData(folder);
            addToAncestors(folder.getParentId(), delta);
        }
    }

    public void removeFromAncestors(UUID parentId, long delta) {
        addToAncestors(parentId, -delta);
    }

    @Transactional
    public void moveFolder(String folderId, UUID newParentId, UUID newFolderId) {
        FileMetadataEntity folder = getFileById(folderId);
        long folderSize = folder.getSize();

        if (folder.getParentId() != null) {
            removeFromAncestors(folder.getParentId(), folderSize);
        }

        folder.setParentId(newParentId);
        folder.setFolderId(newFolderId);
        saveFileMetaData(folder);

        if (newParentId != null) {
            addToAncestors(newParentId, folderSize);
        }
    }

    @Transactional
    public void deleteFolderRecursively(String folderId) {
        FileMetadataEntity folder = getFileById(folderId);
        List<FileMetadataEntity> children = getFilesByParent(UUID.fromString(folderId));
        for (FileMetadataEntity child : children) {
            if (child.isDirectory()) {
                deleteFolderRecursively(child.getId());
            } else {
                fileStorage.delete(child.getId());
                fileMetadataRepository.deleteById(child.getId());
            }
        }
        long folderSize = folder.getSize();
        if (folder.getParentId() != null) {
            removeFromAncestors(folder.getParentId(), folderSize);
        }
        fileMetadataRepository.deleteById(folderId);
    }

    @Transactional
    public void deleteFileAndUpdateAncestors(String fileId) {
        FileMetadataEntity entity = getFileById(fileId);
        long size = entity.getSize();
        UUID parentId = entity.getParentId();
        fileMetadataRepository.deleteById(fileId);
        fileStorage.delete(fileId);
        if (parentId != null && size > 0) {
            removeFromAncestors(parentId, size);
        }
    }

    @Transactional
    public void updateFileSize(String fileId, long newSize) {
        FileMetadataEntity entity = getFileById(fileId);
        long oldSize = entity.getSize();
        if (oldSize == newSize) return;
        entity.setSize(newSize);
        saveFileMetaData(entity);
        long delta = newSize - oldSize;
        if (entity.getParentId() != null && delta != 0) {
            addToAncestors(entity.getParentId(), delta);
        }
    }
}