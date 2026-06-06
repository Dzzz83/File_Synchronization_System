package com.filesync.client.dialog;

import com.filesync.client.controller.CreateFolderController;
import com.filesync.client.http.SyncHttpClient;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class CreateFolderDialog {

    public static void show(Stage owner, SyncHttpClient httpClient, String ownerId,
                            UUID folderId, UUID parentId, Runnable onSuccess,
                            ExecutorService executorService) {
        try {
            FXMLLoader loader = new FXMLLoader(CreateFolderDialog.class.getResource("/com/filesync/client/dialog/new-folder-dialog.fxml"));
            Scene scene = new Scene(loader.load(), 300, 150);
            CreateFolderController controller = loader.getController();
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(owner);
            dialogStage.setTitle("Create New Folder");
            dialogStage.setScene(scene);
            controller.setData(dialogStage, httpClient, ownerId, folderId, parentId, onSuccess, executorService);
            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}