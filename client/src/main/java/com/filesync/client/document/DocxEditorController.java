package com.filesync.client.document;

import com.filesync.client.file.FileHasher;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.service.ProgressService;
import com.filesync.common.dto.FileMetadataDto;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.web.HTMLEditor;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public class DocxEditorController {
    private static final long MAX_DOCX_SIZE_BYTES = 20L * 1024L * 1024L;

    @FXML private HTMLEditor htmlEditor;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private Stage stage;
    private Path tempFile;
    private String fileName;
    private String fileId;
    private SyncHttpClient httpClient;

    public static void show(Stage owner,
                            Path file,
                            String fileName,
                            String fileId,
                            SyncHttpClient httpClient) throws Exception {
        if (Files.size(file) > MAX_DOCX_SIZE_BYTES) {
            DocumentViewerDialog.deleteQuietly(file);
            throw new IllegalArgumentException("DOCX is too large to edit safely. Max supported size is 20 MB.");
        }

        FXMLLoader loader = new FXMLLoader(
                DocxEditorController.class.getResource("/com/filesync/client/document/docx-editor.fxml")
        );

        Parent root = loader.load();

        Stage dialogStage = new Stage();
        dialogStage.setTitle("DOCX Editor - " + fileName);
        dialogStage.initModality(Modality.NONE);

        if (owner != null) {
            dialogStage.initOwner(owner);
        }

        dialogStage.setScene(new Scene(root, 1000, 750));
        dialogStage.setResizable(true);

        DocxEditorController controller = loader.getController();
        controller.open(dialogStage, file, fileName, fileId, httpClient);

        dialogStage.show();
    }

    private void open(Stage stage,
                      Path file,
                      String fileName,
                      String fileId,
                      SyncHttpClient httpClient) throws Exception {
        this.stage = stage;
        this.tempFile = file;
        this.fileName = fileName;
        this.fileId = fileId;
        this.httpClient = httpClient;

        htmlEditor.setHtmlText(DocumentConverter.docxToHtml(file.toFile()));

        stage.setOnCloseRequest(event -> cleanup());
    }

    @FXML
    private void saveDocument() {
        saveButton.setDisable(true);
        cancelButton.setDisable(true);

        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Saving " + fileName);

        String editedHtml = htmlEditor.getHtmlText();

        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Path editedDocx = Files.createTempFile("filesync_docx_edited_", ".docx");

                try {
                    updateMessage("Converting document...");
                    DocumentConverter.htmlToDocx(editedHtml, editedDocx.toFile());

                    updateMessage("Updating metadata...");
                    FileMetadataDto currentMeta = httpClient.getFileMetadata(fileId);

                    FileMetadataDto updatedMeta = FileMetadataDto.forUpload(
                            currentMeta.getFileId(),
                            currentMeta.getRelativePath(),
                            Files.size(editedDocx),
                            FileHasher.computeHash(editedDocx),
                            Instant.now(),
                            currentMeta.getOwnerId(),
                            currentMeta.getFolderId(),
                            currentMeta.getParentId()
                    );

                    httpClient.createMetadata(updatedMeta);

                    updateMessage("Uploading document...");

                    long size = Files.size(editedDocx);

                    if (size > 5L * 1024L * 1024L) {
                        httpClient.uploadLargeFile(fileId, editedDocx, currentMeta.getFolderId(), (uploaded, total) -> {
                            updateProgress(uploaded, total);
                            updateMessage(String.format(
                                    "Uploading %s: %d / %d KB",
                                    fileName,
                                    uploaded / 1024,
                                    total / 1024
                            ));
                        });
                    } else {
                        httpClient.uploadFile(fileId, editedDocx, currentMeta.getFolderId());
                        updateProgress(size, size);
                    }

                    return null;
                } finally {
                    Files.deleteIfExists(editedDocx);
                }
            }
        };

        saveTask.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            ps.updateMessage(newMsg);
        });

        saveTask.progressProperty().addListener((obs, oldVal, newVal) -> {
            double progress = newVal == null ? -1 : newVal.doubleValue();
            if (progress >= 0) {
                ps.updateProgress(progress, 1.0);
            }
        });

        saveTask.setOnSucceeded(event -> {
            ps.finishOperation();

            cleanup();
            stage.close();

            showAlert(Alert.AlertType.INFORMATION, "Saved", "Document saved successfully.");
        });

        saveTask.setOnFailed(event -> {
            ps.finishOperation();

            saveButton.setDisable(false);
            cancelButton.setDisable(false);

            Throwable ex = saveTask.getException();
            String message = buildSaveErrorMessage(ex);

            showAlert(Alert.AlertType.ERROR, "Save failed", message);
        });

        Thread thread = new Thread(saveTask, "docx-save-" + fileId);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void closeWindow() {
        cleanup();
        stage.close();
    }

    private String buildSaveErrorMessage(Throwable ex) {
        if (ex instanceof WebClientResponseException.Forbidden) {
            return "You don't have write permission for this file.";
        }

        if (ex instanceof WebClientResponseException responseException
                && responseException.getStatusCode().value() == 403) {
            return "You don't have write permission for this file.";
        }

        return ex == null ? "Unknown error" : ex.getMessage();
    }

    private void cleanup() {
        DocumentViewerDialog.deleteQuietly(tempFile);
        tempFile = null;
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}