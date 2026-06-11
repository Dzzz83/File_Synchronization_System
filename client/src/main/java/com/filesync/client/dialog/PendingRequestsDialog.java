package com.filesync.client.dialog;

import com.filesync.client.controller.PendingRequestsDialogController;
import com.filesync.client.http.SyncHttpClient;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.UUID;

public class PendingRequestsDialog {
    public static void show(UUID folderId, SyncHttpClient httpClient, Runnable onApproved) {
        try {
            FXMLLoader loader = new FXMLLoader(PendingRequestsDialog.class.getResource("/com/filesync/client/dialog/pending-requests-dialog.fxml"));
            Scene scene = new Scene(loader.load(), 350, 250);
            PendingRequestsDialogController controller = loader.getController();
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Pending Access Requests");
            dialogStage.setScene(scene);
            controller.setData(folderId, httpClient, dialogStage, onApproved);
            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}