package com.filesync.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import java.nio.file.Path;

public class ImageViewerController {
    @FXML private ImageView imageView;
    @FXML private ScrollPane scrollPane;
    @FXML private Button zoomInButton;
    @FXML private Button zoomOutButton;
    @FXML private Button fitButton;
    @FXML private Button originalButton;

    private Stage stage;
    private Path tempFile;
    private Image image;
    private double originalWidth, originalHeight;
    private double currentZoom = 1.0;

    public void init(Stage stage, Path tempFile, String fileName) {
        this.stage = stage;
        this.tempFile = tempFile;

        image = new Image(tempFile.toUri().toString());
        imageView.setImage(image);
        originalWidth = image.getWidth();
        originalHeight = image.getHeight();

        fitToWindow();

        zoomInButton.setOnAction(e -> zoom(1.25));
        zoomOutButton.setOnAction(e -> zoom(0.8));
        fitButton.setOnAction(e -> fitToWindow());
        originalButton.setOnAction(e -> resetToOriginal());

        stage.widthProperty().addListener((obs, old, newVal) -> fitIfAuto());
        stage.heightProperty().addListener((obs, old, newVal) -> fitIfAuto());
    }

    private void zoom(double factor) {
        currentZoom *= factor;
        applyZoom();
    }

    private void applyZoom() {
        imageView.setFitWidth(originalWidth * currentZoom);
        imageView.setFitHeight(originalHeight * currentZoom);
        imageView.setPreserveRatio(true);
    }

    private void fitToWindow() {
        double viewportWidth = scrollPane.getViewportBounds().getWidth();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            viewportWidth = stage.getWidth() - 20;
            viewportHeight = stage.getHeight() - 20;
        }
        double scaleX = viewportWidth / originalWidth;
        double scaleY = viewportHeight / originalHeight;
        currentZoom = Math.min(scaleX, scaleY);
        applyZoom();
    }

    private void resetToOriginal() {
        currentZoom = 1.0;
        applyZoom();
    }

    private void fitIfAuto() {
    }

    public void cleanup() {
        if (tempFile != null) {
            try { java.nio.file.Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
        }
        if (image != null) image.cancel();
    }
}