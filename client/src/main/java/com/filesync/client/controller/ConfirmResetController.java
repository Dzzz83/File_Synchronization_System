package com.filesync.client.controller;

import com.filesync.client.service.PasswordResetService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ConfirmResetController {
    @FXML private TextField tokenField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;

    private PasswordResetService resetService;
    private Stage dialogStage;

    public void setData(PasswordResetService resetService, Stage dialogStage) {
        this.resetService = resetService;
        this.dialogStage = dialogStage;
    }

    @FXML
    private void handleReset() {
        String token = tokenField.getText().trim();
        String newPassword = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        if (token.isEmpty() || newPassword.isEmpty()) {
            showAlert("Error", "Please fill all fields.");
            return;
        }
        if (!newPassword.equals(confirm)) {
            showAlert("Error", "Passwords do not match.");
            return;
        }
        try {
            boolean success = resetService.resetPassword(token, newPassword);
            if (success) {
                showAlert("Success", "Password reset successfully. You can now log in.");
                dialogStage.close();
            } else {
                showAlert("Error", "Invalid or expired token.");
            }
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
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