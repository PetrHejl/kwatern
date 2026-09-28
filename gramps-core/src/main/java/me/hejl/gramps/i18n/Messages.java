package me.hejl.gramps.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Translated texts loaded from UTF-8 properties files on the classpath.
 *
 * <p>For base name {@code a/b/messages} and locale {@code cs_CZ} it reads {@code a/b/messages.properties},
 * then {@code messages_cs.properties}, then {@code messages_cs_CZ.properties}, each overriding the previous.
 * Supporting a language means adding a file; missing keys fall back to the base file.
 *
 * <p>Values are {@link MessageFormat} patterns, so a literal apostrophe is written as two.
 */
public final class Messages {

    private final Locale locale;
    private final Map<String, String> values;

    private Messages(Locale locale, Map<String, String> values) {
        this.locale = locale;
        this.values = values;
    }

    public static Messages load(String baseName, Locale locale) {
        List<String> names = new ArrayList<>();
        names.add(baseName);
        if (!locale.getLanguage().isEmpty()) {
            names.add(baseName + "_" + locale.getLanguage());
            if (!locale.getCountry().isEmpty()) {
                names.add(baseName + "_" + locale.getLanguage() + "_" + locale.getCountry());
            }
        }
        Map<String, String> values = new HashMap<>();
        for (String name : names) {
            try (InputStream in = Messages.class.getClassLoader().getResourceAsStream(name + ".properties")) {
                if (in == null) {
                    continue;
                }
                var properties = new Properties();
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                properties.forEach((key, value) -> values.put((String) key, (String) value));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + name + ".properties", e);
            }
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("No messages found for " + baseName);
        }
        return new Messages(locale, Map.copyOf(values));
    }

    public Locale locale() {
        return locale;
    }

    /** The raw value, or {@code null} if no file defines the key. */
    public String find(String key) {
        return values.get(key);
    }

    /** The raw value, or the key itself if it is missing so the gap is visible. */
    public String get(String key) {
        return values.getOrDefault(key, key);
    }

    /** Formats the pattern for {@code key}. Pass numbers as strings to avoid digit grouping. */
    public String format(String key, Object... args) {
        return new MessageFormat(get(key), locale).format(args);
    }
}
