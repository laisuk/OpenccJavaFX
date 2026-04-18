package org.example.openccjavafx.ui.icon;

import javafx.scene.control.Label;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;

public final class AppIcons {
    private AppIcons() {
    }

    public static Text text(AppIconGlyph glyph, double size) {
        Text text = new Text(glyph.glyph());
        text.setFont(AppIconFont.font(size));
        text.getStyleClass().add("app-icon");
        return text;
    }

    public static Text text(AppIconGlyph glyph, double size, Paint fill) {
        Text text = text(glyph, size);
        text.setFill(fill);
        return text;
    }

    public static Label label(AppIconGlyph glyph, double size) {
        Label label = new Label(glyph.glyph());
        label.setFont(AppIconFont.font(size));
        label.getStyleClass().add("app-icon");
        return label;
    }
}