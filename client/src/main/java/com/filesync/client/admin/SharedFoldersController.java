package com.filesync.client.admin;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.CreateFolderDto;
import com.filesync.common.dto.SharedFolderDto;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.List;
import java.util.UUID;

public class SharedFoldersController {
    @FXML private TableView<SharedFolderItem> foldersTable;
    @FXML private TableColumn<SharedFolderItem, String> nameColumn;
    @FXML private TableColumn<SharedFolderItem, String> ownerColumn;
    @FXML private TableColumn<SharedFolderItem, String> permissionColumn;

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
        refreshFolders();
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
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Request Access");
        dialog.setHeaderText("Enter folder ID to request access:");
        dialog.setContentText("Folder ID:");
        dialog.showAndWait().ifPresent(folderIdStr -> {
            showAlert("Info", "Request access feature not yet fully implemented.\nContact folder owner.");
        });
    }

    @FXML
    private void handleOpenFolder() {
        SharedFolderItem selected = foldersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No selection", "Please select a folder");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filesync/client/admin/server-file-list.fxml"));
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
        AddMemberDialog.show(selected.getId(), httpClient, this::refreshFolders);
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