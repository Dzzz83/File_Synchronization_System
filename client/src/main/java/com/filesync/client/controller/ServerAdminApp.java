package com.filesync.client.controller;

import com.filesync.client.http.SyncHttpClient;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.*;
import javafx.stage.Stage;

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

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filesync/client/controller/startup-dialog.fxml"));
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
            FXMLLoader personalLoader = new FXMLLoader(ServerAdminApp.class.getResource("/com/filesync/client/controller/server-file-list.fxml"));
            VBox personalRoot = personalLoader.load();
            FileExplorerController personalController = personalLoader.getController();
            personalController.setExecutorService(ServerAdminApp.getInstance().getExecutor());
            personalController.initialize(httpClient, username, null, null, "My Files");
            Tab personalTab = new Tab("My Files", personalRoot);
            personalTab.setClosable(false);

            // Shared folders tab
            FXMLLoader sharedLoader = new FXMLLoader(ServerAdminApp.class.getResource("/com/filesync/client/controller/shared-folders-view.fxml"));
            VBox sharedRoot = sharedLoader.load();
            SharedFoldersController sharedController = sharedLoader.getController();
            sharedController.initialize(httpClient, username, ServerAdminApp.getInstance().getExecutor());
            Tab sharedTab = new Tab("Shared Folders", sharedRoot);
            sharedTab.setClosable(false);

            tabPane.getTabs().addAll(personalTab, sharedTab);

            // Load progress bar
            FXMLLoader progressLoader = new FXMLLoader(ServerAdminApp.class.getResource("/com/filesync/client/controller/global-progress.fxml"));
            Parent progressRoot = progressLoader.load();
            progressRoot.setMouseTransparent(true);  // Allow clicks to pass through

            AnchorPane root = new AnchorPane();
            root.getChildren().addAll(tabPane, progressRoot);

            // Make tabPane fill entire AnchorPane
            AnchorPane.setTopAnchor(tabPane, 0.0);
            AnchorPane.setBottomAnchor(tabPane, 0.0);
            AnchorPane.setLeftAnchor(tabPane, 0.0);
            AnchorPane.setRightAnchor(tabPane, 0.0);

            // Position progress bar at top-right
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