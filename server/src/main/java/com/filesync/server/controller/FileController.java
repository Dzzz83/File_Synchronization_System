package com.filesync.server.controller;

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
    public FileMetadataEntity saveMetaData(@RequestBody FileMetadataEntity fileMetadataEntity)
    {
        return fileMetaDataService.saveFileMetaData(fileMetadataEntity);
    }

    @GetMapping("/user/{ownerId}")
    public List<FileMetadataEntity> getFilesByOwner(@PathVariable("ownerId") String ownerId)
    {
        return fileMetaDataService.getFilesByOwner(ownerId);
    }

}
