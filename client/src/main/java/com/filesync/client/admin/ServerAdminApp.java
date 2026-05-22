package com.filesync.client.admin;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.SharedFolderDto;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class ServerAdminApp extends Application {

    private static Stage mainStage;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load login dialog
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filesync/client/admin/startup-dialog.fxml"));
        Scene scene = new Scene(loader.load(), 1100, 700);
        StartupController controller = loader.getController();
        controller.setPrimaryStage(primaryStage);
        primaryStage.setTitle("File Server Admin");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Creates and shows the main application window with personal and shared folders tabs.
     * Called by StartupController after successful login.
     */
    public static void showMainWindow(String serverUrl, String token, String username) {
        try {
            SyncHttpClient httpClient = new SyncHttpClient(serverUrl);
            httpClient.setAuthToken(token);

            TabPane tabPane = new TabPane();

            // Personal files tab
            FXMLLoader personalLoader = new FXMLLoader(ServerAdminApp.class.getResource("/com/filesync/client/admin/server-file-list.fxml"));
            VBox personalRoot = personalLoader.load();
            ServerFileListController personalController = personalLoader.getController();
            personalController.initialize(httpClient, username, null); // null folderId = personal
            Tab personalTab = new Tab("My Files", personalRoot);
            personalTab.setClosable(false);

            // Shared folders tab
            FXMLLoader sharedLoader = new FXMLLoader(ServerAdminApp.class.getResource("/com/filesync/client/admin/shared-folders-view.fxml"));
            VBox sharedRoot = sharedLoader.load();
            SharedFoldersController sharedController = sharedLoader.getController();
            sharedController.initialize(httpClient, username);
            Tab sharedTab = new Tab("Shared Folders", sharedRoot);
            sharedTab.setClosable(false);

            tabPane.getTabs().addAll(personalTab, sharedTab);

            Scene mainScene = new Scene(tabPane, 900, 600);
            Stage mainWindow = new Stage();
            mainWindow.setTitle("File Sync - " + username);
            mainWindow.setScene(mainScene);
            mainWindow.setOnCloseRequest(e -> httpClient.close());
            mainWindow.show();

            // Close the login window if it's still open
            if (mainStage != null) {
                mainStage.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setMainStage(Stage stage) {
        mainStage = stage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}