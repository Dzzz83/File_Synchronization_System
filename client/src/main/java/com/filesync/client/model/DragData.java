package com.filesync.client.model;

import java.io.Serializable;
import java.util.List;

public class DragData implements Serializable {
    private final List<String> fileIds;
    private final List<String> fileNames;

    public DragData(List<String> fileIds, List<String> fileNames) {
        this.fileIds = fileIds;
        this.fileNames = fileNames;
    }

    public List<String> getFileIds() { return fileIds; }
    public List<String> getFileNames() { return fileNames; }
}