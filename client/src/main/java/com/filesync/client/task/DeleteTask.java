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
            fileService.deleteFile(fileId);
            updateProgress(i + 1, total);
        }

        updateMessage("Delete complete");
        return null;
    }
}