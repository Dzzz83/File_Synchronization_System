package com.filesync.client.files;

import com.filesync.client.document.DocumentViewerDialog;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.viewer.ImageViewerDialog;
import com.filesync.client.viewer.MediaPlayerDialog;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.Permission;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

import static com.filesync.client.util.FileTypeHelper.*;

public class FileOpenHandler {

    private static final Logger log = LoggerFactory.getLogger(FileOpenHandler.class);
    private final SyncHttpClient httpClient;
    private final ExecutorService executorService;

    public FileOpenHandler(SyncHttpClient httpClient, ExecutorService executorService) {
        this.httpClient = httpClient;
        this.executorService = executorService;
    }

    public void openItem(ServerFileItem item, Stage ownerStage, FileExplorerController controller) {
        log.info("📂 Opening item: {} (ID: {})", item.getRelativePath(), item.getFileId());
        if (item.isDirectory()) {
            if ("..".equals(item.getRelativePath())) {
                if (controller.canGoUp()) {
                    controller.navigateUp();
                } else if (controller.isSharedFolder()) {
                    controller.exitSharedFolder();
                }
            } else {
                controller.navigateInto(item);
            }
            return;
        }

        // --- Validate file still exists on server ---
        try {
            FileMetadataDto meta = httpClient.getFileMetadata(item.getFileId());
            // Optional: you can update the item with fresh metadata if needed
        } catch (WebClientResponseException e) {
            log.warn("🔥 File not found on server, removing: {}", item.getRelativePath());
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("File {} no longer exists on server ({}). Removing stale entry.", item.getRelativePath(), e.getStatusCode());
                controller.removeItemByFileId(item.getFileId());
                showAlert("File Not Found", "This file no longer exists on the server. It has been removed from the list.");
                return;
            }
            log.error("Unexpected error checking file metadata for {}", item.getRelativePath(), e);
            showAlert("Error", "Could not verify file: " + e.getMessage());
            return;
        } catch (Exception e) {
            log.error("Error checking file metadata for {}", item.getRelativePath(), e);
            showAlert("Error", "Could not verify file: " + e.getMessage());
            return;
        }

        // Handle files by type
        String path = item.getRelativePath();
        if (isMediaFile(path)) {
            if (item.getUserPermission() == Permission.READ || item.getUserPermission() == Permission.WRITE) {
                MediaPlayerDialog.show(item.getFileId(), path, httpClient);
            } else {
                showAlert("Permission Denied", "You don't have permission to play this file.");
            }
        } else if (isTextFile(item)) {
            if (item.getUserPermission() != Permission.WRITE) {
                showAlert("Permission Denied", "You don't have write permission to edit this file.");
                return;
            }
            controller.editFile(item);
        } else if (isPdfOrDocx(item)) {
            if (isDocx(item) && item.getUserPermission() != Permission.WRITE) {
                showAlert("Permission Denied", "You need write permission to edit a DOCX file.");
                return;
            }
            if (isPdf(item) && (item.getUserPermission() != Permission.READ && item.getUserPermission() != Permission.WRITE)) {
                showAlert("Permission Denied", "You need read permission to view this PDF.");
                return;
            }
            DocumentViewerDialog.show(ownerStage, item, httpClient, executorService);
        } else if (isImageFile(path)) {
            ImageViewerDialog.show(ownerStage, item, httpClient, executorService);
        } else {
            showAlert("Unsupported File Type", "This file type cannot be opened directly.");
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}