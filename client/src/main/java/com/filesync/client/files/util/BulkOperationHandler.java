package com.filesync.client.files.util;

import com.filesync.client.files.ServerFileItem;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.service.FileOperationService;
import com.filesync.client.service.ProgressService;
import com.filesync.client.task.DeleteTask;
import com.filesync.client.task.MoveTask;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class BulkOperationHandler {
    private final SyncHttpClient httpClient;
    private final FileOperationService fileService;
    private final Runnable refreshCallback;
    private final ExecutorService executorService;
    private final String ownerId;          // added
    private final UUID folderId;           // added
    private static final Logger log = LoggerFactory.getLogger(BulkOperationHandler.class);

    public BulkOperationHandler(SyncHttpClient httpClient, FileOperationService fileService,
                                Runnable refreshCallback, ExecutorService executorService,
                                String ownerId, UUID folderId) {
        this.httpClient = httpClient;
        this.fileService = fileService;
        this.refreshCallback = refreshCallback;
        this.executorService = executorService;
        this.ownerId = ownerId;
        this.folderId = folderId;
    }

    public void bulkMove(List<String> fileIds, List<String> fileNames, String targetFolderId) {
        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Moving " + fileIds.size() + " item(s)");

        MoveTask task = new MoveTask(httpClient, fileIds, targetFolderId, fileNames);
        task.messageProperty().addListener((obs, old, msg) -> ps.updateMessage(msg));
        task.progressProperty().addListener((obs, old, val) -> ps.updateProgress(val.doubleValue(), 1.0));

        task.setOnSucceeded(e -> {
            ps.finishOperation();
            refreshCallback.run();
            showInfo("Success", "Moved " + fileIds.size() + " item(s)");
        });
        task.setOnFailed(e -> {
            ps.finishOperation();
            showError("Move failed", task.getException().getMessage());
        });

        executorService.submit(task);
    }

    public void bulkDelete(ObservableList<ServerFileItem> items) {
        log.info("🗑️ bulkDelete called with {} items", items.size());

        List<String> fileIds = items.stream()
                .map(ServerFileItem::getFileId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toList());

        List<String> fileNames = items.stream()
                .map(ServerFileItem::getRelativePath)
                .collect(Collectors.toList());

        if (fileIds.isEmpty()) {
            log.warn("⚠️ No valid file IDs found, aborting delete");
            showInfo("No items to delete", "The selected items do not have valid file IDs.");
            return;
        }

        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Deleting " + fileIds.size() + " item(s)");

        DeleteTask task = new DeleteTask(fileService, fileIds, fileNames, ownerId, folderId);
        task.messageProperty().addListener((obs, old, msg) -> ps.updateMessage(msg));
        task.progressProperty().addListener((obs, old, val) -> ps.updateProgress(val.doubleValue(), 1.0));

        task.setOnSucceeded(e -> {
            ps.finishOperation();
            refreshCallback.run();
            log.info("✅ Successfully deleted {} item(s)", fileIds.size());
            showInfo("Success", "Deleted " + fileIds.size() + " item(s)");
        });
        task.setOnFailed(e -> {
            ps.finishOperation();
            log.error("❌ Delete failed for {} items", fileIds.size(), task.getException());
            showError("Delete failed", task.getException().getMessage());
        });

        executorService.submit(task);
    }

    private void showInfo(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}