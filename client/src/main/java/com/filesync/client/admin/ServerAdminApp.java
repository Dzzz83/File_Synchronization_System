package com.filesync.client.admin;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ServerAdminApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filesync/client/admin/startup-dialog.fxml"));
        Scene scene = new Scene(loader.load(), 450, 400);
        StartupController controller = loader.getController();
        controller.setPrimaryStage(primaryStage);
        primaryStage.setTitle("File Server Admin");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}