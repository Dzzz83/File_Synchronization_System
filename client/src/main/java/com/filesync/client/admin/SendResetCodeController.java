package com.filesync.client.admin;

import com.filesync.client.http.SyncHttpClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class SendResetCodeController {
    @FXML
    private TextField emailField;
    private Stage dialogStage;
    private String serverUrl;

    public void setData(String serverUrl, Stage dialogStage)
    {
        this.serverUrl = serverUrl;
        this.dialogStage = dialogStage;
    }

    @FXML
    private void handleSendCode()
    {
        String email = emailField.getText().trim();
        if (email.isEmpty())
        {
            showAlert("Error", "Please enter your email.");
            return;
        }

        try
        {
            SyncHttpClient syncHttpClient = new SyncHttpClient(serverUrl);
            syncHttpClient.forgotPassword(email);
            showAlert("Success", "A 6-digit code has been sent to your email.");
            dialogStage.close();

            Platform.runLater(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
                openConfirmReset();
            });

        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    private void openConfirmReset()
    {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/filesync/client/admin/confirm-reset.fxml"));
            Parent root = fxmlLoader.load();
            ConfirmResetController confirmResetController = fxmlLoader.getController();
            Stage stage2 = new Stage();
            stage2.setTitle("Reset Password");
            stage2.initModality(Modality.WINDOW_MODAL);
            stage2.initOwner(dialogStage.getOwner());
            stage2.setScene(new Scene(root));
            stage2.setResizable(false);
            confirmResetController.setData(serverUrl, stage2);
            stage2.showAndWait();
        }
        catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Could not open enter reset code box: " + e.getMessage());
        }
    }
    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
