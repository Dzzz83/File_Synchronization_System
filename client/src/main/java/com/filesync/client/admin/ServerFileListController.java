package com.filesync.client.admin;

import com.filesync.client.conflict.ConflictResolver;
import com.filesync.client.file.FileHasher;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.SyncStatus;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ServerFileListController {
    @FXML private TableView<ServerFileItem> fileTable;
    @FXML private TableColumn<ServerFileItem, String> pathColumn;
    @FXML private TableColumn<ServerFileItem, Long> sizeColumn;
    @FXML private TableColumn<ServerFileItem, String> lastModifiedColumn;

    private SyncHttpClient httpClient;
    private String ownerId;
    private ObservableList<ServerFileItem> fileItems = FXCollections.observableArrayList();

    // display an alert dialog
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // refresh the window
    private void refreshWindow()
    {
        try
        {
            // get files belongs to the current user
            List<FileMetadataDto> files = httpClient.getFilesByOwner(ownerId);
            // create the old list of files
            fileItems.clear();
            for (FileMetadataDto dto : files)
            {
                // add the files again
                fileItems.add(new ServerFileItem(
                   dto.getFileId(),
                   dto.getRelativePath(),
                   dto.getSize(),
                   dto.getLastModified(),
                   dto.getSha256Hash()
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to load files: " + e.getMessage());
        }
    }

    public void initialize(String ownerId, String serverBaseUrl)
    {
        // save the ownerId
        this.ownerId = ownerId;
        // create an instance configured with the serverBaseUrl
        this.httpClient = new SyncHttpClient(serverBaseUrl);

        // set the values for the columns
        pathColumn.setCellValueFactory(new PropertyValueFactory<>("relativePath"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        lastModifiedColumn.setCellValueFactory(new PropertyValueFactory<>("lastModified"));

        fileTable.setItems(fileItems);
        refreshWindow();
    }

    // refresh button implementation
    @FXML
    private void handleRefresh()
    {
        refreshWindow();
    }

    // delete button implementation
    @FXML
    private void handleDelete()
    {
        // get the file user is currently choosing
        ServerFileItem serverFileItem = fileTable.getSelectionModel().getSelectedItem();

        if (serverFileItem == null)
        {
            showAlert("No selection", "Please select a file to delete");
            return;
        }

        // create an alert dialog of type CONFIRMATION
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + serverFileItem.getRelativePath() + "?",
                ButtonType.YES, ButtonType.NO); // add 2 buttons
        alert.showAndWait().ifPresent(response ->{ // wait for response
            if (response == ButtonType.YES)
            {
                try
                {
                    // delete the file
                    httpClient.deleteFile(serverFileItem.getFileId());
                    refreshWindow();
                    showAlert("Success", "File deleted " + serverFileItem.getRelativePath());
                } catch (Exception e) {
                    showAlert("Delete failed", e.getMessage());
                }
            }
        });
    }

    // download button
    @FXML
    private void handleDownload()
    {
        // get the file user is currently choosing
        ServerFileItem serverFileItem = fileTable.getSelectionModel().getSelectedItem();

        if (serverFileItem == null)
        {
            showAlert("No selection", "Please select a file to download");
            return;
        }

        FileChooser chooser = new FileChooser();
        // set the suggested file name when saved with the original file name
        chooser.setInitialFileName(serverFileItem.getRelativePath());
        // open the save dialog
        File saveFile = chooser.showSaveDialog(fileTable.getScene().getWindow());
        // if user cancels, don't save
        if (saveFile == null)
        {
            return;
        }
        try
        {
            // download the file
            httpClient.downloadFile(serverFileItem.getFileId(), saveFile.toPath());
            showAlert("Download completed", "File saved to " + saveFile.getPath());
        } catch (Exception e)
        {
            showAlert("Download failed", e.getMessage());
        }
    }

    // upload file button
    @FXML
    private void handleUpload() {
        // create a file chooser
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select file to upload");
        // show the dialog and wait for user's input
        File selectedFile = chooser.showOpenDialog(fileTable.getScene().getWindow());
        if (selectedFile == null)
        {
            return;
        }
        // convert File to Path obj
        Path filePath = selectedFile.toPath();
        String fileName = selectedFile.getName();
        String fileId = UUID.randomUUID().toString();

        try {
            // get the file size
            long fileSize = Files.size(filePath);
            long THRESHOLD = 5 * 1024 * 1024; // 5 MB

            // create a dto and set all the parameters
            FileMetadataDto dto = new FileMetadataDto();
            dto.setFileId(fileId);
            dto.setRelativePath(fileName);
            dto.setSize(fileSize);
            dto.setSha256Hash(FileHasher.computeHash(filePath));
            dto.setLastModified(Files.getLastModifiedTime(filePath).toInstant());
            dto.setOwnerId(ownerId);
            dto.setStatus(SyncStatus.SYNCED);

            // send the dto to the server
            httpClient.createMetadata(dto);

            // upload the file
            if (fileSize > THRESHOLD) {
                httpClient.uploadLargeFile(fileId, filePath); // chunked upload
            } else {
                httpClient.uploadFile(fileId, filePath); // normal upload
            }

            refreshWindow();
            showAlert("Upload complete", "File uploaded: " + fileName);
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Upload failed", e.getMessage());
        }
    }
    @FXML
    private void handleEdit() {
        // get the file the user is currently selecting
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
            // create a temporary file
            Path tempFile = Files.createTempFile("edit_", ".tmp");
            // download the content into the temporary file
            httpClient.downloadFile(selected.getFileId(), tempFile);
            // read the original content
            String originalContent = Files.readString(tempFile);

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/filesync/client/admin/edit-dialog.fxml"));
            Parent root = fxmlLoader.load();
            EditDialogController dialogController = fxmlLoader.getController();

            // create a stage (window)
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit File");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(true);

            // make the array effective final
            final String[] newContent = {null};

            dialogController.setData(originalContent, editedContent -> {
                // change the value of the content of the array
                newContent[0] = editedContent;
                dialogStage.close();
            });

            dialogStage.showAndWait();

            if (newContent[0] != null) {
                // get the new content
                String newContentStr = newContent[0];

                // get the current metadata
                FileMetadataDto currentMeta = httpClient.getFileMetadata(selected.getFileId());
                // compare the hash
                if (!currentMeta.getSha256Hash().equals(selected.getSha256Hash())) {
                    // create temp file
                    Path userTemp = Files.createTempFile("user_", ".tmp");
                    // update the temp file with the new content
                    Files.writeString(userTemp, newContentStr);

                    // create a dto
                    FileMetadataDto dto = new FileMetadataDto();
                    // set the variables
                    dto.setFileId(selected.getFileId());
                    dto.setRelativePath(selected.getRelativePath());
                    dto.setSha256Hash(currentMeta.getSha256Hash());

                    try {
                        // solve the conflict
                        ConflictResolver.resolve(dto, userTemp, httpClient, null);
                        refreshWindow();
                        showAlert("success", "Conflict resolved and file is updated: " + selected.getRelativePath());
                    } catch (NullPointerException e) {
                        System.err.println("ConflictResolver failed due to null repository: " + e.getMessage());
                        showAlert("Conflict Error", "Unable to resolve conflict. " +
                                "The file may have been changed. Please refresh and try again.");
                    } finally {
                        Files.deleteIfExists(userTemp);
                    }
                } else {
                    // no conflict logic
                    // create temp file
                    Path userTemp = Files.createTempFile("new_", ".tmp");
                    // write the new content into the temp file
                    Files.writeString(userTemp, newContentStr);
                    // compute new hash
                    String newHash = FileHasher.computeHash(userTemp);

                    // create a dto
                    FileMetadataDto updatedDto = new FileMetadataDto();
                    // set the variables
                    updatedDto.setFileId(selected.getFileId());
                    updatedDto.setRelativePath(selected.getRelativePath());
                    updatedDto.setSize(Files.size(userTemp));
                    updatedDto.setSha256Hash(newHash);
                    updatedDto.setLastModified(Instant.now());
                    updatedDto.setOwnerId(ownerId);
                    updatedDto.setStatus(SyncStatus.SYNCED);

                    // send the metadata to the server
                    httpClient.createMetadata(updatedDto);

                    // get the file size
                    long fileSize = Files.size(userTemp);
                    // upload the file
                    if (fileSize > 5 * 1024 * 1024) {
                        httpClient.uploadLargeFile(selected.getFileId(), userTemp); // chunked upload
                    } else {
                        httpClient.uploadFile(selected.getFileId(), userTemp); // normal upload
                    }

                    Files.deleteIfExists(userTemp);
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
}

