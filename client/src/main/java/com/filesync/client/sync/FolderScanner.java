package com.filesync.client.sync;

import com.filesync.client.files.FileExplorerController;
import com.filesync.client.util.FileHasher;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.SyncStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FolderScanner {
    private final Path syncFolder;
    private static final Logger log = LoggerFactory.getLogger(FolderScanner.class);

    // Remove the repository field
    public FolderScanner(Path syncFolder) {
        this.syncFolder = syncFolder;
    }

    public List<FileMetadataDto> scan() throws IOException {
        List<FileMetadataDto> files = new ArrayList<>();
        if (!Files.exists(syncFolder)) {
            Files.createDirectories(syncFolder);
            return files;
        }

        log.info("🔍 Scanning local folder: {}", syncFolder.toAbsolutePath());

        Files.walk(syncFolder)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String relativePath = syncFolder.relativize(file).toString().replace("\\", "/");
                        String fileId = UUID.nameUUIDFromBytes(relativePath.getBytes()).toString();
                        String hash = FileHasher.computeHash(file);
                        long size = Files.size(file);
                        Instant lastModified = Files.getLastModifiedTime(file).toInstant();

                        log.debug("📄 Found file: {} (size: {}, hash: {})", relativePath, size, hash);

                        FileMetadataDto dto = new FileMetadataDto();
                        dto.setFileId(fileId);
                        dto.setRelativePath(relativePath);
                        dto.setSha256Hash(hash);
                        dto.setSize(size);
                        dto.setLastModified(lastModified);
                        dto.setStatus(SyncStatus.SYNCED);
                        files.add(dto);
                    } catch (IOException e) {
                        log.error("Error scanning file: {}", file, e);
                    }
                });

        log.info("📊 Scan complete: {} files found", files.size());
        return files;
    }
}