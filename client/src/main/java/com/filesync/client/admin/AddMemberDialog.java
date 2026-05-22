package com.filesync.client.admin;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.UserSearchResult;
import com.filesync.common.enums.Permission;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class AddMemberDialog {
    public static void show(UUID folderId, SyncHttpClient httpClient, Runnable onSuccess) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Add Member to Folder");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        TextField searchField = new TextField();
        searchField.setPromptText("Search by username or email");
        ListView<String> resultsList = new ListView<>();
        ComboBox<Permission> permissionBox = new ComboBox<>();
        permissionBox.getItems().setAll(Permission.READ, Permission.WRITE);
        permissionBox.setValue(Permission.READ);
        Button addButton = new Button("Add");
        Button closeButton = new Button("Close");

        // debounced search
        final long[] lastSearchTime = {0};
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().length() < 2) return;
            long now = System.currentTimeMillis();
            if (now - lastSearchTime[0] < 300) return;
            lastSearchTime[0] = now;
            Platform.runLater(() -> {
                try {
                    List<UserSearchResult> users = httpClient.searchUsers(newVal.trim());
                    resultsList.getItems().clear();
                    for (UserSearchResult u : users) {
                        resultsList.getItems().add(u.getUsername() + " (" + u.getEmail() + ")");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });

        addButton.setOnAction(e -> {
            String selected = resultsList.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            String username = selected.split(" ")[0];
            Permission perm = permissionBox.getValue();
            try {
                httpClient.addMemberToFolder(folderId, username, perm);
                if (onSuccess != null) onSuccess.run();
                dialog.close();
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to add member: " + ex.getMessage());
                alert.showAndWait();
            }
        });

        closeButton.setOnAction(e -> dialog.close());

        vbox.getChildren().addAll(new Label("Search user:"), searchField, resultsList,
                new Label("Permission:"), permissionBox, addButton, closeButton);
        Scene scene = new Scene(vbox, 300, 400);
        dialog.setScene(scene);
        dialog.showAndWait();
    }
}