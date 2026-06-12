package com.filesync.server.websocket.service;

import java.util.Set;
import java.util.UUID;

public interface ActiveUserService {
    void userJoined(UUID folderId, String username);
    void userLeft(UUID folderId, String username);
    Set<String> getActiveUsers(UUID folderId);
}