package com.filesync.client.admin;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.CreateFolderDto;
import com.filesync.common.dto.MemberDto;
import com.filesync.common.dto.UserSearchResult;
import com.filesync.common.enums.Permission;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CreateFolderDialog {

    public static CreateFolderDto show(Stage owner, SyncHttpClient httpClient) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Create Shared Folder");

        VBox mainBox = new VBox(15);
        mainBox.setPadding(new Insets(15));

        // Folder name
        TextField nameField = new TextField();
        nameField.setPromptText("Folder name");
        Label nameLabel = new Label("Name:");

        // Members section
        Label membersLabel = new Label("Members (optional):");
        ListView<MemberItem> membersList = new ListView<>();
        membersList.setPrefHeight(150);
        HBox searchBox = new HBox(5);
        TextField searchField = new TextField();
        searchField.setPromptText("Search by username or email");
        Button searchBtn = new Button("Search");
        ListView<String> searchResults = new ListView<>();
        searchResults.setPrefHeight(100);
        ComboBox<Permission> permissionCombo = new ComboBox<>();
        permissionCombo.getItems().setAll(Permission.READ, Permission.WRITE);
        permissionCombo.setValue(Permission.READ);
        Button addMemberBtn = new Button("Add Member");
        addMemberBtn.setDisable(true);

        // Store selected members
        List<MemberDto> selectedMembers = new ArrayList<>();
        membersList.setItems(javafx.collections.FXCollections.observableArrayList());

        // Search users
        searchBtn.setOnAction(e -> {
            String query = searchField.getText().trim();
            if (query.length() < 2) return;
            try {
                List<UserSearchResult> users = httpClient.searchUsers(query);
                searchResults.getItems().clear();
                for (UserSearchResult u : users) {
                    searchResults.getItems().add(u.getUsername() + " (" + u.getEmail() + ")");
                }
                if (!searchResults.getItems().isEmpty()) {
                    searchResults.getSelectionModel().selectFirst();
                    addMemberBtn.setDisable(false);
                } else {
                    addMemberBtn.setDisable(true);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                addMemberBtn.setDisable(true);
            }
        });

        // Add member from search results
        addMemberBtn.setOnAction(e -> {
            String selected = searchResults.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            String username = selected.split(" ")[0];
            Permission perm = permissionCombo.getValue();
            // Check if already added
            boolean exists = selectedMembers.stream().anyMatch(m -> m.getUserId().equals(username));
            if (exists) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "User already added");
                alert.showAndWait();
                return;
            }
            MemberDto member = new MemberDto();
            member.setUserId(username);
            member.setPermission(perm);
            selectedMembers.add(member);
            membersList.getItems().add(new MemberItem(username, perm));
            searchResults.getItems().clear();
            addMemberBtn.setDisable(true);
        });

        // Allow removing a member
        membersList.setOnMouseClicked(event -> {
            MemberItem item = membersList.getSelectionModel().getSelectedItem();
            if (item != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Remove " + item.getUsername() + "?", ButtonType.YES, ButtonType.NO);
                confirm.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.YES) {
                        selectedMembers.removeIf(m -> m.getUserId().equals(item.getUsername()));
                        membersList.getItems().remove(item);
                    }
                });
            }
        });

        searchBox.getChildren().addAll(searchField, searchBtn);
        VBox memberArea = new VBox(5, membersLabel, membersList, searchBox, searchResults,
                new Label("Permission:"), permissionCombo, addMemberBtn);

        // Buttons
        Button createBtn = new Button("Create");
        Button cancelBtn = new Button("Cancel");
        HBox buttonBar = new HBox(10, createBtn, cancelBtn);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        mainBox.getChildren().addAll(nameLabel, nameField, memberArea, buttonBar);
        Scene scene = new Scene(mainBox, 450, 550);
        dialog.setScene(scene);

        final CreateFolderDto[] result = {null};

        createBtn.setOnAction(e -> {
            String folderName = nameField.getText().trim();
            if (folderName.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Folder name cannot be empty");
                alert.showAndWait();
                return;
            }
            CreateFolderDto dto = new CreateFolderDto();
            dto.setName(folderName);
            dto.setMembers(selectedMembers);
            result[0] = dto;
            dialog.close();
        });

        cancelBtn.setOnAction(e -> dialog.close());

        dialog.showAndWait();
        return result[0];
    }

    private static class MemberItem {
        private final String username;
        private final Permission permission;
        MemberItem(String username, Permission permission) {
            this.username = username;
            this.permission = permission;
        }
        public String getUsername() { return username; }
        public Permission getPermission() { return permission; }
        @Override
        public String toString() { return username + " (" + permission + ")"; }
    }
}