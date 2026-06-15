package com.filesync.server.service;

import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FileMetaDataService {
    private final FileMetadataRepository fileMetadataRepository;

    public FileMetaDataService(FileMetadataRepository fileMetadataRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
    }

    public boolean existsById(String fileId) {
        return fileMetadataRepository.existsById(fileId);
    }

    public FileMetadataEntity getFileById(String fileId) {
        return fileMetadataRepository.findById(fileId).orElse(null);
    }

    @Transactional
    public FileMetadataEntity saveFileMetaData(FileMetadataEntity entity) {
        return fileMetadataRepository.save(entity);
    }

    @Transactional
    public void deleteFileAndUpdateAncestors(String fileId) {
        FileMetadataEntity file = getFileById(fileId);
        if (file == null) return;
        long size = file.getSize();
        UUID parentId = file.getParentId();
        fileMetadataRepository.deleteById(fileId);
        if (parentId != null && size > 0) {
            removeFromAncestors(parentId, size);
        }
    }

    @Transactional
    public void deleteFolderRecursively(String folderId) {
        FileMetadataEntity folder = getFileById(folderId);
        if (folder == null) return;
        List<FileMetadataEntity> children = fileMetadataRepository.findByParentId(UUID.fromString(folderId));
        for (FileMetadataEntity child : children) {
            if (child.isDirectory()) {
                deleteFolderRecursively(child.getId());
            } else {
                fileMetadataRepository.deleteById(child.getId());
            }
        }
        fileMetadataRepository.deleteById(folderId);
    }

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

    @Transactional
    public void moveFolder(String folderId, UUID newParentId, UUID newFolderId) {
        FileMetadataEntity folder = getFileById(folderId);
        if (folder == null) return;
        long folderSize = getTotalSizeOfFolder(folderId);
        if (folder.getParentId() != null && folderSize > 0) {
            removeFromAncestors(folder.getParentId(), folderSize);
        }
        folder.setParentId(newParentId);
        folder.setFolderId(newFolderId);
        fileMetadataRepository.save(folder);
        if (newParentId != null && folderSize > 0) {
            addToAncestors(newParentId, folderSize);
        }
    }

    @Transactional
    public FileMetadataEntity createFolder(String name, String ownerId, UUID parentId, UUID sharedFolderId) {
        FileMetadataEntity folder = new FileMetadataEntity();
        folder.setId(UUID.randomUUID().toString());
        folder.setRelativePath(name);
        folder.setDirectory(true);
        folder.setOwnerId(ownerId);
        folder.setParentId(parentId);
        folder.setFolderId(sharedFolderId);
        folder.setSize(0L);
        return fileMetadataRepository.save(folder);
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

    // ========== QUOTA HELPER METHODS ==========

    public long getTotalSizeOfFolder(String folderId) {
        FileMetadataEntity folder = getFileById(folderId);
        if (folder == null || !folder.isDirectory()) {
            return 0;
        }
        List<FileMetadataEntity> children = fileMetadataRepository.findByParentId(UUID.fromString(folderId));
        long total = 0;
        for (FileMetadataEntity child : children) {
            if (child.isDirectory()) {
                total += getTotalSizeOfFolder(child.getId());
            } else {
                total += child.getSize();
            }
        }
        return total;
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
}