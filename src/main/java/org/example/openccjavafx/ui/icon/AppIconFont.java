package org.example.openccjavafx.ui.icon;

import javafx.scene.text.Font;

import java.io.InputStream;
import java.util.Objects;

public final class AppIconFont {
    private static final String FONT_RESOURCE = "/fonts/FluentAvalonia.ttf";
    private static final double DEFAULT_SIZE = 16;

    private static String familyName;
    private static boolean loaded;

    private AppIconFont() {
    }

    public static void load() {
        if (loaded) {
            return;
        }

        try (InputStream is = AppIconFont.class.getResourceAsStream(FONT_RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException("Icon font not found: " + FONT_RESOURCE);
            }

            Font font = Font.loadFont(is, DEFAULT_SIZE);
            if (font == null) {
                throw new IllegalStateException("Failed to load icon font: " + FONT_RESOURCE);
            }

            familyName = font.getFamily();
            loaded = true;
        } catch (Exception ex) {
            throw new RuntimeException("Unable to load icon font.", ex);
        }
    }

    public static Font font(double size) {
        ensureLoaded();
        return Font.font(familyName, size);
    }

    public static String family() {
        ensureLoaded();
        return familyName;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
        }
        Objects.requireNonNull(familyName, "Icon font family not initialized.");
    }
}