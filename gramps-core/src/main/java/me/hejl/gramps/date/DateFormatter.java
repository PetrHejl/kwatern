package me.hejl.gramps.date;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.ULocale;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.hejl.gramps.i18n.Messages;
import me.hejl.gramps.model.DateValue;
import me.hejl.gramps.model.GrampsCalendar;
import me.hejl.gramps.model.GrampsDate;

/**
 * Formats Gramps dates for display in a given locale.
 *
 * <p>Month names and the order of day, month and year come from ICU's locale data, in the date's own
 * calendar. Words such as "about" or "between ... and ..." come from {@code messages*.properties} next to
 * this class, which can also override the ICU skeletons used for each kind of date. Thread-safe.
 */
public final class DateFormatter {

    private final Messages messages;
    private final ULocale locale;
    private final Map<String, SimpleDateFormat> formats = new ConcurrentHashMap<>();

    public DateFormatter(Locale locale) {
        this.messages = Messages.load("me/hejl/gramps/date/messages", locale);
        this.locale = ULocale.forLocale(locale);
    }

    /** Formats the date; returns an empty string for {@code null}. */
    public String format(GrampsDate date) {
        if (date == null) {
            return "";
        }
        if (date.modifier() == GrampsDate.Modifier.TEXT_ONLY || date.start() == null) {
            return date.text() == null ? "" : date.text();
        }
        String start = value(date, date.start());
        String text = switch (date.modifier()) {
            case NONE, TEXT_ONLY -> start;
            case BEFORE -> messages.format("date.before", start);
            case AFTER -> messages.format("date.after", start);
            case ABOUT -> messages.format("date.about", start);
            case FROM -> messages.format("date.from", start);
            case TO -> messages.format("date.to", start);
            case RANGE -> messages.format("date.range", start, value(date, date.stop()));
            case SPAN -> messages.format("date.span", start, value(date, date.stop()));
        };
        text = switch (date.quality()) {
            case REGULAR -> text;
            case ESTIMATED -> messages.format("date.estimated", text);
            case CALCULATED -> messages.format("date.calculated", text);
        };
        List<String> extras = new ArrayList<>(2);
        if (date.calendar() != GrampsCalendar.GREGORIAN) {
            extras.add(messages.get("calendar." + date.calendar().name().toLowerCase(Locale.ROOT)));
        }
        if (DateMath.newYearSplit(date.newYear()) != null) {
            extras.add(date.newYear());
        }
        return extras.isEmpty() ? text : messages.format("date.extras", text, String.join(", ", extras));
    }

    private String value(GrampsDate date, DateValue value) {
        if (value == null) {
            return "";
        }
        int year = value.year(), month = value.month(), day = value.day();
        String skeleton = skeleton(year != 0, month != 0, day != 0);
        if (skeleton == null) {
            return "";
        }
        if (date.calendar() == GrampsCalendar.FRENCH_REPUBLICAN) {
            return frenchRepublican(year, month, day);
        }
        Calendar calendar = Calendars.icuCalendar(date.calendar());
        // Any valid year will do for month names when the year is unknown.
        Calendars.set(
                calendar,
                date.calendar(),
                year != 0 ? year : calendar.get(Calendar.EXTENDED_YEAR),
                Math.max(month, 1),
                Math.max(day, 1));
        if ((day != 0 && calendar.get(Calendar.DAY_OF_MONTH) != day)) {
            // Not a valid day in this calendar, such as the Swedish 1712-02-30; show it as written.
            return numeric(year, month, day);
        }
        SimpleDateFormat format = format(date.calendar(), calendar, skeleton);
        if (date.dualDated() && year != 0) {
            format.applyPattern(replaceYear(format.toPattern(), dualYear(year)));
        }
        return format.format(calendar);
    }

    private String skeleton(boolean year, boolean month, boolean day) {
        if (month && day) {
            return messages.get(year ? "skeleton.ymd" : "skeleton.md");
        }
        if (month) {
            return messages.get(year ? "skeleton.ym" : "skeleton.m");
        }
        return year ? messages.get("skeleton.y") : null;
    }

    /** A private copy of the cached format for this calendar and skeleton. */
    private SimpleDateFormat format(GrampsCalendar grampsCalendar, Calendar calendar, String skeleton) {
        SimpleDateFormat cached = formats.computeIfAbsent(grampsCalendar + "/" + skeleton, key ->
                (SimpleDateFormat) DateFormat.getInstanceForSkeleton(calendar, skeleton, locale));
        var copy = (SimpleDateFormat) cached.clone();
        copy.setCalendar(calendar);
        return copy;
    }

    private String frenchRepublican(int year, int month, int day) {
        String[] months = messages.get("calendar.french_republican.months").split(",");
        String monthName = month >= 1 && month <= months.length ? months[month - 1].strip() : String.valueOf(month);
        if (month != 0 && day != 0) {
            return messages.format("french_republican.ymd", String.valueOf(year), monthName, String.valueOf(day));
        }
        if (month != 0) {
            return messages.format("french_republican.ym", String.valueOf(year), monthName);
        }
        return messages.format("french_republican.y", String.valueOf(year));
    }

    private static String numeric(int year, int month, int day) {
        return (year == 0 ? "????" : String.valueOf(year)) + "-" + month + "-" + day;
    }

    // Gramps: DateDisplay._slash_year, e.g. 1827 -> "1826/7", 1800 -> "1799/800"
    static String dualYear(int year) {
        int previous = year - 1;
        if (previous % 100 == 99) {
            return previous + "/" + (year % 1000);
        }
        if (previous % 10 == 9) {
            return previous + "/" + (year % 100);
        }
        return previous + "/" + (year % 10);
    }

    /** Replaces the year field of an ICU pattern with fixed text. */
    static String replaceYear(String pattern, String year) {
        var result = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
                result.append(c);
            } else if (!quoted && (c == 'y' || c == 'Y' || c == 'u')) {
                while (i + 1 < pattern.length() && pattern.charAt(i + 1) == c) {
                    i++;
                }
                result.append('\'').append(year.replace("'", "''")).append('\'');
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
