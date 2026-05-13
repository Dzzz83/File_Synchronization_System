package com.filesync.client;

public class SessionManager
{
    private static SessionManager sessionManager;
    private String authToken;
    private String currentUsername;

    private SessionManager() {}

    public static SessionManager getInstance()
    {
        if (sessionManager == null)
        {
            sessionManager = new SessionManager();
        }
        return sessionManager;
    }

    public void login(String authToken, String currentUsername)
    {
        this.authToken = authToken;
        this.currentUsername = currentUsername;
    }

    public void logout()
    {
        this.authToken = null;
        this.currentUsername = null;
    }

    public String getAuthToken()
    {
        return authToken;
    }

    public String getCurrentUsername()
    {
        return currentUsername;
    }

    public boolean isLoggedIn()
    {
        return authToken != null && !authToken.isEmpty();
    }
}
