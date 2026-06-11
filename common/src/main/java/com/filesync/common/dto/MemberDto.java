package com.filesync.common.dto;

import com.filesync.common.enums.Permission;

public class MemberDto {
    private String userId;
    private Permission permission;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
}