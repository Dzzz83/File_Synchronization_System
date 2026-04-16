package com.filesync.server.service;

import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FileMetaDataService {
    @Autowired
    private FileMetadataRepository repository;
    public FileMetadataEntity saveFileMetaData(FileMetadataEntity entity)
    {
        return repository.save(entity);
    }
    public List<FileMetadataEntity> getFilesByOwner(String ownerId)
    {
        return repository.findByOwnerId(ownerId);
    }

    public FileMetadataEntity getFileById(String fileId) {
        return repository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));
    }

    public void deleteFile(String fileId) {
        repository.deleteById(fileId);
    }
}
