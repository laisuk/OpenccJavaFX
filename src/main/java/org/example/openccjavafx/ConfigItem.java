package org.example.openccjavafx;

import org.example.openccjavafx.i18n.I18n;

public final class ConfigItem {
    private final String code;
    private final String labelKey;

    public ConfigItem(String code, String labelKey) {
        this.code = code;
        this.labelKey = labelKey;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return I18n.get(labelKey);
    }

    @Override
    public String toString() {
        return getLabel();
    }
}
