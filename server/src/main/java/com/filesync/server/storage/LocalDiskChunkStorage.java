package com.filesync.server.storage;

import jakarta.persistence.criteria.CriteriaBuilder;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LocalDiskChunkStorage implements ChunkStorageService
{
    private final Path chunkRoot = Paths.get("./uploads/chunks");
    private final FileStorageService fileStorageService;

    public LocalDiskChunkStorage(FileStorageService fileStorageService) throws IOException
    {
        this.fileStorageService = fileStorageService;
        Files.createDirectories(chunkRoot);
    }

    private Path getChunkPath(String fileId, int chunkIndex)
    {
        return chunkRoot.resolve(fileId).resolve(chunkIndex + ".part");
    }

    @Override
    public void saveChunk(String fileId, int chunkIndex, InputStream data, long length)
    {
        try
        {
            Path fileDir = chunkRoot.resolve(fileId);
            Files.createDirectories(fileDir);
            Path chunkPath = fileDir.resolve(chunkIndex + ".part");
            Files.copy(data, chunkPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save chunk " + chunkIndex + " for file " + fileId, e);
        }
    }

    @Override
    public InputStream readChunk(String fileId, int chunkIndex)
    {
        try
        {
            return Files.newInputStream(getChunkPath(fileId, chunkIndex));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read chunk " + chunkIndex + " for file " + fileId, e);
        }
    }

    @Override
    public Set<Integer> getUploadedChunks(String fileId) {
        Path fileDir = chunkRoot.resolve(fileId);
        if (!Files.exists(fileDir))
        {
            return Set.of();
        }
        try
        {
            return Files.list(fileDir)
                    .map(p -> Integer.parseInt(p.getFileName().toString().replace(".part", "")))
                    .collect(Collectors.toSet());
        }catch (IOException e)
        {
            return Set.of();
        }
    }

    @Override
    public void deleteChunks(String fileId) {
        Path fileDir = chunkRoot.resolve(fileId);
        if (Files.exists(fileDir)) {
            try {
                Files.walk(fileDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); }
                            catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }
    }


    @Override
    public void assembleFile(String fileId, String destinationFileId) {
        Set<Integer> chunks = getUploadedChunks(fileId);
        if (chunks.isEmpty())
        {
            throw new IllegalStateException("No chunks found for file " + fileId);
        }
        int maxChunk = chunks.stream().max(Integer::compareTo).orElse(-1);
        for (int i = 0; i <= maxChunk; i++)
        {
            if (!chunks.contains(i))
            {
                throw new IllegalStateException("Missing chunk " + i + " for file " + fileId);
            }
        }
        Path tempFile = null;
        try
        {
            tempFile = Files.createTempFile("assemble_", ".tmp");
            try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile.toFile()))
            {
                for (int i = 0; i <= maxChunk; i++)
                {
                    try (InputStream inputStream = readChunk(fileId, i))
                    {
                        inputStream.transferTo(fileOutputStream);
                    }
                }
            }
            try (InputStream inputStream = Files.newInputStream(tempFile))
            {
                fileStorageService.save(destinationFileId, inputStream, Files.size(tempFile));
            }
        } catch (IOException e)
        {
            throw new RuntimeException("Failed to assemble file " + fileId, e);
        }
        finally {
            if (tempFile != null)
            {
                try
                {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            }
            deleteChunks(fileId);
        }
    }
}
