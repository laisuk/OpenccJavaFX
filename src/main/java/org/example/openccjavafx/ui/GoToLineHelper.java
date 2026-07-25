package org.example.openccjavafx.ui;

/**
 * Pure validation and 1-based to 0-based conversion for Go to Line.
 */
public final class GoToLineHelper {
    private GoToLineHelper() {
    }

    public record Result(boolean valid, int paragraphIndex, String message) {

        private static Result valid(int paragraphIndex) {
                return new Result(true, paragraphIndex, null);
            }

            private static Result invalid(String message) {
                return new Result(false, -1, message);
            }
        }

    public static Result validate(String input, int paragraphCount) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            return Result.invalid("Enter a line number");
        }
        if (value.matches("-[0-9]+") || value.equals("0")) {
            return Result.invalid("Line number must be positive");
        }
        if (!value.matches("[0-9]+")) {
            return Result.invalid("Invalid line number");
        }

        final int lineNumber;
        try {
            lineNumber = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return Result.invalid("Invalid line number");
        }
        if (lineNumber <= 0) {
            return Result.invalid("Line number must be positive");
        }

        int maximum = Math.max(1, paragraphCount);
        if (lineNumber > maximum) {
            return Result.invalid("Line must be between 1 and " + maximum);
        }
        return Result.valid(lineNumber - 1);
    }
}
