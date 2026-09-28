package me.hejl.gramps.model;

import java.util.List;

/** Note text with style ranges given as character offsets into {@link #text()}. */
public record StyledText(String text, List<Style> styles) {

    public StyledText {
        styles = List.copyOf(styles);
    }

    /**
     * A style applied to one or more ranges.
     *
     * @param name  {@code bold}, {@code italic}, {@code link}, {@code fontcolor}, ...
     * @param value style argument, such as the link target or colour; may be {@code null}
     */
    public record Style(String name, String value, List<Range> ranges) {

        public Style {
            ranges = List.copyOf(ranges);
        }
    }

    /** Half-open range {@code [start, end)} of character offsets. */
    public record Range(int start, int end) {}
}
