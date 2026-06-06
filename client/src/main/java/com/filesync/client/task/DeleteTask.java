package com.filesync.client.task;

import com.filesync.client.service.FileOperationService;
import javafx.concurrent.Task;
import java.util.List;

public class DeleteTask extends Task<Void> {
    private final FileOperationService fileService;
    private final List<String> fileIds;
    private final List<String> fileNames;

    public DeleteTask(FileOperationService fileService, List<String> fileIds, List<String> fileNames) {
        this.fileService = fileService;
        this.fileIds = fileIds;
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
            updateMessage("Deleting " + fileNames.get(i) + "...");
            fileService.deleteFile(fileIds.get(i));
        }
        updateProgress(total, total);
        updateMessage("Delete complete");
        return null;
    }
}