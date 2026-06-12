package com.filesync.client.shared.requests;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.enums.Permission;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RequestAccessController {
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private ListView<String> resultsList;
    @FXML private ComboBox<Permission> permissionCombo;
    @FXML private Button requestButton;
    @FXML private Button cancelButton;

    private SyncHttpClient httpClient;
    private Stage dialogStage;
    private List<Map<String, String>> searchResults;

    public void initialize() {
        permissionCombo.getItems().setAll(Permission.READ, Permission.WRITE);
        permissionCombo.setValue(Permission.READ);

        searchButton.setOnAction(e -> performSearch());
        requestButton.setOnAction(e -> sendRequest());
        cancelButton.setOnAction(e -> dialogStage.close());

        resultsList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) ->
                requestButton.setDisable(newVal == null));
    }

    private void performSearch() {
        String folderName = searchField.getText().trim();
        if (folderName.isEmpty()) {
            showAlert("Error", "Please enter a folder name.");
            return;
        }
        try {
            searchResults = httpClient.searchSharedFoldersByName(folderName);
            resultsList.getItems().clear();
            for (Map<String, String> folder : searchResults) {
                resultsList.getItems().add(folder.get("name") + " (owner: " + folder.get("ownerId") + ")");
            }
            if (searchResults.isEmpty()) {
                showAlert("Not Found", "No shared folders found with that name.");
            }
        } catch (Exception e) {
            showAlert("Error", "Failed to search: " + e.getMessage());
        }
    }

    private void sendRequest() {
        int idx = resultsList.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && searchResults != null && idx < searchResults.size()) {
            UUID folderId = UUID.fromString(searchResults.get(idx).get("folderId"));
            Permission requestedPermission = permissionCombo.getValue();
            if (requestedPermission == null) {
                showAlert("Error", "Please select a permission level.");
                return;
            }
            try {
                httpClient.requestAccessToFolder(folderId, requestedPermission);
                showAlert("Request Sent", "Your " + requestedPermission + " access request has been sent to the folder owner.");
                dialogStage.close();
            } catch (Exception e) {
                showAlert("Error", "Failed to send request: " + e.getMessage());
            }
        }
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void setData(SyncHttpClient httpClient, Stage dialogStage) {
        this.httpClient = httpClient;
        this.dialogStage = dialogStage;
    }
}