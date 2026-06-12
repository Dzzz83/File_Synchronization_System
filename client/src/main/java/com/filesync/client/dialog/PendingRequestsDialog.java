package com.filesync.client.dialog;

import com.filesync.client.shared.requests.ApproveRequestsController;
import com.filesync.client.http.SyncHttpClient;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class PendingRequestsDialog {
    public static void show(UUID folderId, SyncHttpClient httpClient, Runnable onApproved, ExecutorService executorService) {
        try {
            FXMLLoader loader = new FXMLLoader(PendingRequestsDialog.class.getResource("/com/filesync/client/shared/requests/pending-requests-dialog.fxml"));
            Scene scene = new Scene(loader.load(), 350, 250);
            ApproveRequestsController controller = loader.getController();
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Pending Access Requests");
            dialogStage.setScene(scene);
            controller.setData(folderId, httpClient, dialogStage, onApproved, executorService);
            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}