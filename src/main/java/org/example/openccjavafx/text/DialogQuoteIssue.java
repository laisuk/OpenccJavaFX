package org.example.openccjavafx.text;

import java.util.Objects;

/** One suspicious logical line found by dialog-quote validation. */
public record DialogQuoteIssue(int lineNumber, String text) {
    public DialogQuoteIssue {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be positive");
        }
        text = Objects.requireNonNull(text, "text");
    }
}
