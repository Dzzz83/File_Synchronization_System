package com.filesync.server.controller;

import com.filesync.server.service.FileStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileTransferController {
    private final FileStorageService fileStorageServices;

    public FileTransferController(FileStorageService fileStorageService)
    {
        this.fileStorageServices = fileStorageService;
    }

    @PostMapping("/upload/{fileId}")
    public ResponseEntity<String> uploadFile(@PathVariable("fileId") String fileId, @RequestParam("file") MultipartFile file)
    {
        // save the file to the server's database
        fileStorageServices.save(fileId, file);
        return ResponseEntity.ok("File uploaded successfully");
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable("fileId") String fileId)
    {
        byte[] data = fileStorageServices.load(fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileId + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);

    }
}
