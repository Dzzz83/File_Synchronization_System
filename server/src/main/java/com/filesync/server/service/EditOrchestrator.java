package com.filesync.server.service;

import com.filesync.common.dto.ConflictContextDto;
import com.filesync.server.conflict.detector.ConflictDetector;
import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import org.springframework.stereotype.Service;

@Service
public class EditOrchestrator {
    private final FileMetadataRepository metadataRepository;
    private final FileContentService contentService;
    private final ConflictDetector conflictDetector;

    public EditOrchestrator(FileMetadataRepository metadataRepository,
                            FileContentService contentService,
                            ConflictDetector conflictDetector) {
        this.metadataRepository = metadataRepository;
        this.contentService = contentService;
        this.conflictDetector = conflictDetector;
    }

    public FileMetadataEntity getFileMetadata(String fileId) {
        return metadataRepository.findById(fileId).orElseThrow();
    }

    public String getFileContent(String fileId) {
        return contentService.readContent(fileId);
    }

    public ConflictContextDto trySave(String fileId, String originalHash, String newContent) {
        if (conflictDetector.hasConflict(fileId, originalHash)) {
            String serverContent = contentService.readContent(fileId);
            return new ConflictContextDto(fileId, serverContent, newContent);
        } else {
            contentService.writeContent(fileId, newContent);
            return null;
        }
    }

    public void resolveAndSave(String fileId, String resolvedContent) {
        contentService.writeContent(fileId, resolvedContent);
    }
}