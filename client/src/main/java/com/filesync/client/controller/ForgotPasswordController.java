package com.filesync.client.controller;

import com.filesync.client.http.SyncHttpClient;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

import java.util.concurrent.ExecutorService;

public class ForgotPasswordController {
    @FXML private TextField emailField;
    @FXML private TextField tokenField;
    @FXML private PasswordField passwordField;
    @FXML private Button resetButton;

    private SyncHttpClient syncHttpClient;
    private String serverUrl;
    private Stage dialogStage;
    private ExecutorService executorService;
    private boolean resetSuccess = false;

    public void setData(String serverUrl, Stage dialogStage, ExecutorService executorService) {
        this.serverUrl = serverUrl;
        this.dialogStage = dialogStage;
        this.executorService = executorService;
        this.syncHttpClient = new SyncHttpClient(serverUrl);
    }

    @FXML
    public void handleReset() {
        String email = emailField.getText().trim();
        String token = tokenField.getText().trim();
        String newPassword = passwordField.getText().trim();

        if (email.isEmpty() || token.isEmpty() || newPassword.isEmpty()) {
            showAlert("Error", "All fields are required");
            return;
        }

        // Disable button during operation
        resetButton.setDisable(true);
        resetButton.setText("Resetting...");

        // Step 1: Request reset code (forgotPassword)
        Task<Void> forgotTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                syncHttpClient.forgotPassword(email);
                return null;
            }
        };
        forgotTask.setOnSucceeded(e -> {
            // Step 2: Reset password with token
            Task<Boolean> resetTask = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    return syncHttpClient.resetPassword(token, newPassword);
                }
            };
            resetTask.setOnSucceeded(ev -> {
                boolean success = resetTask.getValue();
                Platform.runLater(() -> {
                    if (success) {
                        showAlert("Success", "Password reset successfully. You can now login");
                        resetSuccess = true;
                        dialogStage.close();
                    } else {
                        showAlert("Error", "Invalid or expired token");
                        resetButton.setDisable(false);
                        resetButton.setText("Reset Password");
                    }
                });
            });
            resetTask.setOnFailed(ev -> {
                Platform.runLater(() -> {
                    showAlert("Error", resetTask.getException().getMessage());
                    resetButton.setDisable(false);
                    resetButton.setText("Reset Password");
                });
            });
            executorService.submit(resetTask);
        });
        forgotTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                showAlert("Error", forgotTask.getException().getMessage());
                resetButton.setDisable(false);
                resetButton.setText("Reset Password");
            });
        });
        executorService.submit(forgotTask);
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    public boolean isResetSuccessful() {
        return resetSuccess;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}