package me.hejl.kwatern.view;

import java.util.List;
import java.util.Locale;
import me.hejl.kwatern.view.Pages.MapMarker;

/** Map markers as JSON for the page's script, which reads them from an HTML attribute. */
final class Json {

    private Json() {}

    static String markers(List<MapMarker> markers) {
        StringBuilder json = new StringBuilder("[");
        for (MapMarker m : markers) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append(String.format(Locale.ROOT, "{\"lat\":%.6f,\"lon\":%.6f,", m.lat(), m.lon()))
                    .append("\"label\":")
                    .append(string(m.label()))
                    .append(",\"title\":")
                    .append(string(m.title()))
                    .append(",\"url\":")
                    .append(string(m.url()))
                    .append(",\"note\":")
                    .append(string(m.note()))
                    .append(",\"size\":")
                    .append(m.size())
                    .append(",\"secondary\":")
                    .append(m.secondary())
                    .append('}');
        }
        return json.append(']').toString();
    }

    /** A JSON string; angle brackets and ampersands escaped too, although the template escapes the attribute. */
    static String string(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '<', '>', '&' -> json.append(String.format("\\u%04x", (int) c));
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"').toString();
    }
}
