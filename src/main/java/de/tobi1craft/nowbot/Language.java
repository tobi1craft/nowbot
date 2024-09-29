package de.tobi1craft.nowbot;

import java.util.Locale;
import java.util.ResourceBundle;

public class Language {

    private static ResourceBundle messages;

    public static void initLanguages() {
        Locale.setDefault(Locale.of("en", "US"));
        Locale currentLocale = Locale.getDefault();

        messages = ResourceBundle.getBundle("translation", currentLocale);
    }

    public static String get(String key) {
        return messages.getString(key);
    }
}
