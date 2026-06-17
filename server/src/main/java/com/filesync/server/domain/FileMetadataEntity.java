package com.filesync.server.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.filesync.common.enums.SyncStatus;

@Entity
@Table(name = "file_metadata",
        indexes = {
                @Index(name = "idx_file_metadata_folder_id", columnList = "folder_id"),
                @Index(name = "idx_file_metadata_parent_id", columnList = "parent_id")
        })
public class FileMetadataEntity {
    @Id
    private String id;

    private String relativePath;

    private String sha256Hash;

    private long size;

    private Instant lastModified;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @ElementCollection(fetch = FetchType.EAGER)   // back to EAGER to avoid lazy init error
    @CollectionTable(name = "file_shared_with", joinColumns = @JoinColumn(name = "file_id"))
    @Column(name = "user_id")
    private Set<String> sharedWith = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private SyncStatus status;

    @Column(name = "folder_id")
    private UUID folderId;   // null = personal file

    @Column(name = "is_directory", nullable = false)
    private boolean isDirectory;

    @Column(name = "parent_id")
    private UUID parentId;

    // Constructors
    public FileMetadataEntity() {}

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public Instant getLastModified() { return lastModified; }
    public void setLastModified(Instant lastModified) { this.lastModified = lastModified; }

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

    // equals, hashCode, toString
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FileMetadataEntity that = (FileMetadataEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FileMetadataEntity{" +
                "id='" + id + '\'' +
                ", relativePath='" + relativePath + '\'' +
                ", sha256Hash='" + sha256Hash + '\'' +
                ", size=" + size +
                ", lastModified=" + lastModified +
                ", ownerId='" + ownerId + '\'' +
                ", status=" + status +
                ", isDirectory=" + isDirectory +
                '}';
    }
}