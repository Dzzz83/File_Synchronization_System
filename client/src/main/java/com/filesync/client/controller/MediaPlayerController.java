package com.filesync.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import java.io.File;

public class MediaPlayerController {
    @FXML private MediaView mediaView;
    @FXML private Button playPauseButton;
    @FXML private Label titleLabel;

    private MediaPlayer mediaPlayer;
    private File tempMediaFile;

    public void setMediaFile(File file, String fileName) {
        this.tempMediaFile = file;
        titleLabel.setText(fileName);

        // Khởi tạo Media và MediaPlayer
        Media media = new Media(file.toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        mediaView.setMediaPlayer(mediaPlayer);

        // Tự động phát khi tải xong
        mediaPlayer.setAutoPlay(true);
        playPauseButton.setText("Pause");

        // Khi chạy hết video/audio thì đổi text nút bấm
        mediaPlayer.setOnEndOfMedia(() -> {
            mediaPlayer.stop();
            playPauseButton.setText("Play");
        });
    }

    @FXML
    private void togglePlayPause() {
        if (mediaPlayer != null) {
            if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause();
                playPauseButton.setText("Play");
            } else {
                mediaPlayer.play();
                playPauseButton.setText("Pause");
            }
        }
    }

    // Hàm này sẽ được gọi khi đóng cửa sổ
    public void stopAndCleanup() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose(); // Giải phóng bộ nhớ
        }
        if (tempMediaFile != null && tempMediaFile.exists()) {
            tempMediaFile.delete(); // Xóa file tạm
        }
    }
}