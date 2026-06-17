package com.filesync.client.files;

import com.filesync.client.document.DocumentViewerDialog;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.viewer.ImageViewerDialog;
import com.filesync.client.viewer.MediaPlayerDialog;
import com.filesync.common.enums.Permission;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.concurrent.ExecutorService;

import static com.filesync.client.util.FileTypeHelper.*;

public class FileOpenHandler {

    private final SyncHttpClient httpClient;
    private final ExecutorService executorService;

    public FileOpenHandler(SyncHttpClient httpClient, ExecutorService executorService) {
        this.httpClient = httpClient;
        this.executorService = executorService;
    }

    public void openItem(ServerFileItem item, Stage ownerStage, FileExplorerController controller) {
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