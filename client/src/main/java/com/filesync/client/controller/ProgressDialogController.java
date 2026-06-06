package com.filesync.client.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

public class ProgressDialogController {
    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Button cancelButton;

    private Stage stage;
    private Runnable onCancel;

    public void setData(String title, String message, Stage stage, Runnable onCancel) {
        this.stage = stage;
        this.onCancel = onCancel;
        titleLabel.setText(title);
        messageLabel.setText(message);
        cancelButton.setOnAction(e -> {
            if (onCancel != null) onCancel.run();
            stage.close();
        });
    }

    public void setProgress(double progress) {
        Platform.runLater(() -> progressBar.setProgress(progress));
    }

    public void setMessage(String message) {
        Platform.runLater(() -> messageLabel.setText(message));
    }

    public void close() {
        Platform.runLater(() -> stage.close());
    }

    public void show() {
        Platform.runLater(() -> stage.show());
    }
}