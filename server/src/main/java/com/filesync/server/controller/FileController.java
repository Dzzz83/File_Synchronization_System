package com.filesync.server.controller;

import com.filesync.common.dto.FileMetadataDto;
import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.service.FileMetaDataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileMetaDataService fileMetaDataService;

    // constructor
    public FileController(FileMetaDataService fileMetaDataService)
    {
        this.fileMetaDataService = fileMetaDataService;
    }

    @PostMapping("/metadata")
    public FileMetadataEntity saveMetaData(@RequestBody FileMetadataDto dto) {
        // Convert DTO to entity
        FileMetadataEntity entity = new FileMetadataEntity();
        entity.setId(dto.getFileId());                 // critical: set the ID from client
        entity.setRelativePath(dto.getRelativePath());
        entity.setSha256Hash(dto.getSha256Hash());
        entity.setSize(dto.getSize());
        entity.setLastModified(dto.getLastModified());
        entity.setVersionVectorJson(dto.getVersionVectorJson());
        entity.setOwnerId(dto.getOwnerId());
        entity.setSharedWith(dto.getSharedWith());    // Set<String> directly
        entity.setStatus(dto.getStatus());
        return fileMetaDataService.saveFileMetaData(entity);
    }

    @GetMapping("/user/{ownerId}")
    public List<FileMetadataEntity> getFilesByOwner(@PathVariable("ownerId") String ownerId)
    {
        return fileMetaDataService.getFilesByOwner(ownerId);
    }

}
