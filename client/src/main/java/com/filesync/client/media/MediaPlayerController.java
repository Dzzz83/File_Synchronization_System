package com.filesync.client.media;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MediaPlayerController {
    @FXML private MediaView mediaView;
    @FXML private Button playPauseButton;
    @FXML private Button rewindButton;
    @FXML private Button forwardButton;
    @FXML private Label titleLabel;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;
    @FXML private Slider progressSlider;
    @FXML private Slider volumeSlider;

    private MediaPlayer mediaPlayer;
    private static final List<String> SUPPORTED_EXTENSIONS = List.of("mp3", "wav", "mp4", "avi", "mov", "mkv");

    public void setMediaFile(File file, String fileName) {
        titleLabel.setText(fileName);

        if (!file.exists() || file.length() == 0) {
            showAlert("Playback Error", "File is empty or missing.");
            return;
        }

        String ext = getFileExtension(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            showAlert("Unsupported Format", "File format ." + ext + " is not supported.");
            return;
        }

        try {
            Media media = new Media(file.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);
            volumeSlider.valueProperty().bindBidirectional(mediaPlayer.volumeProperty());

            mediaPlayer.setOnReady(() -> {
                Platform.runLater(() -> {
                    if (mediaPlayer == null) return;
                    if (mediaView.getScene() != null && mediaView.getScene().getWindow() instanceof Stage) {
                        Stage stage = (Stage) mediaView.getScene().getWindow();

                        stage.setMaximized(true);
                        // Keep video aspect ratio intact and bind it to fill the scene dynamically
                        mediaView.setPreserveRatio(true);
                        mediaView.fitWidthProperty().bind(mediaView.getScene().widthProperty());
                        mediaView.fitHeightProperty().bind(mediaView.getScene().heightProperty().subtract(150));
                    }
                    Duration total = mediaPlayer.getTotalDuration();
                    if (total != null && !total.isUnknown()) {
                        totalTimeLabel.setText(formatTime(total));
                    }
                    progressSlider.setDisable(false);
                    playPauseButton.setDisable(false);
                    rewindButton.setDisable(false);
                    forwardButton.setDisable(false);

                    progressSlider.setOnMousePressed(event -> {
                        if (mediaPlayer == null) return;
                        double mouseX = event.getX();
                        double width = progressSlider.getWidth();
                        if (width > 0) {
                            double percent = Math.min(1.0, Math.max(0.0, mouseX / width));
                            Duration totalDur = mediaPlayer.getTotalDuration();
                            if (totalDur != null && !totalDur.isUnknown()) {
                                Duration seekTime = totalDur.multiply(percent);
                                mediaPlayer.seek(seekTime);
                                currentTimeLabel.setText(formatTime(seekTime));
                                progressSlider.setValue(percent);
                            }
                        }
                        event.consume();
                    });
                });
            });

            mediaPlayer.currentTimeProperty().addListener((obs, old, current) -> {
                Platform.runLater(() -> {
                    if (mediaPlayer == null) return;
                    if (!progressSlider.isValueChanging()) {
                        Duration total = mediaPlayer.getTotalDuration();
                        if (total != null && !total.isUnknown() && total.greaterThan(Duration.ZERO)) {
                            progressSlider.setValue(current.toMillis() / total.toMillis());
                        }
                        currentTimeLabel.setText(formatTime(current));
                    }
                });
            });

            progressSlider.valueProperty().addListener((obs, old, val) -> {
                if (mediaPlayer != null && progressSlider.isValueChanging()) {
                    Duration total = mediaPlayer.getTotalDuration();
                    if (total != null) {
                        Duration seekTime = total.multiply(val.doubleValue());
                        mediaPlayer.seek(seekTime);
                        currentTimeLabel.setText(formatTime(seekTime));
                    }
                }
            });

            mediaPlayer.statusProperty().addListener((obs, old, status) -> {
                Platform.runLater(() -> {
                    if (mediaPlayer == null) return;
                    if (status == MediaPlayer.Status.PLAYING) {
                        playPauseButton.setText("⏸");
                    } else {
                        playPauseButton.setText("▶");
                    }
                });
            });

            mediaPlayer.setOnEndOfMedia(() -> {
                Platform.runLater(() -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.stop();
                        progressSlider.setValue(0);
                        currentTimeLabel.setText("00:00");
                        playPauseButton.setText("▶");
                    }
                });
            });

            mediaPlayer.setOnError(() -> {
                System.err.println("JavaFX media error, falling back to external player.");
                Platform.runLater(() -> fallbackToExternalPlayer(file));
            });

            mediaPlayer.play();

        } catch (MediaException e) {
            System.err.println("MediaException, falling back to external player: " + e.getMessage());
            fallbackToExternalPlayer(file);
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to load media: " + e.getMessage());
        }
    }

    private void fallbackToExternalPlayer(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
                showAlert("Info", "Playing with external player because JavaFX could not decode the file.");
            } else {
                showAlert("Error", "Cannot play file: no default player found.");
            }
        } catch (IOException e) {
            showAlert("Error", "Could not open external player: " + e.getMessage());
        }
    }

    private String getFileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) return "";
        return fileName.substring(dot + 1).toLowerCase();
    }

    @FXML private void onPlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.play();
        }
    }

    @FXML private void onRewind() { skip(-10); }
    @FXML private void onForward() { skip(10); }

    private void skip(double seconds) {
        if (mediaPlayer == null) return;
        Duration current = mediaPlayer.getCurrentTime();
        Duration newTime = current.add(Duration.seconds(seconds));
        if (newTime.lessThan(Duration.ZERO)) newTime = Duration.ZERO;
        if (newTime.greaterThan(mediaPlayer.getTotalDuration())) newTime = mediaPlayer.getTotalDuration();
        mediaPlayer.seek(newTime);
    }

    private String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown()) return "00:00";
        int minutes = (int) duration.toMinutes();
        int seconds = (int) duration.toSeconds() % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void showAlert(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void disposeAndDelete(Path tempFile) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        mediaView.setMediaPlayer(null);
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            System.err.println("Failed to delete temp file: " + tempFile);
        }
    }
}