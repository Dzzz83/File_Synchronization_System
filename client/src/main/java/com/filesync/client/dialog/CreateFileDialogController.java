package com.filesync.client.dialog;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class CreateFileDialogController {
    @FXML private TextField nameField;
    @FXML private ComboBox<String> extensionCombo;
    @FXML private Button createButton;
    @FXML private Button cancelButton;

    private Stage stage;
    private Runnable onSuccess;

    @FXML
    private void initialize() {
        extensionCombo.getItems().addAll(
                ".txt", ".json", ".xml", ".html", ".css", ".js", ".md", ".csv", ".yml", ".properties", ".docx"
        );
        extensionCombo.setEditable(true);
        extensionCombo.setValue(".txt");
        nameField.requestFocus();
    }

    public void setData(Stage stage, Runnable onSuccess) {
        this.stage = stage;
        this.onSuccess = onSuccess;
        createButton.setOnAction(e -> {
            if (onSuccess != null) onSuccess.run();
            stage.close();
        });
        cancelButton.setOnAction(e -> stage.close());
    }

    public String getFullFileName() {
        String name = nameField.getText().trim();
        String ext = extensionCombo.getValue();
        if (ext == null || ext.isEmpty()) ext = ".txt";
        if (!ext.startsWith(".")) ext = "." + ext;
        if (name.isEmpty()) name = "newfile";
        return name + ext;
    }
}