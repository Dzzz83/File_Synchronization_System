package com.filesync.client.util;

import com.filesync.client.files.ServerFileItem;
import java.util.List;
import java.util.Locale;

public final class FileTypeHelper {

    private FileTypeHelper() {}

    public static boolean isMediaFile(String fileName) {
        if (fileName == null) return false;
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) return false;
        String ext = fileName.substring(dot + 1).toLowerCase();
        return List.of("mp3", "wav", "mp4", "avi", "mov", "mkv").contains(ext);
    }

    public static boolean isImageFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    public static boolean isTextFile(ServerFileItem item) {
        if (item.isDirectory()) return false;
        String path = item.getRelativePath();
        return path != null && path.toLowerCase().endsWith(".txt");
    }

    public static boolean isPdf(ServerFileItem item) {
        return item.getRelativePath().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    public static boolean isDocx(ServerFileItem item) {
        return item.getRelativePath().toLowerCase(Locale.ROOT).endsWith(".docx");
    }

    public static boolean isPdfOrDocx(ServerFileItem item) {
        if (item.isDirectory()) return false;
        String name = item.getRelativePath();
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".docx");
    }
}