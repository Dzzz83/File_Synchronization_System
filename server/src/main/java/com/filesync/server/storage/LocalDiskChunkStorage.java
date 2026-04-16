package com.filesync.server.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LocalDiskChunkStorage implements ChunkStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskChunkStorage.class);
    private final Path chunkRoot = Paths.get("./uploads/chunks");
    private final FileStorageService fileStorageService;

    public LocalDiskChunkStorage(FileStorageService fileStorageService) throws IOException {
        this.fileStorageService = fileStorageService;
        Files.createDirectories(chunkRoot);
        log.info("Chunk storage initialized at {}", chunkRoot.toAbsolutePath());
    }

    private Path getChunkPath(String fileId, int chunkIndex) {
        return chunkRoot.resolve(fileId).resolve(chunkIndex + ".part");
    }

    @Override
    public void saveChunk(String fileId, int chunkIndex, InputStream data, long length) {
        try {
            Path fileDir = chunkRoot.resolve(fileId);
            Files.createDirectories(fileDir);
            Path chunkPath = fileDir.resolve(chunkIndex + ".part");
            Files.copy(data, chunkPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Saved chunk {} for fileId {} ({} bytes)", chunkIndex, fileId, length);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save chunk " + chunkIndex + " for file " + fileId, e);
        }
    }

    @Override
    public InputStream readChunk(String fileId, int chunkIndex) {
        try {
            return Files.newInputStream(getChunkPath(fileId, chunkIndex));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read chunk " + chunkIndex + " for file " + fileId, e);
        }
    }

    @Override
    public Set<Integer> getUploadedChunks(String fileId) {
        Path fileDir = chunkRoot.resolve(fileId);
        if (!Files.exists(fileDir)) return Set.of();
        try (var stream = Files.list(fileDir)) {
            return stream
                    .map(p -> Integer.parseInt(p.getFileName().toString().replace(".part", "")))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            log.warn("Could not list chunks for {}", fileId, e);
            return Set.of();
        }
    }

    @Override
    public void deleteChunks(String fileId) {
        Path fileDir = chunkRoot.resolve(fileId);
        if (Files.exists(fileDir)) {
            try (var walk = Files.walk(fileDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); }
                            catch (IOException ignored) {}
                        });
                log.info("Deleted chunks for {}", fileId);
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void assembleFile(String fileId, String destinationFileId, int totalChunks) {
        System.out.println("=== assembleFile called: fileId=" + fileId + ", destId=" + destinationFileId + ", totalChunks=" + totalChunks);
        Set<Integer> chunks = getUploadedChunks(fileId);
        if (chunks.isEmpty()) {
            throw new IllegalStateException("No chunks found for " + fileId);
        }
        // Validate all chunks present (0 to totalChunks-1)
        for (int i = 0; i < totalChunks; i++) {
            if (!chunks.contains(i)) {
                throw new IllegalStateException("Missing chunk " + i);
            }
        }
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("assemble_", ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                for (int i = 0; i < totalChunks; i++) {
                    try (InputStream is = readChunk(fileId, i)) {
                        is.transferTo(fos);
                    }
                }
            }
            try (InputStream assembledStream = Files.newInputStream(tempFile)) {
                fileStorageService.save(destinationFileId, assembledStream, Files.size(tempFile));
            }
            Path finalPath = Paths.get("./uploads").resolve(destinationFileId);
            System.out.println("Final file path: " + finalPath.toAbsolutePath() + ", exists: " + Files.exists(finalPath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to assemble file " + fileId, e);
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
            deleteChunks(fileId);
            System.out.println("Chunks deleted for " + fileId);
        }
    }
}