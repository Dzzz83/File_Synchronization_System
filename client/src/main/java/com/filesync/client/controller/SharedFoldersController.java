package com.filesync.client.controller;

import com.filesync.client.dialog.AddMemberDialog;
import com.filesync.client.dialog.CreateFolderDialog;
import com.filesync.client.dialog.RequestAccessDialog;
import com.filesync.client.dialog.PendingRequestsDialog;
import com.filesync.common.dto.CreateFolderDto;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.SharedFolderDto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.List;
import java.util.UUID;

public class SharedFoldersController {
    @FXML private TableView<SharedFolderItem> foldersTable;
    @FXML private TableColumn<SharedFolderItem, String> nameColumn;
    @FXML private TableColumn<SharedFolderItem, String> ownerColumn;
    @FXML private TableColumn<SharedFolderItem, String> permissionColumn;
    @FXML private Button manageRequestsButton;
    @FXML private Button deleteFolderButton;
    @FXML private VBox container;
    @FXML private HBox actionButtons;

    private SyncHttpClient httpClient;
    private String ownerId;
    private ObservableList<SharedFolderItem> folderItems = FXCollections.observableArrayList();
    private FileExplorerController currentExplorer;
    private boolean showingFoldersList = true;

    public void initialize(SyncHttpClient httpClient, String ownerId) {
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        ownerColumn.setCellValueFactory(new PropertyValueFactory<>("ownerId"));
        permissionColumn.setCellValueFactory(new PropertyValueFactory<>("permission"));
        foldersTable.setItems(folderItems);

        foldersTable.setRowFactory(tv -> {
            TableRow<SharedFolderItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    onFolderDoubleClick(row.getItem());
                }
            });
            return row;
        });

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

        showSharedFoldersList();
        refreshFolders();
    }

    private void showSharedFoldersList() {
        showingFoldersList = true;
        actionButtons.setVisible(true);
        actionButtons.setManaged(true);   // re-insert into layout
        container.getChildren().clear();
        container.getChildren().add(foldersTable);
    }

    private void showFolderExplorer(UUID folderId) {
        showingFoldersList = false;
        actionButtons.setVisible(false);
        actionButtons.setManaged(false);  // remove from layout
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filesync/client/controller/server-file-list.fxml"));
            VBox explorerRoot = loader.load();
            currentExplorer = loader.getController();
            currentExplorer.initialize(httpClient, ownerId, folderId, null);
            currentExplorer.setOnExitSharedFolder(this::showSharedFoldersList);
            container.getChildren().clear();
            container.getChildren().add(explorerRoot);
            VBox.setVgrow(explorerRoot, Priority.ALWAYS);
            explorerRoot.setMaxHeight(Double.MAX_VALUE);
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Could not open folder: " + e.getMessage());
            actionButtons.setVisible(true);
            actionButtons.setManaged(true);
            showingFoldersList = true;
        }
    }

    private void onFolderDoubleClick(SharedFolderItem item) {
        showFolderExplorer(item.getId());
    }

    private void updateRequestsButton(UUID folderId) {
        try {
            int count = httpClient.getPendingRequestsCount(folderId);
            Platform.runLater(() -> {
                if (count > 0) {
                    manageRequestsButton.setText("Manage Requests (" + count + ")");
                    manageRequestsButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 15;");
                } else {
                    manageRequestsButton.setText("Manage Requests");
                    manageRequestsButton.setStyle("");
                }
            });
        } catch (Exception e) {
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