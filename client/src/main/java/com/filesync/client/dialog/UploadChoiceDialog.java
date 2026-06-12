package com.filesync.client.dialog;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class UploadChoiceDialog {
    public static void show(Stage owner, Runnable onFile, Runnable onFolder) {
        try {
            FXMLLoader loader = new FXMLLoader(UploadChoiceDialog.class.getResource("/com/filesync/client/dialog/upload-choice-dialog.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(owner);
            stage.setTitle("Upload");
            stage.setScene(scene);
            UploadChoiceController controller = loader.getController();
            controller.setData(stage, onFile, onFolder);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}