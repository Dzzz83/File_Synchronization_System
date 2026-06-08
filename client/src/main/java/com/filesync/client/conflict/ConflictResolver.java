package com.filesync.client.conflict;

import com.filesync.client.db.LocalMetadataRepository;
import com.filesync.client.file.FileHasher;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.SyncStatus;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConflictResolver {
    private static final ConcurrentMap<String, Boolean> resolvingFiles = new ConcurrentHashMap<>();

    public static void resolve(FileMetadataDto file, Path localPath, SyncHttpClient httpClient,
                               LocalMetadataRepository localRepo) throws IOException {
        String filePath = file.getRelativePath();
        if (resolvingFiles.putIfAbsent(filePath, true) != null) {
            return;
        }
        try {
            Path tempServerFile = Files.createTempFile("server_", ".tmp");
            httpClient.downloadFile(file.getFileId(), tempServerFile);
            String serverContent = Files.readString(tempServerFile);
            String localContent = Files.readString(localPath);
            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(
                            ConflictResolver.class.getResource("/com/filesync/client/conflict/conflict-view.fxml")
                    );
                    Scene scene = new Scene(loader.load());
                    ConflictController controller = loader.getController();
                    Stage stage = new Stage();
                    stage.setTitle("Conflict Resolution");
                    stage.setScene(scene);
                    stage.setResizable(true);
                    stage.setOnCloseRequest(e -> resolvingFiles.remove(filePath));
                    controller.setData(file.getRelativePath(), serverContent, localContent, mergedContent -> {
                        try {
                            Files.writeString(localPath, mergedContent);
                            String newHash = FileHasher.computeHash(localPath);

                            FileMetadataDto updatedDto = FileMetadataDto.builder()
                                    .fileId(file.getFileId())
                                    .relativePath(file.getRelativePath())
                                    .size(Files.size(localPath))
                                    .sha256Hash(newHash)
                                    .lastModified(Files.getLastModifiedTime(localPath).toInstant())
                                    .ownerId(file.getOwnerId())
                                    .status(SyncStatus.SYNCED)
                                    .folderId(file.getFolderId())
                                    .parentId(file.getParentId())
                                    .build();

                            // Preserve optional fields
                            updatedDto.setVersionVectorJson(file.getVersionVectorJson());
                            updatedDto.setSharedWith(file.getSharedWith());
                            updatedDto.setDirectory(file.isDirectory());

                            httpClient.createMetadata(updatedDto);
                            // ========================================================

                            long fileSize = Files.size(localPath);
                            if (fileSize > 5 * 1024 * 1024) {
                                httpClient.uploadLargeFile(file.getFileId(), localPath, file.getFolderId(), null);
                            } else {
                                httpClient.uploadFile(file.getFileId(), localPath, file.getFolderId());
                            }
                            if (localRepo != null) {
                                localRepo.saveFile(file.getRelativePath(), file.getFileId(), newHash);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        } finally {
                            try { Files.deleteIfExists(tempServerFile); } catch (IOException ignored) {}
                            resolvingFiles.remove(filePath);
                            stage.close();
                        }
                    });
                    stage.showAndWait();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    resolvingFiles.remove(filePath);
                }
            });
        } catch (Exception e) {
            resolvingFiles.remove(filePath);
            throw e;
        }
    }
}