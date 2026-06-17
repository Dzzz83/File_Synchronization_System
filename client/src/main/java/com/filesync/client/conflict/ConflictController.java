package com.filesync.client.conflict;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.function.Consumer;

public class ConflictController {

    private static final Logger log = LoggerFactory.getLogger(ConflictController.class);

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

        // Guard against missing FXML elements
        if (titleLabel != null) {
            titleLabel.setText("Conflict: " + fileName);
        } else {
            log.warn("titleLabel is null – please check conflict-view.fxml for fx:id=\"titleLabel\"");
        }

        mergedArea.setText(localContent);

        DiffMatchPatch diffMatchPatch = new DiffMatchPatch();
        LinkedList<DiffMatchPatch.Diff> diffs = diffMatchPatch.diffMain(serverContent, localContent);
        diffMatchPatch.diffCleanupSemantic(diffs);

        if (leftTextFlow != null && rightTextFlow != null) {
            buildTextFlow(leftTextFlow, diffs, true);
            buildTextFlow(rightTextFlow, diffs, false);
        } else {
            log.warn("leftTextFlow or rightTextFlow is null – diff views will not be shown");
        }
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
        if (useLeftButton != null) {
            useLeftButton.setOnAction(e -> {
                if (onSaveCallback != null) onSaveCallback.accept(leftText);
            });
        }
        if (useRightButton != null) {
            useRightButton.setOnAction(e -> {
                if (onSaveCallback != null) onSaveCallback.accept(rightText);
            });
        }
        if (saveButton != null) {
            saveButton.setOnAction(e -> {
                if (onSaveCallback != null) onSaveCallback.accept(mergedArea.getText());
            });
        }
    }
}