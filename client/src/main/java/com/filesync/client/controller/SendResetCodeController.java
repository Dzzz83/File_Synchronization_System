package com.filesync.client.controller;

import com.filesync.client.service.PasswordResetService;
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
    @FXML private TextField emailField;
    private Stage dialogStage;
    private PasswordResetService resetService;

    public void setData(String serverUrl, Stage dialogStage) {
        this.dialogStage = dialogStage;
        this.resetService = new PasswordResetService(serverUrl);
    }

    @FXML
    private void handleSendCode() {
        String email = emailField.getText().trim();
        if (email.isEmpty()) {
            showAlert("Error", "Please enter your email.");
            return;
        }
        try {
            resetService.requestResetCode(email);
            showAlert("Success", "A 6-digit code has been sent to your email.");
            dialogStage.close();
            Platform.runLater(this::openConfirmReset);
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    private void openConfirmReset() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filesync/client/controller/confirm-reset.fxml"));
            Parent root = loader.load();
            ConfirmResetController controller = loader.getController();
            Stage stage = new Stage();
            stage.setTitle("Reset Password");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(dialogStage.getOwner());
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            controller.setData(resetService, stage);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Could not open reset code dialog: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
        if (resetService != null) resetService.close();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}