package org.example.openccjavafx.theme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

public final class SystemThemeDetector {
    private SystemThemeDetector() {
    }

    public static boolean isSystemDarkMode() {
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
