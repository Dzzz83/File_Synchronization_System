package com.filesync.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class UploadChoiceController {
    @FXML private Button fileButton;
    @FXML private Button folderButton;
    @FXML private Button cancelButton;

    private Stage stage;
    private Runnable onFile;
    private Runnable onFolder;

    public void setData(Stage stage, Runnable onFile, Runnable onFolder) {
        this.stage = stage;
        this.onFile = onFile;
        this.onFolder = onFolder;

        fileButton.setOnAction(e -> {
            if (onFile != null) onFile.run();
            stage.close();
        });
        folderButton.setOnAction(e -> {
            if (onFolder != null) onFolder.run();
            stage.close();
        });
        cancelButton.setOnAction(e -> stage.close());
    }
}