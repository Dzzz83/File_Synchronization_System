package com.filesync.client.controller;

import com.filesync.client.websocket.ChatClient;
import com.filesync.common.dto.ChatMessage;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

public class ChatController {

    @FXML private ListView<String> messageList;
    @FXML private ListView<String> activeUsersList;
    @FXML private TextField messageField;
    @FXML private Button sendButton;

    private ChatClient chatClient;
    private UUID folderId;
    private String currentUser;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public void setData(ChatClient chatClient, UUID folderId, String currentUser) {
        this.chatClient = chatClient;
        this.folderId = folderId;
        this.currentUser = currentUser;

        chatClient.connect(folderId, this::onMessageReceived, this::onActiveUsersUpdate);
    }

    private void onMessageReceived(ChatMessage msg) {
        Platform.runLater(() -> {
            String timeStr = "";
            if (msg.getTimestamp() != null) {
                LocalDateTime ldt = LocalDateTime.ofInstant(msg.getTimestamp(), ZoneId.systemDefault());
                timeStr = "[" + ldt.format(timeFormatter) + "] ";
            }
            String displayMsg = timeStr + msg.getSender() + ": " + msg.getContent();
            messageList.getItems().add(displayMsg);
            messageList.scrollTo(messageList.getItems().size() - 1);
        });
    }

    private void onActiveUsersUpdate(Set<?> users) {
        Platform.runLater(() -> {
            activeUsersList.getItems().clear();
            for (Object user : users) {
                activeUsersList.getItems().add(String.valueOf(user));
            }
        });
    }

    @FXML
    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        ChatMessage msg = new ChatMessage(currentUser, text, null, folderId);
        chatClient.sendMessage(msg);
        messageField.clear();
    }

    public void dispose() {
        if (chatClient != null) {
            chatClient.disconnect();
        }
    }
}