package com.filesync.client.controller;

import com.filesync.client.service.PasswordResetService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.concurrent.ExecutorService;

public class RequestResetController {
    @FXML private TextField emailField;
    @FXML private Button sendButton;
    private Stage dialogStage;
    private PasswordResetService resetService;
    private ExecutorService executorService;

    public void setData(String serverUrl, Stage dialogStage, ExecutorService executorService) {
        this.dialogStage = dialogStage;
        this.executorService = executorService;
        this.resetService = new PasswordResetService(serverUrl);
    }

    @FXML
    private void handleSendCode() {
        String email = emailField.getText().trim();
        if (email.isEmpty()) {
            showAlert("Error", "Please enter your email.");
            return;
        }

        sendButton.setDisable(true);
        sendButton.setText("Sending...");

        Task<Void> sendTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                resetService.requestResetCode(email);
                return null;
            }
        };
        sendTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                showAlert("Success", "A 6-digit code has been sent to your email.");
                dialogStage.close();
                openConfirmReset();
            });
        });
        sendTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                sendButton.setDisable(false);
                sendButton.setText("Send Code");
                showAlert("Error", sendTask.getException().getMessage());
            });
        });
        executorService.submit(sendTask);
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
            // Pass the same executor to ConfirmResetController
            controller.setData(resetService, stage, executorService);
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