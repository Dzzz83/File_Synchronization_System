package com.filesync.client.dialog;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public final class CreateFileDialog {
    private CreateFileDialog() {}

    public static String showAndWait(Stage owner) {
        try {
            FXMLLoader loader = new FXMLLoader(CreateFileDialog.class.getResource("/com/filesync/client/dialog/create-file-dialog.fxml"));
            Scene scene = new Scene(loader.load());
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) dialog.initOwner(owner);
            dialog.setTitle("Create New File");
            dialog.setScene(scene);
            dialog.setResizable(false);

            CreateFileDialogController controller = loader.getController();
            final String[] result = {null};
            controller.setData(dialog, () -> result[0] = controller.getFullFileName());
            dialog.showAndWait();
            return result[0];
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}