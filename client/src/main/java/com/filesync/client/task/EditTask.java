package com.filesync.client.task;

import com.filesync.client.http.SyncHttpClient;
import javafx.concurrent.Task;
import java.nio.file.Path;

public class EditTask extends Task<Path> {
    private final SyncHttpClient httpClient;
    private final String fileId;
    private final String fileName;

    public EditTask(SyncHttpClient httpClient, String fileId, String fileName) {
        this.httpClient = httpClient;
        this.fileId = fileId;
        this.fileName = fileName;
    }

    @Override
    protected Path call() throws Exception {
        updateMessage("Downloading " + fileName + " for editing...");
        Path tempFile = java.nio.file.Files.createTempFile("edit_", ".tmp");
        httpClient.downloadFile(fileId, tempFile);
        updateMessage("Download complete");
        return tempFile;
    }
}