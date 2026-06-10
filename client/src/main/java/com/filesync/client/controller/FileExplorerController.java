package com.filesync.client.controller;

import com.filesync.client.controller.helper.BulkOperationHandler;
import com.filesync.client.controller.helper.ButtonPermissionManager;
import com.filesync.client.controller.helper.DragDropHandler;
import com.filesync.client.controller.helper.BreadcrumbManager;
import com.filesync.client.dialog.*;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.service.FileOperationService;
import com.filesync.client.service.FolderUploadService;
import com.filesync.client.service.ProgressService;
import com.filesync.client.task.*;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.Permission;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.stage.*;
import javafx.scene.Node;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileExplorerController {
    @FXML private TableView<ServerFileItem> fileTable;
    @FXML private TableColumn<ServerFileItem, String> pathColumn;
    @FXML private TableColumn<ServerFileItem, Long> sizeColumn;
    @FXML private TableColumn<ServerFileItem, String> lastModifiedColumn;
    @FXML private TableColumn<ServerFileItem, Node> iconColumn;
    @FXML private Label pathLabel;

    @FXML private Button uploadButton;
    @FXML private Button downloadButton;
    @FXML private Button deleteButton;
    @FXML private Button newFolderButton;
    @FXML private Button refreshButton;

    private SyncHttpClient httpClient;
    private String ownerId;
    private UUID folderId;
    private ExecutorService executorService;
    private FileOperationService fileService;

    private BreadcrumbManager breadcrumbManager;
    private BulkOperationHandler bulkOperationHandler;
    private DragDropHandler dragDropHandler;
    private final ObservableList<ServerFileItem> fileItems = FXCollections.observableArrayList();

    public void initialize(SyncHttpClient httpClient, String ownerId, UUID folderId, UUID parentId, String rootDisplayName) {
        if (executorService == null) {
            throw new IllegalStateException("ExecutorService must be set before calling initialize()");
        }
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.folderId = folderId;
        this.fileService = new FileOperationService(httpClient, ownerId, folderId);

        configureTableColumns();
        configureTableSelection();
        configureRowFactory();

        breadcrumbManager = new BreadcrumbManager(pathLabel, rootDisplayName);
        breadcrumbManager.setCurrentParentId(parentId);
        breadcrumbManager.setOnExitSharedFolder(this::showSharedFoldersList);

        bulkOperationHandler = new BulkOperationHandler(httpClient, fileService, this::refreshWindow, executorService);
        dragDropHandler = new DragDropHandler(fileTable, (fileIds, targetId) -> {
            List<String> names = fileIds.stream().map(id -> "item").collect(Collectors.toList());
            bulkOperationHandler.bulkMove(fileIds, names, targetId);
        });

        new ButtonPermissionManager(fileTable, ProgressService.getInstance(), deleteButton, downloadButton);

        refreshWindow();
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public void setOnExitSharedFolder(Runnable callback) {
        // Override the exit handler in breadcrumb manager
        breadcrumbManager.setOnExitSharedFolder(() -> {
            breadcrumbManager.reset();
            callback.run();
        });
    }

    private void configureTableColumns() {
        iconColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Node item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : item);
            }
        });
        iconColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getIcon()));

        pathColumn.setCellValueFactory(new PropertyValueFactory<>("relativePath"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        sizeColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatFileSize(item));
            }
        });
        lastModifiedColumn.setCellValueFactory(new PropertyValueFactory<>("lastModified"));
        fileTable.setItems(fileItems);
    }

    private void configureTableSelection() {
        fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    private void configureRowFactory() {
        fileTable.setRowFactory(tv -> {
            TableRow<ServerFileItem> row = new TableRow<>();
            dragDropHandler.setupDragAndDrop(row);
            row.setOnMouseClicked(this::onRowDoubleClick);
            return row;
        });
    }

    private void onRowDoubleClick(MouseEvent event) {
        if (event.getClickCount() == 2) {
            TableRow<ServerFileItem> row = (TableRow<ServerFileItem>) event.getSource();
            if (!row.isEmpty()) {
                onItemDoubleClick(row.getItem());
            }
        }
    }

    private void onItemDoubleClick(ServerFileItem item) {
        if (item.isDirectory()) {
            if ("..".equals(item.getRelativePath())) {
                if (breadcrumbManager.canGoUp()) {
                    breadcrumbManager.navigateUp();
                    refreshWindow();
                } else if (folderId != null) {
                    breadcrumbManager.exitSharedFolder();
                }
            } else {
                breadcrumbManager.navigateInto(UUID.fromString(item.getFileId()), item.getRelativePath());
                refreshWindow();
            }
        } else if (isTextFile(item)) {
            if (item.getUserPermission() != Permission.WRITE) {
                showAlert("Permission Denied", "You don't have write permission to edit this file.");
                return;
            }
            fileTable.getSelectionModel().select(item);
            handleEdit();
        }
    }

    private boolean isTextFile(ServerFileItem item) {
        if (item.isDirectory()) return false;
        String path = item.getRelativePath();
        return path != null && path.toLowerCase().endsWith(".txt");
    }

    private void refreshWindow() {
        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Refreshing...");

        boolean showParent = (breadcrumbManager.getCurrentParentId() != null || !breadcrumbManager.getPathStack().isEmpty() || folderId != null);
        RefreshTask task = new RefreshTask(httpClient, ownerId, folderId, breadcrumbManager.getCurrentParentId(), fileItems, showParent);
        task.setOnFailed(e -> {
            ps.finishOperation();
            showAlert("Error", "Failed to load files: " + task.getException().getMessage());
        });
        executorService.submit(task);
    }

    private void showSharedFoldersList() {
        // This method is called when exiting a shared folder – close the explorer and return to parent view.
        // In practice, the parent (SharedFoldersController) should handle this via the callback.
        // Here we simply run the callback set via setOnExitSharedFolder.
        if (breadcrumbManager != null) breadcrumbManager.reset();
    }

    // ==================== File Operations (event handlers) ====================

    @FXML private void handleRefresh() { refreshWindow(); }

    @FXML private void handleDelete() {
        ObservableList<ServerFileItem> selected = fileTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) {
            showAlert("No selection", "Please select at least one item to delete");
            return;
        }
        if (selected.stream().anyMatch(item -> "..".equals(item.getRelativePath()))) {
            showAlert("Invalid Action", "Cannot delete the parent directory entry.");
            return;
        }

        String message = selected.size() == 1 ?
                "Delete \"" + selected.get(0).getRelativePath() + "\"?\nThis action cannot be undone." :
                "Delete " + selected.size() + " items?\nThis action cannot be undone.";
        if (ConfirmationDialog.show((Stage) fileTable.getScene().getWindow(), message)) {
            bulkOperationHandler.bulkDelete(selected);
        }
    }

    @FXML private void handleDownload() {
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

        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Downloading " + selected.getRelativePath());
        DownloadTask task = new DownloadTask(httpClient, selected.getFileId(), saveFile.toPath(), selected.getRelativePath());
        task.messageProperty().addListener((obs, old, msg) -> ps.updateMessage(msg));
        task.setOnSucceeded(e -> { ps.finishOperation(); showAlert("Download completed", "File saved to " + saveFile.getPath()); });
        task.setOnFailed(e -> { ps.finishOperation(); showAlert("Download failed", task.getException().getMessage()); });
        executorService.submit(task);
    }

    @FXML private void handleUpload() {
        UploadChoiceDialog.show((Stage) fileTable.getScene().getWindow(), this::uploadFile, this::uploadFolder);
    }

    private void uploadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select file to upload");
        File selectedFile = chooser.showOpenDialog(fileTable.getScene().getWindow());
        if (selectedFile == null) return;

        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Uploading " + selectedFile.getName());
        UploadTask task = new UploadTask(httpClient, ownerId, folderId, breadcrumbManager.getCurrentParentId(), selectedFile.toPath());
        task.messageProperty().addListener((obs, old, msg) -> ps.updateMessage(msg));
        task.progressProperty().addListener((obs, old, val) -> ps.updateProgress(val.doubleValue(), 1.0));
        task.setOnSucceeded(e -> { ps.finishOperation(); refreshWindow(); showAlert("Success", "Uploaded: " + selectedFile.getName()); });
        task.setOnFailed(e -> { ps.finishOperation(); showAlert("Error", "Upload failed: " + task.getException().getMessage()); });
        executorService.submit(task);
    }

    private void uploadFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder to Upload");
        File selectedDir = chooser.showDialog(fileTable.getScene().getWindow());
        if (selectedDir == null) return;

        int totalFiles;
        try (Stream<Path> walk = Files.walk(selectedDir.toPath())) {
            totalFiles = (int) walk.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            showAlert("Error", "Failed to count files: " + e.getMessage());
            return;
        }
        final int finalTotalFiles = totalFiles;
        final ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Uploading folder " + selectedDir.getName());
        ps.updateProgress(0, finalTotalFiles);

        executorService.submit(() -> {
            try {
                FolderUploadService service = new FolderUploadService(
                        httpClient, ownerId, folderId, breadcrumbManager.getCurrentParentId(),
                        selectedDir.toPath(), msg -> {}, ps, finalTotalFiles
                );
                service.upload();
                Platform.runLater(() -> { ps.finishOperation(); refreshWindow(); showAlert("Success", "Folder uploaded successfully."); });
            } catch (Exception e) {
                Platform.runLater(() -> { ps.finishOperation(); showAlert("Folder Upload Error", e.getMessage()); });
            }
        });
    }

    @FXML private void handleEdit() {
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

        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Downloading " + selected.getRelativePath() + " for editing");
        EditTask editTask = new EditTask(httpClient, selected.getFileId(), selected.getRelativePath());
        editTask.messageProperty().addListener((obs, old, msg) -> ps.updateMessage(msg));

        editTask.setOnSucceeded(e -> {
            ps.finishOperation();
            try {
                Path tempFile = editTask.getValue();
                String originalContent = Files.readString(tempFile);
                Platform.runLater(() -> openEditDialog(selected, tempFile, originalContent));
            } catch (Exception ex) {
                showAlert("Edit failed", "Could not open file: " + ex.getMessage());
            }
        });
        editTask.setOnFailed(e -> { ps.finishOperation(); showAlert("Edit failed", editTask.getException().getMessage()); });
        executorService.submit(editTask);
    }

    private void openEditDialog(ServerFileItem selected, Path tempFile, String originalContent) {
        try {
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
                    handleConflict(selected, currentMeta, newContent[0]);
                } else {
                    fileService.editFile(currentMeta, newContent[0]);
                    refreshWindow();
                    showAlert("Success", "File updated: " + selected.getRelativePath());
                }
            }
            Files.deleteIfExists(tempFile);
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Edit failed", ex.getMessage());
        }
    }

    private void handleConflict(ServerFileItem selected, FileMetadataDto currentMeta, String newContent) throws IOException {
        Path userTemp = Files.createTempFile("user_", ".tmp");
        Files.writeString(userTemp, newContent);
        try {
            fileService.resolveConflict(currentMeta, userTemp);
            refreshWindow();
            showAlert("Success", "Conflict resolved and file updated: " + selected.getRelativePath());
        } catch (Exception e) {
            showAlert("Conflict Error", "Unable to resolve conflict: " + e.getMessage());
        } finally {
            Files.deleteIfExists(userTemp);
        }
    }

    @FXML private void handleNewFolder() {
        Stage owner = (Stage) fileTable.getScene().getWindow();
        CreateFolderDialog.show(owner, httpClient, ownerId, folderId, breadcrumbManager.getCurrentParentId(), this::refreshWindow, executorService);
    }

    @FXML private void handleLogout() {
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        int unitIndex = 0;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double converted = size;
        while (converted >= 1024 && unitIndex < units.length - 1) {
            converted /= 1024;
            unitIndex++;
        }
        return String.format("%.1f %s", converted, units[unitIndex]);
    }
}