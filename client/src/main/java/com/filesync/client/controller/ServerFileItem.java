package com.filesync.client.controller;

import java.time.Instant;
import java.util.UUID;

public class ServerFileItem {
    private final String fileId;
    private final String relativePath;
    private final long size;
    private final Instant lastModified;
    private final String sha256Hash;
    private final UUID folderId;

    public ServerFileItem(String fileId, String relativePath, long size, Instant lastModified, String sha256Hash,
                          UUID folderId) {
        this.fileId = fileId;
        this.relativePath = relativePath;
        this.size = size;
        this.lastModified = lastModified;
        this.sha256Hash = sha256Hash;
        this.folderId = folderId;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public String getFileId() { return fileId; }
    public String getRelativePath() { return relativePath; }
    public long getSize() { return size; }
    public Instant getLastModified() { return lastModified; }
    public String getSha256Hash() { return sha256Hash; }
}