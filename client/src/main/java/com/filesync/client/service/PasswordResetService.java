package com.filesync.client.service;

import com.filesync.client.http.SyncHttpClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class PasswordResetService {
    private final SyncHttpClient httpClient;

    public PasswordResetService(String serverUrl) {
        this.httpClient = new SyncHttpClient(serverUrl);
    }

    public void requestResetCode(String email) throws Exception {
        try {
            httpClient.forgotPassword(email);
        } catch (WebClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            String errorMessage = extractErrorMessage(errorBody, e.getStatusCode().toString());
            throw new Exception(errorMessage);
        } catch (Exception e) {
            throw new Exception("Network error: " + e.getMessage());
        }
    }

    public boolean resetPassword(String email, String token, String newPassword) throws Exception {
        try {
            return httpClient.resetPassword(email, token, newPassword);
        } catch (WebClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            String errorMessage = extractErrorMessage(errorBody, e.getStatusCode().toString());
            throw new Exception(errorMessage);
        } catch (Exception e) {
            throw new Exception("Network error: " + e.getMessage());
        }
    }

    private String extractErrorMessage(String responseBody, String fallback) {
        if (responseBody == null) return fallback;
        // Try to extract "error" field from JSON
        try {
            int start = responseBody.indexOf("\"error\":\"");
            if (start != -1) {
                start += 9;
                int end = responseBody.indexOf("\"", start);
                if (end != -1) {
                    return responseBody.substring(start, end);
                }
            }
        } catch (Exception ignored) {}
        return responseBody.length() > 100 ? fallback : responseBody;
    }

    public void close() {
        httpClient.close();
    }
}