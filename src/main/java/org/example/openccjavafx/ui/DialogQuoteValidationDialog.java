package org.example.openccjavafx.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.example.openccjavafx.OpenccJavaFxApplication;
import org.example.openccjavafx.i18n.I18n;
import org.example.openccjavafx.text.DialogQuoteIssue;
import org.example.openccjavafx.text.DialogQuoteValidationResult;
import org.example.openccjavafx.theme.ThemeManager;

import java.util.List;
import java.util.Objects;

/** Theme-aware presenter for structured dialog-quote validation results. */
public final class DialogQuoteValidationDialog {
    private static final int MAX_VISIBLE_ISSUES = 5;

    private DialogQuoteValidationDialog() {
    }

    public static ValidationDialogAction show(Window owner, DialogQuoteValidationResult result) {
        Objects.requireNonNull(result, "result");

        Dialog<ValidationDialogAction> dialog = new Dialog<>();
        dialog.setTitle(result.isValid()
                ? I18n.get("dialog.dialogQuote.title")
                : I18n.get("dialog.dialogQuote.warningTitle"));
        if (owner != null) {
            dialog.initOwner(owner);
        }

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().addAll("app-root", "dialog-quote-validation");
        pane.getStylesheets().add(Objects.requireNonNull(
                OpenccJavaFxApplication.class.getResource("styles.css")).toExternalForm());
        ThemeManager.applyTheme(pane, ThemeManager.isEffectiveDarkMode());
        pane.setPrefWidth(680);
        pane.setMinWidth(560);
        pane.setMaxWidth(760);

        ButtonType closeType = new ButtonType(
                I18n.get("dialog.dialogQuote.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType goToType = new ButtonType(
                I18n.get("dialog.dialogQuote.goToFirstSuspiciousLine"),
                ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().add(closeType);
        if (!result.isValid()) {
            pane.getButtonTypes().add(0, goToType);
        }

        Label summary = new Label(buildLocalizedSummary(result));
        summary.getStyleClass().add("dialog-quote-summary");
        summary.setWrapText(true);
        summary.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(12, summary);
        content.setFillWidth(true);
        List<DialogQuoteIssue> visibleIssues = result.suspiciousLines().stream()
                .limit(MAX_VISIBLE_ISSUES)
                .toList();
        if (!visibleIssues.isEmpty()) {
            ListView<DialogQuoteIssue> issueList = new ListView<>(
                    FXCollections.observableArrayList(visibleIssues));
            issueList.getStyleClass().add("dialog-quote-issue-list");
            issueList.setFocusTraversable(false);
            issueList.setSelectionModel(null);
            issueList.setFixedCellSize(44);
            issueList.setPrefHeight(visibleIssues.size() * 44 + 2);
            issueList.setMinHeight(issueList.getPrefHeight());
            issueList.setMaxHeight(issueList.getPrefHeight());
            issueList.setCellFactory(ignored -> new IssueCell());
            content.getChildren().add(issueList);

            int remaining = result.suspiciousLines().size() - visibleIssues.size();
            if (remaining > 0) {
                Label more = new Label(I18n.format(
                        "dialog.dialogQuote.more", remaining));
                more.getStyleClass().add("dialog-quote-more");
                content.getChildren().add(more);
            }
        }
        pane.setContent(content);

        dialog.setResultConverter(buttonType ->
                buttonType == goToType
                        ? ValidationDialogAction.GO_TO_FIRST_ISSUE
                        : ValidationDialogAction.CLOSE);

        if (!result.isValid()) {
            Button goToButton = (Button) pane.lookupButton(goToType);
            goToButton.getStyleClass().add("dialog-quote-primary");
        }
        return dialog.showAndWait().orElse(ValidationDialogAction.CLOSE);
    }

    private static String buildLocalizedSummary(DialogQuoteValidationResult result) {
        if (result.isValid()) {
            return I18n.get("dialog.dialogQuote.noIssues");
        }

        return I18n.format("dialog.dialogQuote.foundLines", result.suspiciousLines().size())
                + "\n\n"
                + I18n.get("dialog.dialogQuote.hintTitle") + "\n"
                + I18n.get("dialog.dialogQuote.hintCause") + "\n"
                + I18n.get("dialog.dialogQuote.hintLocation") + "\n"
                + I18n.get("dialog.dialogQuote.hintFixAgain");
    }

    private static final class IssueCell extends ListCell<DialogQuoteIssue> {
        @Override
        protected void updateItem(DialogQuoteIssue issue, boolean empty) {
            super.updateItem(issue, empty);
            getStyleClass().remove("dialog-quote-issue-cell");
            setText(null);
            setGraphic(null);
            setTooltip(null);
            if (empty || issue == null) {
                return;
            }

            getStyleClass().add("dialog-quote-issue-cell");
            Label line = new Label(I18n.format(
                    "dialog.dialogQuote.line", issue.lineNumber()));
            line.getStyleClass().add("dialog-quote-issue-line");
            line.setMinWidth(70);
            line.setPrefWidth(70);

            Label text = new Label(issue.text());
            text.getStyleClass().add("dialog-quote-issue-text");
            text.setTextOverrun(OverrunStyle.ELLIPSIS);
            text.setMinWidth(0);
            text.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);

            HBox row = new HBox(10, line, text);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(0, 6, 0, 4));
            row.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(row, Priority.ALWAYS);
            setGraphic(row);
            setTooltip(new Tooltip(issue.text()));
        }
    }
}
