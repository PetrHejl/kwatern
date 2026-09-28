package me.hejl.gramps.i18n;

import com.ibm.icu.text.NumberFormat;
import java.util.Locale;

/**
 * Numbers formatted for a language, with ICU's locale data. The JDK's own formats would work on the JVM, but a
 * native image includes JDK locale data only for the default locale.
 */
public final class Numbers {

    private Numbers() {}

    /** A number with exactly {@code digits} decimal places, such as "39,0483" in Czech. */
    public static String decimal(double value, int digits, Locale locale) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(digits);
        format.setMaximumFractionDigits(digits);
        return format.format(value);
    }
}
