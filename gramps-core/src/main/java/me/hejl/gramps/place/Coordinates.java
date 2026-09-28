package me.hejl.gramps.place;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A place's position in decimal degrees, north and east positive.
 *
 * <p>Gramps stores latitude and longitude as text in whatever notation the user typed (gen/utils/place.py
 * conv_lat_lon accepts many): decimal ({@code 39.0483}, {@code -95.678}, {@code 50,8498}), degrees and minutes
 * or degrees, minutes and seconds separated by colons, spaces or {@code ° ' "}, and a hemisphere letter before
 * or after ({@code 50°50'59.60"N}, {@code N50:50:59.6}).
 */
public record Coordinates(double latitude, double longitude) {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    /** Parses both values; empty if either is missing or not a valid coordinate. */
    public static Optional<Coordinates> parse(String latitude, String longitude) {
        Optional<Double> lat = parse(latitude, 'N', 'S', 90);
        Optional<Double> lon = parse(longitude, 'E', 'W', 180);
        if (lat.isEmpty() || lon.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Coordinates(lat.get(), lon.get()));
    }

    private static Optional<Double> parse(String text, char positive, char negative, double limit) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String value = text.strip().toUpperCase(Locale.ROOT);
        int sign = value.startsWith("-") ? -1 : 1;
        String letters = value.replaceAll("[^A-Z]", "");
        if (letters.equals(String.valueOf(negative))) {
            sign = -1;
        } else if (!letters.isEmpty() && !letters.equals(String.valueOf(positive))) {
            return Optional.empty();
        }
        List<Double> parts = new ArrayList<>(3);
        Matcher m = NUMBER.matcher(value);
        while (m.find()) {
            parts.add(Double.parseDouble(m.group().replace(',', '.')));
        }
        if (parts.isEmpty() || parts.size() > 3) {
            return Optional.empty();
        }
        double degrees = parts.get(0);
        if (parts.size() > 1) {
            double minutes = parts.get(1);
            double seconds = parts.size() > 2 ? parts.get(2) : 0;
            if (minutes >= 60 || seconds >= 60 || degrees != Math.floor(degrees)) {
                return Optional.empty();
            }
            degrees += minutes / 60 + seconds / 3600;
        }
        if (degrees > limit) {
            return Optional.empty();
        }
        return Optional.of(sign * degrees);
    }
}
