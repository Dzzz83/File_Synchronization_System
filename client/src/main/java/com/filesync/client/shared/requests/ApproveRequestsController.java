package com.filesync.client.shared.requests;

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
                        String requestedPerm = (String) req.get("requestedPermission");
                        if (requestedPerm == null) requestedPerm = "READ";
                        requestsList.getItems().add(requester + " (requested: " + requestedPerm + ")");
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

        Map<String, Object> selectedReq = requests.get(idx);
        UUID requestId = UUID.fromString((String) selectedReq.get("requestId"));
        String requester = (String) selectedReq.get("requesterId");
        String requestedPerm = (String) selectedReq.get("requestedPermission");
        if (requestedPerm == null) requestedPerm = "READ";

        final String requesterFinal = requester;
        final String requestedPermFinal = requestedPerm;

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
                showAlert("Approved", "User " + requesterFinal + " has been added as " + requestedPermFinal + " member.");
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