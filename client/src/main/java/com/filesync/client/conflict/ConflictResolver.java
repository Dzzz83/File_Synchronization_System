package com.filesync.client.conflict;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.util.FileHasher;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.SyncStatus;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConflictResolver {

    private static final Logger log = LoggerFactory.getLogger(ConflictResolver.class);
    private static final ConcurrentMap<String, Boolean> resolvingFiles = new ConcurrentHashMap<>();

    public static void resolve(FileMetadataDto file, Path localPath, SyncHttpClient httpClient) throws IOException {
        String filePath = file.getRelativePath();
        if (resolvingFiles.putIfAbsent(filePath, true) != null) {
            return;
        }

        final Path[] tempServerFileHolder = new Path[1];

        try {
            tempServerFileHolder[0] = Files.createTempFile("server_", ".tmp");
            Path tempServerFile = tempServerFileHolder[0];
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
                            // Write merged content to local file
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
                                    .userPermission(file.getUserPermission())
                                    .build();

                            updatedDto.setSharedWith(file.getSharedWith());
                            updatedDto.setDirectory(file.isDirectory());

                            // Step 1: Create metadata
                            httpClient.createMetadata(updatedDto);

                            // Step 2: Upload file content
                            try {
                                long fileSize = Files.size(localPath);
                                if (fileSize > 5 * 1024 * 1024) {
                                    httpClient.uploadLargeFile(file.getFileId(), localPath, file.getFolderId(), null);
                                } else {
                                    httpClient.uploadFile(file.getFileId(), localPath, file.getFolderId());
                                }
                                log.info("Conflict resolved and uploaded: {}", file.getRelativePath());
                                // Close the dialog on success
                                stage.close();
                            } catch (Exception uploadEx) {
                                // Compensating transaction: roll back metadata
                                log.error("Upload failed for {}, rolling back metadata", file.getRelativePath(), uploadEx);
                                try {
                                    httpClient.deleteFile(file.getFileId(), file.getFolderId());
                                } catch (Exception deleteEx) {
                                    log.error("Failed to delete orphaned metadata for fileId {}: {}", file.getFileId(), deleteEx.getMessage());
                                }
                                // Show error alert to user
                                Platform.runLater(() -> {
                                    Alert alert = new Alert(Alert.AlertType.ERROR);
                                    alert.setTitle("Upload Failed");
                                    alert.setHeaderText("Conflict resolution upload failed");
                                    alert.setContentText("The merged file could not be uploaded to the server.\n" +
                                            "The metadata has been rolled back.\n\n" +
                                            "Reason: " + uploadEx.getMessage());
                                    alert.showAndWait();
                                    // Close the dialog after alert
                                    stage.close();
                                });
                            }
                        } catch (Exception ex) {
                            log.error("Conflict resolution failed for {}", file.getRelativePath(), ex);
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("Conflict Resolution Error");
                                alert.setHeaderText("Failed to resolve conflict");
                                alert.setContentText("An unexpected error occurred: " + ex.getMessage());
                                alert.showAndWait();
                                stage.close();
                            });
                        } finally {
                            resolvingFiles.remove(filePath);
                            // Clean up temp server file if not already done
                            try {
                                if (tempServerFileHolder[0] != null) {
                                    Files.deleteIfExists(tempServerFileHolder[0]);
                                }
                            } catch (IOException ignored) {}
                        }
                    });

                    stage.showAndWait();

                } catch (IOException ex) {
                    log.error("Failed to load conflict dialog", ex);
                    resolvingFiles.remove(filePath);
                }
            });

        } catch (Exception e) {
            resolvingFiles.remove(filePath);
            throw new IOException("Failed to resolve conflict for " + filePath, e);
        } finally {
            if (tempServerFileHolder[0] != null) {
                try {
                    Files.deleteIfExists(tempServerFileHolder[0]);
                } catch (IOException ignored) {
                }
            }
        }
    }
}