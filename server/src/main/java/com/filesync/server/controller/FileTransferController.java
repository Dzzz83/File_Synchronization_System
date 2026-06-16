package com.filesync.server.controller;

import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.service.FileMetaDataService;
import com.filesync.server.service.PermissionService;
import com.filesync.server.storage.FileStorage;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileTransferController {
    private final FileStorage fileStorage;
    private final PermissionService permissionService;
    private final FileMetaDataService fileMetaDataService; // added for metadata lookup

    public FileTransferController(FileStorage fileStorage,
                                  PermissionService permissionService,
                                  FileMetaDataService fileMetaDataService) {
        this.fileStorage = fileStorage;
        this.permissionService = permissionService;
        this.fileMetaDataService = fileMetaDataService;
    }

    @PostMapping("/upload/{fileId}")
    public ResponseEntity<String> uploadFile(@PathVariable("fileId") String fileId,
                                             @RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "folderId", required = false) UUID folderId,
                                             Authentication authentication)  {
        String userId = authentication.getName();

        if (folderId != null) {
            // upload to a shared folder
            if (!permissionService.canWriteToFolder(userId, folderId)) {
                return ResponseEntity.status(403).body("No write permission on folder");
            }
        } else {
            // Upload to personal file – check write permission on the file (must exist)
            if (!permissionService.canWrite(userId, fileId)) {
                return ResponseEntity.status(403).body("No write permission on this file");
            }
        }

        fileStorage.save(fileId, file);
        System.out.println("Uploaded fileId: " + fileId + ", size: " + file.getSize() + ", folderId: " + folderId);
        return ResponseEntity.ok("File uploaded successfully");
    }

    @GetMapping("/download/{fileId}")
    public void downloadFile(@PathVariable("fileId") String fileId,
                             HttpServletResponse response,
                             Authentication authentication) throws IOException {
        String userId = authentication.getName();

        // 1. Check if file metadata exists
        FileMetadataEntity file = fileMetaDataService.getFileById(fileId);
        if (file == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
            return;
        }

        // 2. If it's a directory, we cannot download it
        if (file.isDirectory()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Cannot download a directory");
            return;
        }

        // 3. Check permission
        if (!permissionService.canRead(userId, fileId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No read permission");
            return;
        }

        // 4. Stream the file content
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileId + "\"");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        try {
            fileStorage.stream(fileId, response.getOutputStream());
            response.getOutputStream().flush();
        } catch (RuntimeException e) {
            // Catch runtime exceptions (e.g., from S3 when key is missing)
            if (e.getMessage() != null && e.getMessage().contains("File not found")) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "File content missing");
            } else {
                throw new RuntimeException("Failed to send file", e);
            }
        } catch (IOException e) {
            if (e.getMessage() != null && (e.getMessage().contains("not found") || e.getCause() instanceof java.nio.file.NoSuchFileException)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "File content missing");
            } else {
                throw new RuntimeException("Failed to send file", e);
            }
        }
    }
}