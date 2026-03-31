package org.example.openccjavafx.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class I18n {
    private static final String BUNDLE_BASE = "i18n.messages";
    private static Locale locale = Locale.ENGLISH;
    private static ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE, locale);

    private I18n() {
    }

    public static void setLocale(Locale newLocale) {
        locale = newLocale;
        bundle = ResourceBundle.getBundle(BUNDLE_BASE, locale);
    }

    public static Locale getLocale() {
        return locale;
    }

    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException ex) {
            return "!" + key + "!";
        }
    }

    // ✅ NEW: format helper
    public static String format(String key, Object... args) {
        try {
            String pattern = bundle.getString(key);
            MessageFormat formatter = new MessageFormat(pattern, locale);
            return formatter.format(args);
        } catch (MissingResourceException ex) {
            return "!" + key + "!";
        }
    }
}