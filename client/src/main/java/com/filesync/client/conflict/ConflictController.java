package com.filesync.client.conflict;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;

import java.util.LinkedList;
import java.util.function.Consumer;

public class ConflictController {

    @FXML private Label titleLabel;
    @FXML private TextFlow leftTextFlow;
    @FXML private TextFlow rightTextFlow;
    @FXML private TextArea mergedArea;
    @FXML private Button useLeftButton;
    @FXML private Button useRightButton;
    @FXML private Button saveButton;

    private Consumer<String> onSaveCallback;
    private String leftText;
    private String rightText;

    public void setData(String fileName, String serverContent, String localContent,
                        Consumer<String> onSave) {
        this.onSaveCallback = onSave;
        this.leftText = serverContent;
        this.rightText = localContent;

        titleLabel.setText("Conflict: " + fileName);
        mergedArea.setText(localContent);

        // compute character‑level diff using DiffMatchPatch
        DiffMatchPatch diffMatchPatch = new DiffMatchPatch();
        LinkedList<DiffMatchPatch.Diff> diffs = diffMatchPatch.diffMain(serverContent, localContent);
        diffMatchPatch.diffCleanupSemantic(diffs);

        // build left side (show only deletions and equal text)
        buildTextFlow(leftTextFlow, diffs, true);
        // build right side (show only insertions and equal text)
        buildTextFlow(rightTextFlow, diffs, false);
    }

    private void buildTextFlow(TextFlow flow, LinkedList<DiffMatchPatch.Diff> diffs, boolean leftSide) {
        flow.getChildren().clear();
        for (DiffMatchPatch.Diff diff : diffs) {
            String text = diff.text;
            if (leftSide && diff.operation == DiffMatchPatch.Operation.INSERT) continue;
            if (!leftSide && diff.operation == DiffMatchPatch.Operation.DELETE) continue;

            Text node = new Text(text);
            if (diff.operation == DiffMatchPatch.Operation.DELETE) {
                node.setStyle("-fx-fill: red; -fx-strikethrough: true;");
            } else if (diff.operation == DiffMatchPatch.Operation.INSERT) {
                node.setStyle("-fx-fill: green;");
            } else {
                node.setStyle("-fx-fill: black;");
            }
            flow.getChildren().add(node);
        }
    }

    @FXML
    public void initialize() {
        useLeftButton.setOnAction(e -> onSaveCallback.accept(leftText));
        useRightButton.setOnAction(e -> onSaveCallback.accept(rightText));
        saveButton.setOnAction(e -> onSaveCallback.accept(mergedArea.getText()));
    }
}