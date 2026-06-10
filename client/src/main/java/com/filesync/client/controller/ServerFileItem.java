package com.filesync.client.controller;

import java.time.Instant;
import java.util.UUID;

import com.filesync.common.enums.Permission;
import javafx.scene.Node;

public class ServerFileItem {
    private final String fileId;
    private final String relativePath;
    private final long size;
    private final Instant lastModified;
    private final String sha256Hash;
    private final UUID folderId;
    private final boolean isDirectory;
    private final UUID parentId;
    private final Node icon;
    private final Permission userPermission;

    public ServerFileItem(String fileId, String relativePath, long size, Instant lastModified, String sha256Hash,
                          UUID folderId, boolean isDirectory, UUID parentId, Node icon, Permission userPermission) {
        this.fileId = fileId;
        this.relativePath = relativePath;
        this.size = size;
        this.lastModified = lastModified;
        this.sha256Hash = sha256Hash;
        this.folderId = folderId;
        this.isDirectory = isDirectory;
        this.parentId = parentId;
        this.icon = icon;
        this.userPermission = userPermission;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public String getFileId() { return fileId; }
    public String getRelativePath() { return relativePath; }
    public long getSize() { return size; }
    public Instant getLastModified() { return lastModified; }
    public String getSha256Hash() { return sha256Hash; }
    public boolean isDirectory() { return isDirectory; }
    public Node getIcon() { return icon; }
    public Permission getUserPermission() { return userPermission; }
}