package com.filesync.client.document;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class PdfViewerController {
    @FXML private ImageView pageImageView;
    @FXML private TextField pageField;
    @FXML private Label totalPagesLabel;
    @FXML private Label statusLabel;
    @FXML private Slider zoomSlider;
    @FXML private Button previousButton;
    @FXML private Button nextButton;

    private Stage stage;
    private Path tempFile;

    private PDDocument document;
    private PDFRenderer renderer;

    private int currentPageIndex = 0;
    private int totalPages = 0;
    private int renderVersion = 0;

    private final Map<String, SoftReference<Image>> pageCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SoftReference<Image>> eldest) {
            return size() > 8;
        }
    };

    public static void show(Stage owner, Path file, String fileName) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                PdfViewerController.class.getResource("/com/filesync/client/document/pdf-viewer.fxml")
        );

        Parent root = loader.load();

        Stage dialogStage = new Stage();
        dialogStage.setTitle("PDF Viewer - " + fileName);
        dialogStage.initModality(Modality.NONE);

        if (owner != null) {
            dialogStage.initOwner(owner);
        }

        dialogStage.setScene(new Scene(root, 1000, 750));
        dialogStage.setResizable(true);

        PdfViewerController controller = loader.getController();
        controller.open(dialogStage, file);

        dialogStage.show();
    }

    private void open(Stage stage, Path file) throws Exception {
        this.stage = stage;
        this.tempFile = file;

        this.document = PDDocument.load(file.toFile());
        this.renderer = new PDFRenderer(document);
        this.totalPages = document.getNumberOfPages();

        totalPagesLabel.setText("/ " + totalPages);
        pageField.setText("1");

        updateButtons();
        renderCurrentPage();

        stage.setOnCloseRequest(event -> cleanup());
    }

    @FXML
    private void previousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--;
            renderCurrentPage();
        }
    }

    @FXML
    private void nextPage() {
        if (currentPageIndex < totalPages - 1) {
            currentPageIndex++;
            renderCurrentPage();
        }
    }

    @FXML
    private void goToPage() {
        try {
            int requestedPage = Integer.parseInt(pageField.getText().trim());

            if (requestedPage < 1 || requestedPage > totalPages) {
                showAlert("Invalid page", "Page must be between 1 and " + totalPages + ".");
                pageField.setText(String.valueOf(currentPageIndex + 1));
                return;
            }

            currentPageIndex = requestedPage - 1;
            renderCurrentPage();
        } catch (NumberFormatException e) {
            showAlert("Invalid page", "Please enter a valid page number.");
            pageField.setText(String.valueOf(currentPageIndex + 1));
        }
    }

    @FXML
    private void zoomIn() {
        zoomSlider.setValue(Math.min(3.0, zoomSlider.getValue() + 0.25));
        renderCurrentPage();
    }

    @FXML
    private void zoomOut() {
        zoomSlider.setValue(Math.max(0.5, zoomSlider.getValue() - 0.25));
        renderCurrentPage();
    }

    @FXML
    private void fitWidth() {
        zoomSlider.setValue(1.25);
        renderCurrentPage();
    }

    private void renderCurrentPage() {
        if (document == null || renderer == null || totalPages == 0) {
            return;
        }

        int pageToRender = currentPageIndex;
        float scale = (float) zoomSlider.getValue();

        String cacheKey = pageToRender + "@" + scale;
        Image cached = getCachedImage(cacheKey);

        if (cached != null) {
            pageImageView.setImage(cached);
            updateUiAfterRender();
            return;
        }

        int version = ++renderVersion;
        statusLabel.setText("Rendering page " + (pageToRender + 1) + "...");

        Task<Image> renderTask = new Task<>() {
            @Override
            protected Image call() throws Exception {
                BufferedImage bufferedImage = renderer.renderImage(pageToRender, scale, ImageType.RGB);
                return SwingFXUtils.toFXImage(bufferedImage, null);
            }
        };

        renderTask.setOnSucceeded(event -> {
            if (version != renderVersion) {
                return;
            }

            Image image = renderTask.getValue();
            pageCache.put(cacheKey, new SoftReference<>(image));
            pageImageView.setImage(image);

            updateUiAfterRender();
        });

        renderTask.setOnFailed(event -> {
            Throwable ex = renderTask.getException();
            statusLabel.setText("Render failed");
            showAlert("Render failed", ex == null ? "Unknown error" : ex.getMessage());
        });

        Thread thread = new Thread(renderTask, "pdf-render-page-" + (pageToRender + 1));
        thread.setDaemon(true);
        thread.start();
    }

    private Image getCachedImage(String cacheKey) {
        SoftReference<Image> ref = pageCache.get(cacheKey);
        return ref == null ? null : ref.get();
    }

    private void updateUiAfterRender() {
        pageField.setText(String.valueOf(currentPageIndex + 1));
        statusLabel.setText("Page " + (currentPageIndex + 1) + " of " + totalPages);
        updateButtons();
    }

    private void updateButtons() {
        if (previousButton != null) {
            previousButton.setDisable(currentPageIndex <= 0);
        }

        if (nextButton != null) {
            nextButton.setDisable(currentPageIndex >= totalPages - 1);
        }
    }

    private void cleanup() {
        try {
            if (document != null) {
                document.close();
            }
        } catch (Exception ignored) {
        } finally {
            DocumentViewerDialog.deleteQuietly(tempFile);
        }
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}