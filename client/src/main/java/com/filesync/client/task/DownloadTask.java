package com.filesync.client.task;

import com.filesync.client.http.SyncHttpClient;
import com.filesync.client.service.ProgressService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import java.nio.file.Path;

public class DownloadTask extends Task<Void> {
    private final SyncHttpClient httpClient;
    private final String fileId;
    private final Path destination;
    private final String fileName;

    public DownloadTask(SyncHttpClient httpClient, String fileId, Path destination, String fileName) {
        this.httpClient = httpClient;
        this.fileId = fileId;
        this.destination = destination;
        this.fileName = fileName;
    }

    @Override
    protected Void call() throws Exception {
        ProgressService ps = ProgressService.getInstance();
        Platform.runLater(() -> ps.startOperation("Downloading " + fileName + "..."));

        try {
            updateMessage("Downloading " + fileName + "...");
            updateProgress(0, 1); // Will be updated by callback

            httpClient.downloadFile(fileId, destination, (bytesDownloaded, totalBytes) -> {
                updateProgress(bytesDownloaded, totalBytes);
                Platform.runLater(() -> {
                    ps.updateProgress(bytesDownloaded, totalBytes);
                    ps.updateMessage(String.format("Downloading %s: %d / %d KB",
                            fileName, bytesDownloaded / 1024, totalBytes / 1024));
                });
            });

            updateMessage("Download complete");
            updateProgress(1, 1);
            return null;
        } finally {
            Platform.runLater(ps::finishOperation);
        }
    }
}