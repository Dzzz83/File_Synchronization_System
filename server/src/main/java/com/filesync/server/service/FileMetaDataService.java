package com.filesync.server.service;

import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
