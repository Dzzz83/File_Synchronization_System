package com.filesync.client.dialog;

import com.filesync.client.controller.ImageViewerController;
import com.filesync.client.files.ServerFileItem;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.service.ProgressService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

public final class ImageViewerDialog {
    private ImageViewerDialog() {}

    public static void show(Stage owner, ServerFileItem item,
                            SyncHttpClient httpClient, ExecutorService executorService) {
        if (item == null || item.isDirectory()) return;

        String fileName = item.getRelativePath();
        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Opening " + fileName);

        Task<Path> downloadTask = new Task<>() {
            @Override
            protected Path call() throws Exception {
                updateMessage("Downloading " + fileName + "...");
                String ext = getExtension(fileName);
                Path tempFile = Files.createTempFile("img_", "." + ext);
                httpClient.downloadFile(item.getFileId(), tempFile, (downloaded, total) -> {
                    updateProgress(downloaded, total);
                    updateMessage(String.format("Downloading %s: %d / %d KB",
                            fileName, downloaded / 1024, total / 1024));
                });
                return tempFile;
            }
        };

        downloadTask.messageProperty().addListener((obs, oldMsg, newMsg) -> ps.updateMessage(newMsg));
        downloadTask.progressProperty().addListener((obs, oldVal, newVal) -> {
            double progress = newVal == null ? -1 : newVal.doubleValue();
            if (progress >= 0) ps.updateProgress(progress, 1.0);
        });

        downloadTask.setOnSucceeded(event -> {
            ps.finishOperation();
            Path tempFile = downloadTask.getValue();
            try {
                FXMLLoader loader = new FXMLLoader(ImageViewerDialog.class.getResource("/com/filesync/client/dialog/image-viewer.fxml"));
                Scene scene = new Scene(loader.load());
                ImageViewerController controller = loader.getController();
                Stage dialogStage = new Stage();
                dialogStage.initModality(Modality.NONE);
                if (owner != null) dialogStage.initOwner(owner);
                dialogStage.setTitle("Image Viewer - " + fileName);
                dialogStage.setScene(scene);
                dialogStage.setResizable(true);
                controller.init(dialogStage, tempFile, fileName);
                dialogStage.setOnCloseRequest(e -> controller.cleanup());
                dialogStage.show();
            } catch (Exception e) {
                deleteQuietly(tempFile);
                showError("Could not open image: " + e.getMessage());
            }
        });

        downloadTask.setOnFailed(event -> {
            ps.finishOperation();
            showError("Download failed: " + downloadTask.getException().getMessage());
        });

        executorService.submit(downloadTask);
    }

    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot == -1) ? "" : fileName.substring(dot + 1);
    }

    private static void deleteQuietly(Path path) {
        try {
            if (path != null) Files.deleteIfExists(path);
        } catch (Exception ignored) {}
    }

    private static void showError(String message) {
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Image Viewer Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}