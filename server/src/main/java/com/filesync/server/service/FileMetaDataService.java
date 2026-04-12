package com.filesync.server.service;

import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FileMetaDataService {
    @Autowired
    private FileMetadataRepository repository;
    public FileMetadataEntity saveFileMetaData(FileMetadataEntity entity)
    {
        return repository.save(entity);
    }
}
