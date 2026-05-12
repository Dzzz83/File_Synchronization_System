package com.filesync.server.controller;

import aj.org.objectweb.asm.commons.TryCatchBlockSorter;
import com.filesync.server.storage.FileStorage;
import com.filesync.server.storage.LocalFileStorage;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/files")
public class FileTransferController {
    private final FileStorage fileStorage;

    public FileTransferController(FileStorage fileStorage)
    {
        this.fileStorage = fileStorage;
    }

    @PostMapping("/upload/{fileId}")
    public ResponseEntity<String> uploadFile(@PathVariable("fileId") String fileId, @RequestParam("file") MultipartFile file)
    {
        // save the file to the server's database
        fileStorage.save(fileId, file);
        System.out.println("Uploading fileId: " + fileId + ", size: " + file.getSize());
        return ResponseEntity.ok("File uploaded successfully");
    }

    @GetMapping("/download/{fileId}")
    public void downloadFile(@PathVariable("fileId") String fileId, HttpServletResponse response)
    {
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileId + "\"");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        try
        {
            fileStorage.stream(fileId, response.getOutputStream());
            response.getOutputStream().flush();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to send file", e);
        }
    }


}
