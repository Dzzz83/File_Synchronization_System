package com.filesync.client.dialog;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class ConfirmationDialogController {
    @FXML private Label messageLabel;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;

    private Stage stage;
    private Runnable onConfirm;

    public void setData(String message, Stage stage, Runnable onConfirm) {
        this.stage = stage;
        this.onConfirm = onConfirm;
        messageLabel.setText(message);
        confirmButton.setOnAction(e -> {
            if (onConfirm != null) onConfirm.run();
            stage.close();
        });
        cancelButton.setOnAction(e -> stage.close());
    }
}