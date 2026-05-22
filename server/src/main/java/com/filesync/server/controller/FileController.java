package com.filesync.server.controller;

import com.filesync.common.dto.FileMetadataDto;
import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.service.FileMetaDataService;
import com.filesync.server.service.PermissionService;
import com.filesync.server.storage.FileStorage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileMetaDataService fileMetaDataService;
    private final FileStorage fileStorage;
    private final PermissionService permissionService;

    // constructor
    public FileController(FileMetaDataService fileMetaDataService, FileStorage fileStorage,
                          PermissionService permissionService)
    {
        this.fileMetaDataService = fileMetaDataService;
        this.fileStorage = fileStorage;
        this.permissionService = permissionService;
    }

    @PostMapping("/metadata")
    public ResponseEntity<?> saveMetaData(@RequestBody FileMetadataDto fileMetadataDto, Authentication authentication) {
        String userId = authentication.getName();
        boolean exists = fileMetaDataService.existsById(fileMetadataDto.getFileId());

        if (exists)
        {
            // update existing files if the user has write permission
            if (!permissionService.canWrite(userId, fileMetadataDto.getFileId()))
            {
                return ResponseEntity.status(403).body("No write permission");
            }
            FileMetadataEntity entity = convertToEntity(fileMetadataDto);
            FileMetadataEntity saved = fileMetaDataService.saveFileMetaData(entity);
            return ResponseEntity.ok(convertToDto(saved));
        }
        else
        {
            // New file: check folder or personal ownership
            if (fileMetadataDto.getFolderId() != null)
            {
                if (!permissionService.canWriteToFolder(userId, fileMetadataDto.getFolderId()))
                {
                    return ResponseEntity.status(403).body("No write permission on folder");
                }
            }
            else
            {
                if (!fileMetadataDto.getOwnerId().equals(userId))
                {
                    return ResponseEntity.status(403).body("Not owner of personal file");
                }
            }
            FileMetadataEntity entity = convertToEntity(fileMetadataDto);
            FileMetadataEntity saved = fileMetaDataService.saveFileMetaData(entity);
            return ResponseEntity.ok(convertToDto(saved));
        }
    }

    @GetMapping("/user/{ownerId}")
    public ResponseEntity<?> getFilesByOwner(@PathVariable("ownerId") String ownerId,
                                             @RequestParam(value = "folderId", required = false) UUID folderId,
                                             Authentication authentication) {
        String userId = authentication.getName();
        if (!ownerId.equals(userId)) {
            return ResponseEntity.status(403).body("Not authorized");
        }

        List<FileMetadataEntity> entities;
        if (folderId != null) {
            // Shared folder – user must have at least READ permission
            if (!permissionService.canReadFolder(userId, folderId)) {
                return ResponseEntity.status(403).body("No access to this folder");
            }
            entities = fileMetaDataService.getFilesByFolder(folderId);
        } else {
            // Personal files
            entities = fileMetaDataService.getPersonalFilesByOwner(ownerId);
        }

        List<FileMetadataDto> dtos = entities.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<?> getFileById(@PathVariable("fileId") String fileId,
                                         Authentication authentication) {
        String userId = authentication.getName();
        if (!permissionService.canRead(userId, fileId)) {
            return ResponseEntity.status(403).body("No read permission");
        }
        FileMetadataEntity entity = fileMetaDataService.getFileById(fileId);
        return ResponseEntity.ok(convertToDto(entity));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable("fileId") String fileId,
                                           Authentication authentication) {
        String userId = authentication.getName();
        if (!permissionService.canWrite(userId, fileId)) {
            return ResponseEntity.status(403).build();
        }
        fileMetaDataService.deleteFile(fileId);
        fileStorage.delete(fileId);
        return ResponseEntity.ok().build();
    }

    private FileMetadataEntity convertToEntity(FileMetadataDto dto) {
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
        entity.setFolderId(dto.getFolderId());
        return entity;
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
        dto.setFolderId(entity.getFolderId());
        return dto;
    }
}