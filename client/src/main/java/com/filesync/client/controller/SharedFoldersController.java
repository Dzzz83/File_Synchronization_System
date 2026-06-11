package com.filesync.client.controller;

import com.filesync.client.dialog.CreateFolderDialog;
import com.filesync.client.dialog.PendingRequestsDialog;
import com.filesync.client.dialog.RequestAccessDialog;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.CreateFolderDto;
import com.filesync.common.dto.SharedFolderDto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.filesync.client.dialog.AddMemberDialog;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SharedFoldersController {
    @FXML private TableView<SharedFolderItem> foldersTable;
    @FXML private TableColumn<SharedFolderItem, String> nameColumn;
    @FXML private TableColumn<SharedFolderItem, String> ownerColumn;
    @FXML private TableColumn<SharedFolderItem, String> permissionColumn;
    @FXML private Button manageRequestsButton;
    @FXML private Button deleteFolderButton;

    private SyncHttpClient httpClient;
    private String ownerId;
    private ObservableList<SharedFolderItem> folderItems = FXCollections.observableArrayList();

    public void initialize(SyncHttpClient httpClient, String ownerId) {
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        ownerColumn.setCellValueFactory(new PropertyValueFactory<>("ownerId"));
        permissionColumn.setCellValueFactory(new PropertyValueFactory<>("permission"));
        foldersTable.setItems(folderItems);
        foldersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getOwnerId().equals(ownerId)) {
                manageRequestsButton.setDisable(false);
                deleteFolderButton.setDisable(false);
                updateRequestsButton(newVal.getId());
            } else {
                manageRequestsButton.setDisable(true);
                deleteFolderButton.setDisable(true);
                manageRequestsButton.setText("Manage Requests");
                manageRequestsButton.setStyle("");
            }
        });
        refreshFolders();
    }

    // Helper to update the button's text and style based on pending request count
    private void updateRequestsButton(UUID folderId) {
        try {
            int count = httpClient.getPendingRequestsCount(folderId);
            Platform.runLater(() -> {
                if (count > 0) {
                    manageRequestsButton.setText("Manage Requests (" + count + ")");
                    // Red dot effect: red background, white text, bold, rounded corners
                    manageRequestsButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 15;");
                } else {
                    manageRequestsButton.setText("Manage Requests");
                    manageRequestsButton.setStyle(""); // revert to default
                }
            });
        } catch (Exception e) {
            // If fetch fails, keep default appearance
            Platform.runLater(() -> {
                manageRequestsButton.setText("Manage Requests");
                manageRequestsButton.setStyle("");
            });
        }
    }

    private void refreshFolders() {
        try {
            List<SharedFolderDto> folders = httpClient.getUserSharedFolders(ownerId);
            folderItems.clear();
            for (SharedFolderDto dto : folders) {
                folderItems.add(new SharedFolderItem(
                        dto.getId(),
                        dto.getName(),
                        dto.getOwnerId(),
                        dto.getYourPermission() != null ? dto.getYourPermission().name() : "NONE"
                ));
            }
        } catch (Exception e) {
            showAlert("Error", "Failed to load shared folders: " + e.getMessage());
        }
    }

    @FXML
    private void handleCreateFolder() {
        Stage owner = (Stage) foldersTable.getScene().getWindow();
        CreateFolderDto dto = CreateFolderDialog.show(owner, httpClient);
        if (dto != null) {
            try {
                httpClient.createSharedFolder(dto.getName(), dto.getMembers());
                refreshFolders();
                showAlert("Success", "Folder created: " + dto.getName());
            } catch (Exception e) {
                showAlert("Error", "Failed to create folder: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleRequestAccess() {
        RequestAccessDialog.show(httpClient);
    }

    @FXML
    private void handleOpenFolder() {
        SharedFolderItem selected = foldersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No selection", "Please select a folder");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filesync/client/controller/server-file-list.fxml"));
            VBox root = loader.load();
            ServerFileListController fileController = loader.getController();
            fileController.initialize(httpClient, ownerId, selected.getId());
            Stage stage = new Stage();
            stage.setTitle("Shared Folder: " + selected.getName());
            stage.setScene(new Scene(root, 900, 500));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Cannot open folder: " + e.getMessage());
        }
    }

    @FXML
    private void handleManageMembers() {
        SharedFolderItem selected = foldersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No selection", "Please select a folder");
            return;
        }
        AddMemberDialog.show(selected.getId(), httpClient, () -> {
            refreshFolders();
            if (foldersTable.getSelectionModel().getSelectedItem() != null &&
                    foldersTable.getSelectionModel().getSelectedItem().getOwnerId().equals(ownerId)) {
                updateRequestsButton(selected.getId());
            }
        });
    }

    @FXML
    private void handleManageRequests() {
        SharedFolderItem selected = foldersTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        PendingRequestsDialog.show(selected.getId(), httpClient, () -> {
            refreshFolders();
            if (foldersTable.getSelectionModel().getSelectedItem() != null &&
                    foldersTable.getSelectionModel().getSelectedItem().getOwnerId().equals(ownerId)) {
                updateRequestsButton(selected.getId());
            }
        });
    }

    @FXML
    private void handleDeleteFolder() {
        SharedFolderItem selected = foldersTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete shared folder '" + selected.getName() + "' and all its files?\nThis action cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    httpClient.deleteSharedFolder(selected.getId());
                    showAlert("Success", "Folder deleted.");
                    refreshFolders();
                } catch (Exception e) {
                    showAlert("Error", "Failed to delete folder: " + e.getMessage());
                }
            }
        });
    }



    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Inner class for table items
    public static class SharedFolderItem {
        private final UUID id;
        private final String name;
        private final String ownerId;
        private final String permission;
        public SharedFolderItem(UUID id, String name, String ownerId, String permission) {
            this.id = id; this.name = name; this.ownerId = ownerId; this.permission = permission;
        }
        public UUID getId() { return id; }
        public String getName() { return name; }
        public String getOwnerId() { return ownerId; }
        public String getPermission() { return permission; }
    }
}