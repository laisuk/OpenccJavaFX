package org.example.openccjavafx.ui;

import javafx.scene.control.ListCell;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class EditorFontHelper {
    private EditorFontHelper() {
    }

    public static List<String> getAvailableEditorFonts() {
        Set<String> uniqueFonts = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<String> availableFontNames = Font.getFontNames();

        for (String fontName : availableFontNames) {
            if (fontName == null) {
                continue;
            }

            String trimmed = fontName.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.contains("wingdings") || lower.contains("symbol")) {
                continue;
            }

            uniqueFonts.add(trimmed);
        }

        return new ArrayList<>(uniqueFonts);
    }

    public static String resolveInitialEditorFontFamily(List<String> fonts, String savedFontFamily) {
        if (savedFontFamily != null && fonts.contains(savedFontFamily)) {
            return savedFontFamily;
        }

        if (fonts.contains("Arial")) {
            return "Arial";
        }

        return null;
    }

    public static ListCell<String> createFontListCell() {
        return new ListCell<String>() {
            @Override
            protected void updateItem(String font, boolean empty) {
                super.updateItem(font, empty);

                if (empty || font == null || font.trim().isEmpty()) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(font);
                setStyle("-fx-font-family: \"" + escapeCssFontFamily(font) + "\";");
            }
        };
    }

    public static String buildEditorFontStyle(String fontFamily, Integer fontSize, int defaultFontSize) {
        int size = fontSize != null ? fontSize : defaultFontSize;
        if (size <= 0) {
            size = defaultFontSize;
        }

        StringBuilder style = new StringBuilder();

        if (fontFamily != null && !fontFamily.trim().isEmpty()) {
            style.append("-fx-font-family: \"")
                    .append(escapeCssFontFamily(fontFamily))
                    .append("\";");
        }

        style.append("-fx-font-size: ")
                .append(size)
                .append("px;");

        return style.toString();
    }

    public static String escapeCssFontFamily(String fontFamily) {
        return fontFamily.replace("\"", "\\\"");
    }
}
