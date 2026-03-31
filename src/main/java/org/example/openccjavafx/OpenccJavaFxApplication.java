package org.example.openccjavafx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.example.openccjavafx.i18n.I18n;
import org.example.openccjavafx.i18n.UiLanguage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Objects;
import java.util.prefs.Preferences;

public class OpenccJavaFxApplication extends Application {
    private static final String THEME_DARK_CLASS = "dark";
    private static final String PREF_THEME = "theme";
    private static final String PREF_SHOW_LINE_NUMBER = "showLineNumber";
    private static final String PREF_CONVERT_FILENAME = "convertFilename";
    private static final String THEME_SYSTEM = "system";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";


    private static final Preferences PREFS =
            Preferences.userNodeForPackage(OpenccJavaFxApplication.class);

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

//    private static final Preferences PREFS =
//            Preferences.userNodeForPackage(MainController.class);

    private static final String PREF_UI_LANG = "ui.language";

    public static void saveLanguagePreference(UiLanguage lang) {
        PREFS.put(PREF_UI_LANG, lang.name());
    }

    public static UiLanguage loadLanguagePreference() {
        String value = PREFS.get(PREF_UI_LANG, UiLanguage.ENGLISH.name());
        try {
            return UiLanguage.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return UiLanguage.ENGLISH;
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
                OpenccJavaFxApplication.class.getResource("openccjavafx-view.fxml")
        );

        Parent root = fxmlLoader.load();
        Scene scene = new Scene(root, 1000, 750);

        scene.getStylesheets().add(
                Objects.requireNonNull(
                        OpenccJavaFxApplication.class.getResource("styles.css")
                ).toExternalForm()
        );

        Image icon = new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icon.png"))
        );
        stage.getIcons().add(icon);

        stage.setTitle(I18n.get("app.title"));

        applySavedOrSystemTheme(root);

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

    public static void applySavedOrSystemTheme(Parent root) {
        String theme = getSavedThemeMode();

        switch (theme) {
            case THEME_DARK:
                applyTheme(root, true);
                break;
            case THEME_LIGHT:
                applyTheme(root, false);
                break;
            default:
                applyTheme(root, isSystemDarkMode());
                break;
        }
    }

    public static void applyTheme(Parent root, boolean dark) {
        root.getStyleClass().remove(THEME_DARK_CLASS);
        if (dark) {
            root.getStyleClass().add(THEME_DARK_CLASS);
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

    public static boolean isEffectiveDarkMode() {
        String theme = getSavedThemeMode();
        switch (theme) {
            case THEME_DARK:
                return true;
            case THEME_LIGHT:
                return false;
            default:
                return isSystemDarkMode();
        }
    }

    static boolean isSystemDarkMode() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        try {
            if (os.contains("win")) {
                return isWindowsDarkMode();
            } else if (os.contains("mac")) {
                return isMacDarkMode();
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                return isLinuxDarkMode();
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private static boolean isWindowsDarkMode() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme"
        ).start();

        String output = readProcessOutput(process);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            return false;
        }

        return output.contains("0x0");
    }

    private static boolean isMacDarkMode() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "defaults", "read", "-g", "AppleInterfaceStyle"
        ).start();

        String output = readProcessOutput(process);
        int exitCode = process.waitFor();

        return exitCode == 0 && output.toLowerCase(Locale.ROOT).contains("dark");
    }

    private static boolean isLinuxDarkMode() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "gsettings", "get", "org.gnome.desktop.interface", "color-scheme"
        ).start();

        String output = readProcessOutput(process);
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            return output.toLowerCase(Locale.ROOT).contains("dark");
        }

        Process process2 = new ProcessBuilder(
                "gsettings", "get", "org.gnome.desktop.interface", "gtk-theme"
        ).start();

        String output2 = readProcessOutput(process2);
        int exitCode2 = process2.waitFor();

        return exitCode2 == 0 && output2.toLowerCase(Locale.ROOT).contains("dark");
    }

    private static String readProcessOutput(Process process) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream())
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }

        return sb.toString();
    }
}