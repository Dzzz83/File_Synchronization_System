package com.filesync.common.dto;

import com.filesync.common.enums.SyncStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class FileMetadataDto {
    private String fileId;
    private String relativePath;
    private String sha256Hash;
    private long size;
    private Instant lastModified;
    private String versionVectorJson;
    private String ownerId;
    private Set<String> sharedWith;
    private SyncStatus status;
    private UUID folderId;
    private boolean isDirectory;
    private UUID parentId;

    public FileMetadataDto() {}

    public FileMetadataDto(String fileId, String relativePath, String sha256Hash, long size,
                           Instant lastModified, String versionVectorJson, String ownerId,
                           Set<String> sharedWith, SyncStatus status) {
        this.fileId = fileId;
        this.relativePath = relativePath;
        this.sha256Hash = sha256Hash;
        this.size = size;
        this.lastModified = lastModified;
        this.versionVectorJson = versionVectorJson;
        this.ownerId = ownerId;
        this.sharedWith = sharedWith;
        this.status = status;
    }

    // Getters and setters (generate in IntelliJ: Alt+Insert -> Getter and Setter)
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public Instant getLastModified() { return lastModified; }
    public void setLastModified(Instant lastModified) { this.lastModified = lastModified; }
    public String getVersionVectorJson() { return versionVectorJson; }
    public void setVersionVectorJson(String versionVectorJson) { this.versionVectorJson = versionVectorJson; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public Set<String> getSharedWith() { return sharedWith; }
    public void setSharedWith(Set<String> sharedWith) { this.sharedWith = sharedWith; }
    public SyncStatus getStatus() { return status; }
    public void setStatus(SyncStatus status) { this.status = status; }
    public UUID getFolderId() { return folderId; }
    public void setFolderId(UUID folderId) { this.folderId = folderId; }
    public boolean isDirectory() { return isDirectory; }
    public void setDirectory(boolean directory) { isDirectory = directory; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
}