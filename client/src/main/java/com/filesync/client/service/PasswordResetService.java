package com.filesync.client.service;

import com.filesync.client.http.SyncHttpClient;

public class PasswordResetService {
    private final SyncHttpClient httpClient;

    public PasswordResetService(String serverUrl) {
        this.httpClient = new SyncHttpClient(serverUrl);
    }

    public void requestResetCode(String email) {
        httpClient.forgotPassword(email);
    }

    public boolean resetPassword(String token, String newPassword) {
        return httpClient.resetPassword(token, newPassword);
    }

    public void close() {
        httpClient.close();
    }
}