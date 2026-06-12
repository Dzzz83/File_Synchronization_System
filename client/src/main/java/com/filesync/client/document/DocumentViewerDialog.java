package com.filesync.client.document;

import com.filesync.client.files.ServerFileItem;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.service.ProgressService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public final class DocumentViewerDialog {
    private DocumentViewerDialog() {}

    public static void show(Stage owner,
                            ServerFileItem item,
                            SyncHttpClient httpClient,
                            ExecutorService executorService) {
        if (item == null || item.isDirectory()) {
            return;
        }

        String fileName = item.getRelativePath();
        String ext = getExtension(fileName);

        if (!ext.equals("pdf") && !ext.equals("docx")) {
            showAlert(Alert.AlertType.WARNING, "Unsupported file", "Only PDF and DOCX files can be opened here.");
            return;
        }

        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Opening " + fileName);

        Task<Path> downloadTask = new Task<>() {
            @Override
            protected Path call() throws Exception {
                updateMessage("Downloading " + fileName + "...");

                Path tempFile = Files.createTempFile("filesync_view_", "." + ext);

                httpClient.downloadFile(item.getFileId(), tempFile, (downloaded, total) -> {
                    updateProgress(downloaded, total);
                    updateMessage(String.format(
                            "Downloading %s: %d / %d KB",
                            fileName,
                            downloaded / 1024,
                            total / 1024
                    ));
                });

                return tempFile;
            }
        };

        downloadTask.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            ps.updateMessage(newMsg);
        });

        downloadTask.progressProperty().addListener((obs, oldVal, newVal) -> {
            double progress = newVal == null ? -1 : newVal.doubleValue();
            if (progress >= 0) {
                ps.updateProgress(progress, 1.0);
            }
        });

        downloadTask.setOnSucceeded(event -> {
            ps.finishOperation();

            Path tempFile = downloadTask.getValue();

            try {
                if (ext.equals("pdf")) {
                    PdfViewerController.show(owner, tempFile, fileName);
                } else {
                    DocxEditorController.show(owner, tempFile, fileName, item.getFileId(), httpClient);
                }
            } catch (Exception e) {
                deleteQuietly(tempFile);
                showAlert(Alert.AlertType.ERROR, "Open failed", e.getMessage());
            }
        });

        downloadTask.setOnFailed(event -> {
            ps.finishOperation();

            Throwable ex = downloadTask.getException();
            String message = ex == null ? "Unknown error" : ex.getMessage();

            showAlert(Alert.AlertType.ERROR, "Open failed", message);
        });

        executorService.submit(downloadTask);
    }

    static void deleteQuietly(Path path) {
        if (path == null) return;

        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private static String getExtension(String fileName) {
        if (fileName == null) return "";

        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";

        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}