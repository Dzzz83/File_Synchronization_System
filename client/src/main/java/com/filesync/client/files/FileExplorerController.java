package com.filesync.client.files;

import com.filesync.client.GUIApplication;
import com.filesync.client.dialog.*;
import com.filesync.client.files.edit.EditDialogController;
import com.filesync.client.files.util.BreadcrumbManager;
import com.filesync.client.files.util.BulkOperationHandler;
import com.filesync.client.files.util.ButtonPermissionManager;
import com.filesync.client.files.util.DragDropHandler;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.service.FileOperationService;
import com.filesync.client.service.FolderUploadService;
import com.filesync.client.service.ProgressService;
import com.filesync.client.sync.SyncScheduler;
import com.filesync.client.task.DownloadTask;
import com.filesync.client.task.EditTask;
import com.filesync.client.task.RefreshTask;
import com.filesync.client.task.UploadTask;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.dto.FileUpdateMessage;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static com.filesync.client.util.FileTypeHelper.isTextFile;

public class FileExplorerController {

    @FXML private TableView<ServerFileItem> fileTable;
    @FXML private TableColumn<ServerFileItem, String> pathColumn;
    @FXML private TableColumn<ServerFileItem, Long> sizeColumn;
    @FXML private TableColumn<ServerFileItem, Instant> lastModifiedColumn;
    @FXML private TableColumn<ServerFileItem, String> fileTypeColumn;
    @FXML private TableColumn<ServerFileItem, Node> iconColumn;
    @FXML private Label pathLabel;

    @FXML private Button uploadButton;
    @FXML private Button downloadButton;
    @FXML private Button deleteButton;
    @FXML private Button newFileButton;
    @FXML private Button newFolderButton;
    @FXML private Button refreshButton;

    private SyncHttpClient httpClient;
    private String ownerId;
    private UUID folderId;
    private ExecutorService executorService;
    private FileOperationService fileOpService;

    private static final Logger log = LoggerFactory.getLogger(FileExplorerController.class);

    private SyncScheduler syncScheduler;
    private BreadcrumbManager breadcrumbManager;
    private BulkOperationHandler bulkOperationHandler;
    private DragDropHandler dragDropHandler;
    private FileOpenHandler fileOpenHandler;

    private final ObservableList<ServerFileItem> fileItems = FXCollections.observableArrayList();

    // ==================== Initialization ====================

    public void initialize(SyncHttpClient httpClient, String ownerId, UUID folderId, UUID parentId, String rootDisplayName) {
        if (executorService == null) {
            throw new IllegalStateException("ExecutorService must be set before calling initialize()");
        }
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.folderId = folderId;
        this.fileOpService = new FileOperationService(httpClient, ownerId, folderId);

        configureTableColumns();
        configureTableSelection();
        configureRowFactory();

        breadcrumbManager = new BreadcrumbManager(pathLabel, rootDisplayName);
        breadcrumbManager.setCurrentParentId(parentId);
        breadcrumbManager.setOnExitSharedFolder(this::showSharedFoldersList);

        bulkOperationHandler = new BulkOperationHandler(httpClient, fileOpService, this::refreshWindow, executorService, ownerId, folderId);

        dragDropHandler = new DragDropHandler(fileTable, (fileIds, targetId) -> {
            List<String> names = fileIds.stream().map(id -> "item").collect(Collectors.toList());
            bulkOperationHandler.bulkMove(fileIds, names, targetId);
        });
        fileOpenHandler = new FileOpenHandler(httpClient, executorService);

        new ButtonPermissionManager(fileTable, ProgressService.getInstance(), deleteButton, downloadButton);

        ProgressService ps = ProgressService.getInstance();
        newFileButton.disableProperty().bind(ps.busyProperty());
        newFolderButton.disableProperty().bind(ps.busyProperty());

        syncScheduler = new SyncScheduler(httpClient, ownerId, folderId);
        if (httpClient != null && httpClient.getAuthToken() != null) {
            syncScheduler.start();
        }

        refreshWindow();
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public void setOnExitSharedFolder(Runnable callback) {
        breadcrumbManager.setOnExitSharedFolder(() -> {
            if (syncScheduler != null) syncScheduler.stop();
            breadcrumbManager.reset();
            callback.run();
        });
    }

    // ==================== UI Configuration ====================

    private void configureTableColumns() {
        iconColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Node item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : item);
            }
        });
        iconColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getIcon()));

        pathColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRelativePath()));
        fileTypeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFileType()));

        sizeColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getSize()));
        sizeColumn.setCellFactory(column -> new TableCell<ServerFileItem, Long>() {
            @Override
            protected void updateItem(Long size, boolean empty) {
                super.updateItem(size, empty);
                if (empty || size == null) {
                    setText(null);
                } else {
                    setText(formatFileSize(size));
                }
            }
        });

        lastModifiedColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getLastModified()));
        lastModifiedColumn.setCellFactory(column -> new TableCell<ServerFileItem, Instant>() {
            @Override
            protected void updateItem(Instant instant, boolean empty) {
                super.updateItem(instant, empty);
                if (empty || instant == null) {
                    setText(null);
                } else {
                    java.time.format.DateTimeFormatter formatter =
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                    .withZone(java.time.ZoneId.systemDefault());
                    setText(formatter.format(instant));
                }
            }
        });

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

    // ==================== Navigation (exposed to FileOpenHandler) ====================

    public boolean canGoUp() {
        return breadcrumbManager.canGoUp();
    }

    public boolean isSharedFolder() {
        return folderId != null;
    }

    public void navigateUp() {
        breadcrumbManager.navigateUp();
        refreshWindow();
    }

    public void exitSharedFolder() {
        breadcrumbManager.exitSharedFolder();
    }

    public void navigateInto(ServerFileItem item) {
        breadcrumbManager.navigateInto(UUID.fromString(item.getFileId()), item.getRelativePath());
        refreshWindow();
    }

    public void editFile(ServerFileItem item) {
        fileTable.getSelectionModel().select(item);
        handleEdit();
    }

    // ==================== UI Events ====================

    private void onRowDoubleClick(MouseEvent event) {
        if (event.getClickCount() == 2) {
            TableRow<ServerFileItem> row = (TableRow<ServerFileItem>) event.getSource();
            if (!row.isEmpty()) {
                Stage ownerStage = (Stage) fileTable.getScene().getWindow();
                fileOpenHandler.openItem(row.getItem(), ownerStage, this);
            }
        }
    }

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
        UploadTask task = new UploadTask(httpClient, ownerId, folderId, breadcrumbManager.getCurrentParentId(), selectedFile.toPath());
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

        int totalFiles;
        try (var walk = Files.walk(selectedDir.toPath())) {
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

        if (!isTextFile(selected)) {
            showAlert("Unsupported", "Only .txt files can be edited with this tool.");
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
        editTask.setOnFailed(e -> {
            ps.finishOperation();
            showAlert("Edit failed", editTask.getException().getMessage());
        });
        executorService.submit(editTask);
    }

    private void openEditDialog(ServerFileItem selected, Path tempFile, String originalContent) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filesync/client/files/edit/edit-dialog.fxml"));
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
                FileMetadataDto currentMeta = fileOpService.getMetadata(selected.getFileId());
                if (!currentMeta.getSha256Hash().equals(selected.getSha256Hash())) {
                    handleConflict(selected, currentMeta, newContent[0]);
                } else {
                    fileOpService.editFile(currentMeta, newContent[0]);
                    refreshWindow();
                    showAlert("Success", "File updated: " + selected.getRelativePath());
                }
            }
            Files.deleteIfExists(tempFile);
        } catch (Exception ex) {
            log.error("Edit dialog error", ex);
            showAlert("Edit failed", ex.getMessage());
        }
    }

    private void handleConflict(ServerFileItem selected, FileMetadataDto currentMeta, String newContent) throws IOException {
        Path userTemp = Files.createTempFile("user_", ".tmp");
        Files.writeString(userTemp, newContent);
        try {
            fileOpService.resolveConflict(currentMeta, userTemp);
            refreshWindow();
            showAlert("Success", "Conflict resolved and file updated: " + selected.getRelativePath());
        } catch (Exception e) {
            showAlert("Conflict Error", "Unable to resolve conflict: " + e.getMessage());
        } finally {
            Files.deleteIfExists(userTemp);
        }
    }

    @FXML
    private void handleNewFolder() {
        Stage owner = (Stage) fileTable.getScene().getWindow();
        CreateFolderDialog.show(owner, httpClient, ownerId, folderId, breadcrumbManager.getCurrentParentId(), this::refreshWindow, executorService);
    }

    @FXML
    private void handleNewFile() {
        Stage owner = (Stage) fileTable.getScene().getWindow();
        String fullName = CreateFileDialog.showAndWait(owner);
        if (fullName == null) return;

        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Creating " + fullName);

        try {
            Path tempFile = Files.createTempFile("newfile_", fullName);
            String ext = fullName.substring(fullName.lastIndexOf('.'));
            if (".docx".equals(ext)) {
                createMinimalDocx(tempFile);
            } else {
                String initialContent = getInitialContentForExtension(ext);
                if (!initialContent.isEmpty()) {
                    Files.writeString(tempFile, initialContent);
                }
            }
            UploadTask task = new UploadTask(httpClient, ownerId, folderId, breadcrumbManager.getCurrentParentId(), tempFile, fullName);
            task.messageProperty().addListener((obs, old, msg) -> ps.updateMessage(msg));
            task.progressProperty().addListener((obs, old, val) -> ps.updateProgress(val.doubleValue(), 1.0));
            task.setOnSucceeded(e -> {
                ps.finishOperation();
                refreshWindow();
                showAlert("Success", "File created: " + fullName);
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            });
            task.setOnFailed(e -> {
                ps.finishOperation();
                showAlert("Error", "Failed to create file: " + task.getException().getMessage());
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            });
            executorService.submit(task);
        } catch (Exception e) {
            ps.finishOperation();
            showAlert("Error", "Could not create temporary file: " + e.getMessage());
        }
    }

    private void createMinimalDocx(Path targetFile) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(targetFile.toFile())) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("");
            document.write(out);
        }
    }

    private String getInitialContentForExtension(String ext) {
        switch (ext) {
            case ".json": return "{}";
            case ".xml": return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
            case ".html": return "<!DOCTYPE html>\n<html>\n<head><title>New Page</title></head>\n<body>\n</body>\n</html>";
            case ".css": return "/* CSS */";
            case ".js": return "// JavaScript";
            case ".md": return "# Title";
            default: return "";
        }
    }

    @FXML
    private void handleLogout() {
        if (syncScheduler != null) syncScheduler.shutdown();
        fileOpService.logout();
        fileOpService.close();
        Stage stage = (Stage) fileTable.getScene().getWindow();
        stage.close();
        try {
            new GUIApplication().start(new Stage());
        } catch (Exception e) {
            log.error("Failed to restart application", e);
        }
    }

    // ==================== Refresh Logic ====================

    private void refreshWindow() {
        doRefresh(true);
    }

    public void refreshWindowSilent() {
        doRefresh(false);
    }

    private void doRefresh(boolean showUserFeedback) {
        ProgressService ps = ProgressService.getInstance();
        if (showUserFeedback) {
            ps.startOperation("Refreshing...");
        }

        boolean showParent = (breadcrumbManager.getCurrentParentId() != null ||
                !breadcrumbManager.getPathStack().isEmpty() || folderId != null);

        RefreshTask task = new RefreshTask(
                httpClient, ownerId, folderId, breadcrumbManager.getCurrentParentId(),
                fileItems, showParent
        );

        if (showUserFeedback) {
            task.setOnSucceeded(e -> ps.finishOperation());
            task.setOnFailed(e -> {
                ps.finishOperation();
                showAlert("Error", "Failed to load files: " + task.getException().getMessage());
            });
        } else {
            task.setOnFailed(e -> log.warn("Silent refresh failed", task.getException()));
        }

        executorService.submit(task);
    }

    // ==================== WebSocket Update Handler ====================

    public void handleFileUpdate(FileUpdateMessage msg) {
        Platform.runLater(() -> {
            try {
                String eventType = msg.getEventType();
                String relativePath = msg.getRelativePath();

                if ("DELETED".equals(eventType)) {
                    log.info("Received DELETED event for file: {}", relativePath);
                    fileItems.removeIf(item -> item.getRelativePath().equals(relativePath));

                    try {
                        String basePath = System.getProperty("user.home") + "/FileSync";
                        String folderName = (folderId != null) ? "shared_" + folderId.toString() : "personal_" + ownerId;
                        Path filePath = Paths.get(basePath, ownerId, folderName, relativePath);
                        if (Files.exists(filePath)) {
                            Files.delete(filePath);
                            log.info("Deleted local file: {}", filePath);
                        } else {
                            log.warn("Local file not found: {}", filePath);
                        }
                    } catch (IOException e) {
                        log.warn("Could not delete local file: {}", relativePath, e);
                    }

                    fileTable.refresh();

                } else if ("CREATED_OR_UPDATED".equals(eventType)) {
                    log.info("Received CREATED_OR_UPDATED event for file: {}", relativePath);
                    refreshWindowSilent();
                }

            } catch (Exception e) {
                log.error("Error processing file update", e);
            }
        });
    }

    public void stopAutoSync() {
        if (syncScheduler != null) {
            syncScheduler.stop();
        }
    }

    // ==================== Utility Methods ====================

    private void showSharedFoldersList() {
        if (breadcrumbManager != null) breadcrumbManager.reset();
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

    public void removeItemByFileId(String fileId) {
        Platform.runLater(() -> {
            ServerFileItem toRemove = fileItems.stream()
                    .filter(item -> fileId.equals(item.getFileId()))
                    .findFirst()
                    .orElse(null);
            if (toRemove != null) {
                String basePath = System.getProperty("user.home") + "/FileSync";
                String folderName = (folderId != null) ? "shared_" + folderId.toString() : "personal_" + ownerId;
                Path filePath = Paths.get(basePath, ownerId, folderName, toRemove.getRelativePath());
                try {
                    Files.deleteIfExists(filePath);
                    log.info("Deleted stale local file: {}", filePath);
                } catch (IOException e) {
                    log.warn("Failed to delete stale local file: {}", filePath, e);
                }
                fileItems.remove(toRemove);
                fileTable.refresh();
                log.info("Removed stale entry: {}", toRemove.getRelativePath());
            }
        });
    }
}