package com.filesync.server.domain;

import com.filesync.common.enums.Permission;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "shared_folder_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"folder_id", "user_id"})
})
public class SharedFolderMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "folder_id")
    private UUID folderId;

    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    private Permission permission;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getFolderId() { return folderId; }
    public void setFolderId(UUID folderId) { this.folderId = folderId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
}