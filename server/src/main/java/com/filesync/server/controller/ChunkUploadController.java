package com.filesync.server.controller;

import com.filesync.common.dto.UploadStatusDto;
import com.filesync.server.storage.ChunkStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api/chunk")
public class ChunkUploadController {

    private final ChunkStorageService chunkStorage;

    public ChunkUploadController(ChunkStorageService chunkStorage) {
        this.chunkStorage = chunkStorage;
    }

    @PostMapping("/upload")
    public ResponseEntity<Void> uploadChunk(
            @RequestParam(name = "fileId") String fileId,
            @RequestParam(name = "chunkIndex") int chunkIndex,
            @RequestParam(name = "totalChunks") int totalChunks,
            @RequestParam(name = "chunk") MultipartFile chunk) throws IOException {
        chunkStorage.saveChunk(fileId, chunkIndex, chunk.getInputStream(), chunk.getSize());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status")
    public ResponseEntity<UploadStatusDto> getStatus(@RequestParam(name = "fileId") String fileId) {
        Set<Integer> uploaded = chunkStorage.getUploadedChunks(fileId);
        return ResponseEntity.ok(new UploadStatusDto(fileId, uploaded, false));
    }

    @PostMapping("/assemble")
    public ResponseEntity<Void> assemble(
            @RequestParam(name = "fileId") String fileId,
            @RequestParam(name = "finalFileId") String finalFileId,
            @RequestParam(name = "totalChunks") int totalChunks) {
        chunkStorage.assembleFile(fileId, finalFileId, totalChunks);
        return ResponseEntity.ok().build();
    }
}