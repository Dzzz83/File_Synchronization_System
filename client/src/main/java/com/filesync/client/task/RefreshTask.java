package com.filesync.client.task;

import com.filesync.client.files.ServerFileItem;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.icon.FileIconResolver;
import com.filesync.common.dto.FileMetadataDto;
import com.filesync.common.enums.Permission;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;

import java.util.List;
import java.util.UUID;

public class RefreshTask extends Task<Void> {
    private final SyncHttpClient httpClient;
    private final String ownerId;
    private final UUID folderId;
    private final UUID currentParentId;
    private final ObservableList<ServerFileItem> fileItems;
    private final boolean showParentEntry;

    public RefreshTask(SyncHttpClient httpClient, String ownerId, UUID folderId, UUID currentParentId,
                       ObservableList<ServerFileItem> fileItems, boolean showParentEntry) {
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.folderId = folderId;
        this.currentParentId = currentParentId;
        this.fileItems = fileItems;
        this.showParentEntry = showParentEntry;
    }

    @Override
    protected Void call() throws Exception {
        List<FileMetadataDto> files = httpClient.getFiles(ownerId, folderId, currentParentId);
        if (files == null) {
            files = List.of();
        }

        final List<FileMetadataDto> finalFiles = files;
        Platform.runLater(() -> {
            fileItems.clear();

            if (showParentEntry) {
                fileItems.add(new ServerFileItem(
                        "parent", "..", 0, null, null, folderId, true, null, new Label("◀--"),
                        Permission.NONE
                ));
            }

            for (FileMetadataDto dto : finalFiles) {
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
        });
        return null;
    }
}