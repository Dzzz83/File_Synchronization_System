package com.filesync.client.service;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

public class GlobalProgressController {
    @FXML private Label progressLabel;
    @FXML private ProgressBar progressBar;

    @FXML
    public void initialize() {
        ProgressService ps = ProgressService.getInstance();
        progressLabel.textProperty().bind(ps.messageProperty());
        progressBar.progressProperty().bind(ps.progressProperty());
        progressBar.visibleProperty().bind(ps.visibleProperty());
        progressLabel.visibleProperty().bind(ps.visibleProperty());
    }
}