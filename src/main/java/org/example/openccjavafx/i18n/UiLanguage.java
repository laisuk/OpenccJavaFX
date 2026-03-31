package org.example.openccjavafx.i18n;

import java.util.Locale;

public enum UiLanguage {
    ENGLISH("lang.english", Locale.ENGLISH),
    ZH_HANS("lang.zhHans", Locale.SIMPLIFIED_CHINESE),
    ZH_HANT("lang.zhHant", Locale.TRADITIONAL_CHINESE);

    private final String labelKey;
    private final Locale locale;

    UiLanguage(String labelKey, Locale locale) {
        this.labelKey = labelKey;
        this.locale = locale;
    }

    public String getLabel() {
        return I18n.get(labelKey);
    }

    public Locale getLocale() {
        return locale;
    }

    @Override
    public String toString() {
        return getLabel();
    }
}