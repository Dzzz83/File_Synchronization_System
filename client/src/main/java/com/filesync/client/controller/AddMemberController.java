package com.filesync.client.controller;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.UserSearchResult;
import com.filesync.common.enums.Permission;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class AddMemberController {
    @FXML private TextField searchField;
    @FXML private ListView<String> resultsList;
    @FXML private ComboBox<Permission> permissionBox;
    @FXML private Button addButton;
    @FXML private Button closeButton;

    private SyncHttpClient httpClient;
    private UUID folderId;
    private Runnable onSuccess;
    private Stage dialogStage;
    private ExecutorService executorService;

    private Task<List<UserSearchResult>> currentSearchTask;

    public void initialize() {
        permissionBox.getItems().setAll(Permission.READ, Permission.WRITE);
        permissionBox.setValue(Permission.READ);

        final long[] lastSearchTime = {0};
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().length() < 2) return;
            long now = System.currentTimeMillis();
            if (now - lastSearchTime[0] < 300) return;
            lastSearchTime[0] = now;

            // Cancel ongoing search
            if (currentSearchTask != null && !currentSearchTask.isDone()) {
                currentSearchTask.cancel(true);
            }

            String query = newVal.trim();

            currentSearchTask = new Task<List<UserSearchResult>>() {
                @Override
                protected List<UserSearchResult> call() throws Exception {
                    return httpClient.searchUsers(query);
                }
            };
            currentSearchTask.setOnSucceeded(e -> {
                List<UserSearchResult> users = currentSearchTask.getValue();
                Platform.runLater(() -> {
                    resultsList.getItems().clear();
                    for (UserSearchResult u : users) {
                        resultsList.getItems().add(u.getUsername() + " (" + u.getEmail() + ")");
                    }
                });
            });
            currentSearchTask.setOnFailed(e -> {
                Platform.runLater(() -> {
                    showAlert("Search Error", currentSearchTask.getException().getMessage());
                });
            });
            executorService.submit(currentSearchTask);
        });

        addButton.setOnAction(e -> addMember());
        closeButton.setOnAction(e -> dialogStage.close());
    }

    private void addMember() {
        String selected = resultsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No selection", "Please select a user from the list.");
            return;
        }
        String username = selected.split(" ")[0];
        Permission perm = permissionBox.getValue();

        // Disable buttons during operation
        addButton.setDisable(true);
        closeButton.setDisable(true);

        Task<Void> addTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                httpClient.addMemberToFolder(folderId, username, perm);
                return null;
            }
        };
        addTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                if (onSuccess != null) onSuccess.run();
                dialogStage.close();
            });
        });
        addTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                addButton.setDisable(false);
                closeButton.setDisable(false);
                showAlert("Failed to add member", addTask.getException().getMessage());
            });
        });
        executorService.submit(addTask);
    }

    public void setData(UUID folderId, SyncHttpClient httpClient, Runnable onSuccess,
                        Stage dialogStage, ExecutorService executorService) {
        this.folderId = folderId;
        this.httpClient = httpClient;
        this.onSuccess = onSuccess;
        this.dialogStage = dialogStage;
        this.executorService = executorService;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}