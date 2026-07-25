package org.example.openccjavafx.text;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable result of conservative dialog-quote validation. */
public record DialogQuoteValidationResult(List<DialogQuoteIssue> suspiciousLines) {
    public DialogQuoteValidationResult {
        suspiciousLines = List.copyOf(Objects.requireNonNull(suspiciousLines, "suspiciousLines"));
    }

    public boolean isValid() {
        return suspiciousLines.isEmpty();
    }

    public Optional<DialogQuoteIssue> firstIssue() {
        return suspiciousLines.stream().findFirst();
    }

    public String buildSummary() {
        if (isValid()) {
            return "No suspicious dialog quote issues found.";
        }

        return "Found " + suspiciousLines.size() + " suspicious dialog quote line(s).\n\n"
                + "Hint:\n"
                + "The actual typo is often a missing, extra, reversed, or mixed dialog quote.\n"
                + "It may appear on the reported line or a few lines above it.\n"
                + "Fix the source text and validate again.";
    }
}
