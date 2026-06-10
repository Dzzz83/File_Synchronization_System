package com.filesync.client.controller.helper;

import com.filesync.client.controller.ServerFileItem;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.service.FileOperationService;
import com.filesync.client.service.ProgressService;
import com.filesync.client.task.DeleteTask;
import com.filesync.client.task.MoveTask;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class BulkOperationHandler {
    private final SyncHttpClient httpClient;
    private final FileOperationService fileService;
    private final Runnable refreshCallback;
    private final ExecutorService executorService;

    public BulkOperationHandler(SyncHttpClient httpClient, FileOperationService fileService,
                                Runnable refreshCallback, ExecutorService executorService) {
        this.httpClient = httpClient;
        this.fileService = fileService;
        this.refreshCallback = refreshCallback;
        this.executorService = executorService;
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
        List<String> fileIds = items.stream().map(ServerFileItem::getFileId).collect(Collectors.toList());
        List<String> fileNames = items.stream().map(ServerFileItem::getRelativePath).collect(Collectors.toList());

        ProgressService ps = ProgressService.getInstance();
        ps.startOperation("Deleting " + fileIds.size() + " item(s)");

        DeleteTask task = new DeleteTask(fileService, fileIds, fileNames);
        task.messageProperty().addListener((obs, old, msg) -> ps.updateMessage(msg));
        task.progressProperty().addListener((obs, old, val) -> ps.updateProgress(val.doubleValue(), 1.0));

        task.setOnSucceeded(e -> {
            ps.finishOperation();
            refreshCallback.run();
            showInfo("Success", "Deleted " + items.size() + " item(s)");
        });
        task.setOnFailed(e -> {
            ps.finishOperation();
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