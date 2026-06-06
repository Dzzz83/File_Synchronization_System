package com.filesync.client.task;

import com.filesync.client.http.SyncHttpClient;
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
        updateMessage("Downloading " + fileName + "...");
        httpClient.downloadFile(fileId, destination);
        updateMessage("Download complete");
        return null;
    }
}