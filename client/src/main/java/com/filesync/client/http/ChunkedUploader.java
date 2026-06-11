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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ChunkedUploader {
    private final WebClient webClient;
    private static final int CHUNK_SIZE = 5 * 1024 * 1024;
    private static final int MAX_CONCURRENT_CHUNKS = 5;
    private String authToken;
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_CHUNKS);

    public ChunkedUploader(WebClient webClient) {
        this.webClient = webClient;
    }

    private WebClient.RequestHeadersSpec<?> addAuth(WebClient.RequestHeadersSpec<?> spec) {
        if (authToken != null && !authToken.isEmpty()) {
            return spec.header("Authorization", "Bearer " + authToken);
        }
        return spec;
    }

    private void assembleFile(String fileId, String finalFileId, int totalChunks) {
        if (authToken == null || authToken.isEmpty()) {
            throw new IllegalStateException("JWT token not set in ChunkedUploader.");
        }
        addAuth(webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/chunk/assemble")
                        .queryParam("fileId", fileId)
                        .queryParam("finalFileId", finalFileId)
                        .queryParam("totalChunks", totalChunks)
                        .build()))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    private Set<Integer> getUploadedChunks(String fileId) {
        UploadStatusDto status = addAuth(webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/chunk/status")
                        .queryParam("fileId", fileId)
                        .build()))
                .retrieve()
                .bodyToMono(UploadStatusDto.class)
                .block();
        if (status == null) {
            throw new RuntimeException("Failed to get upload status for " + fileId);
        }
        return status.getUploadedChunks();
    }

    private byte[] readChunk(Path filePath, int chunkIndex) throws IOException {
        long start = (long) chunkIndex * CHUNK_SIZE;
        long end = Math.min(start + CHUNK_SIZE, Files.size(filePath));
        int length = (int) (end - start);
        byte[] buffer = new byte[length];
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            raf.seek(start);
            raf.readFully(buffer);
        }
        return buffer;
    }

    private void uploadChunk(String fileId, int chunkIndex, int totalChunks, byte[] data) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("fileId", fileId);
        body.add("chunkIndex", String.valueOf(chunkIndex));
        body.add("totalChunks", String.valueOf(totalChunks));
        ByteArrayResource resource = new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return "chunk_" + chunkIndex + ".part";
            }
        };
        body.add("chunk", resource);
        addAuth(webClient.post()
                .uri("/api/chunk/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public void uploadFile(String fileId, Path filePath) throws IOException {
        long fileSize = Files.size(filePath);
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        Set<Integer> uploadedChunks = getUploadedChunks(fileId);
        List<Integer> chunksToUpload = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            if (!uploadedChunks.contains(i)) {
                chunksToUpload.add(i);
            }
        }
        if (chunksToUpload.isEmpty()) {
            assembleFile(fileId, fileId, totalChunks);
            return;
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int chunkIndex : chunksToUpload) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    byte[] chunkData = readChunk(filePath, chunkIndex);
                    uploadChunk(fileId, chunkIndex, totalChunks, chunkData);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to upload chunk " + chunkIndex, e);
                }
            }, executor);
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        assembleFile(fileId, fileId, totalChunks);
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
}