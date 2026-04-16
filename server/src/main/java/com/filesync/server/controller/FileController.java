package com.filesync.server.controller;

import com.filesync.common.dto.FileMetadataDto;
import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.service.FileMetaDataService;
import com.filesync.server.storage.FileStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileMetaDataService fileMetaDataService;
    private final FileStorageService fileStorageService;

    // constructor
    public FileController(FileMetaDataService fileMetaDataService, FileStorageService fileStorageService)
    {
        this.fileMetaDataService = fileMetaDataService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/metadata")
    public FileMetadataEntity saveMetaData(@RequestBody FileMetadataDto dto) {
        // Convert DTO to entity
        FileMetadataEntity entity = new FileMetadataEntity();
        entity.setId(dto.getFileId());
        entity.setRelativePath(dto.getRelativePath());
        entity.setSha256Hash(dto.getSha256Hash());
        entity.setSize(dto.getSize());
        entity.setLastModified(dto.getLastModified());
        entity.setVersionVectorJson(dto.getVersionVectorJson());
        entity.setOwnerId(dto.getOwnerId());
        entity.setSharedWith(dto.getSharedWith());
        entity.setStatus(dto.getStatus());
        return fileMetaDataService.saveFileMetaData(entity);
    }
    private FileMetadataDto convertToDto(FileMetadataEntity entity) {
        FileMetadataDto dto = new FileMetadataDto();
        dto.setFileId(entity.getId());
        dto.setRelativePath(entity.getRelativePath());
        dto.setSha256Hash(entity.getSha256Hash());
        dto.setSize(entity.getSize());
        dto.setLastModified(entity.getLastModified());
        dto.setVersionVectorJson(entity.getVersionVectorJson());
        dto.setOwnerId(entity.getOwnerId());
        dto.setSharedWith(entity.getSharedWith());
        dto.setStatus(entity.getStatus());
        return dto;
    }

    @GetMapping("/user/{ownerId}")
    public FileMetadataDto[] getFilesByOwner(@PathVariable("ownerId") String ownerId) {
        List<FileMetadataEntity> entities = fileMetaDataService.getFilesByOwner(ownerId);
        return entities.stream()
                .map(this::convertToDto)
                .toArray(FileMetadataDto[]::new);
    }

    @GetMapping("/{fileId}")
    public FileMetadataDto getFileById(@PathVariable("fileId") String fileId) {
        FileMetadataEntity entity = fileMetaDataService.getFileById(fileId);
        // Convert entity to DTO
        FileMetadataDto dto = new FileMetadataDto();
        dto.setFileId(entity.getId());
        dto.setRelativePath(entity.getRelativePath());
        dto.setSha256Hash(entity.getSha256Hash());
        dto.setSize(entity.getSize());
        dto.setLastModified(entity.getLastModified());
        dto.setVersionVectorJson(entity.getVersionVectorJson());
        dto.setOwnerId(entity.getOwnerId());
        dto.setSharedWith(entity.getSharedWith());
        dto.setStatus(entity.getStatus());
        return dto;
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable("fileId") String fileId) {
        // 1. Delete metadata from database
        fileMetaDataService.deleteFile(fileId);
        // 2. Delete the actual file from disk
        fileStorageService.delete(fileId);
        return ResponseEntity.ok().build();
    }


}
