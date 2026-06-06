package com.filesync.client.dialog;

import com.filesync.client.controller.ProgressDialogController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ProgressDialog {
    public static ProgressDialogController show(String title, String message, Stage owner, Runnable onCancel) {
        try {
            FXMLLoader loader = new FXMLLoader(ProgressDialog.class.getResource("/com/filesync/client/dialog/progress-dialog.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(owner);
            stage.setTitle(title);
            stage.setScene(scene);
            ProgressDialogController controller = loader.getController();
            controller.setData(title, message, stage, onCancel);
            stage.show();
            return controller;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}