package org.example.openccjavafx.ui.icon;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.DoubleProperty;
import javafx.scene.control.Label;

public class SymbolIcon extends Label {
    private final ObjectProperty<AppIconGlyph> icon = new SimpleObjectProperty<>();
    private final DoubleProperty iconSize = new SimpleDoubleProperty(16);

    public SymbolIcon() {
        initialize();
    }

    public SymbolIcon(AppIconGlyph glyph, double size) {
        initialize();
        setIcon(glyph);
        setIconSize(size);
    }

    private void initialize() {
        getStyleClass().add("app-icon");

        icon.addListener((obs, oldVal, newVal) -> refresh());
        iconSize.addListener((obs, oldVal, newVal) -> refresh());

        refresh();
    }

    private void refresh() {
        AppIconGlyph glyph = getIcon();
        setText(glyph == null ? "" : glyph.glyph());
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