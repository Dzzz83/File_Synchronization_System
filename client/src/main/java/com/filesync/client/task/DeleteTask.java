package com.filesync.client.task;

import com.filesync.client.service.FileOperationService;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

public class DeleteTask extends Task<Void> {
    private static final Logger log = LoggerFactory.getLogger(DeleteTask.class);
    private final FileOperationService fileService;
    private final List<String> fileIds;
    private final List<String> fileNames;
    private final String ownerId;
    private final UUID folderId;

    public DeleteTask(FileOperationService fileService, List<String> fileIds,
                      List<String> fileNames, String ownerId, UUID folderId) {
        this.fileService = fileService;
        this.fileIds = fileIds;
        this.fileNames = fileNames;
        this.ownerId = ownerId;
        this.folderId = folderId;
    }

    @Override
    protected Void call() throws Exception {
        int total = fileIds.size();
        updateMessage("Deleting " + total + " item(s)...");
        updateProgress(0, total);

        for (int i = 0; i < total; i++) {
            if (isCancelled()) {
                updateMessage("Delete cancelled");
                break;
            }
            String fileId = fileIds.get(i);
            String fileName = (i < fileNames.size()) ? fileNames.get(i) : fileId;
            updateMessage("Deleting " + fileName + "...");

            // 1. Delete from server
            log.info("🗑️ Deleting file from server: {} (ID: {})", fileName, fileId);
            fileService.deleteFile(fileId);

            // 2. Delete local file
            Path localPath = getLocalPath(fileName);
            try {
                boolean deleted = Files.deleteIfExists(localPath);
                if (deleted) {
                    log.info("🗑️ Deleted local file: {}", localPath);
                } else {
                    log.warn("Local file not found (already deleted): {}", localPath);
                }
            } catch (Exception e) {
                log.warn("Failed to delete local file: {}", localPath, e);
            }

            updateProgress(i + 1, total);
        }

        updateMessage("Delete complete");
        log.info("✅ Server and local delete complete for {} items", total);
        return null;
    }

    private Path getLocalPath(String relativePath) {
        String basePath = System.getProperty("user.home") + "/FileSync";
        String folderName = (folderId != null) ? "shared_" + folderId.toString() : "personal_" + ownerId;
        return Paths.get(basePath, ownerId, folderName, relativePath);
    }
}