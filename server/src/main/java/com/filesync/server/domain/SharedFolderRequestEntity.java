package com.filesync.server.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shared_folder_requests")
public class SharedFolderRequestEntity {
    @Id
    private UUID id;
    private UUID folderId;
    private String requesterId;
    private String status;  // PENDING, APPROVED, REJECTED
    private Instant requestedAt;

    public SharedFolderRequestEntity() {
        this.id = UUID.randomUUID();
        this.requestedAt = Instant.now();
        this.status = "PENDING";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getFolderId() { return folderId; }
    public void setFolderId(UUID folderId) { this.folderId = folderId; }

    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String requesterId) { this.requesterId = requesterId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
}