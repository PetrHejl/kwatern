package me.hejl.gramps.i18n;

import com.ibm.icu.util.ULocale;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The languages ICU has locale data for, such as dates and the alphabet: about 250. */
public final class Languages {

    private static final Set<String> KNOWN = Stream.of(ULocale.getAvailableLocales())
            .map(ULocale::getLanguage)
            .filter(language -> !language.isEmpty())
            .collect(Collectors.toUnmodifiableSet());

    private Languages() {}

    /** Whether ICU has locale data for a language, given as its primary language subtag such as {@code cs}. */
    public static boolean known(String language) {
        return KNOWN.contains(language);
    }

    /**
     * The name of a language in another, e.g. {@code el} as "řečtina" in Czech; empty for no language. From ICU's
     * data, as a native image includes the JDK's only for the default locale.
     *
     * @param tag a language tag such as {@code el} or {@code pt-BR}, also with {@code _}
     */
    public static String name(String tag, Locale in) {
        if (tag == null || tag.isBlank()) {
            return "";
        }
        return ULocale.forLanguageTag(tag.strip().replace('_', '-')).getDisplayLanguage(ULocale.forLocale(in));
    }

    /** How many languages ICU has locale data for; none if its data is missing, as it may be in a native image. */
    public static int count() {
        return KNOWN.size();
    }
}
