package com.filesync.client.dialog;

import com.filesync.client.controller.CreateFolderDialogController;
import com.filesync.client.http.SyncHttpClient;
import com.filesync.common.dto.CreateFolderDto;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class CreateFolderDialog {
    public static CreateFolderDto show(Stage owner, SyncHttpClient httpClient) {
        try {
            FXMLLoader loader = new FXMLLoader(CreateFolderDialog.class.getResource("/com/filesync/client/dialog/create-folder-dialog.fxml"));
            Scene scene = new Scene(loader.load(), 450, 550);
            CreateFolderDialogController controller = loader.getController();
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(owner);
            dialogStage.setTitle("Create Shared Folder");
            dialogStage.setScene(scene);
            controller.setData(dialogStage, httpClient);
            dialogStage.showAndWait();
            return controller.getResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}