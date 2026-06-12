package com.filesync.client.controller.helper;

import com.filesync.client.files.ServerFileItem;
import com.filesync.client.model.DragData;
import com.filesync.client.model.FileTransferData;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.*;

import java.io.*;
import java.util.List;
import java.util.function.BiConsumer;

public class DragDropHandler {
    private final TableView<ServerFileItem> fileTable;
    private final BiConsumer<List<String>, String> moveCallback;

    public DragDropHandler(TableView<ServerFileItem> fileTable, BiConsumer<List<String>, String> moveCallback) {
        this.fileTable = fileTable;
        this.moveCallback = moveCallback;
    }

    public void setupDragAndDrop(TableRow<ServerFileItem> row) {
        row.setOnDragDetected(event -> onDragDetected(row, event));
        row.setOnDragOver(event -> onDragOver(row, event));
        row.setOnDragDropped(event -> onDragDropped(row, event));
    }

    private void onDragDetected(TableRow<ServerFileItem> row, MouseEvent event) {
        if (row.isEmpty()) return;
        ServerFileItem item = row.getItem();
        if (item == null) return;

        List<String> fileIds = fileTable.getSelectionModel().getSelectedItems().stream()
                .map(ServerFileItem::getFileId)
                .toList();

        if (fileIds.isEmpty()) return;

        Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
        ClipboardContent content = new ClipboardContent();

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            DragData dragData = new DragData(fileIds, List.of()); // file names optional here
            oos.writeObject(dragData);
            content.put(FileTransferData.DRAG_DATA, bos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        db.setContent(content);
        event.consume();
    }

    private void onDragOver(TableRow<ServerFileItem> row, DragEvent event) {
        if (!row.isEmpty() && row.getItem().isDirectory()) {
            Dragboard db = event.getDragboard();
            if (db.hasContent(FileTransferData.DRAG_DATA)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
        }
        event.consume();
    }

    private void onDragDropped(TableRow<ServerFileItem> row, DragEvent event) {
        Dragboard db = event.getDragboard();
        if (!db.hasContent(FileTransferData.DRAG_DATA)) {
            event.setDropCompleted(false);
            event.consume();
            return;
        }

        try {
            byte[] data = (byte[]) db.getContent(FileTransferData.DRAG_DATA);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                DragData dragData = (DragData) ois.readObject();
                List<String> fileIds = dragData.getFileIds();

                ServerFileItem target = row.getItem();
                String targetId = resolveDropTargetId(target);
                if (targetId != null && moveCallback != null) {
                    moveCallback.accept(fileIds, targetId);
                    event.setDropCompleted(true);
                } else {
                    event.setDropCompleted(false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            event.setDropCompleted(false);
        }
        event.consume();
    }

    private String resolveDropTargetId(ServerFileItem target) {
        if ("..".equals(target.getRelativePath())) {
            return ""; // moving to parent root (empty string indicates root)
        } else if (target.isDirectory()) {
            return target.getFileId();
        }
        return null;
    }
}