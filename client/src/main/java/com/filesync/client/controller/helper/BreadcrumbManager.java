package com.filesync.client.controller.helper;

import javafx.scene.control.Label;

import java.util.Stack;
import java.util.UUID;

public class BreadcrumbManager {
    private final Label pathLabel;
    private final Stack<UUID> pathStack = new Stack<>();
    private final Stack<String> pathNameStack = new Stack<>();
    private String rootDisplayName;
    private String currentFolderName = null;
    private UUID currentParentId = null;
    private Runnable onExitSharedFolder;

    public BreadcrumbManager(Label pathLabel, String rootDisplayName) {
        this.pathLabel = pathLabel;
        this.rootDisplayName = rootDisplayName;
        updateDisplay();
    }

    public void setCurrentParentId(UUID parentId) {
        this.currentParentId = parentId;
    }

    public void setOnExitSharedFolder(Runnable callback) {
        this.onExitSharedFolder = callback;
    }

    public UUID getCurrentParentId() {
        return currentParentId;
    }

    public Stack<UUID> getPathStack() {
        return pathStack;
    }

    public Stack<String> getPathNameStack() {
        return pathNameStack;
    }

    public void navigateInto(UUID folderId, String folderName) {
        if (currentFolderName != null) {
            pathNameStack.push(currentFolderName);
        }
        pathStack.push(currentParentId);
        currentParentId = folderId;
        currentFolderName = folderName;
        updateDisplay();
    }

    public void navigateUp() {
        if (!pathStack.isEmpty()) {
            currentParentId = pathStack.pop();
            if (!pathNameStack.isEmpty()) {
                currentFolderName = pathNameStack.pop();
            } else {
                currentFolderName = null;
            }
            updateDisplay();
        }
    }

    public void exitSharedFolder() {
        if (onExitSharedFolder != null) {
            pathNameStack.clear();
            currentFolderName = null;
            updateDisplay();
            onExitSharedFolder.run();
        }
    }

    public void reset() {
        pathStack.clear();
        pathNameStack.clear();
        currentFolderName = null;
        currentParentId = null;
        updateDisplay();
    }

    private void updateDisplay() {
        StringBuilder sb = new StringBuilder(rootDisplayName);
        for (String name : pathNameStack) {
            sb.append(" / ").append(name);
        }
        if (currentFolderName != null && !currentFolderName.isEmpty()) {
            sb.append(" / ").append(currentFolderName);
        }
        pathLabel.setText(sb.toString());
    }

    public boolean canGoUp() {
        return !pathStack.isEmpty();
    }
}