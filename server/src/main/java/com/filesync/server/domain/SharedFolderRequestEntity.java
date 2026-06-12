package com.filesync.server.domain;

import com.filesync.common.enums.Permission;
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
    private String status;
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_permission", nullable = false)
    private Permission requestedPermission;

    public SharedFolderRequestEntity() {
        this.id = UUID.randomUUID();
        this.requestedAt = Instant.now();
        this.status = "PENDING";
        this.requestedPermission = Permission.READ; // default for existing rows
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

    public Permission getRequestedPermission() { return requestedPermission; }
    public void setRequestedPermission(Permission requestedPermission) { this.requestedPermission = requestedPermission; }
}