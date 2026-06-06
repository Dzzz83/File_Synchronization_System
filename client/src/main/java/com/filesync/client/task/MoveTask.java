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
        for (int i = 0; i < total; i++) {
            if (isCancelled()) {
                updateMessage("Cancelled");
                return null;
            }
            updateProgress(i, total);
            updateMessage("Moving " + fileNames.get(i) + "...");
            httpClient.moveFile(fileIds.get(i), targetFolderId);
        }
        updateProgress(total, total);
        updateMessage("Move complete");
        return null;
    }
}