package org.example.openccjavafx.config;

import org.example.openccjavafx.i18n.UiLanguage;

import java.util.prefs.Preferences;

public final class AppPreferences {
    private static final String PREF_THEME = "theme";
    private static final String PREF_SHOW_LINE_NUMBER = "showLineNumber";
    private static final String PREF_CONVERT_FILENAME = "convertFilename";
    private static final String PREF_UI_LANG = "ui.language";
    public static final String KEY_EDITOR_FONT_FAMILY = "editor.font.family";
    public static final String KEY_EDITOR_FONT_SIZE = "editor.font.size";

    private static final String DEFAULT_EDITOR_FONT_FAMILY = "Arial";
    private static final int DEFAULT_EDITOR_FONT_SIZE = 16;

    private static final String THEME_SYSTEM = "system";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(AppPreferences.class);

    private AppPreferences() {
    }

    public static void saveShowLineNumber(boolean show) {
        PREFS.putBoolean(PREF_SHOW_LINE_NUMBER, show);
    }

    public static boolean getShowLineNumber() {
        return PREFS.getBoolean(PREF_SHOW_LINE_NUMBER, true);
    }

    public static void saveConvertFilename(boolean enabled) {
        PREFS.putBoolean(PREF_CONVERT_FILENAME, enabled);
    }

    public static boolean getConvertFilename() {
        return PREFS.getBoolean(PREF_CONVERT_FILENAME, false); // default = false (safer)
    }

    public static void saveLanguagePreference(UiLanguage lang) {
        PREFS.put(PREF_UI_LANG, lang.name());
    }

    public static UiLanguage loadLanguagePreference() {
        String value = PREFS.get(PREF_UI_LANG, UiLanguage.ZH_HANS.name());
        try {
            return UiLanguage.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return UiLanguage.ENGLISH;
        }
    }

    public static void saveThemeModeSystem() {
        PREFS.put(PREF_THEME, THEME_SYSTEM);
    }

    public static void saveThemeModeDark() {
        PREFS.put(PREF_THEME, THEME_DARK);
    }

    public static void saveThemeModeLight() {
        PREFS.put(PREF_THEME, THEME_LIGHT);
    }

    public static String getSavedThemeMode() {
        return PREFS.get(PREF_THEME, THEME_SYSTEM);
    }

    public static String getEditorFontFamily() {
        String fontFamily = PREFS.get(KEY_EDITOR_FONT_FAMILY, DEFAULT_EDITOR_FONT_FAMILY);
        if (fontFamily == null) {
            return DEFAULT_EDITOR_FONT_FAMILY;
        }

        String trimmed = fontFamily.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_EDITOR_FONT_FAMILY;
        }

        return trimmed;
    }

    public static void setEditorFontFamily(String fontFamily) {
        if (fontFamily == null) {
            return;
        }

        String trimmed = fontFamily.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        PREFS.put(KEY_EDITOR_FONT_FAMILY, trimmed);
    }

    public static int getEditorFontSize() {
        int fontSize = PREFS.getInt(KEY_EDITOR_FONT_SIZE, DEFAULT_EDITOR_FONT_SIZE);
        return fontSize > 0 ? fontSize : DEFAULT_EDITOR_FONT_SIZE;
    }

    public static void setEditorFontSize(int fontSize) {
        if (fontSize <= 0) {
            return;
        }

        PREFS.putInt(KEY_EDITOR_FONT_SIZE, fontSize);
    }
}
