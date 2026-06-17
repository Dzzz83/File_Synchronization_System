package com.filesync.client.shared;

import com.filesync.client.chat.ChatController;
import com.filesync.client.dialog.*;
import com.filesync.client.files.FileExplorerController;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.chat.ChatClient;
import com.filesync.client.websocket.FileUpdateClient;
import com.filesync.common.dto.CreateFolderDto;
import com.filesync.common.dto.SharedFolderDto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SharedFoldersController {

    private static final Logger log = LoggerFactory.getLogger(SharedFoldersController.class);

    @FXML private TableView<SharedFolderItem> foldersTable;
    @FXML private TableColumn<SharedFolderItem, String> nameColumn;
    @FXML private TableColumn<SharedFolderItem, String> ownerColumn;
    @FXML private TableColumn<SharedFolderItem, String> permissionColumn;
    @FXML private Button manageRequestsButton;
    @FXML private Button deleteFolderButton;
    @FXML private VBox container;
    @FXML private HBox actionButtons;

    private SyncHttpClient httpClient;
    private String ownerId;
    private ExecutorService executorService;
    private ObservableList<SharedFolderItem> folderItems = FXCollections.observableArrayList();
    private FileExplorerController currentExplorer;
    private ChatController currentChatController;
    private boolean showingFoldersList = true;
    private FileUpdateClient fileUpdateClient;
    private UUID currentFolderId;
    private ScheduledExecutorService fallbackScheduler = Executors.newSingleThreadScheduledExecutor();

    public SharedFoldersController() {
        // no debug
    }

    public void initialize(SyncHttpClient httpClient, String ownerId, ExecutorService executorService) {
        log.info("SharedFoldersController initialized");
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.executorService = executorService;

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        ownerColumn.setCellValueFactory(new PropertyValueFactory<>("ownerId"));
        permissionColumn.setCellValueFactory(new PropertyValueFactory<>("permission"));
        foldersTable.setItems(folderItems);

        foldersTable.setRowFactory(tv -> {
            TableRow<SharedFolderItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    log.info("Double-click on shared folder: {}", row.getItem().getName());
                    onFolderDoubleClick(row.getItem());
                }
            });
            return row;
        });

        foldersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getOwnerId().equals(ownerId)) {
                manageRequestsButton.setDisable(false);
                deleteFolderButton.setDisable(false);
                updateRequestsButton(newVal.getId());
            } else {
                manageRequestsButton.setDisable(true);
                deleteFolderButton.setDisable(true);
                manageRequestsButton.setText("Manage Requests");
                manageRequestsButton.setStyle("");
            }
        });

        showSharedFoldersList();
        refreshFolders();
        log.info("SharedFoldersController initialization complete");
    }

    private void stopCurrentExplorerSync() {
        if (currentExplorer != null) {
            log.debug("Stopping auto-sync for current explorer");
            currentExplorer.stopAutoSync();
        }
    }

    private void showSharedFoldersList() {
        log.info("Showing shared folders list");
        if (fileUpdateClient != null) {
            log.info("Disconnecting file update WebSocket");
            fileUpdateClient.disconnect();
            fileUpdateClient = null;
        }
        stopCurrentExplorerSync();
        if (fallbackScheduler != null) {
            fallbackScheduler.shutdownNow();
            fallbackScheduler = Executors.newSingleThreadScheduledExecutor();
        }

        showingFoldersList = true;
        actionButtons.setVisible(true);
        actionButtons.setManaged(true);
        if (currentChatController != null) {
            currentChatController.dispose();
            currentChatController = null;
        }
        container.getChildren().clear();
        container.getChildren().add(foldersTable);
        log.info("Shared folders list displayed");
    }

    private void showFolderExplorer(SharedFolderItem item) {
        log.info("Opening folder explorer for folder: {} ({})", item.getName(), item.getId());
        stopCurrentExplorerSync();

        showingFoldersList = false;
        actionButtons.setVisible(false);
        actionButtons.setManaged(false);
        try {
            if (currentChatController != null) {
                currentChatController.dispose();
                currentChatController = null;
            }

            FXMLLoader fileLoader = new FXMLLoader(getClass().getResource("/com/filesync/client/files/server-file-list.fxml"));
            VBox explorerRoot = fileLoader.load();
            currentExplorer = fileLoader.getController();
            currentExplorer.setExecutorService(executorService);
            currentExplorer.initialize(httpClient, ownerId, item.getId(), null, item.getName());
            log.info("FileExplorerController initialized for folder {}", item.getId());

            currentExplorer.setOnExitSharedFolder(() -> {
                log.info("Exit shared folder callback triggered");
                if (fileUpdateClient != null) {
                    fileUpdateClient.disconnect();
                    fileUpdateClient = null;
                }
                stopCurrentExplorerSync();
                showSharedFoldersList();
            });

            FXMLLoader chatLoader = new FXMLLoader(getClass().getResource("/com/filesync/client/shared/chat-view.fxml"));
            VBox chatRoot = chatLoader.load();
            currentChatController = chatLoader.getController();
            ChatClient chatClient = new ChatClient(httpClient.getBaseUrl(), httpClient.getAuthToken());
            currentChatController.setData(chatClient, item.getId(), ownerId);

            currentFolderId = item.getId();
            log.info("Creating FileUpdateClient for folder {}", currentFolderId);
            fileUpdateClient = new FileUpdateClient(httpClient.getBaseUrl(), httpClient.getAuthToken());
            try {
                fileUpdateClient.connect(currentFolderId, msg -> {
                    log.info("Received file update event: {} for file {}", msg.getEventType(), msg.getRelativePath());
                    Platform.runLater(() -> {
                        if (currentExplorer != null) {
                            log.info("Forwarding file update to FileExplorerController");
                            currentExplorer.handleFileUpdate(msg);
                        } else {
                            log.warn("currentExplorer is null, cannot process update");
                        }
                    });
                });
            } catch (Exception e) {
                log.error("Failed to connect/subscribe to file update WebSocket", e);
                fallbackScheduler.scheduleAtFixedRate(() -> {
                    if (currentExplorer != null) {
                        currentExplorer.refreshWindowSilent();
                    }
                }, 5, 300, TimeUnit.SECONDS);  // 5 minutes initial delay, 5 minutes between runs
            }

            TabPane tabPane = new TabPane();
            Tab filesTab = new Tab("Files", explorerRoot);
            filesTab.setClosable(false);
            Tab chatTab = new Tab("Chat", chatRoot);
            chatTab.setClosable(false);
            tabPane.getTabs().addAll(filesTab, chatTab);

            container.getChildren().clear();
            container.getChildren().add(tabPane);
            VBox.setVgrow(tabPane, Priority.ALWAYS);
            tabPane.setMaxHeight(Double.MAX_VALUE);
            log.info("Folder explorer displayed for {}", item.getName());

        } catch (Exception e) {
            log.error("Error opening folder explorer", e);
            showAlert("Error", "Could not open folder: " + e.getMessage());
            actionButtons.setVisible(true);
            actionButtons.setManaged(true);
            showingFoldersList = true;
        }
    }

    private void onFolderDoubleClick(SharedFolderItem item) {
        log.info("onFolderDoubleClick called for folder: {}", item.getName());
        showFolderExplorer(item);
    }

    private void updateRequestsButton(UUID folderId) {
        Task<Integer> countTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return httpClient.getPendingRequestsCount(folderId);
            }
        };
        countTask.setOnSucceeded(e -> {
            int count = countTask.getValue();
            Platform.runLater(() -> {
                if (count > 0) {
                    manageRequestsButton.setText("Manage Requests (" + count + ")");
                    manageRequestsButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 15;");
                } else {
                    manageRequestsButton.setText("Manage Requests");
                    manageRequestsButton.setStyle("");
                }
            });
        });
        countTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                manageRequestsButton.setText("Manage Requests");
                manageRequestsButton.setStyle("");
            });
        });
        executorService.submit(countTask);
    }

    private void refreshFolders() {
        foldersTable.setDisable(true);

        Task<List<SharedFolderDto>> refreshTask = new Task<>() {
            @Override
            protected List<SharedFolderDto> call() throws Exception {
                return httpClient.getUserSharedFolders(ownerId);
            }
        };
        refreshTask.setOnSucceeded(e -> {
            List<SharedFolderDto> folders = refreshTask.getValue();
            Platform.runLater(() -> {
                folderItems.clear();
                for (SharedFolderDto dto : folders) {
                    folderItems.add(new SharedFolderItem(
                            dto.getId(),
                            dto.getName(),
                            dto.getOwnerId(),
                            dto.getYourPermission() != null ? dto.getYourPermission().name() : "NONE"
                    ));
                }
                foldersTable.setDisable(false);
            });
        });
        refreshTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                foldersTable.setDisable(false);
                showAlert("Error", "Failed to load shared folders: " + refreshTask.getException().getMessage());
            });
        });
        executorService.submit(refreshTask);
    }

    @FXML
    private void handleCreateFolder() {
        Stage owner = (Stage) foldersTable.getScene().getWindow();
        CreateFolderDto dto = CreateSharedFolderDialog.show(owner, httpClient, executorService);
        if (dto == null) return;

        disableButtons(true);
        Task<Void> createTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                httpClient.createSharedFolder(dto.getName(), dto.getMembers());
                return null;
            }
        };
        createTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                refreshFolders();
                showAlert("Success", "Folder created: " + dto.getName());
                disableButtons(false);
            });
        });
        createTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                disableButtons(false);
                showAlert("Error", "Failed to create folder: " + createTask.getException().getMessage());
            });
        });
        executorService.submit(createTask);
    }

    @FXML
    private void handleRequestAccess() {
        RequestAccessDialog.show(httpClient);
    }

    @FXML
    private void handleManageMembers() {
        SharedFolderItem selected = foldersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No selection", "Please select a folder");
            return;
        }
        AddMemberDialog.show(selected.getId(), httpClient, () -> {
            refreshFolders();
            if (foldersTable.getSelectionModel().getSelectedItem() != null &&
                    foldersTable.getSelectionModel().getSelectedItem().getOwnerId().equals(ownerId)) {
                updateRequestsButton(selected.getId());
            }
        }, executorService);
    }

    @FXML
    private void handleManageRequests() {
        SharedFolderItem selected = foldersTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        PendingRequestsDialog.show(selected.getId(), httpClient, () -> {
            refreshFolders();
            if (foldersTable.getSelectionModel().getSelectedItem() != null &&
                    foldersTable.getSelectionModel().getSelectedItem().getOwnerId().equals(ownerId)) {
                updateRequestsButton(selected.getId());
            }
        }, executorService);
    }

    @FXML
    private void handleDeleteFolder() {
        SharedFolderItem selected = foldersTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete shared folder '" + selected.getName() + "' and all its files?\nThis action cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                disableButtons(true);
                Task<Void> deleteTask = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        httpClient.deleteSharedFolder(selected.getId());
                        return null;
                    }
                };
                deleteTask.setOnSucceeded(e -> {
                    Platform.runLater(() -> {
                        showAlert("Success", "Folder deleted.");
                        refreshFolders();
                        disableButtons(false);
                    });
                });
                deleteTask.setOnFailed(e -> {
                    Platform.runLater(() -> {
                        disableButtons(false);
                        showAlert("Error", "Failed to delete folder: " + deleteTask.getException().getMessage());
                    });
                });
                executorService.submit(deleteTask);
            }
        });
    }

    private void disableButtons(boolean disable) {
        manageRequestsButton.setDisable(disable);
        deleteFolderButton.setDisable(disable);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class SharedFolderItem {
        private final UUID id;
        private final String name;
        private final String ownerId;
        private final String permission;

        public SharedFolderItem(UUID id, String name, String ownerId, String permission) {
            this.id = id;
            this.name = name;
            this.ownerId = ownerId;
            this.permission = permission;
        }

        public UUID getId() { return id; }
        public String getName() { return name; }
        public String getOwnerId() { return ownerId; }
        public String getPermission() { return permission; }
    }
}