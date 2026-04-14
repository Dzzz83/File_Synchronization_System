package com.filesync.server.conflict.detector;

import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import org.springframework.stereotype.Service;

@Service
public class ConflictDetector {
    private final FileMetadataRepository metadataRepository;

    public ConflictDetector(FileMetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    public boolean hasConflict(String fileId, String originalHash) {
        FileMetadataEntity current = metadataRepository.findById(fileId).orElseThrow();
        return !current.getSha256Hash().equals(originalHash);
    }
}