package com.filesync.client.admin;

import com.filesync.client.http.SyncHttpClient;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

public class ConfirmResetController {
    @FXML private TextField codeField;
    @FXML private PasswordField passwordField;

    private String serverUrl;
    private Stage dialogStage;

    public void setData(String serverUrl, Stage dialogStage) {
        this.serverUrl = serverUrl;
        this.dialogStage = dialogStage;
    }

    @FXML
    private void handleReset() {
        String code = codeField.getText().trim();
        String newPassword = passwordField.getText().trim();

        if (code.isEmpty() || newPassword.isEmpty()) {
            showAlert("Error", "Both fields are required.");
            return;
        }

        SyncHttpClient client = new SyncHttpClient(serverUrl);
        boolean success = client.resetPassword(code, newPassword);
        if (success) {
            showAlert("Success", "Password reset successfully. You can now login.");
            dialogStage.close();
        } else {
            showAlert("Error", "Invalid or expired reset code.");
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