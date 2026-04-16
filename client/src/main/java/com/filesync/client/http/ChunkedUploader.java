package com.filesync.client.http;

import com.filesync.common.dto.UploadStatusDto;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;


import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Set;

public class ChunkedUploader {
    private final WebClient webClient;
    private static final int CHUNK_SIZE = 1024 * 1024; // 1mb

    public ChunkedUploader(WebClient webClient)
    {
        this.webClient = webClient;
    }

    private void assembleFile(String fileId, String finalFileId, int totalChunks) {
        webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/chunk/assemble")
                        .queryParam("fileId", fileId)
                        .queryParam("finalFileId", finalFileId)
                        .queryParam("totalChunks", totalChunks)
                        .build())
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    private Set<Integer> getUploadedChunks(String fileId)
    {
        try
        {
            UploadStatusDto status = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/chunk/status")
                            .queryParam("fileId", fileId)
                            .build())
                    .retrieve()
                    .bodyToMono(UploadStatusDto.class)
                    .block();
            if (status == null)
            {
                return Set.of();
            }
            return status.getUploadedChunks();
        } catch (Exception e) {
            System.err.println("Failed to get upload status for " + fileId);
            return Set.of();
        }
    }
    private byte[] readChunk(Path filePath, int chunkIndex, int totalChunks) throws IOException
    {
        long start = (long) chunkIndex * CHUNK_SIZE;
        long end = Math.min(start + CHUNK_SIZE, Files.size(filePath));
        int length = (int) (end - start);

        byte[] buffer = new byte[length];
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(filePath.toFile(), "r"))
        {
            randomAccessFile.seek(start);
            randomAccessFile.readFully(buffer);
        }
        return buffer;
    }

    private void uploadChunk(String fileId, int chunkIndex, int totalChunks, byte[] data)
    {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("fileId", fileId);
        body.add("chunkIndex", String.valueOf(chunkIndex));
        body.add("totalChunks", String.valueOf(totalChunks));

        ByteArrayResource resource = new ByteArrayResource(data)
        {
           @Override
            public String getFilename()
           {
               return "chunk_" + chunkIndex + ".part";
           }
        };
        body.add("chunk", resource);

        webClient.post()
                .uri("/api/chunk/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }
    public void uploadFile(String fileId, Path filePath) throws IOException
    {
        long fileSize = Files.size(filePath);
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        System.out.println("Starting chunked upload for " + fileId + ", size: " + fileSize + " bytes, chunks: " + totalChunks);

        Set<Integer> uploadedChunks = getUploadedChunks(fileId);
        System.out.println("Already uploaded chunks: " + uploadedChunks);

        for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++)
        {
            if (uploadedChunks.contains(chunkIndex))
            {
                System.out.println("Chunk " + chunkIndex + " already uploaded, skipping.");
                continue;
            }
            byte[] chunkData = readChunk(filePath, chunkIndex, totalChunks);
            uploadChunk(fileId, chunkIndex, totalChunks, chunkData);
            System.out.println("Uploaded chunk " + chunkIndex + "/" + (totalChunks - 1));
        }
        assembleFile(fileId, fileId, totalChunks);
        System.out.println("File assembled successfully:" + fileId);
    }
}
