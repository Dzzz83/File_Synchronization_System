package com.filesync.client.controller.helper;

import com.filesync.client.files.ServerFileItem;
import com.filesync.client.service.ProgressService;
import com.filesync.common.enums.Permission;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;

public class ButtonPermissionManager {
    public ButtonPermissionManager(TableView<ServerFileItem> fileTable, ProgressService progressService,
                                   Button deleteButton, Button downloadButton) {
        // Delete button: disabled when busy OR selected item lacks WRITE permission
        deleteButton.disableProperty().bind(
                Bindings.createBooleanBinding(() -> {
                    ServerFileItem selected = fileTable.getSelectionModel().getSelectedItem();
                    return progressService.busyProperty().get() ||
                            (selected != null && selected.getUserPermission() != Permission.WRITE);
                }, fileTable.getSelectionModel().selectedItemProperty(), progressService.busyProperty())
        );

        // Download button: disabled when busy OR no selection OR selected has NONE permission
        downloadButton.disableProperty().bind(
                Bindings.createBooleanBinding(() -> {
                    ServerFileItem selected = fileTable.getSelectionModel().getSelectedItem();
                    if (progressService.busyProperty().get()) return true;
                    if (selected == null) return true;
                    Permission perm = selected.getUserPermission();
                    return perm != Permission.READ && perm != Permission.WRITE;
                }, fileTable.getSelectionModel().selectedItemProperty(), progressService.busyProperty())
        );
    }
}