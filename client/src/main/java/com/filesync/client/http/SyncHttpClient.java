package com.filesync.client.http;

import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.dto.SyncRequestDto;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class SyncHttpClient {
    private final WebClient webClient;
    private String authToken;
    private final ChunkedUploader chunkedUploader;

    public SyncHttpClient(String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)) // 100 MB
                .build();
        this.chunkedUploader = new ChunkedUploader(webClient);
    }

    public String login(String username, String password)
    {
        try
        {
            Map<String, String> body = Map.of("username", username, "password", password);
            Map<?, ?> response = webClient.post()
                    .uri("/api/auth/login")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response != null && response.containsKey("token"))
            {
                this.authToken = (String) response.get("token");
                return this.authToken;
            }
            throw new RuntimeException("Login failed: no token in response");
        } catch (WebClientResponseException e)
        {
            if (e.getStatusCode().value() == 401)
            {
                throw new RuntimeException("Invalid username or password");
            }
            throw new RuntimeException("Login error: " + e.getMessage());
        }
    }

    private WebClient.RequestHeadersSpec<?> addAuth(WebClient.RequestHeadersSpec<?> spec)
    {
        if (authToken != null && !authToken.isEmpty())
        {
            return spec.header("Authorization", "Bearer " + authToken);
        }
        return spec;
    }

    public String startSync(SyncRequestDto request) {
        Map<?, ?> response = addAuth(webClient.post().uri("/api/sync/start").bodyValue(request))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        return (String) response.get("taskId");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSyncStatus(String taskId) {
        return addAuth(webClient.get().uri("/api/sync/status/{taskId}", taskId))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public void createMetadata(FileMetadataDto dto) {
        addAuth(webClient.post().uri("/api/files/metadata").bodyValue(dto))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void uploadFile(String fileId, Path localFile) throws IOException
    {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        // create a body part name "file" with the file content wrapped in a FileSystemResource
        builder.part("file", new FileSystemResource(localFile.toFile()));

        addAuth(webClient.post()
                .uri("/api/files/upload/{fileId}", fileId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
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
        byte[] data = addAuth(webClient.get().uri("/api/files/download/{fileId}", fileId))
                .retrieve()
                .bodyToMono(byte[].class)
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

    public void uploadLargeFile(String fileId, Path filePath) throws IOException {
        if (authToken == null || authToken.isEmpty()) {
            throw new IllegalStateException("Cannot upload large file: not logged in. Call login() first.");
        }
        chunkedUploader.setAuthToken(authToken);
        chunkedUploader.uploadFile(fileId, filePath);
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
            FileMetadataDto[] files = addAuth(webClient.get().uri("/api/files/user/{ownerId}", ownerId))
                    .retrieve()
                    .bodyToMono(FileMetadataDto[].class)
                    .block();
            return files == null ? List.of() : List.of(files);
        } catch (Exception e) {
            System.err.println("Failed to get files: " + e.getMessage());
            return List.of();
        }
    }

    public void deleteFile(String fileId) {
        addAuth(webClient.delete().uri("/api/files/{fileId}", fileId))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public FileMetadataDto getFileMetadata(String fileId) {
        return addAuth(webClient.get().uri("/api/files/{fileId}", fileId))
                .retrieve()
                .bodyToMono(FileMetadataDto.class)
                .block();
    }

    public void forgotPassword(String email) {
        try {
            Map<String, String> body = Map.of("email", email);
            webClient.post()
                    .uri("/api/users/forgot-password")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (Exception e) {
            throw new RuntimeException("Forgot password request failed: " + e.getMessage());
        }
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

    public void close()
    {
        chunkedUploader.close();
    }

    public void logout() {
        this.authToken = null;
    }
}
