package com.filesync.client.http;

import com.filesync.common.dto.*;
import com.filesync.common.enums.Permission;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class SyncHttpClient {
    private final WebClient webClient;
    private String authToken;
    private final ChunkedUploader chunkedUploader;

    public SyncHttpClient(String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .filter((request, next) -> {
                    request.headers().forEach((k, v) -> System.out.println("  " + k + ": " + v));
                    return next.exchange(request);
                })
                .build();
        this.chunkedUploader = new ChunkedUploader(webClient);
    }

    public String login(String loginInput, String password) {
        try {
            Map<String, String> body = Map.of("username", loginInput, "password", password);
            Map<?, ?> response = webClient.post()
                    .uri("/api/auth/login")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response != null && response.containsKey("token")) {
                this.authToken = (String) response.get("token");
                return (String) response.get("username");
            }
            throw new RuntimeException("Login failed: no token in response");
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new RuntimeException("Invalid username or password");
            }
            throw new RuntimeException("Login error: " + e.getMessage());
        }
    }

    public void moveFile(String fileId, String newParentId) {
        Map<String, Object> body = new HashMap<>();
        body.put("parentId", (newParentId == null || newParentId.isEmpty()) ? null : newParentId);
        addAuth(webClient.put().uri("/api/files/{fileId}/parent", fileId).bodyValue(body))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    private WebClient.RequestHeadersSpec<?> addAuth(WebClient.RequestHeadersSpec<?> spec) {
        if (authToken != null && !authToken.isEmpty()) {
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

    public void uploadFile(String fileId, Path localFile) throws IOException {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new FileSystemResource(localFile.toFile()));
        addAuth(webClient.post()
                .uri("/api/files/upload/{fileId}", fileId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void downloadFile(String fileId, Path destination) throws IOException {
        downloadFile(fileId, destination, null);
    }

    public void downloadFile(String fileId, Path destination, ChunkedUploader.ProgressCallback progressCallback) throws IOException {
        if (fileId == null || fileId.trim().isEmpty()) {
            throw new IllegalArgumentException("File ID cannot be null or empty");
        }
        if (destination == null) {
            throw new IllegalArgumentException("Destination path cannot be null");
        }
        Files.createDirectories(destination.getParent());

        // Get total size for progress reporting
        long totalBytes = -1;
        try {
            FileMetadataDto meta = getFileMetadata(fileId);
            totalBytes = meta.getSize();
        } catch (Exception e) {
        }

        Flux<DataBuffer> flux = addAuth(webClient.get().uri("/api/files/download/{fileId}", fileId))
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .timeout(Duration.ofMinutes(5));

        // Write manually with progress tracking
        try (java.io.OutputStream os = Files.newOutputStream(destination)) {
            final long finalTotal = totalBytes;
            final AtomicLong bytesWritten = new AtomicLong(0);

            // Blocking collect: iterate over each DataBuffer
            flux.toIterable().forEach(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                try {
                    os.write(bytes);
                    long written = bytesWritten.addAndGet(bytes.length);
                    if (progressCallback != null && finalTotal > 0) {
                        progressCallback.onProgress(written, finalTotal);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    DataBufferUtils.release(buffer);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException("Download failed", e);
        }
    }

    public List<FileMetadataDto> getFiles(String ownerId, UUID sharedFolderId, UUID parentId) {
        try {
            List<String> queryParams = new ArrayList<>();
            if (sharedFolderId != null) queryParams.add("folderId=" + sharedFolderId);
            if (parentId != null) queryParams.add("parentId=" + parentId);
            String uri = "/api/files/user/" + ownerId;
            if (!queryParams.isEmpty()) uri += "?" + String.join("&", queryParams);
            FileMetadataDto[] files = addAuth(webClient.get().uri(uri))
                    .retrieve()
                    .bodyToMono(FileMetadataDto[].class)
                    .block();
            return files == null ? List.of() : List.of(files);
        } catch (Exception e) {
            return List.of();
        }
    }

    public void uploadFile(String fileId, Path localFile, UUID folderId) throws IOException {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new FileSystemResource(localFile.toFile()));
        String uri = "/api/files/upload/" + fileId;
        if (folderId != null) {
            uri += "?folderId=" + folderId;
        }
        addAuth(webClient.post().uri(uri)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void uploadLargeFile(String fileId, Path filePath, UUID folderId, ChunkedUploader.ProgressCallback progressCallback) throws IOException {
        if (authToken == null || authToken.isEmpty()) {
            throw new IllegalStateException("Cannot upload large file: not logged in.");
        }
        chunkedUploader.setAuthToken(authToken);
        chunkedUploader.uploadFile(fileId, filePath, progressCallback);
    }

    public void deleteFile(String fileId, UUID folderId) {
        String uri = "/api/files/" + fileId;
        if (folderId != null) {
            uri += "?folderId=" + folderId;
        }
        addAuth(webClient.delete().uri(uri))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public void setAuthToken(String token) {
        this.authToken = token;
        this.chunkedUploader.setAuthToken(token);
    }

    public boolean registerUser(String userName, String password, String email) {
        try {
            Map<String, String> body = Map.of("username", userName, "password", password, "email", email);
            Map<?, ?> response = webClient.post()
                    .uri("/api/users/register")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return response != null && !response.containsKey("error");
        } catch (Exception e) {
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
            return false;
        }
    }

    public List<SharedFolderDto> getUserSharedFolders(String userId) {
        SharedFolderDto[] folders = addAuth(webClient.get()
                .uri("/api/shared-folders/user/{userId}", userId))
                .retrieve()
                .bodyToMono(SharedFolderDto[].class)
                .block();
        return folders == null ? List.of() : List.of(folders);
    }

    public void createSharedFolder(String name, List<MemberDto> members) {
        CreateFolderDto dto = new CreateFolderDto();
        dto.setName(name);
        dto.setMembers(members);
        addAuth(webClient.post().uri("/api/shared-folders").bodyValue(dto))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public List<UserSearchResult> searchUsers(String query) {
        UserSearchResult[] results = addAuth(webClient.get()
                .uri("/api/users/search?q={query}", query))
                .retrieve()
                .bodyToMono(UserSearchResult[].class)
                .block();
        return results == null ? List.of() : List.of(results);
    }

    public void addMemberToFolder(UUID folderId, String userId, Permission permission) {
        MemberDto member = new MemberDto();
        member.setUserId(userId);
        member.setPermission(permission);
        addAuth(webClient.post()
                .uri("/api/shared-folders/{folderId}/members", folderId)
                .bodyValue(member))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public void requestAccessToFolder(UUID folderId) {
        addAuth(webClient.post()
                .uri("/api/shared-folders/{folderId}/request-access", folderId))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public List<Map<String, Object>> getPendingRequests(UUID folderId) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> requests = addAuth(webClient.get()
                .uri("/api/shared-folders/{folderId}/requests/pending", folderId))
                .retrieve()
                .bodyToMono(List.class)
                .block();
        return requests == null ? List.of() : requests;
    }

    public void approveRequest(UUID requestId) {
        addAuth(webClient.post()
                .uri("/api/shared-folders/requests/{requestId}/approve", requestId))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public List<Map<String, String>> searchSharedFoldersByName(String name) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> result = addAuth(webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/shared-folders/search")
                        .queryParam("name", name).build()))
                .retrieve()
                .bodyToMono(List.class)
                .block();
        return result == null ? List.of() : result;
    }

    public int getPendingRequestsCount(UUID folderId) {
        Integer count = addAuth(webClient.get()
                .uri("/api/shared-folders/{folderId}/requests/pending/count", folderId))
                .retrieve()
                .bodyToMono(Integer.class)
                .block();
        return count != null ? count : 0;
    }

    public void deleteSharedFolder(UUID folderId) {
        addAuth(webClient.delete().uri("/api/shared-folders/{folderId}", folderId))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public UUID createFolder(String name, UUID parentId, UUID sharedFolderId) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        if (parentId != null) params.put("parentId", parentId);
        if (sharedFolderId != null) params.put("folderId", sharedFolderId);

        FileMetadataDto response = addAuth(webClient.post().uri("/api/files/folder").bodyValue(params))
                .retrieve()
                .bodyToMono(FileMetadataDto.class)
                .block();

        if (response == null || response.getFileId() == null) {
            throw new RuntimeException("Failed to create folder: server returned null response");
        }
        try {
            return UUID.fromString(response.getFileId());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid UUID returned from server: " + response.getFileId(), e);
        }
    }

    public String getAuthToken() {
        return authToken;
    }

    public void close() {
        chunkedUploader.close();
    }

    public void logout() {
        this.authToken = null;
    }
}