package com.filesync.client.dialog;

import com.filesync.client.controller.MediaPlayerController;
import com.filesync.client.http.SyncHttpClient;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.nio.file.Files;
import java.nio.file.Path;

public class MediaPlayerDialog {
    public static void show(String fileId, String fileName, SyncHttpClient httpClient) {
        try {
            String ext = "";
            int dot = fileName.lastIndexOf('.');
            if (dot > 0) {
                ext = fileName.substring(dot);
            }
            Path tempFile = Files.createTempFile("media_", ext);
            httpClient.downloadFile(fileId, tempFile);

            if (Files.size(tempFile) == 0) {
                throw new RuntimeException("Downloaded file is empty");
            }

            FXMLLoader loader = new FXMLLoader(MediaPlayerDialog.class.getResource("/com/filesync/client/player/media-player.fxml"));
            Scene scene = new Scene(loader.load(), 800, 600);
            MediaPlayerController controller = loader.getController();
            Stage stage = new Stage();
            stage.setTitle("Media Player - " + fileName);
            stage.initModality(Modality.NONE);
            stage.setScene(scene);
            stage.setOnHidden(e -> controller.disposeAndDelete(tempFile));
            controller.setMediaFile(tempFile.toFile(), fileName);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Media Player Error");
            alert.setHeaderText("Failed to load media");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }
}