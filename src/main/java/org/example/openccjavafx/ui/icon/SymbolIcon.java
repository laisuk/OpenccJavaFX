package org.example.openccjavafx.ui.icon;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;

public class SymbolIcon extends Label {
    private static final double DEFAULT_SIZE = 16;

    private final ObjectProperty<AppIconGlyph> icon = new SimpleObjectProperty<>();
    private final DoubleProperty iconSize = new SimpleDoubleProperty(DEFAULT_SIZE);

    public SymbolIcon() {
        initialize();
    }

    public SymbolIcon(AppIconGlyph glyph) {
        this(glyph, DEFAULT_SIZE);
    }

    public SymbolIcon(AppIconGlyph glyph, double size) {
        initialize();
        setIcon(glyph);
        setIconSize(size);
    }

    private void initialize() {
        getStyleClass().add("app-icon");

        icon.addListener((obs, oldVal, newVal) -> updateGlyph());
        iconSize.addListener((obs, oldVal, newVal) -> updateFont());

        updateGlyph();
        updateFont();
    }

    private void updateGlyph() {
        AppIconGlyph glyph = getIcon();
        setText(glyph == null ? "" : glyph.glyph());
    }

    private void updateFont() {
        setFont(AppIconFont.font(getIconSize()));
    }

    public AppIconGlyph getIcon() {
        return icon.get();
    }

    public void setIcon(AppIconGlyph value) {
        icon.set(value);
    }

    public ObjectProperty<AppIconGlyph> iconProperty() {
        return icon;
    }

    public double getIconSize() {
        return iconSize.get();
    }

    public void setIconSize(double value) {
        iconSize.set(value);
    }

    public DoubleProperty iconSizeProperty() {
        return iconSize;
    }
}