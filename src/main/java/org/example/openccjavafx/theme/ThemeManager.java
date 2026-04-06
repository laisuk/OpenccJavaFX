package org.example.openccjavafx.theme;

import javafx.scene.Parent;
import org.example.openccjavafx.config.AppPreferences;

public final class ThemeManager {
    private static final String THEME_DARK_CLASS = "dark";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";

    private ThemeManager() {
    }

    public static void applySavedOrSystemTheme(Parent root) {
        String theme = AppPreferences.getSavedThemeMode();

        switch (theme) {
            case THEME_DARK:
                applyTheme(root, true);
                break;
            case THEME_LIGHT:
                applyTheme(root, false);
                break;
            default:
                applyTheme(root, SystemThemeDetector.isSystemDarkMode());
                break;
        }
    }

    public static void applyTheme(Parent root, boolean dark) {
        root.getStyleClass().remove(THEME_DARK_CLASS);
        if (dark) {
            root.getStyleClass().add(THEME_DARK_CLASS);
        }
    }

    public static boolean isEffectiveDarkMode() {
        String theme = AppPreferences.getSavedThemeMode();
        switch (theme) {
            case THEME_DARK:
                return true;
            case THEME_LIGHT:
                return false;
            default:
                return SystemThemeDetector.isSystemDarkMode();
        }
    }
}
