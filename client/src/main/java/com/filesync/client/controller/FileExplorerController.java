package com.filesync.client.controller;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.service.FileOperationService;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.client.service.FolderUploadService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Stack;
import java.util.UUID;

import javafx.stage.DirectoryChooser;
import javafx.scene.layout.VBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.Label;
import javafx.geometry.Insets;

public class FileExplorerController {
    @FXML private TableView<ServerFileItem> fileTable;
    @FXML private TableColumn<ServerFileItem, String> pathColumn;
    @FXML private TableColumn<ServerFileItem, Long> sizeColumn;
    @FXML private TableColumn<ServerFileItem, String> lastModifiedColumn;

    private UUID currentParentId;
    private final Stack<UUID> pathStack = new Stack<>();
    private SyncHttpClient httpClient;
    private String ownerId;
    private UUID folderId;
    private Runnable onExitSharedFolder;

    private FileOperationService fileService;
    private ObservableList<ServerFileItem> fileItems = FXCollections.observableArrayList();

    public void initialize(SyncHttpClient httpClient, String ownerId, UUID folderId, UUID parentId) {
        this.fileService = new FileOperationService(httpClient, ownerId, folderId);
        this.currentParentId = parentId;
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.folderId = folderId;

        pathColumn.setCellValueFactory(new PropertyValueFactory<>("relativePath"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        lastModifiedColumn.setCellValueFactory(new PropertyValueFactory<>("lastModified"));
        fileTable.setItems(fileItems);
        fileTable.setRowFactory(tv -> {
            TableRow<ServerFileItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    onFolderDoubleClick(row.getItem());
                }
            });
            return row;
        });
        refreshWindow();

        Platform.runLater(() -> {
            Stage stage = (Stage) fileTable.getScene().getWindow();
            stage.setOnCloseRequest(event -> fileService.close());
        });
    }

    public void setOnExitSharedFolder(Runnable callback) {
        this.onExitSharedFolder = callback;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void refreshWindow() {
        try {
            List<FileMetadataDto> files = httpClient.getFiles(ownerId, folderId, currentParentId);
            fileItems.clear();

            // Add ".." entry if not at root
            if (currentParentId != null || !pathStack.isEmpty() || folderId != null) {
                fileItems.add(new ServerFileItem(
                        "parent",           // dummy fileId
                        "..",               // relativePath
                        0,                  // size
                        null,               // lastModified
                        null,               // sha256Hash
                        folderId,           // folderId (shared or null)
                        true,               // isDirectory
                        null                // parentId
                ));
            }

            for (FileMetadataDto dto : files) {
                fileItems.add(new ServerFileItem(
                        dto.getFileId(),
                        dto.getRelativePath(),
                        dto.getSize(),
                        dto.getLastModified(),
                        dto.getSha256Hash(),
                        dto.getFolderId(),
                        dto.isDirectory(),
                        dto.getParentId()
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to load files: " + e.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        refreshWindow();
    }

    @FXML
    private void handleDelete() {
        ServerFileItem selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No selection", "Please select a file to delete");
            return;
        }
        if ("..".equals(selected.getRelativePath())) {
            showAlert("Invalid Action", "Cannot delete the parent directory entry.");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + selected.getRelativePath() + "?",
                ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    fileService.deleteFile(selected.getFileId());
                    refreshWindow();
                    showAlert("Success", "File deleted " + selected.getRelativePath());
                } catch (Exception e) {
                    showAlert("Delete failed", e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleDownload() {
        ServerFileItem selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No selection", "Please select a file to download");
            return;
        }
        if ("..".equals(selected.getRelativePath())) {
            showAlert("Invalid Action", "Cannot download the parent directory entry.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(selected.getRelativePath());
        File saveFile = chooser.showSaveDialog(fileTable.getScene().getWindow());
        if (saveFile == null) return;
        try {
            fileService.downloadFile(selected.getFileId(), saveFile.toPath());
            showAlert("Download completed", "File saved to " + saveFile.getPath());
        } catch (Exception e) {
            showAlert("Download failed", e.getMessage());
        }
    }

    @FXML
    private void handleUpload() {
        Alert choice = new Alert(Alert.AlertType.CONFIRMATION);
        choice.setTitle("Upload");
        choice.setHeaderText("What do you want to upload?");
        choice.setContentText("Choose an option:");
        ButtonType fileButton = new ButtonType("File");
        ButtonType folderButton = new ButtonType("Folder");
        ButtonType cancelButton = ButtonType.CANCEL;
        choice.getButtonTypes().setAll(fileButton, folderButton, cancelButton);
        choice.showAndWait().ifPresent(button -> {
            if (button == fileButton) {
                uploadFile();
            } else if (button == folderButton) {
                uploadFolder();
            }
        });
    }

    private void uploadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select file to upload");
        File selectedFile = chooser.showOpenDialog(fileTable.getScene().getWindow());
        if (selectedFile == null) return;
        try {
            fileService.uploadFile(selectedFile.toPath(), currentParentId);
            refreshWindow();
            showAlert("Upload complete", "File uploaded: " + selectedFile.getName());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Upload failed", e.getMessage());
        }
    }

    private void uploadFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder to Upload");
        File selectedDir = chooser.showDialog(fileTable.getScene().getWindow());
        if (selectedDir == null) return;

        Stage progressStage = new Stage();
        progressStage.initModality(Modality.APPLICATION_MODAL);
        VBox progressBox = new VBox(10);
        progressBox.setPadding(new Insets(10));
        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        Button closeBtn = new Button("Close");
        closeBtn.setDisable(true);
        progressBox.getChildren().addAll(new Label("Uploading folder..."), logArea, closeBtn);
        Scene scene = new Scene(progressBox, 400, 300);
        progressStage.setScene(scene);
        progressStage.show();

        new Thread(() -> {
            try {
                FolderUploadService service = new FolderUploadService(httpClient, ownerId, folderId, currentParentId,
                        selectedDir.toPath(), log -> Platform.runLater(() -> logArea.appendText(log + "\n")));
                service.upload();
                Platform.runLater(() -> {
                    closeBtn.setDisable(false);
                    closeBtn.setOnAction(e -> progressStage.close());
                    refreshWindow();
                });
            } catch (Exception e) {
                Platform.runLater(() -> logArea.appendText("Error: " + e.getMessage() + "\n"));
            }
        }).start();
    }

    @FXML
    private void handleEdit() {
        ServerFileItem selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No selection", "Please select a file to edit.");
            return;
        }
        if ("..".equals(selected.getRelativePath())) {
            showAlert("Invalid Action", "Cannot edit the parent directory entry.");
            return;
        }
        if (selected.getFileId() == null || selected.getFileId().trim().isEmpty()) {
            showAlert("Error", "Selected file has an invalid ID. Please refresh the list.");
            return;
        }
        try {
            Path tempFile = Files.createTempFile("edit_", ".tmp");
            fileService.downloadFile(selected.getFileId(), tempFile);
            String originalContent = Files.readString(tempFile);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filesync/client/controller/edit-dialog.fxml"));
            Parent root = loader.load();
            EditDialogController dialogController = loader.getController();
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit File");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(true);

            final String[] newContent = {null};
            dialogController.setData(originalContent, editedContent -> {
                newContent[0] = editedContent;
                dialogStage.close();
            });
            dialogStage.showAndWait();

            if (newContent[0] != null) {
                FileMetadataDto currentMeta = fileService.getMetadata(selected.getFileId());
                if (!currentMeta.getSha256Hash().equals(selected.getSha256Hash())) {
                    Path userTemp = Files.createTempFile("user_", ".tmp");
                    Files.writeString(userTemp, newContent[0]);
                    FileMetadataDto conflictDto = new FileMetadataDto();
                    conflictDto.setFileId(selected.getFileId());
                    conflictDto.setRelativePath(selected.getRelativePath());
                    conflictDto.setSha256Hash(currentMeta.getSha256Hash());
                    conflictDto.setFolderId(selected.getFolderId());
                    try {
                        fileService.resolveConflict(conflictDto, userTemp, currentMeta);
                        refreshWindow();
                        showAlert("Success", "Conflict resolved and file updated: " + selected.getRelativePath());
                    } catch (Exception e) {
                        showAlert("Conflict Error", "Unable to resolve conflict. Please refresh and try again.");
                    } finally {
                        Files.deleteIfExists(userTemp);
                    }
                } else {
                    fileService.editFile(currentMeta, newContent[0]);
                    refreshWindow();
                    showAlert("Success", "File updated: " + selected.getRelativePath());
                }
            }
            Files.deleteIfExists(tempFile);
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Edit failed", e.getMessage());
        }
    }

    private void onFolderDoubleClick(ServerFileItem item) {
        if (item.isDirectory()) {
            if ("..".equals(item.getRelativePath())) {
                handleGoUp();
            } else {
                pathStack.push(currentParentId);
                currentParentId = UUID.fromString(item.getFileId());
                refreshWindow();
            }
        }
    }

    private void handleGoUp() {
        if (!pathStack.isEmpty()) {
            currentParentId = pathStack.pop();
            refreshWindow();
        } else if (folderId != null && onExitSharedFolder != null) {
            // At root of shared folder – exit back to shared folders list
            onExitSharedFolder.run();
        }
    }

    @FXML
    private void handleNewFolder() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filesync/client/dialog/new-folder-dialog.fxml"));
            Parent root = loader.load();
            CreateFolderController controller = loader.getController();
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Create New Folder");
            dialogStage.setScene(new Scene(root, 300, 150));
            controller.setData(dialogStage, httpClient, ownerId, folderId, currentParentId, () -> refreshWindow());
            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Could not open new folder dialog: " + e.getMessage());
        }
    }

    @FXML
    private void handleLogout() {
        fileService.logout();
        fileService.close();
        Stage stage = (Stage) fileTable.getScene().getWindow();
        stage.close();
        try {
            new ServerAdminApp().start(new Stage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}