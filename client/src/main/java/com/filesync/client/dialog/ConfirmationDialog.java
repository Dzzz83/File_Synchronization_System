package com.filesync.client.dialog;

import com.filesync.client.controller.ConfirmationDialogController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ConfirmationDialog {
    public static boolean show(Stage owner, String message) {
        try {
            FXMLLoader loader = new FXMLLoader(ConfirmationDialog.class.getResource("/com/filesync/client/dialog/confirmation-dialog.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(owner);
            stage.setTitle("Confirm");
            stage.setScene(scene);
            ConfirmationDialogController controller = loader.getController();
            final boolean[] confirmed = {false};
            controller.setData(message, stage, () -> confirmed[0] = true);
            stage.showAndWait();
            return confirmed[0];
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}