package com.filesync.client.controller;

import com.filesync.client.http.SyncHttpClient;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ApproveRequestsController {
    @FXML private ListView<String> requestsList;
    @FXML private Button approveButton;
    @FXML private Button closeButton;

    private SyncHttpClient httpClient;
    private UUID folderId;
    private Stage dialogStage;
    private List<Map<String, Object>> requests;
    private Runnable onApproved;

    public void setData(UUID folderId, SyncHttpClient httpClient, Stage dialogStage, Runnable onApproved) {
        this.folderId = folderId;
        this.httpClient = httpClient;
        this.dialogStage = dialogStage;
        this.onApproved = onApproved;
        loadRequests();
    }

    @FXML
    private void initialize() {
        requestsList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) ->
                approveButton.setDisable(newVal == null));
        approveButton.setOnAction(e -> approveSelected());
        closeButton.setOnAction(e -> dialogStage.close());
    }

    private void loadRequests() {
        try {
            requests = httpClient.getPendingRequests(folderId);
            requestsList.getItems().clear();
            for (Map<String, Object> req : requests) {
                String requester = (String) req.get("requesterId");
                requestsList.getItems().add(requester);
            }
            if (requests.isEmpty()) {
                approveButton.setDisable(true);
            }
        } catch (Exception e) {
            showAlert("Error", "Could not fetch pending requests: " + e.getMessage());
            dialogStage.close();
        }
    }

    private void approveSelected() {
        int idx = requestsList.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && requests != null && idx < requests.size()) {
            UUID requestId = UUID.fromString((String) requests.get(idx).get("requestId"));
            try {
                httpClient.approveRequest(requestId);
                showAlert("Approved", "User " + requestsList.getSelectionModel().getSelectedItem() + " has been added as READ-only member.");
                if (onApproved != null) onApproved.run();
                dialogStage.close();
            } catch (Exception e) {
                showAlert("Error", "Failed to approve: " + e.getMessage());
            }
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