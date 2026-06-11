package com.filesync.common.dto;

public class ResetPasswordRequestDto {
    private String email;
    private String token;
    private String newPassword;

    public ResetPasswordRequestDto() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}