package com.filesync.client.admin;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.w3c.dom.Text;
import javafx.scene.control.TextInputDialog;
import java.util.Optional;
import com.filesync.client.http.SyncHttpClient;

public class StartupController {
    @FXML private TextField serverUrlField;
    @FXML private TextField ownerIdField;
    @FXML private TextField registerUsernameField;
    @FXML private TextField registerPasswordField;
    @FXML private TextField registerEmailField;

    private Stage primaryStage;

    public void setPrimaryStage(Stage stage)
    {
        this.primaryStage = stage;
    }

    private void showAlert(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void openMainWindow(String serverUrl, String ownerId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filesync/client/admin/server-file-list.fxml"));
            Scene scene = new Scene(loader.load(), 800, 600);
            ServerFileListController controller = loader.getController();
            controller.initialize(ownerId, serverUrl);
            Stage stage = new Stage();
            stage.setTitle("File Server Admin - " + ownerId);
            stage.setScene(scene);
            stage.show();
            primaryStage.close();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to open main window: " + e.getMessage());
        }
    }
    @FXML
    private void handleLogin()
    {
        String serverUrl = serverUrlField.getText().trim();
        String ownerId = ownerIdField.getText().trim();

        if (serverUrl.isEmpty() || ownerId.isEmpty())
        {
            showAlert("Error", "Please enter server URL and username");
            return;
        }
        openMainWindow(serverUrl, ownerId);
    }
    @FXML
    private void handleRegister() {
        String serverUrl = serverUrlField.getText().trim();
        String username = registerUsernameField.getText().trim();
        String password = registerPasswordField.getText().trim();
        String email = registerEmailField.getText().trim();

        if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty() || email.isEmpty()) {
            showAlert("Error", "Please fill all fields");
            return;
        }

        SyncHttpClient tempClient = new SyncHttpClient(serverUrl);
        boolean success = tempClient.registerUser(username, password, email);

        if (success) {
            showAlert("Success", "Registration successful. You can now login.");
            // Pre-fill login username field
            ownerIdField.setText(username);
        } else {
            showAlert("Registration failed", "Username or email may already exist.");
        }
    }
    @FXML
    private void handleForgotPassword() {
        String serverUrl = serverUrlField.getText().trim();
        if (serverUrl.isEmpty()) {
            showAlert("Error", "Please enter server URL first.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Forgot Password");
        dialog.setHeaderText("Enter your email address");
        dialog.setContentText("Email:");
        Optional<String> result = dialog.showAndWait();

        result.ifPresent(email -> {
            SyncHttpClient tempClient = new SyncHttpClient(serverUrl);
            String token = tempClient.forgotPassword(email);
            if (token != null) {
                showAlert("Success", "Reset token generated: " + token + "\nUse this token in Reset Password.");
            } else {
                showAlert("Error", "Email not found or server error.");
            }
        });
    }

    @FXML
    private void handleResetPassword() {
        String serverUrl = serverUrlField.getText().trim();
        if (serverUrl.isEmpty()) {
            showAlert("Error", "Please enter server URL first.");
            return;
        }

        TextInputDialog tokenDialog = new TextInputDialog();
        tokenDialog.setTitle("Reset Password");
        tokenDialog.setHeaderText("Enter the reset token");
        tokenDialog.setContentText("Token:");
        Optional<String> tokenResult = tokenDialog.showAndWait();

        tokenResult.ifPresent(token -> {
            TextInputDialog passDialog = new TextInputDialog();
            passDialog.setTitle("Reset Password");
            passDialog.setHeaderText("Enter your new password");
            passDialog.setContentText("New Password:");
            Optional<String> passResult = passDialog.showAndWait();

            passResult.ifPresent(newPassword -> {
                SyncHttpClient tempClient = new SyncHttpClient(serverUrl);
                boolean success = tempClient.resetPassword(token, newPassword);
                if (success) {
                    showAlert("Success", "Password reset successfully. You can now login.");
                } else {
                    showAlert("Error", "Invalid or expired token.");
                }
            });
        });
    }
}
