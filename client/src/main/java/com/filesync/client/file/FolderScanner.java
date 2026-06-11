package com.filesync.client.file;

import com.filesync.client.db.LocalMetadataRepository;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.SyncStatus;
import java.io.IOException;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FolderScanner {
    private final Path syncFolder;
    private final LocalMetadataRepository localMetadataRepository;

    public FolderScanner(Path syncFolder, LocalMetadataRepository repository)
    {
        this.syncFolder = syncFolder;
        this.localMetadataRepository = repository;
    }

    public List<FileMetadataDto> scan() throws IOException {
        // create sync folder
        List<FileMetadataDto> files = new ArrayList<>();
        if (!Files.exists(syncFolder)) {
            Files.createDirectories(syncFolder);
            return files;
        }
        // Files.walk returns a Stream<Path> that traverses the folder recursively
        Files.walk(syncFolder)
                // keep only regular files
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        // convert abs path to relative path
                        String relativePath = syncFolder.relativize(file).toString().replace("\\", "/");
                        // get the hash of file content
                        String hash = FileHasher.computeHash(file);
                        // get or create fileId
                        String fileId = localMetadataRepository.getFileId(relativePath);
                        if (fileId == null) {
                            fileId = UUID.randomUUID().toString();
                            // store the mapping now (hash may be temporary, but we'll update it later)
                            localMetadataRepository.saveFile(relativePath, fileId, hash);
                        }
                        // get the file size in bytes
                        long size = Files.size(file);
                        // get the last modification timestamp as an instant
                        Instant lastModified = Files.getLastModifiedTime(file).toInstant();
                        // create a new data transfer object
                        FileMetadataDto dto = new FileMetadataDto();
                        dto.setFileId(fileId);
                        // set the fields
                        dto.setRelativePath(relativePath);
                        dto.setSha256Hash(hash);
                        dto.setSize(size);
                        dto.setLastModified(lastModified);
                        dto.setStatus(SyncStatus.SYNCED);
                        files.add(dto);
                    } catch (IOException | SQLException e) {
                        e.printStackTrace();
                    }
                });
        return files;
    }
}