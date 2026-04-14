package com.filesync.server.service;

import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import com.filesync.server.storage.FileStorageService;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class FileContentService {
    private final FileMetadataRepository metadataRepository;
    private final FileStorageService storageService;
    private final HashCalculator hashCalculator;

    public FileContentService(FileMetadataRepository metadataRepository,
                              FileStorageService storageService,
                              HashCalculator hashCalculator) {
        this.metadataRepository = metadataRepository;
        this.storageService = storageService;
        this.hashCalculator = hashCalculator;
    }


    public String readContent(String fileId) {
        // load the file in byte
        byte[] data = storageService.load(fileId);
        return new String(data, StandardCharsets.UTF_8);
    }

    public void writeContent(String fileId, String content) {
        // load the file in bytes
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        // save the byte data
        storageService.save(fileId, new ByteArrayInputStream(bytes), (long) bytes.length);
        FileMetadataEntity entity = metadataRepository.findById(fileId).orElseThrow();
        // compute new hash
        entity.setSha256Hash(hashCalculator.computeHash(bytes));
        // set the new size
        entity.setSize((long) bytes.length);
        // set the time modified
        entity.setLastModified(Instant.now());
        metadataRepository.save(entity);
    }
}