package com.filesync.client.admin;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class EditDialogController {

    @FXML private TextArea textArea;

    private Consumer<String> onSaveCallback;
    private String originalContent;

    public void setData(String content, Consumer<String> onSave) {
        this.originalContent = content;
        this.onSaveCallback = onSave;
        textArea.setText(content);
    }

    @FXML
    private void handleSave() {
        String newContent = textArea.getText();
        if (onSaveCallback != null) {
            onSaveCallback.accept(newContent);
        }
        closeStage();
    }

    @FXML
    private void handleCancel() {
        closeStage();
    }

    private void closeStage() {
        Stage stage = (Stage) textArea.getScene().getWindow();
        stage.close();
    }
}