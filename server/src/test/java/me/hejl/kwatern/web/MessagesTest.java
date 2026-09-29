package me.hejl.kwatern.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.hejl.gramps.i18n.Messages;
import org.junit.jupiter.api.Test;

/** The page texts of every language: complete, without stray keys, and valid patterns. */
class MessagesTest {

    private static final List<String> LANGUAGES = List.of("cs", "de", "sk");

    // Gramps types have no English entries (the Gramps name is shown), so only the translations list them.
    private static final Pattern GRAMPS_TYPE =
            Pattern.compile("(event|role|family|child|name|attribute|placetype|medium)\\.[a-z0-9_]+");

    // Language-neutral texts a translation may leave to the base file.
    private static final Set<String> NEUTRAL = Set.of("lifespan.both");

    private static Properties load(String suffix) throws Exception {
        String name = "me/hejl/kwatern/messages" + suffix + ".properties";
        try (InputStream in = MessagesTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name);
            var properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        }
    }

    private static boolean isGrampsType(String key, Properties base) {
        return !base.containsKey(key) && GRAMPS_TYPE.matcher(key).matches();
    }

    @Test
    void everyLanguageHasEveryText() throws Exception {
        Properties base = load("");
        Set<String> types = null;
        for (String language : LANGUAGES) {
            Properties texts = load("_" + language);
            Set<String> missing = new TreeSet<>(base.stringPropertyNames());
            missing.removeAll(texts.stringPropertyNames());
            missing.removeAll(NEUTRAL);
            assertTrue(missing.isEmpty(), language + " misses " + missing);

            // Every key is a base text, a gendered variant of one, or a Gramps type.
            for (String key : texts.stringPropertyNames()) {
                String plain = key.replaceFirst("\\.(female|male|unknown|other)$", "");
                assertTrue(
                        base.containsKey(key) || base.containsKey(plain) || isGrampsType(key, base),
                        language + ": unknown key " + key);
            }

            Set<String> languageTypes = texts.stringPropertyNames().stream()
                    .filter(key -> isGrampsType(key, base))
                    .collect(Collectors.toCollection(TreeSet::new));
            if (types == null) {
                types = languageTypes;
            } else {
                assertEquals(types, languageTypes, language + " translates other Gramps types");
            }
        }
    }

    @Test
    void everyTextIsAValidPattern() throws Exception {
        List<String> all = new ArrayList<>(LANGUAGES);
        all.add("en");
        for (String language : all) {
            Properties texts = load(language.equals("en") ? "" : "_" + language);
            var messages = Messages.load("me/hejl/kwatern/messages", Locale.of(language));
            for (String key : texts.stringPropertyNames()) {
                // Formatting reads the pattern, which fails if it is not a valid one.
                assertDoesNotThrow(() -> messages.format(key), language + " " + key);
            }
        }
    }
}
