package com.filesync.server.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.filesync.common.enums.SyncStatus;

@Entity
@Table(name = "file_metadata")
public class FileMetadataEntity {
    @Id
    private String id;
    private String relativePath;
    private String sha256Hash;
    private long size;
    private Instant lastModified;

    @Column(columnDefinition = "TEXT")
    private String versionVectorJson;
    private String ownerId;

    @ElementCollection
    @CollectionTable(name = "file_shared_with", joinColumns = @JoinColumn(name = "file_id"))
    @Column(name = "user_id")
    private Set<String> sharedWith = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private SyncStatus status;

    // constructor
    public FileMetadataEntity(){}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public String getVersionVectorJson() {
        return versionVectorJson;
    }

    public void setVersionVectorJson(String versionVectorJson) {
        this.versionVectorJson = versionVectorJson;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Set<String> getSharedWith() {
        return sharedWith;
    }

    public void setSharedWith(Set<String> sharedWith) {
        this.sharedWith = sharedWith;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public void setStatus(SyncStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FileMetadataEntity that = (FileMetadataEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public String toString() {
        return "FileMetadataEntity{" +
                "id='" + id + '\'' +
                ", relativePath='" + relativePath + '\'' +
                ", sha256Hash='" + sha256Hash + '\'' +
                ", size=" + size +
                ", lastModified=" + lastModified +
                ", versionVectorJson='" + versionVectorJson + '\'' +
                ", ownerId='" + ownerId + '\'' +
                ", sharedWith='" + sharedWith + '\'' +
                ", status=" + status +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
