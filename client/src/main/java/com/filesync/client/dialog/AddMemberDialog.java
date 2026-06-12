package com.filesync.client.dialog;

import com.filesync.client.shared.members.AddMemberController;
import com.filesync.client.http.SyncHttpClient;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class AddMemberDialog {
    public static void show(UUID folderId, SyncHttpClient httpClient, Runnable onSuccess, ExecutorService executorService) {
        try {
            FXMLLoader loader = new FXMLLoader(AddMemberDialog.class.getResource("/com/filesync/client/shared/members/add-member-dialog.fxml"));
            Scene scene = new Scene(loader.load(), 300, 400);
            AddMemberController controller = loader.getController();
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Add Member to Folder");
            dialogStage.setScene(scene);
            controller.setData(folderId, httpClient, onSuccess, dialogStage, executorService);
            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}