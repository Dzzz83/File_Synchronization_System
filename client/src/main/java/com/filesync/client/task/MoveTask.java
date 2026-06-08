package com.filesync.client.task;

import com.filesync.client.http.SyncHttpClient;
import javafx.concurrent.Task;

import java.util.List;

public class MoveTask extends Task<Void> {
    private final SyncHttpClient httpClient;
    private final List<String> fileIds;
    private final String targetFolderId;
    private final List<String> fileNames;

    public MoveTask(SyncHttpClient httpClient, List<String> fileIds, String targetFolderId, List<String> fileNames) {
        this.httpClient = httpClient;
        this.fileIds = fileIds;
        this.targetFolderId = targetFolderId;
        this.fileNames = fileNames;
    }

    @Override
    protected Void call() throws Exception {
        int total = fileIds.size();
        updateMessage("Moving " + total + " item(s)...");
        updateProgress(0, total);

        for (int i = 0; i < total; i++) {
            if (isCancelled()) {
                updateMessage("Move cancelled");
                break;
            }
            String fileId = fileIds.get(i);
            String fileName = (i < fileNames.size()) ? fileNames.get(i) : fileId;
            updateMessage("Moving " + fileName + "...");
            httpClient.moveFile(fileId, targetFolderId);
            updateProgress(i + 1, total);
        }

        updateMessage("Move complete");
        return null;
    }
}