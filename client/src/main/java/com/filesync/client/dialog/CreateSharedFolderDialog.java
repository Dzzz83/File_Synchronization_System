package com.filesync.client.dialog;

import com.filesync.client.controller.CreateSharedFolderController;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.CreateFolderDto;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.concurrent.ExecutorService;

public class CreateSharedFolderDialog {

    public static CreateFolderDto show(Stage owner, SyncHttpClient httpClient, ExecutorService executorService) {
        try {
            FXMLLoader loader = new FXMLLoader(CreateSharedFolderDialog.class.getResource("/com/filesync/client/dialog/create-shared-folder.fxml"));
            Scene scene = new Scene(loader.load(), 400, 500);
            CreateSharedFolderController controller = loader.getController();
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(owner);
            dialogStage.setTitle("Create Shared Folder");
            dialogStage.setScene(scene);
            controller.setData(dialogStage, httpClient, executorService);
            dialogStage.showAndWait();
            return controller.getResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}