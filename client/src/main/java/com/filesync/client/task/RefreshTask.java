package com.filesync.client.task;

import com.filesync.client.controller.ServerFileItem;
import com.filesync.common.enums.Permission;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.service.ProgressService;
import com.filesync.common.dto.FileMetadataDto;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.collections.ObservableList;
import com.filesync.client.icon.FileIconResolver;
import javafx.scene.Node;
import javafx.scene.control.Label;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class RefreshTask extends Task<Void> {
    private final SyncHttpClient httpClient;
    private final String ownerId;
    private final UUID folderId;
    private final UUID currentParentId;
    private final ObservableList<ServerFileItem> fileItems;
    private final boolean showParentEntry;
    private final Consumer<List<FileMetadataDto>> onSuccess;

    public RefreshTask(SyncHttpClient httpClient, String ownerId, UUID folderId, UUID currentParentId,
                       ObservableList<ServerFileItem> fileItems, boolean showParentEntry) {
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.folderId = folderId;
        this.currentParentId = currentParentId;
        this.fileItems = fileItems;
        this.showParentEntry = showParentEntry;
        this.onSuccess = null;
    }

    public RefreshTask(SyncHttpClient httpClient, String ownerId, UUID folderId, UUID currentParentId,
                       ObservableList<ServerFileItem> fileItems, boolean showParentEntry,
                       Consumer<List<FileMetadataDto>> onSuccess) {
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.folderId = folderId;
        this.currentParentId = currentParentId;
        this.fileItems = fileItems;
        this.showParentEntry = showParentEntry;
        this.onSuccess = onSuccess;
    }

    @Override
    protected Void call() throws Exception {
        List<FileMetadataDto> files = httpClient.getFiles(ownerId, folderId, currentParentId);

        Platform.runLater(() -> {
            fileItems.clear();
            if (showParentEntry) {
                fileItems.add(new ServerFileItem(
                        "parent", "..", 0, null, null, folderId, true, null, new Label("◀--"),
                        Permission.NONE
                ));
            }
            for (FileMetadataDto dto : files) {
                Node icon = FileIconResolver.getIconForFile(dto.getRelativePath());
                fileItems.add(new ServerFileItem(
                        dto.getFileId(),
                        dto.getRelativePath(),
                        dto.getSize(),
                        dto.getLastModified(),
                        dto.getSha256Hash(),
                        dto.getFolderId(),
                        dto.isDirectory(),
                        dto.getParentId(),
                        icon,
                        dto.getUserPermission()
                ));
            }
            if (onSuccess != null) {
                onSuccess.accept(files);
            }
            ProgressService.getInstance().finishOperation();
        });
        return null;
    }
}