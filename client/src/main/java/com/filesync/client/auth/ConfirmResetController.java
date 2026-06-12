package com.filesync.client.auth;

import com.filesync.client.service.PasswordResetService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.concurrent.ExecutorService;

public class ConfirmResetController {
    @FXML private TextField tokenField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button resetButton;

    private PasswordResetService resetService;
    private Stage dialogStage;
    private ExecutorService executorService;

    public void setData(PasswordResetService resetService, Stage dialogStage, ExecutorService executorService) {
        this.resetService = resetService;
        this.dialogStage = dialogStage;
        this.executorService = executorService;
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

        // Disable button during operation
        resetButton.setDisable(true);
        resetButton.setText("Resetting...");

        Task<Boolean> resetTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return resetService.resetPassword(token, newPassword);
            }
        };
        resetTask.setOnSucceeded(e -> {
            boolean success = resetTask.getValue();
            Platform.runLater(() -> {
                resetButton.setDisable(false);
                resetButton.setText("Reset Password");
                if (success) {
                    showAlert("Success", "Password reset successfully. You can now log in.");
                    dialogStage.close();
                } else {
                    showAlert("Error", "Invalid or expired token.");
                }
            });
        });
        resetTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                resetButton.setDisable(false);
                resetButton.setText("Reset Password");
                showAlert("Error", resetTask.getException().getMessage());
            });
        });
        executorService.submit(resetTask);
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