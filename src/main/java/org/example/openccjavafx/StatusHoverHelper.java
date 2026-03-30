package org.example.openccjavafx;

import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Utility to show a temporary status-bar message when hovering over UI controls.
 */
public final class StatusHoverHelper {

    private StatusHoverHelper() {
    }

    /**
     * Binds a hover hint message to a Node, temporarily overriding the status label.
     *
     * @param target       UI control to attach (Button, Label, ImageView, etc.)
     * @param statusBar    Label that shows the current status message
     * @param hintSupplier Supplies the hover text to display at hover time
     */
    public static void bind(Node target, Label statusBar, Supplier<String> hintSupplier) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(statusBar, "statusBar must not be null");
        Objects.requireNonNull(hintSupplier, "hintSupplier must not be null");

        AtomicReference<String> savedStatus = new AtomicReference<>("");
        AtomicReference<String> shownHint = new AtomicReference<>("");
        AtomicBoolean hintActive = new AtomicBoolean(false);

        PauseTransition delay = new PauseTransition(Duration.millis(250));

        delay.setOnFinished(e -> {
            if (!target.isHover()) {
                return;
            }

            if (!statusBar.getText().equals(savedStatus.get())) {
                return;
            }

            String hintText = hintSupplier.get();
            if (hintText == null) {
                hintText = "";
            }

            statusBar.setText(hintText);
            shownHint.set(hintText);
            hintActive.set(true);
        });

        target.setOnMouseEntered(event -> {
            savedStatus.set(statusBar.getText());
            shownHint.set("");
            hintActive.set(false);
            delay.playFromStart();
        });

        target.setOnMouseExited(event -> {
            delay.stop();

            if (hintActive.get() && statusBar.getText().equals(shownHint.get())) {
                statusBar.setText(savedStatus.get());
            }

            hintActive.set(false);
            shownHint.set("");
        });
    }

    /**
     * Convenience overload for static text.
     */
    public static void bind(Node target, Label statusBar, String hintText) {
        bind(target, statusBar, () -> hintText);
    }
}
