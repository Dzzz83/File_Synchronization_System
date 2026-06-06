package com.filesync.client.controller;

import com.filesync.client.http.SyncHttpClient;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class ApproveRequestsController {
    @FXML private ListView<String> requestsList;
    @FXML private Button approveButton;
    @FXML private Button closeButton;

    private SyncHttpClient httpClient;
    private UUID folderId;
    private Stage dialogStage;
    private List<Map<String, Object>> requests;
    private Runnable onApproved;
    private ExecutorService executorService;

    @FXML
    private void initialize() {
        requestsList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) ->
                approveButton.setDisable(newVal == null));
        approveButton.setOnAction(e -> approveSelected());
        closeButton.setOnAction(e -> dialogStage.close());
    }

    public void setData(UUID folderId, SyncHttpClient httpClient, Stage dialogStage,
                        Runnable onApproved, ExecutorService executorService) {
        this.folderId = folderId;
        this.httpClient = httpClient;
        this.dialogStage = dialogStage;
        this.onApproved = onApproved;
        this.executorService = executorService;
        loadRequests();
    }

    private void loadRequests() {
        // Disable buttons while loading
        approveButton.setDisable(true);
        closeButton.setDisable(true);

        Task<List<Map<String, Object>>> loadTask = new Task<>() {
            @Override
            protected List<Map<String, Object>> call() throws Exception {
                return httpClient.getPendingRequests(folderId);
            }
        };
        loadTask.setOnSucceeded(e -> {
            requests = loadTask.getValue();
            Platform.runLater(() -> {
                requestsList.getItems().clear();
                if (requests != null) {
                    for (Map<String, Object> req : requests) {
                        String requester = (String) req.get("requesterId");
                        requestsList.getItems().add(requester);
                    }
                }
                approveButton.setDisable(requests == null || requests.isEmpty());
                closeButton.setDisable(false);
            });
        });
        loadTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                showAlert("Error", "Could not fetch pending requests: " + loadTask.getException().getMessage());
                dialogStage.close();
            });
        });
        executorService.submit(loadTask);
    }

    private void approveSelected() {
        int idx = requestsList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || requests == null || idx >= requests.size()) return;

        UUID requestId = UUID.fromString((String) requests.get(idx).get("requestId"));
        String selectedUser = requestsList.getSelectionModel().getSelectedItem();

        // Disable buttons during approval
        approveButton.setDisable(true);
        closeButton.setDisable(true);

        Task<Void> approveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                httpClient.approveRequest(requestId);
                return null;
            }
        };
        approveTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                showAlert("Approved", "User " + selectedUser + " has been added as READ-only member.");
                if (onApproved != null) onApproved.run();
                dialogStage.close();
            });
        });
        approveTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                approveButton.setDisable(false);
                closeButton.setDisable(false);
                showAlert("Error", "Failed to approve: " + approveTask.getException().getMessage());
            });
        });
        executorService.submit(approveTask);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}