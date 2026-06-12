package com.filesync.client.shared.create;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.CreateFolderDto;
import com.filesync.common.dto.MemberDto;
import com.filesync.common.dto.UserSearchResult;
import com.filesync.common.enums.Permission;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class CreateSharedFolderController {
    @FXML private TextField nameField;
    @FXML private ListView<MemberItem> membersList;
    @FXML private TextField searchField;
    @FXML private ListView<String> searchResults;
    @FXML private ComboBox<Permission> permissionCombo;
    @FXML private Button addMemberBtn;
    @FXML private Button createBtn;
    @FXML private Button cancelBtn;
    @FXML private Button searchButton;

    private SyncHttpClient httpClient;
    private Stage dialogStage;
    private CreateFolderDto result;
    private List<MemberDto> selectedMembers = new ArrayList<>();
    private ExecutorService executorService;

    public void setData(Stage dialogStage, SyncHttpClient httpClient, ExecutorService executorService) {
        this.dialogStage = dialogStage;
        this.httpClient = httpClient;
        this.executorService = executorService;
        initialize();
    }

    @FXML
    private void initialize() {
        permissionCombo.getItems().setAll(Permission.READ, Permission.WRITE);
        permissionCombo.setValue(Permission.READ);

        membersList.setItems(FXCollections.observableArrayList());

        searchButton.setOnAction(e -> searchUsers());
        addMemberBtn.setOnAction(e -> addMember());
        membersList.setOnMouseClicked(event -> removeMember());
        createBtn.setOnAction(e -> onCreate());
        cancelBtn.setOnAction(e -> dialogStage.close());

        addMemberBtn.setDisable(true);
    }

    private void searchUsers() {
        String query = searchField.getText().trim();
        if (query.length() < 2) return;

        searchButton.setDisable(true);
        searchButton.setText("Searching...");
        searchResults.getItems().clear();

        Task<List<UserSearchResult>> searchTask = new Task<>() {
            @Override
            protected List<UserSearchResult> call() throws Exception {
                return httpClient.searchUsers(query);
            }
        };
        searchTask.setOnSucceeded(e -> {
            List<UserSearchResult> users = searchTask.getValue();
            Platform.runLater(() -> {
                searchResults.getItems().clear();
                for (UserSearchResult u : users) {
                    searchResults.getItems().add(u.getUsername() + " (" + u.getEmail() + ")");
                }
                addMemberBtn.setDisable(searchResults.getItems().isEmpty());
                if (!searchResults.getItems().isEmpty()) {
                    searchResults.getSelectionModel().selectFirst();
                }
                searchButton.setDisable(false);
                searchButton.setText("Search");
            });
        });
        searchTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                searchButton.setDisable(false);
                searchButton.setText("Search");
                showAlert("Search Error", searchTask.getException().getMessage());
            });
        });
        executorService.submit(searchTask);
    }

    private void addMember() {
        String selected = searchResults.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        String username = selected.split(" ")[0];
        Permission perm = permissionCombo.getValue();
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
    }

    private void removeMember() {
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
    }

    private void onCreate() {
        String folderName = nameField.getText().trim();
        if (folderName.isEmpty()) {
            showAlert("Error", "Folder name cannot be empty");
            return;
        }
        CreateFolderDto dto = new CreateFolderDto();
        dto.setName(folderName);
        dto.setMembers(selectedMembers);
        result = dto;
        dialogStage.close();
    }

    public CreateFolderDto getResult() {
        return result;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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