package me.hejl.gramps.model;

/** The calendars Gramps supports, with the names used in Gramps XML. */
public enum GrampsCalendar {
    GREGORIAN("Gregorian"),
    JULIAN("Julian"),
    HEBREW("Hebrew"),
    FRENCH_REPUBLICAN("French Republican"),
    PERSIAN("Persian"),
    ISLAMIC("Islamic"),
    SWEDISH("Swedish");

    private final String xmlName;

    GrampsCalendar(String xmlName) {
        this.xmlName = xmlName;
    }

    /** The name used in the {@code cformat} attribute. */
    public String xmlName() {
        return xmlName;
    }

    /** Looks up a calendar by its XML name; returns {@code null} if unknown. */
    public static GrampsCalendar fromXmlName(String name) {
        for (GrampsCalendar calendar : values()) {
            if (calendar.xmlName.equalsIgnoreCase(name)) {
                return calendar;
            }
        }
        return null;
    }
}
