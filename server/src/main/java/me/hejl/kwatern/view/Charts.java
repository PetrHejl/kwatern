package me.hejl.kwatern.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.hejl.kwatern.view.Pages.ChartBox;
import me.hejl.kwatern.view.Pages.ChartLine;
import me.hejl.kwatern.view.Pages.FanSegment;
import me.hejl.kwatern.view.Pages.LifespanChart;
import me.hejl.kwatern.view.Pages.LifespanRow;
import me.hejl.kwatern.view.Pages.PersonLink;
import me.hejl.kwatern.view.Pages.Tick;

/**
 * The geometry of ancestor charts, in SVG coordinates. Ancestors are numbered as in an Ahnentafel: the person
 * is 1, the parents of n are 2n and 2n + 1. An unknown ancestor is shown where their child is known.
 */
final class Charts {

    static final int BOX_HEIGHT = 64;
    // The chart fits the card of a 1184 px page: 1102 px inside. Five generations need narrower boxes, which
    // leave out the initials.
    private static final int BOX_WIDTH = 240;
    private static final int COLUMN = 284;
    private static final int COMPACT_WIDTH = 204;
    private static final int COMPACT_COLUMN = 222;
    private static final int ROW = 84;

    static final int FAN_CENTRE = 110;
    private static final int FAN_RING = 112;

    private Charts() {}

    record Tree(int width, int height, List<ChartBox> boxes, List<ChartLine> lines) {}

    record Fan(int width, int height, List<FanSegment> segments) {}

    /** Whether slot n is shown: the person, or a parent of someone known. */
    private static boolean shown(Map<Integer, PersonLink> people, int n) {
        return n == 1 || people.containsKey(n / 2);
    }

    private static int generation(int n) {
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    /**
     * A tree chart: the person on the left, each generation a column to the right.
     *
     * @param generations columns, including the person
     * @param earlier     for people in the last column, the URL of the chart continued from them
     */
    static Tree tree(int generations, Map<Integer, PersonLink> people, Map<Integer, String> earlier) {
        boolean compact = generations >= 5;
        int width = compact ? COMPACT_WIDTH : BOX_WIDTH;
        int column = compact ? COMPACT_COLUMN : COLUMN;
        int last = 1 << (generations - 1);
        int[] y = new int[2 * last];
        for (int n = last; n < 2 * last; n++) {
            y[n] = (n - last) * ROW;
        }
        for (int n = last - 1; n >= 1; n--) {
            y[n] = (y[2 * n] + y[2 * n + 1]) / 2;
        }
        List<ChartBox> boxes = new ArrayList<>();
        List<ChartLine> lines = new ArrayList<>();
        for (int n = 1; n < 2 * last; n++) {
            if (!shown(people, n)) {
                continue;
            }
            int x = generation(n) * column;
            PersonLink person = people.get(n);
            String name = person == null ? "" : shortenName(person.name(), 26);
            boxes.add(new ChartBox(x, y[n], width, compact, person, n == 1, name, earlier.getOrDefault(n, "")));
            if (person != null && n < last) {
                int middle = x + width + (column - width) / 2;
                int centre = y[n] + BOX_HEIGHT / 2;
                int father = y[2 * n] + BOX_HEIGHT / 2;
                int mother = y[2 * n + 1] + BOX_HEIGHT / 2;
                lines.add(new ChartLine(x + width, centre, middle, centre));
                lines.add(new ChartLine(middle, father, middle, mother));
                lines.add(new ChartLine(middle, father, x + column, father));
                lines.add(new ChartLine(middle, mother, x + column, mother));
            }
        }
        // Two pixels more than the boxes, drawn one pixel in, so that their borders are not cut off.
        return new Tree((generations - 1) * column + width + 2, last * ROW - (ROW - BOX_HEIGHT) + 2, boxes, lines);
    }

    /**
     * A fan chart: the person in a half disc at the bottom, each generation a ring around it, the father's side
     * on the left.
     */
    static Fan fan(int generations, Map<Integer, PersonLink> people) {
        int radius = FAN_CENTRE + (generations - 1) * FAN_RING;
        double cx = radius + 10;
        double cy = radius + 10;
        List<FanSegment> segments = new ArrayList<>();
        PersonLink root = people.get(1);
        segments.add(new FanSegment(
                "M %s A %d %d 0 0 1 %s Z"
                        .formatted(
                                point(cx, cy, FAN_CENTRE, Math.PI),
                                FAN_CENTRE,
                                FAN_CENTRE,
                                point(cx, cy, FAN_CENTRE, 0)),
                root,
                "translate(%s %s)".formatted(number(cx), number(cy - 44)),
                16,
                shortenName(root.name(), 22),
                root.lifespan()));
        for (int n = 2; n < 1 << generations; n++) {
            if (!shown(people, n)) {
                continue;
            }
            int k = generation(n);
            int count = 1 << k;
            int index = n - count;
            double a0 = Math.PI - index * Math.PI / count;
            double a1 = Math.PI - (index + 1) * Math.PI / count;
            int inner = FAN_CENTRE + (k - 1) * FAN_RING;
            int outer = inner + FAN_RING;
            String path = "M %s A %d %d 0 0 1 %s L %s A %d %d 0 0 0 %s Z"
                    .formatted(
                            point(cx, cy, outer, a0),
                            outer,
                            outer,
                            point(cx, cy, outer, a1),
                            point(cx, cy, inner, a1),
                            inner,
                            inner,
                            point(cx, cy, inner, a0));
            double middle = (a0 + a1) / 2;
            double r = (inner + outer) / 2.0;
            double degrees = Math.toDegrees(middle);
            double arc = r * Math.PI / count;
            // Along the ring where there is room, otherwise along the radius, read from the outside in.
            boolean along = arc >= 150;
            double rotate = along ? 90 - degrees : degrees > 90 ? 180 - degrees : -degrees;
            // Sizes of the type scale (style.css): base for the parents, sm along the rings, xs along the radius.
            int size = along ? (k == 1 ? 18 : 16) : 14;
            int chars = (int) ((along ? arc - 16 : FAN_RING - 10) / (size * 0.48));
            PersonLink person = people.get(n);
            segments.add(new FanSegment(
                    path,
                    person,
                    "translate(%s %s) rotate(%s)"
                            .formatted(
                                    number(cx + r * Math.cos(middle)),
                                    number(cy - r * Math.sin(middle)),
                                    number(rotate)),
                    size,
                    person == null ? "" : shortenName(person.name(), chars),
                    person == null ? "" : person.lifespan()));
        }
        return new Fan((int) (2 * cx), (int) cy + 2, segments);
    }

    /** How the bar of a life ends. */
    enum End {
        /** At the death. */
        DEATH,
        /** Today: the person may still be alive. */
        ALIVE,
        /** Unknown: the bar fades out after the last year the person is known to have lived. */
        UNKNOWN
    }

    /**
     * A life for the lifespan chart.
     *
     * @param group the heading of its group, such as "Parents"
     * @param to    the year of death, this year for someone who may be alive, or the last year someone is known to
     *              have lived when the date of death is unknown
     */
    record Life(String group, PersonLink person, int from, int to, End end, String years, boolean self) {}

    private static final int LIFESPAN_WIDTH = 640;
    private static final int LIFESPAN_LEFT = 210;
    private static final int LIFESPAN_ROW = 32;
    private static final int BAR_HEIGHT = 20;
    // Over how many years a bar without a known end fades out.
    private static final int FADE_YEARS = 20;

    /** Bars on one time axis, grouped as given; the person's lifetime is a band behind them. */
    static LifespanChart lifespans(List<Life> lives, int currentYear, String note) {
        int first = lives.stream().mapToInt(Life::from).min().orElse(0);
        int last = lives.stream().mapToInt(l -> stop(l, currentYear)).max().orElse(first);
        int step = last - first > 200 ? 40 : 20;
        int start = Math.floorDiv(first, step) * step;
        int ceiling = Math.max(start + step, -Math.floorDiv(-last, step) * step);
        // The axis does not run into the future unless the dates do.
        int end = Math.max(Math.max(last, start + 1), Math.min(ceiling, currentYear));
        // Room at the right for half of the last year label.
        double scale = (LIFESPAN_WIDTH - LIFESPAN_LEFT - 20.0) / (end - start);
        List<Tick> ticks = new ArrayList<>();
        for (int year = start; year <= end; year += step) {
            ticks.add(new Tick(LIFESPAN_LEFT + (int) Math.round((year - start) * scale), String.valueOf(year)));
        }
        List<LifespanRow> rows = new ArrayList<>();
        int y = 34;
        String group = null;
        int bandX = 0;
        int bandWidth = 0;
        for (Life life : lives) {
            if (!life.group().equals(group)) {
                group = life.group();
                rows.add(new LifespanRow(y + 6, group, null, "", 0, 0, "", 0, false, "", true));
                y += LIFESPAN_ROW;
            }
            int x = LIFESPAN_LEFT + (int) Math.round((life.from() - start) * scale);
            int width = Math.max(3, (int) Math.round((stop(life, currentYear) - life.from()) * scale));
            int fade = life.end() == End.UNKNOWN
                    ? Math.min(x + width - 1, LIFESPAN_LEFT + (int) Math.round((life.to() - start) * scale))
                    : 0;
            boolean after = x + width + 8 + life.years().length() * 9 <= LIFESPAN_WIDTH;
            rows.add(new LifespanRow(
                    y,
                    "",
                    life.person(),
                    shortenName(life.person().name(), 24),
                    x,
                    width,
                    bar(x, y + 6, width, life.end() == End.ALIVE),
                    fade,
                    life.self(),
                    life.years(),
                    after));
            if (life.self()) {
                bandX = x;
                bandWidth = width;
            }
            y += LIFESPAN_ROW;
        }
        return new LifespanChart(LIFESPAN_WIDTH, y + 4, LIFESPAN_LEFT, bandX, bandWidth, ticks, rows, note);
    }

    /** The year where the bar of a life stops. */
    private static int stop(Life life, int currentYear) {
        if (life.end() != End.UNKNOWN) {
            return life.to();
        }
        return Math.max(life.to(), Math.min(life.to() + FADE_YEARS, currentYear));
    }

    /** A bar with rounded corners, or with a point at the right end for a life that goes on. */
    private static String bar(int x, int y, int width, boolean pointed) {
        double r = Math.min(4, width / 2.0);
        int tip = pointed && width > 12 ? 6 : 0;
        int right = x + width;
        int bottom = y + BAR_HEIGHT;
        String end = tip > 0
                ? "H%d L%d %s L%d %d".formatted(right - tip, right, number(y + BAR_HEIGHT / 2.0), right - tip, bottom)
                : "H%s A%s %s 0 0 1 %d %s V%s A%s %s 0 0 1 %s %d"
                        .formatted(
                                number(right - r),
                                number(r),
                                number(r),
                                right,
                                number(y + r),
                                number(bottom - r),
                                number(r),
                                number(r),
                                number(right - r),
                                bottom);
        return "M%s %d %s H%s A%s %s 0 0 1 %d %s V%s A%s %s 0 0 1 %s %d Z"
                .formatted(
                        number(x + r),
                        y,
                        end,
                        number(x + r),
                        number(r),
                        number(r),
                        x,
                        number(bottom - r),
                        number(y + r),
                        number(r),
                        number(r),
                        number(x + r),
                        y);
    }

    private static String point(double cx, double cy, double radius, double angle) {
        return number(cx + radius * Math.cos(angle)) + " " + number(cy - radius * Math.sin(angle));
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * A name that fits {@code limit} characters: as it is, else with the given names as initials ("K. Holanová"),
     * since the surname matters most in a family tree, else cut.
     */
    static String shortenName(String name, int limit) {
        if (name.length() <= limit) {
            return name;
        }
        String[] words = name.strip().split(" +");
        // The surname is the last word, but for short trailing words such as "Sr" or "III".
        int surname = words.length - 1;
        while (surname > 0 && words[surname].length() <= 3) {
            surname--;
        }
        // Middle names first ("Lewis A. Garner Sr"), then the first name too ("L. A. Garner Sr").
        for (int keep = 1; keep >= 0 && surname > 0; keep--) {
            StringBuilder abbreviated = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                if (i >= keep && i < surname) {
                    abbreviated.appendCodePoint(words[i].codePointAt(0)).append('.');
                } else {
                    abbreviated.append(words[i]);
                }
                if (i < words.length - 1) {
                    abbreviated.append(' ');
                }
            }
            if (abbreviated.length() <= limit) {
                return abbreviated.toString();
            }
        }
        return shorten(name, limit);
    }

    /** The text, cut at a word boundary where possible, with an ellipsis if it is longer than the limit. */
    static String shorten(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        String cut = text.substring(0, Math.max(1, limit - 1));
        int space = cut.lastIndexOf(' ');
        if (space > limit / 2) {
            cut = cut.substring(0, space);
        }
        return cut.strip() + "…";
    }
}
