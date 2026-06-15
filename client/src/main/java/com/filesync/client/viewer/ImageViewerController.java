package com.filesync.client.viewer;

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
    private double currentZoom = 1.0;

    public void init(Stage stage, Path tempFile, String fileName) {
        this.stage = stage;
        this.tempFile = tempFile;

        image = new Image(tempFile.toUri().toString());
        imageView.setImage(image);
        imageView.setPreserveRatio(true);

        // Initial fit to window
        fitToWindow();

        zoomInButton.setOnAction(e -> zoom(1.25));
        zoomOutButton.setOnAction(e -> zoom(0.8));
        fitButton.setOnAction(e -> fitToWindow());
        originalButton.setOnAction(e -> resetToOriginal());

        // Re‑fit when the stage is resized (optional: remove if you prefer to keep zoom)
        stage.widthProperty().addListener((obs, old, newVal) -> refitOnResize());
        stage.heightProperty().addListener((obs, old, newVal) -> refitOnResize());
    }

    private void zoom(double factor) {
        currentZoom *= factor;
        // Clamp zoom to reasonable limits (e.g., 0.1x to 10x)
        if (currentZoom < 0.1) currentZoom = 0.1;
        if (currentZoom > 10.0) currentZoom = 10.0;
        applyZoom();
    }

    private void applyZoom() {
        imageView.setScaleX(currentZoom);
        imageView.setScaleY(currentZoom);
    }

    private void fitToWindow() {
        double viewportWidth = scrollPane.getViewportBounds().getWidth();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();

        // Fallback if viewport bounds not yet available
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            viewportWidth = stage.getWidth() - 20;
            viewportHeight = stage.getHeight() - 20;
        }

        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();

        if (imageWidth <= 0 || imageHeight <= 0) {
            currentZoom = 1.0;
        } else {
            double scaleX = viewportWidth / imageWidth;
            double scaleY = viewportHeight / imageHeight;
            currentZoom = Math.min(scaleX, scaleY);
        }
        applyZoom();
    }

    private void resetToOriginal() {
        currentZoom = 1.0;
        applyZoom();
    }

    private void refitOnResize() {
         fitToWindow();
    }

    public void cleanup() {
        if (tempFile != null) {
            try {
                java.nio.file.Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {}
        }
        if (image != null) {
            image.cancel();
        }
    }
}