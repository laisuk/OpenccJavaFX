package org.example.openccjavafx.ui.icon;

public enum AppIconGlyph {

    // General
    REFRESH("\uE149"),
    PLAY("\uE768"),
    PLAY_ATL("\uE102"),
    HOME("\uE10F"),
    INFO("\uE946"),

    // Clipboard
    COPY("\uE16F"),
    PASTE("\uE16D"),
    DELETE("\uE107"),

    // File / Folder
    OPEN_FILE("\uE1A5"),
    OPEN_FOLDER_HORIZONTAL("\uED25"),
    FOLDER_OPEN("\uE838"),
    SAVE("\uE105"),
    SAVE_AS("\uE792"),
    EDIT("\uE70F"),
    PREVIEW("\uE7B3"),

    // State
    CHECKBOX_COMPOSITE("\uE73A"),
    COMPLETED("\uE930"),

    // Collection ops
    ADD_TO("\uECC8"),
    REMOVE_FROM("\uECC9");

    private final String glyph;

    AppIconGlyph(String glyph) {
        this.glyph = glyph;
    }

    public String glyph() {
        return glyph;
    }

    @Override
    public String toString() {
        return glyph;
    }
}
