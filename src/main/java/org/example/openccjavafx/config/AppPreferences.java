package org.example.openccjavafx.config;

import org.example.openccjavafx.OpenccJavaFxApplication;
import org.example.openccjavafx.i18n.UiLanguage;

import java.util.prefs.Preferences;

public final class AppPreferences {
    private static final String PREF_THEME = "theme";
    private static final String PREF_SHOW_LINE_NUMBER = "showLineNumber";
    private static final String PREF_CONVERT_FILENAME = "convertFilename";
    private static final String PREF_UI_LANG = "ui.language";

    private static final String THEME_SYSTEM = "system";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(OpenccJavaFxApplication.class);

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
}
