package com.filesync.client.admin;

import com.filesync.client.http.SyncHttpClient;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

public class ForgotPasswordController
{
    @FXML private TextField emailField;
    @FXML private TextField tokenField;
    @FXML private PasswordField passwordField;

    private SyncHttpClient syncHttpClient;
    private String serverUrl;
    private Stage dialogStage;
    private boolean resetSuccess = false;

    public void setData(String serverUrl, Stage dialogStage)
    {
        this.serverUrl = serverUrl;
        this.dialogStage = dialogStage;
        this.syncHttpClient = new SyncHttpClient(serverUrl);
    }

    @FXML
    public void handleReset()
    {
        String email = emailField.getText().trim();
        String token = tokenField.getText().trim();
        String newPassword = passwordField.getText().trim();

        if (email.isEmpty() || token.isEmpty() || newPassword.isEmpty())
        {
            showAlert("Error", "All fields are required");
            return;
        }
        try {
            // request a token
            syncHttpClient.forgotPassword(email);
            // reset password
            boolean success = syncHttpClient.resetPassword(token, newPassword);
            if (success)
            {
                showAlert("Success", "Password reset successfully. You can now login");
                resetSuccess = true;
                dialogStage.close();
            }
            else
            {
                showAlert("Error", "Invalid or expired token");
            }
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    private void handleCancel()
    {
        dialogStage.close();
    }

    public boolean isResetSuccessful()
    {
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
