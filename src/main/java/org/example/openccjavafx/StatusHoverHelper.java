package org.example.openccjavafx;

import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Utility to show a temporary status-bar message when hovering over UI controls.
 * <p>
 * All bound controls share one hover session so delayed hints from older controls
 * cannot overwrite the currently active one.
 */
public final class StatusHoverHelper {

    private static final Duration SHOW_DELAY = Duration.millis(250);
    private static final Duration HIDE_DELAY = Duration.millis(300);

    private static Node currentTarget;
    private static Node activeHintOwner;
    private static Label currentStatusBar;

    private static String savedStatus = "";
    private static String shownHint = "";

    private static final PauseTransition showDelay = new PauseTransition(SHOW_DELAY);
    private static final PauseTransition hideDelay = new PauseTransition(HIDE_DELAY);

    private static Supplier<String> pendingHintSupplier;

    static {
        showDelay.setOnFinished(e -> {
            if (currentTarget == null || currentStatusBar == null || pendingHintSupplier == null) {
                return;
            }

            if (!currentTarget.isHover()) {
                return;
            }

            String hintText = pendingHintSupplier.get();
            if (hintText == null) {
                hintText = "";
            }

            currentStatusBar.setText(hintText);
            shownHint = hintText;
            activeHintOwner = currentTarget;
        });

        hideDelay.setOnFinished(e -> {
            if (currentStatusBar == null) {
                return;
            }

            // Only restore if no replacement hover has already taken over.
            if (activeHintOwner != null && !activeHintOwner.isHover()
                    && currentStatusBar.getText().equals(shownHint)) {
                currentStatusBar.setText(savedStatus);
            }

            activeHintOwner = null;
            shownHint = "";
        });
    }

    private StatusHoverHelper() {
    }

    public static void bind(Node target, Label statusBar, Supplier<String> hintSupplier) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(statusBar, "statusBar must not be null");
        Objects.requireNonNull(hintSupplier, "hintSupplier must not be null");

        target.setOnMouseEntered(event -> {
            hideDelay.stop();
            showDelay.stop();

            currentTarget = target;
            currentStatusBar = statusBar;
            pendingHintSupplier = hintSupplier;

            // Save current text only when there is no active hover hint shown.
            if (activeHintOwner == null || !statusBar.getText().equals(shownHint)) {
                savedStatus = statusBar.getText();
            }

            showDelay.playFromStart();
        });

        target.setOnMouseExited(event -> {
            showDelay.stop();

            // Only schedule hide if this target owns the currently shown hint.
            if (activeHintOwner == target) {
                currentTarget = target;
                currentStatusBar = statusBar;
                hideDelay.playFromStart();
            } else if (currentTarget == target) {
                // Left before hint was shown: invalidate pending target.
                currentTarget = null;
                pendingHintSupplier = null;
            }
        });
    }

    public static void bind(Node target, Label statusBar, String hintText) {
        bind(target, statusBar, () -> hintText);
    }
}