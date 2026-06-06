package com.filesync.common.dto;

import java.util.UUID;

public class CreateFolderRequest {
    private String name;
    private UUID parentId;
    private UUID folderId;

    // getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public UUID getFolderId() { return folderId; }
    public void setFolderId(UUID folderId) { this.folderId = folderId; }
}