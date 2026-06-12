package com.filesync.client.files;

import com.filesync.client.http.SyncHttpClient;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class CreateFolderController {
    @FXML private TextField nameField;
    @FXML private Button createButton;

    private Stage stage;
    private SyncHttpClient httpClient;
    private String ownerId;
    private UUID folderId;
    private UUID parentId;
    private Runnable onSuccess;
    private ExecutorService executorService;

    public void setData(Stage stage, SyncHttpClient httpClient, String ownerId, UUID folderId, UUID parentId,
                        Runnable onSuccess, ExecutorService executorService) {
        this.stage = stage;
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.folderId = folderId;
        this.parentId = parentId;
        this.onSuccess = onSuccess;
        this.executorService = executorService;
    }

    @FXML
    private void onCreate() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;

        createButton.setDisable(true);
        createButton.setText("Creating...");

        Task<Void> createTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                httpClient.createFolder(name, parentId, folderId);
                return null;
            }
        };
        createTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                if (onSuccess != null) onSuccess.run();
                stage.close();
            });
        });
        createTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                createButton.setDisable(false);
                createButton.setText("Create");
                showAlert("Error", "Failed to create folder: " + createTask.getException().getMessage());
            });
        });
        executorService.submit(createTask);
    }

    @FXML
    private void onCancel() {
        stage.close();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}