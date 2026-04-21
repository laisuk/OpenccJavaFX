package org.example.openccjavafx;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import org.example.openccjavafx.i18n.I18n;

public final class ConfigItem {
    private final String code;
    private final String labelKey;
    private final ReadOnlyStringWrapper label = new ReadOnlyStringWrapper();

    public ConfigItem(String code, String labelKey) {
        this.code = code;
        this.labelKey = labelKey;
//        refreshLabel();
    }

    public String getCode() {
        return code;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public String getLabel() {
        return label.get();
    }

    public ReadOnlyStringProperty labelProperty() {
        return label.getReadOnlyProperty();
    }

    public void refreshLabel() {
        label.set(I18n.get(labelKey));
    }
}