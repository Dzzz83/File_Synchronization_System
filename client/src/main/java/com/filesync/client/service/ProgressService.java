package com.filesync.client.service;

import javafx.beans.property.*;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyStringProperty;

public class ProgressService {
    private static final ProgressService instance = new ProgressService();

    private final StringProperty message = new SimpleStringProperty("");
    private final DoubleProperty progress = new SimpleDoubleProperty(-1.0);
    private final BooleanProperty visible = new SimpleBooleanProperty(false);

    private ProgressService() {}

    public static ProgressService getInstance() {
        return instance;
    }

    // Read-only properties for binding
    public ReadOnlyStringProperty messageProperty() {
        return message;
    }

    public ReadOnlyDoubleProperty progressProperty() {
        return progress;
    }

    public ReadOnlyBooleanProperty visibleProperty() {
        return visible;
    }

    // Public API for controllers/tasks
    public void startOperation(String operationMessage) {
        message.set(operationMessage);
        progress.set(0.0);
        visible.set(true);
    }

    public void updateProgress(double current, double total) {
        progress.set(current / total);
    }

    public void updateMessage(String msg) {
        message.set(msg);
    }

    public void finishOperation() {
        visible.set(false);
        message.set("");
        progress.set(-1.0);
    }

    // Convenience: busy = visible (used to disable buttons)
    public ReadOnlyBooleanProperty busyProperty() {
        return visible;
    }
}