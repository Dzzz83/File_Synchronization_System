package com.filesync.client.controller;

import com.filesync.client.dialog.*;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.model.DragData;
import com.filesync.client.model.FileTransferData;
import com.filesync.client.service.FileOperationService;
import com.filesync.client.service.FolderUploadService;
import com.filesync.client.service.ProgressService;
import com.filesync.client.task.*;
import com.filesync.common.dto.FileMetadataDto;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.*;
import javafx.stage.*;
import javafx.scene.Node;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileExplorerController {
    // FXML components
    @FXML private TableView<ServerFileItem> fileTable;
    @FXML private TableColumn<ServerFileItem, String> pathColumn;
    @FXML private TableColumn<ServerFileItem, Long> sizeColumn;
    @FXML private TableColumn<ServerFileItem, String> lastModifiedColumn;
    @FXML private TableColumn<ServerFileItem, Node> iconColumn;

    // Buttons for disabling during operations
    @FXML private Button uploadButton;
    @FXML private Button downloadButton;
    @FXML private Button deleteButton;
    @FXML private Button newFolderButton;
    @FXML private Button editButton;
    @FXML private Button refreshButton;

    // State
    private UUID currentParentId;
    private final Stack<UUID> pathStack = new Stack<>();
    private SyncHttpClient httpClient;
    private String ownerId;
    private UUID folderId;
    private Runnable onExitSharedFolder;
    private ExecutorService executorService;

    // Services
    private FileOperationService fileService;
    private final ObservableList<ServerFileItem> fileItems = FXCollections.observableArrayList();

    // ==================== Initialization ====================

    public void initialize(SyncHttpClient httpClient, String ownerId, UUID folderId, UUID parentId) {
        if (executorService == null) {
            throw new IllegalStateException("ExecutorService must be set before calling initialize()");
        }
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.folderId = folderId;
        this.currentParentId = parentId;
        this.fileService = new FileOperationService(httpClient, ownerId, folderId);

        configureTableColumns();
        configureTableSelection();
        configureRowFactory();
        refreshWindow();

        // Disable buttons while any operation is running
        ProgressService ps = ProgressService.getInstance();
        uploadButton.disableProperty().bind(ps.busyProperty());
        downloadButton.disableProperty().bind(ps.busyProperty());
        deleteButton.disableProperty().bind(ps.busyProperty());
        newFolderButton.disableProperty().bind(ps.busyProperty());
        editButton.disableProperty().bind(ps.busyProperty());
        refreshButton.disableProperty().bind(ps.busyProperty());
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    private void configureTableColumns() {
        pathColumn.setCellValueFactory(new PropertyValueFactory<>("relativePath"));

        // Size column: numeric value (for sorting) with custom formatting
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        sizeColumn.setCellFactory(column -> new TableCell<ServerFileItem, Long>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(formatFileSize(item));
                }
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
            setupDragAndDrop(row);
            setupDoubleClick(row);
            return row;
        });
    }

    // ==================== Drag & Drop ====================

    private void setupDragAndDrop(TableRow<ServerFileItem> row) {
        row.setOnDragDetected(event -> onDragDetected(row, event));
        row.setOnDragOver(event -> onDragOver(row, event));
        row.setOnDragDropped(event -> onDragDropped(row, event));
    }

    private void onDragDetected(TableRow<ServerFileItem> row, MouseEvent event) {
        if (row.isEmpty()) return;
        ObservableList<ServerFileItem> selected = fileTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) return;

        List<String> fileIds = selected.stream()
                .map(ServerFileItem::getFileId)
                .collect(Collectors.toList());
        List<String> fileNames = selected.stream()
                .map(ServerFileItem::getRelativePath)
                .collect(Collectors.toList());

        Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
        ClipboardContent content = new ClipboardContent();

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            DragData dragData = new DragData(fileIds, fileNames);
            oos.writeObject(dragData);
            content.put(FileTransferData.DRAG_DATA, bos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        db.setContent(content);
        event.consume();
    }

    private void onDragOver(TableRow<ServerFileItem> row, DragEvent event) {
        if (!row.isEmpty() && row.getItem().isDirectory()) {
            Dragboard db = event.getDragboard();
            if (db.hasContent(FileTransferData.DRAG_DATA)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
        }
        event.consume();
    }

    private void onDragDropped(TableRow<ServerFileItem> row, DragEvent event) {
        Dragboard db = event.getDragboard();
        if (!db.hasContent(FileTransferData.DRAG_DATA)) {
            event.setDropCompleted(false);
            event.consume();
            return;
        }

        try {
            byte[] data = (byte[]) db.getContent(FileTransferData.DRAG_DATA);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                DragData dragData = (DragData) ois.readObject();
                List<String> fileIds = dragData.getFileIds();
                List<String> fileNames = dragData.getFileNames();

                ServerFileItem target = row.getItem();
                String targetId = resolveDropTargetId(target);
                if (targetId != null) {
                    bulkMove(fileIds, fileNames, targetId);
                    event.setDropCompleted(true);
                } else {
                    event.setDropCompleted(false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            event.setDropCompleted(false);
        }
        event.consume();
    }

    private String resolveDropTargetId(ServerFileItem target) {
        if ("..".equals(target.getRelativePath())) {
            if (pathStack.isEmpty()) {
                return ""; // move to personal root
            } else {
                UUID parent = pathStack.peek();
                return parent == null ? "" : parent.toString();
            }
        } else if (target.isDirectory()) {
            return target.getFileId();
        }
        return null;
    }

    private void setupDoubleClick(TableRow<ServerFileItem> row) {
        row.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !row.isEmpty()) {
                onFolderDoubleClick(row.getItem());
            }
        });
    }

    private void onFolderDoubleClick(ServerFileItem item) {
        if (!item.isDirectory()) return;

        if ("..".equals(item.getRelativePath())) {
            handleGoUp();
        } else {
            // Always push the current parent ID (maybe null for personal root)
            pathStack.push(currentParentId);
            currentParentId = UUID.fromString(item.getFileId());
            refreshWindow();
        }
    }

    private void handleGoUp() {
        if (!pathStack.isEmpty()) {
            currentParentId = pathStack.pop();
            refreshWindow();
        } else if (folderId != null && onExitSharedFolder != null) {
            onExitSharedFolder.run();
        }
    }

    // ==================== File Listing ====================

    private void refreshWindow() {
        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Refreshing...");

        boolean showParent = (currentParentId != null || !pathStack.isEmpty() || folderId != null);
        RefreshTask task = new RefreshTask(httpClient, ownerId, folderId, currentParentId, fileItems, showParent);
        task.setOnFailed(e -> {
            ps.finishOperation();
            showAlert("Error", "Failed to load files: " + task.getException().getMessage());
        });
        executorService.submit(task);
    }

    // ==================== File Operations ====================

    @FXML
    private void handleRefresh() {
        refreshWindow();
    }

    @FXML
    private void handleDelete() {
        ObservableList<ServerFileItem> selected = fileTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) {
            showAlert("No selection", "Please select at least one item to delete");
            return;
        }
        if (containsParentEntry(selected)) {
            showAlert("Invalid Action", "Cannot delete the parent directory entry.");
            return;
        }

        String message = buildDeleteConfirmationMessage(selected);
        boolean confirmed = ConfirmationDialog.show((Stage) fileTable.getScene().getWindow(), message);
        if (confirmed) {
            bulkDelete(selected);
        }
    }

    private boolean containsParentEntry(ObservableList<ServerFileItem> items) {
        return items.stream().anyMatch(item -> "..".equals(item.getRelativePath()));
    }

    private String buildDeleteConfirmationMessage(ObservableList<ServerFileItem> items) {
        if (items.size() == 1) {
            return "Delete \"" + items.get(0).getRelativePath() + "\"?\nThis action cannot be undone.";
        } else {
            return "Delete " + items.size() + " items?\nThis action cannot be undone.";
        }
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

        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Downloading " + selected.getRelativePath());

        DownloadTask task = new DownloadTask(httpClient, selected.getFileId(), saveFile.toPath(), selected.getRelativePath());
        task.messageProperty().addListener((obs, old, msg) -> ps.updateMessage(msg));
        // DownloadTask doesn't report progress, so we keep indeterminate progress bar

        task.setOnSucceeded(e -> {
            ps.finishOperation();
            showAlert("Download completed", "File saved to " + saveFile.getPath());
        });
        task.setOnFailed(e -> {
            ps.finishOperation();
            showAlert("Download failed", task.getException().getMessage());
        });

        executorService.submit(task);
    }

    @FXML
    private void handleUpload() {
        UploadChoiceDialog.show((Stage) fileTable.getScene().getWindow(), this::uploadFile, this::uploadFolder);
    }

    private void uploadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select file to upload");
        File selectedFile = chooser.showOpenDialog(fileTable.getScene().getWindow());
        if (selectedFile == null) return;

        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Uploading " + selectedFile.getName());

        UploadTask task = new UploadTask(httpClient, ownerId, folderId, currentParentId, selectedFile.toPath());
        task.messageProperty().addListener((obs, old, msg) -> ps.updateMessage(msg));
        task.progressProperty().addListener((obs, old, val) -> ps.updateProgress(val.doubleValue(), 1.0));

        task.setOnSucceeded(e -> {
            ps.finishOperation();
            refreshWindow();
            showAlert("Success", "Uploaded: " + selectedFile.getName());
        });
        task.setOnFailed(e -> {
            ps.finishOperation();
            showAlert("Error", "Upload failed: " + task.getException().getMessage());
        });
        executorService.submit(task);
    }

    private void uploadFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder to Upload");
        File selectedDir = chooser.showDialog(fileTable.getScene().getWindow());
        if (selectedDir == null) return;

        // Count total files in the folder (including subfolders)
        int totalFiles = 0;
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
                        httpClient, ownerId, folderId, currentParentId,
                        selectedDir.toPath(), msg -> { /* optional: log to console or ignore */ }, ps, finalTotalFiles
                );
                service.upload();
                Platform.runLater(() -> {
                    ps.finishOperation();
                    refreshWindow();
                    showAlert("Success", "Folder uploaded successfully.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    ps.finishOperation();
                    showAlert("Folder Upload Error", e.getMessage());
                });
            }
        });
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

        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Downloading " + selected.getRelativePath() + " for editing");

        EditTask editTask = new EditTask(httpClient, selected.getFileId(), selected.getRelativePath());
        editTask.messageProperty().addListener((obs, old, msg) -> ps.updateMessage(msg));

        editTask.setOnSucceeded(e -> {
            ps.finishOperation();
            try {
                Path tempFile = editTask.getValue();
                String originalContent = Files.readString(tempFile);

                // Open editor on UI thread
                Platform.runLater(() -> {
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
                            // Get fresh metadata (server might have changed)
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
                });
            } catch (Exception ex) {
                showAlert("Edit failed", "Could not open file: " + ex.getMessage());
            }
        });

        editTask.setOnFailed(e -> {
            ps.finishOperation();
            showAlert("Edit failed", editTask.getException().getMessage());
        });

        executorService.submit(editTask);
    }

    private void handleConflict(ServerFileItem selected, FileMetadataDto currentMeta, String newContent) throws IOException {
        Path userTemp = Files.createTempFile("user_", ".tmp");
        Files.writeString(userTemp, newContent);
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
    }

    @FXML
    private void handleNewFolder() {
        Stage owner = (Stage) fileTable.getScene().getWindow();
        CreateFolderDialog.show(owner, httpClient, ownerId, folderId, currentParentId, this::refreshWindow, executorService);
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

    // ==================== Bulk Operations ====================

    private void bulkMove(List<String> fileIds, List<String> fileNames, String targetFolderId) {
        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Moving " + fileIds.size() + " item(s)");

        MoveTask task = new MoveTask(httpClient, fileIds, targetFolderId, fileNames);
        task.messageProperty().addListener((obs, old, msg) -> ps.updateMessage(msg));
        task.progressProperty().addListener((obs, old, val) -> ps.updateProgress(val.doubleValue(), 1.0));

        task.setOnSucceeded(e -> {
            ps.finishOperation();
            refreshWindow();
            showAlert("Success", "Moved " + fileIds.size() + " item(s)");
        });
        task.setOnFailed(e -> {
            ps.finishOperation();
            showAlert("Error", "Move failed: " + task.getException().getMessage());
        });

        executorService.submit(task);
    }

    private void bulkDelete(ObservableList<ServerFileItem> items) {
        List<String> fileIds = items.stream().map(ServerFileItem::getFileId).collect(Collectors.toList());
        List<String> fileNames = items.stream().map(ServerFileItem::getRelativePath).collect(Collectors.toList());

        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Deleting " + fileIds.size() + " item(s)");

        DeleteTask task = new DeleteTask(fileService, fileIds, fileNames);
        task.messageProperty().addListener((obs, old, msg) -> ps.updateMessage(msg));
        task.progressProperty().addListener((obs, old, val) -> ps.updateProgress(val.doubleValue(), 1.0));

        task.setOnSucceeded(e -> {
            ps.finishOperation();
            refreshWindow();
            showAlert("Success", "Deleted " + items.size() + " item(s)");
        });
        task.setOnFailed(e -> {
            ps.finishOperation();
            showAlert("Error", "Delete failed: " + task.getException().getMessage());
        });

        executorService.submit(task);
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

    // ==================== Utilities ====================

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void setOnExitSharedFolder(Runnable callback) {
        this.onExitSharedFolder = callback;
    }
}