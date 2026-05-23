package com.filesync.client.controller;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.UserSearchResult;
import com.filesync.common.enums.Permission;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.List;
import java.util.UUID;

public class AddMemberDialogController {
    @FXML private TextField searchField;
    @FXML private ListView<String> resultsList;
    @FXML private ComboBox<Permission> permissionBox;
    @FXML private Button addButton;
    @FXML private Button closeButton;

    private SyncHttpClient httpClient;
    private UUID folderId;
    private Runnable onSuccess;
    private Stage dialogStage;

    public void initialize() {
        permissionBox.getItems().setAll(Permission.READ, Permission.WRITE);
        permissionBox.setValue(Permission.READ);

        final long[] lastSearchTime = {0};
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().length() < 2) return;
            long now = System.currentTimeMillis();
            if (now - lastSearchTime[0] < 300) return;
            lastSearchTime[0] = now;
            Platform.runLater(() -> {
                try {
                    List<UserSearchResult> users = httpClient.searchUsers(newVal.trim());
                    resultsList.getItems().clear();
                    for (UserSearchResult u : users) {
                        resultsList.getItems().add(u.getUsername() + " (" + u.getEmail() + ")");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });

        addButton.setOnAction(e -> {
            String selected = resultsList.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            String username = selected.split(" ")[0];
            Permission perm = permissionBox.getValue();
            try {
                httpClient.addMemberToFolder(folderId, username, perm);
                if (onSuccess != null) onSuccess.run();
                dialogStage.close();
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to add member: " + ex.getMessage());
                alert.showAndWait();
            }
        });

        closeButton.setOnAction(e -> dialogStage.close());
    }

    public void setData(UUID folderId, SyncHttpClient httpClient, Runnable onSuccess, Stage dialogStage) {
        this.folderId = folderId;
        this.httpClient = httpClient;
        this.onSuccess = onSuccess;
        this.dialogStage = dialogStage;
    }
}