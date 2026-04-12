package com.filesync.server.controller;

import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.service.FileMetaDataService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
