package com.filesync.server.controller;

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

    public FileTransferController(FileStorage fileStorage, PermissionService permissionService) {
        this.fileStorage = fileStorage;
        this.permissionService = permissionService;
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
            // Upload to local files
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
        if (!permissionService.canRead(userId, fileId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No read permission");
            return;
        }

        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileId + "\"");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        try {
            fileStorage.stream(fileId, response.getOutputStream());
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to send file", e);
        }
    }
}