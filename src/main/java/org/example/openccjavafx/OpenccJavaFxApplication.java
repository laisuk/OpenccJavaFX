package org.example.openccjavafx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Objects;

public class OpenccJavaFxApplication extends Application {
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

        stage.setTitle("OpenccJavaFX");

        applySystemTheme(root);

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

    private static void applySystemTheme(Parent root) {
        root.getStyleClass().remove("dark");
        if (isSystemDarkMode()) {
            root.getStyleClass().add("dark");
        }
    }

    private static boolean isSystemDarkMode() {
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

        // AppsUseLightTheme = 0  -> dark
        // AppsUseLightTheme = 1  -> light
        return output.contains("0x0");
    }

    private static boolean isMacDarkMode() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "defaults", "read", "-g", "AppleInterfaceStyle"
        ).start();

        String output = readProcessOutput(process);
        int exitCode = process.waitFor();

        // when dark mode is enabled, output is usually "Dark"
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

        // fallback for older GNOME setups
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