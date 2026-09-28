package me.hejl.gramps.name;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import me.hejl.gramps.i18n.Messages;
import me.hejl.gramps.model.Name;
import me.hejl.gramps.model.NameFormat;
import me.hejl.gramps.model.Surname;

/**
 * Formats names with Gramps name formats (gen/display/name.py).
 *
 * <p>A format is a string of keywords such as {@code "Surname, Given Suffix"} or codes such as
 * {@code "%l, %f %s"}. A keyword in capitals gives the value in capitals. Punctuation next to a field
 * ({@code ", "}, parentheses, quotes) is dropped when the field is empty, so a name without a given name
 * shows as "Novák" rather than "Novák, ". The built-in formats and any custom ones from the export are
 * available by number. Thread-safe.
 */
public final class NameFormatter {

    /** Gramps' built-in format numbers (gen/lib/name.py). Custom formats have negative numbers. */
    public static final int DEFAULT = 0;

    public static final int SURNAME_GIVEN = 1;
    public static final int GIVEN_SURNAME = 2;
    public static final int PATRONYMIC_GIVEN = 3;
    public static final int GIVEN = 4;
    public static final int MAIN_SURNAMES_GIVEN = 5;

    private static final String PATRONYMIC = "Patronymic";
    private static final String MATRONYMIC = "Matronymic";

    /** Field codes with their keywords, longest keywords first so "primary[pre]" wins over "primary". */
    private static final List<Field> FIELDS = List.of(
            new Field("0m", "primary[pre]", n -> primary(n, s -> nonNull(s.prefix()))),
            new Field("1m", "primary[sur]", n -> primary(n, s -> nonNull(s.value()))),
            new Field("2m", "primary[con]", n -> primary(n, s -> nonNull(s.connector()))),
            new Field("0y", "patronymic[pre]", n -> patronymic(n, s -> nonNull(s.prefix()))),
            new Field("1y", "patronymic[sur]", n -> patronymic(n, s -> nonNull(s.value()))),
            new Field("2y", "patronymic[con]", n -> patronymic(n, s -> nonNull(s.connector()))),
            new Field("o", "notpatronymic", NameFormatter::notPatronymic),
            new Field(
                    "q",
                    "rawsurnames",
                    n -> joinWords(
                            n.surnames().stream().map(s -> nonNull(s.value())).toList())),
            new Field("g", "familynick", n -> nonNull(n.familyNick())),
            new Field("i", "initials", NameFormatter::initials),
            new Field("m", "primary", n -> primary(n, NameFormatter::surname)),
            new Field("y", "patronymic", n -> patronymic(n, NameFormatter::surname)),
            new Field("x", "common", NameFormatter::common),
            new Field("l", "surname", NameFormatter::fullSurname),
            new Field("f", "given", n -> nonNull(n.first())),
            new Field("s", "suffix", n -> nonNull(n.suffix())),
            new Field("t", "title", n -> nonNull(n.title())),
            new Field("c", "call", n -> nonNull(n.call())),
            new Field("n", "nickname", n -> nonNull(n.nick())),
            new Field("r", "rest", NameFormatter::rest),
            new Field(
                    "p",
                    "prefix",
                    n -> joinWords(
                            n.surnames().stream().map(s -> nonNull(s.prefix())).toList())));

    private static final Map<String, Field> BY_CODE = new HashMap<>();
    private static final Pattern FIELD_PATTERN;

    static {
        FIELDS.forEach(f -> {
            BY_CODE.put(f.code, f);
            BY_CODE.put(f.code.toUpperCase(Locale.ROOT), f);
        });
        String codes = String.join(
                "|",
                FIELDS.stream()
                        .flatMap(f -> Stream.of(f.code, f.code.toUpperCase(Locale.ROOT)))
                        .map(Pattern::quote)
                        .toList());
        // Gramps: NameDisplay._make_fn - the punctuation around a field goes with the field.
        FIELD_PATTERN = Pattern.compile(String.join(
                "|",
                ",\\W*\"%(" + codes + ")\"",
                ",\\W*\\(%(" + codes + ")\\)",
                ",\\W*%(" + codes + ")",
                "\"%(" + codes + ")\"",
                "_%(" + codes + ")_",
                "\\(%(" + codes + ")\\)",
                "%(" + codes + ")"));
    }

    private final Map<Integer, String> formats = new HashMap<>();
    private final int defaultFormat;
    private final Map<String, List<Part>> compiled = new ConcurrentHashMap<>();

    /**
     * @param customFormats formats stored in the export
     * @param defaultFormat format used for names set to {@link #DEFAULT}; normally {@link #SURNAME_GIVEN}
     */
    public NameFormatter(List<NameFormat> customFormats, int defaultFormat, Locale locale) {
        String comma = Messages.load("me/hejl/gramps/name/messages", locale).get("comma");
        formats.put(SURNAME_GIVEN, "%l" + comma + " %f %s");
        formats.put(GIVEN_SURNAME, "%f %l %s");
        formats.put(PATRONYMIC_GIVEN, "%y" + comma + " %s %f");
        formats.put(GIVEN, "%f");
        formats.put(MAIN_SURNAMES_GIVEN, "%1m %2m %o" + comma + " %f %1y %s %0m");
        for (NameFormat format : customFormats) {
            if (format.format() != null) {
                formats.put(format.number(), format.format());
            }
        }
        this.defaultFormat =
                formats.containsKey(defaultFormat) && defaultFormat != DEFAULT ? defaultFormat : SURNAME_GIVEN;
    }

    /** Formats with the name's own display format, or the default. */
    public String display(Name name) {
        return format(name, name == null ? DEFAULT : name.displayAs());
    }

    /** Formats with the name's own sort format, or the default; for lists sorted by name. */
    public String sorted(Name name) {
        return format(name, name == null ? DEFAULT : name.sortAs());
    }

    /** Formats with a format number; unknown numbers use the default format. */
    public String format(Name name, int number) {
        String format = formats.get(number == DEFAULT ? defaultFormat : number);
        return formatWith(name, format != null ? format : formats.get(defaultFormat));
    }

    /** Formats with a format string of keywords or codes. */
    public String formatWith(Name name, String format) {
        if (name == null) {
            return "";
        }
        var result = new StringBuilder();
        for (Part part : compiled.computeIfAbsent(format, NameFormatter::compile)) {
            result.append(part.render(name));
        }
        return cleanup(result.toString());
    }

    // ---------------------------------------------------------------- format compilation

    private record Field(String code, String keyword, Function<Name, String> value) {}

    private interface Part {
        String render(Name name);
    }

    private record Literal(String text) implements Part {
        public String render(Name name) {
            return text;
        }
    }

    private record FieldPart(String before, Field field, boolean upper, String after) implements Part {
        public String render(Name name) {
            String value = field.value.apply(name);
            if (value.isEmpty()) {
                return "";
            }
            return before + (upper ? value.toUpperCase(Locale.ROOT) : value) + after;
        }
    }

    private static List<Part> compile(String format) {
        String codes = format;
        boolean quoted = format.length() > 2 && format.startsWith("\"") && format.endsWith("\"");
        if (quoted) {
            codes = format.substring(1, format.length() - 1);
        } else {
            for (Field field : FIELDS) {
                codes = codes.replace(field.keyword, "%" + field.code)
                        .replace(capitalize(field.keyword), "%" + field.code)
                        .replace(field.keyword.toUpperCase(Locale.ROOT), "%" + field.code.toUpperCase(Locale.ROOT));
            }
        }
        boolean independent = codes.startsWith("!");
        if (independent) {
            codes = codes.substring(1);
        }
        List<Part> parts = new ArrayList<>();
        Matcher m = (independent
                        ? Pattern.compile("%("
                                + String.join(
                                        "|",
                                        BY_CODE.keySet().stream()
                                                .sorted((a, b) -> b.length() - a.length())
                                                .map(Pattern::quote)
                                                .toList())
                                + ")")
                        : FIELD_PATTERN)
                .matcher(codes);
        int last = 0;
        while (m.find()) {
            parts.add(new Literal(codes.substring(last, m.start()).replace("%%", "%")));
            String match = m.group();
            String code = null;
            for (int g = 1; g <= m.groupCount(); g++) {
                if (m.group(g) != null) {
                    code = m.group(g);
                    break;
                }
            }
            int at = match.indexOf("%" + code);
            parts.add(new FieldPart(
                    match.substring(0, at),
                    BY_CODE.get(code),
                    Character.isUpperCase(code.charAt(code.length() - 1)),
                    match.substring(at + 1 + code.length())));
            last = m.end();
        }
        parts.add(new Literal(codes.substring(last).replace("%%", "%")));
        return List.copyOf(parts);
    }

    private static String capitalize(String keyword) {
        return Character.toUpperCase(keyword.charAt(0)) + keyword.substring(1);
    }

    // Gramps: cleanup_name - collapse spaces and attach lone punctuation to the previous word. Unlike Gramps,
    // separators left at either end are removed, so a name without a surname is "Jan", not ", Jan".
    static String cleanup(String name) {
        String[] words = name.strip().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) {
            return "";
        }
        var result = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            String word = words[i];
            if (word.length() == 1 && ",;:،؛".contains(word)) {
                result.append(word);
            } else {
                result.append(' ').append(word);
            }
        }
        return result.toString().replaceAll("^[,;:،؛\\s]+|[,;:،؛\\s]+$", "");
    }

    // ---------------------------------------------------------------- name parts

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    private static boolean isPatronymic(Surname surname) {
        return PATRONYMIC.equals(surname.origin()) || MATRONYMIC.equals(surname.origin());
    }

    // Gramps: __format_raw_surname - prefix, surname and connector; a hyphen connector is not padded.
    private static String surnameWithConnector(Surname surname) {
        String result = nonNull(surname.prefix());
        if (!result.isEmpty()) {
            result += " ";
        }
        result += nonNull(surname.value());
        String connector = nonNull(surname.connector());
        if (!result.isEmpty() && !connector.equals("-")) {
            result += " " + connector + " ";
        } else {
            result += connector;
        }
        return result;
    }

    private static String surname(Surname surname) {
        return surnameWithConnector(surname).strip();
    }

    private static String fullSurname(Name name) {
        var result = new StringBuilder();
        name.surnames().forEach(s -> result.append(surnameWithConnector(s)));
        return result.toString().strip();
    }

    /** A part of the primary surname; empty for a lone patronymic, which Gramps does not treat as a surname. */
    private static String primary(Name name, Function<Surname, String> part) {
        for (Surname surname : name.surnames()) {
            if (surname.primary()) {
                if (name.surnames().size() == 1 && isPatronymic(surname)) {
                    return "";
                }
                return part.apply(surname);
            }
        }
        return "";
    }

    private static String patronymic(Name name, Function<Surname, String> part) {
        for (Surname surname : name.surnames()) {
            if (isPatronymic(surname)) {
                return joinWords(List.of(part.apply(surname)));
            }
        }
        return "";
    }

    private static String notPatronymic(Name name) {
        var result = new StringBuilder();
        for (Surname surname : name.surnames()) {
            if (!surname.primary() && !isPatronymic(surname)) {
                result.append(surnameWithConnector(surname));
            }
        }
        return result.toString().strip();
    }

    private static String rest(Name name) {
        var result = new StringBuilder();
        for (Surname surname : name.surnames()) {
            if (!surname.primary()) {
                result.append(surnameWithConnector(surname));
            }
        }
        return result.toString().strip();
    }

    private static String common(Name name) {
        if (name.nick() != null && !name.nick().isEmpty()) {
            return name.nick();
        }
        if (name.call() != null && !name.call().isEmpty()) {
            return name.call();
        }
        String first = nonNull(name.first());
        int space = first.indexOf(' ');
        return space < 0 ? first : first.substring(0, space);
    }

    private static String initials(Name name) {
        var result = new StringBuilder();
        for (String word : nonNull(name.first()).split("\\s+")) {
            if (!word.isEmpty()) {
                result.append(word.charAt(0)).append('.');
            }
        }
        return result.toString();
    }

    private static String joinWords(List<String> values) {
        return String.join(" ", String.join(" ", values).strip().split("\\s+")).strip();
    }
}
