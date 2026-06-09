package com.filesync.client.controller;

import com.filesync.client.http.SyncHttpClient;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.concurrent.ExecutorService;

public class StartupController {
    @FXML private TextField serverUrlField;
    @FXML private TextField ownerIdField;
    @FXML private TextField registerUsernameField;
    @FXML private TextField registerPasswordField;
    @FXML private TextField registerEmailField;
    @FXML private PasswordField loginPasswordField;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private Button ForgetpassButton;

    private Stage primaryStage;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
        ServerAdminApp.setMainStage(stage);
    }

    private void showAlert(String title, String message) {
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

        loginButton.setDisable(true);
        loginButton.setText("Logging in...");

        ExecutorService executor = ServerAdminApp.getInstance().getExecutor();
        Task<String[]> loginTask = new Task<>() {
            @Override
            protected String[] call() throws Exception {
                SyncHttpClient client = new SyncHttpClient(serverUrl);
                String username = client.login(loginInput, password);
                String token = client.getAuthToken();
                return new String[]{username, token};
            }
        };
        loginTask.setOnSucceeded(e -> {
            String[] result = loginTask.getValue();
            Platform.runLater(() -> {
                ServerAdminApp.showMainWindow(serverUrl, result[1], result[0]);
                primaryStage.close(); // close login window
            });
        });
        loginTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                loginButton.setDisable(false);
                loginButton.setText("Login");
                showAlert("Login failed", loginTask.getException().getMessage());
            });
        });
        executor.submit(loginTask);
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

        registerButton.setDisable(true);
        registerButton.setText("Registering...");

        ExecutorService executor = ServerAdminApp.getInstance().getExecutor();
        Task<Boolean> registerTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                SyncHttpClient tempClient = new SyncHttpClient(serverUrl);
                return tempClient.registerUser(username, password, email);
            }
        };
        registerTask.setOnSucceeded(e -> {
            boolean success = registerTask.getValue();
            Platform.runLater(() -> {
                registerButton.setDisable(false);
                registerButton.setText("Register");
                if (success) {
                    showAlert("Success", "Registration successful. You can now login.");
                    ownerIdField.setText(username);
                } else {
                    showAlert("Registration failed", "Username or email may already exist.");
                }
            });
        });
        registerTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                registerButton.setDisable(false);
                registerButton.setText("Register");
                showAlert("Registration failed", registerTask.getException().getMessage());
            });
        });
        executor.submit(registerTask);
    }

    @FXML
    private void handleForgotPassword() {
        String serverUrl = serverUrlField.getText().trim();
        if (serverUrl.isEmpty()) {
            showAlert("Error", "Please enter server URL first.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filesync/client/controller/send-reset-code.fxml"));
            Parent root = loader.load();
            RequestResetController controller = loader.getController();
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Reset Password");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(false);
            // Pass the executor
            controller.setData(serverUrl, dialogStage, ServerAdminApp.getInstance().getExecutor());
            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Could not open reset dialog: " + e.getMessage());
        }
    }
}