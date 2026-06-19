package com.filesync.server.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "total_storage_bytes", nullable = false)
    private Long totalStorageBytes = 0L;

    @Column(name = "file_count", nullable = false)
    private Integer fileCount = 0;

    @Column(name = "max_storage_bytes", nullable = false)
    private Long maxStorageBytes = 1073741824L; // 100 MB default

    @Column(name = "max_file_count", nullable = false)
    private Integer maxFileCount = 500;

    @Column(name = "is_demo", nullable = false)
    private Boolean isDemo = false;

    @Column(name = "is_admin", nullable = false)
    private Boolean isAdmin = false;

    private String resetToken;
    private LocalDateTime tokenExpiry;

    // Collection of roles for stateless JWT
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<String> roles = new HashSet<>();

    public User() {
        // Default role for all users
        this.roles.add("USER");
    }

    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.roles = new HashSet<>();
        this.roles.add("USER");
    }

    // Standard getters/setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }
    public LocalDateTime getTokenExpiry() { return tokenExpiry; }
    public void setTokenExpiry(LocalDateTime tokenExpiry) { this.tokenExpiry = tokenExpiry; }

    // Quota getters/setters
    public Long getTotalStorageBytes() { return totalStorageBytes; }
    public void setTotalStorageBytes(Long totalStorageBytes) { this.totalStorageBytes = totalStorageBytes; }
    public Integer getFileCount() { return fileCount; }
    public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }
    public Long getMaxStorageBytes() { return maxStorageBytes; }
    public void setMaxStorageBytes(Long maxStorageBytes) { this.maxStorageBytes = maxStorageBytes; }
    public Integer getMaxFileCount() { return maxFileCount; }
    public void setMaxFileCount(Integer maxFileCount) { this.maxFileCount = maxFileCount; }
    public Boolean getIsDemo() { return isDemo; }
    public void setIsDemo(Boolean isDemo) { this.isDemo = isDemo; }

    // Roles management
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }

    // Sync isAdmin with roles
    public Boolean getIsAdmin() {
        return roles.contains("ADMIN");
    }

    public void setIsAdmin(Boolean isAdmin) {
        if (isAdmin) {
            roles.add("ADMIN");
        } else {
            roles.remove("ADMIN");
        }
        this.isAdmin = isAdmin;
    }

    // Helper method to add a role
    public void addRole(String role) {
        roles.add(role);
    }

    // Helper method to remove a role
    public void removeRole(String role) {
        roles.remove(role);
    }
}