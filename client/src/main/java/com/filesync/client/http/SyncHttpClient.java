package com.filesync.client.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.dto.SyncRequestDto;
import com.filesync.common.dto.SyncResponseDto;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SyncHttpClient {
    private final WebClient webClient;

    public SyncHttpClient(String baseUrl)
    {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public SyncResponseDto sync(SyncRequestDto request) {
        String raw = webClient.post()
                .uri("/api/sync")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        System.out.println("=== RAW RESPONSE FROM SERVER ===\n" + raw + "\n================================");

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        try {
            return mapper.readValue(raw, SyncResponseDto.class);
        } catch (Exception e) {
            System.err.println("Failed to parse response: " + e.getMessage());
            e.printStackTrace();
            return new SyncResponseDto(); // fallback to empty list
        }
    }

    public void createMetadata(FileMetadataDto dto) {
        webClient.post()
                .uri("/api/files/metadata")
                .bodyValue(dto)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void uploadFile(String fileId, Path localFile) throws IOException
    {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        // create a body part name "file" with the file content wrapped in a FileSystemResource
        builder.part("file", new FileSystemResource(localFile.toFile()));

        webClient.post()
                .uri("/api/files/upload/{fileId}", fileId)
                // tell the server that the request contains multiple part
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build())
                .retrieve()
                // wrap the string respond in Mono
                .bodyToMono(String.class)
                // make the call wait for the upload to finish
                .block();
    }

    public void downloadFile(String fileId, Path destination) throws IOException
    {
        // download a file from the server
        byte[] data = webClient.get()
                // get the url of the file to download
                .uri("/api/files/download/{fileId}", fileId)
                .retrieve()
                // wra the byte[] respond in Mono
                .bodyToMono(byte[].class)
                // make the call wait for the download to finish
                .block();

        // create the parent directory
        // ex: destination = "./sync_folder/subdir/file.txt" ==> create ./sync_folder/subdir
        Files.createDirectories(destination.getParent());
        Files.write(destination, data);
    }

}
