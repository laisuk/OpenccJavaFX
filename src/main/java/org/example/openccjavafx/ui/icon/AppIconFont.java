package org.example.openccjavafx.ui.icon;

import javafx.scene.text.Font;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AppIconFont {
    private static final String FONT_RESOURCE = "/fonts/FluentAvalonia.ttf";
    private static final Map<Double, Font> CACHE = new ConcurrentHashMap<>();

    private AppIconFont() {
    }

    public static Font font(double size) {
        return CACHE.computeIfAbsent(size, AppIconFont::loadFontAtSize);
    }

    private static Font loadFontAtSize(double size) {
        try (InputStream is = AppIconFont.class.getResourceAsStream(FONT_RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException("Icon font not found: " + FONT_RESOURCE);
            }

            Font font = Font.loadFont(is, size);
            if (font == null) {
                throw new IllegalStateException("Failed to load icon font: " + FONT_RESOURCE);
            }

            return font;
        } catch (Exception ex) {
            throw new RuntimeException("Unable to load icon font.", ex);
        }
    }
}