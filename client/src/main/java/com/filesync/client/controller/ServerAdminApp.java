package com.filesync.client.controller;

import com.filesync.client.auth.StartupController;
import com.filesync.client.files.FileExplorerController;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.shared.SharedFoldersController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerAdminApp extends Application {

    private static ServerAdminApp instance;
    private static Stage mainStage;
    private ExecutorService executorService;

    public static ServerAdminApp getInstance() {
        return instance;
    }

    public ExecutorService getExecutor() {
        return executorService;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        instance = this;
        executorService = Executors.newCachedThreadPool();

        URL startupUrl = getClass().getResource("/com/filesync/client/auth/startup-dialog.fxml");
        if (startupUrl == null) {
            throw new IllegalStateException("Missing FXML: /com/filesync/client/auth/startup-dialog.fxml");
        }
        FXMLLoader loader = new FXMLLoader(startupUrl);
        Scene scene = new Scene(loader.load(), 1100, 700);
        StartupController controller = loader.getController();
        controller.setPrimaryStage(primaryStage);
        primaryStage.setTitle("File Server Admin");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void showMainWindow(String serverUrl, String token, String username) {
        try {
            SyncHttpClient httpClient = new SyncHttpClient(serverUrl);
            httpClient.setAuthToken(token);

            TabPane tabPane = new TabPane();

            // Personal files tab
            URL personalFxml = ServerAdminApp.class.getResource("/com/filesync/client/files/server-file-list.fxml");
            if (personalFxml == null) {
                throw new IllegalStateException("Missing FXML: /com/filesync/client/files/server-file-list.fxml");
            }
            FXMLLoader personalLoader = new FXMLLoader(personalFxml);
            VBox personalRoot = personalLoader.load();
            FileExplorerController personalController = personalLoader.getController();
            personalController.setExecutorService(ServerAdminApp.getInstance().getExecutor());
            personalController.initialize(httpClient, username, null, null, "My Files");
            Tab personalTab = new Tab("My Files", personalRoot);
            personalTab.setClosable(false);

            // Shared folders tab
            URL sharedFxml = ServerAdminApp.class.getResource("/com/filesync/client/shared/shared-folders-view.fxml");
            if (sharedFxml == null) {
                throw new IllegalStateException("Missing FXML: /com/filesync/client/shared/shared-folders-view.fxml");
            }
            FXMLLoader sharedLoader = new FXMLLoader(sharedFxml);
            VBox sharedRoot = sharedLoader.load();
            SharedFoldersController sharedController = sharedLoader.getController();
            sharedController.initialize(httpClient, username, ServerAdminApp.getInstance().getExecutor());
            Tab sharedTab = new Tab("Shared Folders", sharedRoot);
            sharedTab.setClosable(false);

            tabPane.getTabs().addAll(personalTab, sharedTab);

            // Global progress bar
            URL progressFxml = ServerAdminApp.class.getResource("/com/filesync/client/service/global-progress.fxml");
            if (progressFxml == null) {
                throw new IllegalStateException("Missing FXML: /com/filesync/client/service/global-progress.fxml");
            }
            FXMLLoader progressLoader = new FXMLLoader(progressFxml);
            Parent progressRoot = progressLoader.load();
            progressRoot.setMouseTransparent(true);

            AnchorPane root = new AnchorPane();
            root.getChildren().addAll(tabPane, progressRoot);

            AnchorPane.setTopAnchor(tabPane, 0.0);
            AnchorPane.setBottomAnchor(tabPane, 0.0);
            AnchorPane.setLeftAnchor(tabPane, 0.0);
            AnchorPane.setRightAnchor(tabPane, 0.0);

            AnchorPane.setTopAnchor(progressRoot, 10.0);
            AnchorPane.setRightAnchor(progressRoot, 10.0);

            Scene mainScene = new Scene(root, 900, 600);
            Stage mainWindow = new Stage();
            mainWindow.setTitle("File Sync - " + username);
            mainWindow.setScene(mainScene);
            mainWindow.setOnCloseRequest(e -> httpClient.close());
            mainWindow.show();

            if (mainStage != null) {
                mainStage.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Show a user‑friendly error dialog
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Initialization Error");
            alert.setHeaderText("Failed to open main window");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    @Override
    public void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    public static void setMainStage(Stage stage) {
        mainStage = stage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}