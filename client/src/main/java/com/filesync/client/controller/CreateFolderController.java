package com.filesync.client.controller;

import com.filesync.client.http.SyncHttpClient;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.UUID;

public class CreateFolderController {
    @FXML private TextField nameField;
    private Stage stage;
    private SyncHttpClient httpClient;
    private String ownerId;
    private UUID folderId; // shared folder (or null)
    private UUID parentId;
    private Runnable onSuccess;

    public void setData(Stage stage, SyncHttpClient httpClient, String ownerId, UUID folderId, UUID parentId, Runnable onSuccess) {
        this.stage = stage;
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.folderId = folderId;
        this.parentId = parentId;
        this.onSuccess = onSuccess;
    }

    @FXML private void onCreate() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        try {
            httpClient.createFolder(name, parentId, folderId);
            if (onSuccess != null) onSuccess.run();
            stage.close();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to create folder: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML private void onCancel() {
        stage.close();
    }
}