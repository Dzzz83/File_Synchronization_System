package com.filesync.client.icon;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class FileIconResolver {

    // Default icon size in pixels
    private static final int ICON_SIZE = 24;

    public static Node getIconForFile(String fileName) {
        String ext = getExtension(fileName).toLowerCase();
        String iconName = switch (ext) {
            case "mp4", "avi", "mov", "mkv", "webm" -> "video.png";
            case "mp3", "wav", "flac" -> "audio.png";
            case "doc", "docx" -> "doc.png";
            case "pdf" -> "pdf.png";
            case "txt" -> "text.png";
            case "jpg", "jpeg", "png", "gif" -> "image.png";
            default -> "file.png";
        };
        return loadIcon(iconName);
    }

    private static Node loadIcon(String filename) {
        try {
            String resourcePath = "/com/filesync/client/icons/" + filename;
            Image image = new Image(FileIconResolver.class.getResourceAsStream(resourcePath));
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(ICON_SIZE);
            imageView.setFitHeight(ICON_SIZE);
            imageView.setPreserveRatio(true);
            return imageView;
        } catch (Exception e) {
            // Fallback: if image not found, show a simple text fallback
            javafx.scene.control.Label fallback = new javafx.scene.control.Label("📄");
            fallback.setStyle("-fx-font-size: " + ICON_SIZE + "px;");
            return fallback;
        }
    }

    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot == -1) ? "" : fileName.substring(dot + 1);
    }
}