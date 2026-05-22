package com.filesync.client.admin;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.filesync.client.http.SyncHttpClient;

public class StartupController {
    @FXML private TextField serverUrlField;
    @FXML private TextField ownerIdField;
    @FXML private TextField registerUsernameField;
    @FXML private TextField registerPasswordField;
    @FXML private TextField registerEmailField;
    @FXML private PasswordField loginPasswordField;

    private Stage primaryStage;

    public void setPrimaryStage(Stage stage)
    {
        this.primaryStage = stage;
        ServerAdminApp.setMainStage(stage);
    }

    private void showAlert(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }


    @FXML
    private void handleLogin() {
        String serverUrl = serverUrlField.getText().trim();
        String loginInput = ownerIdField.getText().trim();
        String password = loginPasswordField.getText().trim();

        if (serverUrl.isEmpty() || loginInput.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Please enter server URL, email/username and password");
            return;
        }

        try {
            SyncHttpClient client = new SyncHttpClient(serverUrl);
            String actualUsername = client.login(loginInput, password);
            String token = client.getAuthToken(); // we added this getter
            // Close login window and open main window with tabs
            ServerAdminApp.showMainWindow(serverUrl, token, actualUsername);
        } catch (Exception e) {
            showAlert("Login failed", e.getMessage());
        }
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
            // pre-fill login username field
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

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filesync/client/admin/send-reset-code.fxml"));
            Parent root = loader.load();
            SendResetCodeController controller = loader.getController();
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Reset Password");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(false);
            controller.setData(serverUrl, dialogStage);
            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Could not open reset dialog: " + e.getMessage());
        }
    }
}
