package org.example.openccjavafx;

import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility to show a temporary status-bar message when hovering over UI controls.
 */
public final class StatusHoverHelper {

    private StatusHoverHelper() {
    }

    /**
     * Binds a hover hint message to a Node, temporarily overriding the status label.
     *
     * @param target    UI control to attach (Button, Label, ImageView, etc.)
     * @param statusBar Label that shows the current status message
     * @param hintText  Hover text to display
     */
    public static void bind(Node target, Label statusBar, String hintText) {
        AtomicReference<String> savedStatus = new AtomicReference<>("");
        AtomicBoolean hintActive = new AtomicBoolean(false);

        PauseTransition delay = new PauseTransition(Duration.millis(250));

        delay.setOnFinished(e -> {
            // 1) If mouse already left, do nothing
            if (!target.isHover()) {
                return;
            }

            // 2) Only show hint if status hasn't changed since hover started
            //    (prevents overwriting "Converting...", "Extracting...", etc.)
            if (!statusBar.getText().equals(savedStatus.get())) {
                return;
            }

            statusBar.setText(hintText);
            hintActive.set(true);
        });

        target.setOnMouseEntered(event -> {
            savedStatus.set(statusBar.getText());
            hintActive.set(false);
            delay.playFromStart();
        });

        target.setOnMouseExited(event -> {
            delay.stop();

            // Only restore if:
            // - we actually showed the hint
            // - the status bar is still showing that hint
            if (hintActive.get() && statusBar.getText().equals(hintText)) {
                statusBar.setText(savedStatus.get());
            }

            hintActive.set(false);
        });
    }
}
