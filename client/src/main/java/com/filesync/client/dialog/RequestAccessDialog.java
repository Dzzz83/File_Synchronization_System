package com.filesync.client.dialog;

import com.filesync.client.controller.RequestAccessDialogController;
import com.filesync.client.http.SyncHttpClient;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class RequestAccessDialog {
    public static void show(SyncHttpClient httpClient) {
        try {
            FXMLLoader loader = new FXMLLoader(RequestAccessDialog.class.getResource("/com/filesync/client/dialog/request-access-dialog.fxml"));
            Scene scene = new Scene(loader.load(), 400, 450);
            RequestAccessDialogController controller = loader.getController();
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Request Access");
            dialogStage.setScene(scene);
            controller.setData(httpClient, dialogStage);
            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}