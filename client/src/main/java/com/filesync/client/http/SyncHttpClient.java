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
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SyncHttpClient {
    private final WebClient webClient;

    public SyncHttpClient(String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)) // 100 MB
                .build();
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
        System.out.println("downloadFile called with fileId='" + fileId + "', destination=" + destination);
        if (fileId == null || fileId.trim().isEmpty()) {
            throw new IllegalArgumentException("File ID cannot be null or empty");
        }
        if (destination == null) {
            throw new IllegalArgumentException("Destination path cannot be null");
        }

        // download a file from the server
        byte[] data = webClient.get()
                // get the url of the file to download
                .uri("/api/files/download/{fileId}", fileId)
                .retrieve()
                // wra the byte[] respond in Mono
                .bodyToMono(byte[].class)
                // make the call wait for the download to finish
                .block();

        if (data == null) {
            throw new IOException("Downloaded data is null for fileId: " + fileId);
        }
        System.out.println("Data length: " + data.length);
        System.out.println("Destination parent: " + destination.getParent());
        // create the parent directory
        // ex: destination = "./sync_folder/subdir/file.txt" ==> create ./sync_folder/subdir
        Files.createDirectories(destination.getParent());
        Files.write(destination, data);
        System.out.println("File written successfully");
    }

    public void uploadLargeFile(String fileId, Path filePath) throws IOException
    {
        ChunkedUploader uploader = new ChunkedUploader(webClient);
        uploader.uploadFile(fileId, filePath);
    }

    public boolean registerUser(String userName, String password, String email)
    {
        try
        {
            Map<String, String> body = Map.of(
                    "username", userName,
                    "password", password,
                    "email", email
            );
            Map<?, ?> response = webClient.post()
                    .uri("/api/users/register")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response == null)
            {
                return false;
            }
            return !response.containsKey("error");
        } catch (Exception e) {
            System.err.println("Registration failed: " + e.getMessage());
            return false;
        }
    }
    public List<FileMetadataDto> getFilesByOwner(String ownerId) {
        try {
            FileMetadataDto[] files = webClient.get()
                    .uri("/api/files/user/{ownerId}", ownerId)
                    .retrieve()
                    .bodyToMono(FileMetadataDto[].class)
                    .block();
            if (files == null) {
                return List.of();
            }
            return List.of(files);
        } catch (Exception e) {
            System.err.println("Failed to get files for owner: " + ownerId);
            e.printStackTrace();
            return List.of();
        }
    }

    public void deleteFile(String fileId) {
        webClient.delete()
                .uri("/api/files/{fileId}", fileId)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public FileMetadataDto getFileMetadata(String fileId) {
        return webClient.get()
                .uri("/api/files/{fileId}", fileId)
                .retrieve()
                .bodyToMono(FileMetadataDto.class)
                .block();
    }
    public String forgotPassword(String email) {
        try {
            Map<String, String> body = Map.of("email", email);
            Map<?, ?> response = webClient.post()
                    .uri("/api/users/forgot-password")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response != null && response.containsKey("token")) {
                return (String) response.get("token");
            }
        } catch (Exception e) {
            System.err.println("Forgot password failed: " + e.getMessage());
        }
        return null;
    }

    public boolean resetPassword(String token, String newPassword) {
        try {
            Map<String, String> body = Map.of("token", token, "newPassword", newPassword);
            Map<?, ?> response = webClient.post()
                    .uri("/api/users/reset-password")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return response != null && !response.containsKey("error");
        } catch (Exception e) {
            System.err.println("Reset password failed: " + e.getMessage());
            return false;
        }
    }
}
