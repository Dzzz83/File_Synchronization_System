package com.filesync.client.controller;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.service.FileOperationService;
import com.filesync.common.dto.FileMetadataDto;
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
import java.util.UUID;

public class ServerFileListController {
    @FXML private TableView<ServerFileItem> fileTable;
    @FXML private TableColumn<ServerFileItem, String> pathColumn;
    @FXML private TableColumn<ServerFileItem, Long> sizeColumn;
    @FXML private TableColumn<ServerFileItem, String> lastModifiedColumn;

    private FileOperationService fileService;
    private ObservableList<ServerFileItem> fileItems = FXCollections.observableArrayList();

    public void initialize(SyncHttpClient httpClient, String ownerId, UUID folderId) {
        this.fileService = new FileOperationService(httpClient, ownerId, folderId);

        pathColumn.setCellValueFactory(new PropertyValueFactory<>("relativePath"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        lastModifiedColumn.setCellValueFactory(new PropertyValueFactory<>("lastModified"));
        fileTable.setItems(fileItems);

        fileTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                ServerFileItem selected = fileTable.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openAnyFile(selected);
                }
            }
        });

        refreshWindow();

        Platform.runLater(() -> {
            Stage stage = (Stage) fileTable.getScene().getWindow();
            stage.setOnCloseRequest(event -> fileService.close());
        });
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
            List<FileMetadataDto> files = fileService.listFiles();
            fileItems.clear();
            for (FileMetadataDto dto : files) {
                fileItems.add(new ServerFileItem(
                        dto.getFileId(),
                        dto.getRelativePath(),
                        dto.getSize(),
                        dto.getLastModified(),
                        dto.getSha256Hash(),
                        dto.getFolderId()
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
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select file to upload");
        File selectedFile = chooser.showOpenDialog(fileTable.getScene().getWindow());
        if (selectedFile == null) return;
        try {
            fileService.uploadFile(selectedFile.toPath());
            refreshWindow();
            showAlert("Upload complete", "File uploaded: " + selectedFile.getName());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Upload failed", e.getMessage());
        }
    }

    @FXML
    private void handleEdit() {
        ServerFileItem selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No selection", "Please select a file to edit.");
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

    private void openAnyFile(ServerFileItem selected) {
        String fileName = selected.getRelativePath();
        System.out.println("Downloading file to open: " + fileName);

        new Thread(() -> {
            try {
                String extension = "";
                int i = fileName.lastIndexOf('.');
                if (i > 0) {
                    extension = fileName.substring(i);
                }

                Path tempFile = Files.createTempFile("open_", extension);
                fileService.downloadFile(selected.getFileId(), tempFile);

                Platform.runLater(() -> {
                    try {
                        File file = tempFile.toFile();

                        if (java.awt.Desktop.isDesktopSupported()) {
                            java.awt.Desktop.getDesktop().open(file);
                        } else {
                            showAlert("Unsupported", "Your system does not support opening files directly.");
                        }

                        file.deleteOnExit();

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        showAlert("Open Error", "No default application found to open this file type.");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showAlert("Download Error", "Could not download file: " + e.getMessage()));
            }
        }).start();
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