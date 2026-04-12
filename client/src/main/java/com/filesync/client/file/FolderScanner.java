package com.filesync.client.file;

import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.SyncStatus;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FolderScanner {
    private final Path syncFolder;

    public FolderScanner(Path syncFolder)
    {
        this.syncFolder = syncFolder;
    }

    public List<FileMetadataDto> scan() throws IOException {
        List<FileMetadataDto> files = new ArrayList<>();
        if (!Files.exists(syncFolder)) {
            Files.createDirectories(syncFolder);
            return files;
        }
        Files.walk(syncFolder)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String relativePath = syncFolder.relativize(file).toString().replace("\\", "/");
                        String hash = FileHasher.computeHash(file);
                        long size = Files.size(file);
                        Instant lastModified = Files.getLastModifiedTime(file).toInstant();
                        FileMetadataDto dto = new FileMetadataDto();
                        dto.setRelativePath(relativePath);
                        dto.setSha256Hash(hash);
                        dto.setSize(size);
                        dto.setLastModified(lastModified);
                        dto.setStatus(SyncStatus.SYNCED);
                        files.add(dto);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        return files;
    }
}