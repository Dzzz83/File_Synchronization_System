package com.filesync.server.controller;

import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.dto.CreateFolderRequest;
import com.filesync.common.enums.Permission;
import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.service.FileMetaDataService;
import com.filesync.server.service.PermissionService;
import com.filesync.server.service.QuotaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileMetaDataService fileMetaDataService;
    private final PermissionService permissionService;
    private final QuotaService quotaService;

    public FileController(FileMetaDataService fileMetaDataService,
                          PermissionService permissionService,
                          QuotaService quotaService) {
        this.fileMetaDataService = fileMetaDataService;
        this.permissionService = permissionService;
        this.quotaService = quotaService;
    }

    @PostMapping("/metadata")
    public ResponseEntity<?> saveMetaData(@Valid @RequestBody FileMetadataDto fileMetadataDto,
                                          Authentication authentication) {
        String userId = authentication.getName();
        boolean exists = fileMetaDataService.existsById(fileMetadataDto.getFileId());

        if (exists) {
            // UPDATE EXISTING
            if (!permissionService.canWrite(userId, fileMetadataDto.getFileId())) {
                return ResponseEntity.status(403).body("No write permission");
            }
            FileMetadataEntity oldEntity = fileMetaDataService.getFileById(fileMetadataDto.getFileId());
            long oldSize = getSizeOfEntity(oldEntity);
            FileMetadataEntity entity = convertToEntity(fileMetadataDto);
            FileMetadataEntity saved = fileMetaDataService.saveFileMetaData(entity);
            if (!saved.isDirectory()) {
                long newSize = saved.getSize();
                long delta = newSize - oldSize;
                if (delta != 0) {
                    // Update ancestor folder sizes
                    if (saved.getParentId() != null) {
                        fileMetaDataService.addToAncestors(saved.getParentId(), delta);
                    }
                    // Update quota: delta positive → consume more storage, delta negative → release
                    if (delta > 0) {
                        quotaService.checkAndReserveQuota(userId, delta);
                    } else if (delta < 0) {
                        quotaService.releaseQuota(userId, -delta);
                    }
                }
            }
            return ResponseEntity.ok(convertToDto(saved));
        } else {
            // CREATE NEW
            // Override any client-supplied ownerId with authenticated user
            fileMetadataDto.setOwnerId(userId);

            if (fileMetadataDto.getFolderId() != null) {
                if (!permissionService.canWriteToFolder(userId, fileMetadataDto.getFolderId())) {
                    return ResponseEntity.status(403).body("No write permission on folder");
                }
            }

            if (!fileMetadataDto.isDirectory()) {
                quotaService.checkAndReserveQuota(userId, fileMetadataDto.getSize());
            }

            FileMetadataEntity entity = convertToEntity(fileMetadataDto);
            FileMetadataEntity saved = fileMetaDataService.saveFileMetaData(entity);
            if (!saved.isDirectory() && saved.getSize() > 0 && saved.getParentId() != null) {
                fileMetaDataService.addToAncestors(saved.getParentId(), saved.getSize());
            }
            // File count is already incremented inside checkAndReserveQuota, so no extra call needed
            return ResponseEntity.ok(convertToDto(saved));
        }
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
        FileMetadataEntity entity = fileMetaDataService.getFileById(fileId);
        if (entity == null) return ResponseEntity.notFound().build();
        if (!permissionService.canWrite(userId, fileId)) {
            return ResponseEntity.status(403).build();
        }

        long deletedSize = 0;
        int deletedFileCount = 0;
        if (entity.isDirectory()) {
            deletedSize = fileMetaDataService.getTotalSizeOfFolder(fileId);
            deletedFileCount = fileMetaDataService.getFileCountInFolder(fileId);
            fileMetaDataService.deleteFolderRecursively(fileId);
        } else {
            deletedSize = entity.getSize();
            deletedFileCount = 1;
            fileMetaDataService.deleteFileAndUpdateAncestors(fileId);
        }

        quotaService.releaseQuota(userId, deletedSize);
        // Release file count (one call per file, but for folders we have count)
        quotaService.decrementFileCount(userId, deletedFileCount);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/folder")
    public ResponseEntity<?> createFolder(@Valid @RequestBody CreateFolderRequest request,
                                          Authentication authentication) {
        String userId = authentication.getName();
        String name = request.getName();
        UUID parentId = request.getParentId();
        UUID folderId = request.getFolderId();

        if (folderId != null) {
            if (!permissionService.canWriteToFolder(userId, folderId)) {
                return ResponseEntity.status(403).body("No write permission on shared folder");
            }
        } else {
            if (parentId != null) {
                if (!permissionService.canWrite(userId, parentId.toString())) {
                    return ResponseEntity.status(403).body("No write permission on parent folder");
                }
            }
        }

        FileMetadataEntity folder = fileMetaDataService.createFolder(name, userId, parentId, folderId);
        return ResponseEntity.ok(convertToDto(folder));
    }

    @PutMapping("/{fileId}/parent")
    public ResponseEntity<?> updateParent(@PathVariable("fileId") String fileId,
                                          @RequestBody Map<String, String> request,
                                          Authentication authentication) {
        String userId = authentication.getName();
        String newParentIdStr = request.get("parentId");
        UUID newParentId = null;
        if (newParentIdStr != null && !newParentIdStr.trim().isEmpty()) {
            newParentId = UUID.fromString(newParentIdStr);
        }

        FileMetadataEntity entity = fileMetaDataService.getFileById(fileId);
        if (entity == null) return ResponseEntity.notFound().build();

        if (!permissionService.canWrite(userId, fileId)) {
            return ResponseEntity.status(403).body("No write permission on source item");
        }

        UUID newFolderId = null;
        if (newParentId != null) {
            FileMetadataEntity newParent = fileMetaDataService.getFileById(newParentIdStr);
            if (newParent == null || !newParent.isDirectory()) {
                return ResponseEntity.badRequest().body("Target must be a directory");
            }
            newFolderId = newParent.getFolderId();
            if (newFolderId == null) {
                if (!newParent.getOwnerId().equals(userId)) {
                    return ResponseEntity.status(403).body("Not owner of target folder");
                }
            } else {
                if (!permissionService.canWriteToFolder(userId, newFolderId)) {
                    return ResponseEntity.status(403).body("No write permission on target shared folder");
                }
            }
        }

        if (entity.isDirectory()) {
            fileMetaDataService.moveFolder(fileId, newParentId, newFolderId);
        } else {
            long fileSize = entity.getSize();
            if (entity.getParentId() != null) {
                fileMetaDataService.removeFromAncestors(entity.getParentId(), fileSize);
            }
            entity.setParentId(newParentId);
            entity.setFolderId(newFolderId);
            fileMetaDataService.saveFileMetaData(entity);
            if (newParentId != null && fileSize > 0) {
                fileMetaDataService.addToAncestors(newParentId, fileSize);
            }
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/{ownerId}")
    public ResponseEntity<?> getFilesByOwner(@PathVariable("ownerId") String ownerId,
                                             @RequestParam(name = "parentId", required = false) UUID parentId,
                                             @RequestParam(name = "folderId", required = false) UUID folderId,
                                             Authentication authentication) {
        String userId = authentication.getName();
        if (!ownerId.equals(userId)) {
            return ResponseEntity.status(403).body("Not authorized");
        }

        List<FileMetadataEntity> entities;
        if (folderId != null) {
            if (!permissionService.canReadFolder(userId, folderId)) {
                return ResponseEntity.status(403).body("No access to shared folder");
            }
            if (parentId != null) {
                entities = fileMetaDataService.getFilesByParent(parentId);
            } else {
                entities = fileMetaDataService.getSharedFolderRootFiles(folderId);
            }
        } else {
            if (parentId != null) {
                entities = fileMetaDataService.getFilesByParent(parentId);
            } else {
                entities = fileMetaDataService.getPersonalRootFiles(ownerId);
            }
        }

        List<FileMetadataDto> dtos = entities.stream()
                .map(entity -> {
                    FileMetadataDto dto = convertToDto(entity);
                    Permission perm = computeUserPermission(userId, entity);
                    dto.setUserPermission(perm);
                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    private Permission computeUserPermission(String userId, FileMetadataEntity file) {
        if (file.getFolderId() == null) {
            return file.getOwnerId().equals(userId) ? Permission.WRITE : Permission.NONE;
        } else {
            UUID folderId = file.getFolderId();
            if (permissionService.canWriteToFolder(userId, folderId)) {
                return Permission.WRITE;
            } else if (permissionService.canReadFolder(userId, folderId)) {
                return Permission.READ;
            } else {
                return Permission.NONE;
            }
        }
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
        entity.setDirectory(dto.isDirectory());
        entity.setParentId(dto.getParentId());
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
        dto.setDirectory(entity.isDirectory());
        dto.setParentId(entity.getParentId());
        return dto;
    }

    private long getSizeOfEntity(FileMetadataEntity entity) {
        if (entity.isDirectory()) {
            return 0;
        }
        return entity.getSize();
    }
}