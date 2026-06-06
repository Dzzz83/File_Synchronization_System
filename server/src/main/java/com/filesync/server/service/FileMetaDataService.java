package com.filesync.server.service;

import com.filesync.common.enums.SyncStatus;
import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FileMetaDataService {
    @Autowired
    private FileMetadataRepository fileMetadataRepository;
    public FileMetadataEntity saveFileMetaData(FileMetadataEntity entity)
    {
        return fileMetadataRepository.save(entity);
    }
    public List<FileMetadataEntity> getFilesByOwner(String ownerId)
    {
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

    public FileMetadataEntity updateFileMetaData(FileMetadataEntity entity) {
        if (!fileMetadataRepository.existsById(entity.getId())) {
            throw new RuntimeException("File metadata not found for update: " + entity.getId());
        }
        return fileMetadataRepository.save(entity);
    }

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
        folder.setSize(0);
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

}
