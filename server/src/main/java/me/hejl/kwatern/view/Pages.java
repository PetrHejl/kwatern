package me.hejl.kwatern.view;

import java.util.List;

/**
 * What the templates show. Views build these from the published database so templates contain no logic
 * beyond layout. All texts are already translated and formatted; lists are never {@code null}, optional
 * values are empty strings or {@code null} links.
 */
public final class Pages {

    private Pages() {}

    /**
     * A link to a person.
     *
     * @param initials one or two letters for the avatar; empty for living people
     * @param gender   {@code male}, {@code female}, {@code unknown} or {@code other}, used as a CSS class;
     *                 {@code unknown} for living people, whose gender is not published
     * @param genderLabel the gender in words, for screen readers
     * @param lifespan years of birth and death such as "1850–1910", or empty
     */
    public record PersonLink(
            String url,
            String name,
            String lifespan,
            boolean living,
            String initials,
            String gender,
            String genderLabel) {}

    /** A labelled value, such as an attribute or an alternate name. */
    public record Row(String label, String value) {}

    /** A citation of a source, as shown when an event's sources are opened. */
    public record CitationView(String source, String sourceUrl, String page, String date, String confidence) {}

    /** A source cited on a page, with what it supports ("birth", "this person"). */
    public record SourceUse(String source, String url, String page, String supports) {}

    /**
     * One entry of a timeline.
     *
     * @param year     the Gregorian year for the timeline column, or empty for undated events
     * @param partner  the other spouse for family events such as a marriage, else {@code null}
     * @param relative a relative, for events in the lives of the family such as "Birth of son", else {@code null}
     */
    public record EventRow(
            String year,
            String type,
            String date,
            String place,
            String placeUrl,
            String description,
            String role,
            PersonLink partner,
            List<CitationView> sources,
            PersonLink relative) {}

    public record NoteView(String type, String text, boolean preformatted) {}

    /** A link to a place, with its type such as "City". */
    public record PlaceLink(String url, String name, String type) {}

    /**
     * Something that happened at a place.
     *
     * @param who the people involved: the person, or both spouses for a family event
     */
    public record PlaceEvent(
            String year, String type, String date, List<PersonLink> who, String place, String placeUrl) {}

    /**
     * A place within an area, with the events in it and the places within it.
     *
     * @param share the events as a percentage of the busiest place listed, for a bar
     */
    public record PlaceArea(String url, String name, String type, int events, int share) {}

    public record PlacePage(
            String title,
            List<PlaceLink> breadcrumb,
            String summary,
            String coordinates,
            List<Row> otherNames,
            List<PlaceLink> partOf,
            List<PlaceArea> within,
            List<PlaceEvent> events,
            int moreEvents,
            List<SurnameEntry> surnames,
            List<NoteView> notes,
            List<SourceUse> sources,
            List<MediaTile> media,
            String markers) {}

    /** Something a citation supports, such as a person or "Birth of Lewis Anderson Garner". */
    public record Cited(String label, String url) {}

    /** @param media scans or photos attached to the citation */
    public record CitationEntry(
            String page, String date, String confidence, List<Cited> cited, List<MediaTile> media) {}

    public record SourcePage(
            String title,
            List<Row> details,
            List<Row> repositories,
            List<NoteView> notes,
            List<CitationEntry> citations,
            List<MediaTile> media) {}

    /** A place in the places tree, with how many events happened there and the places within it. */
    public record PlaceNode(String url, String name, String type, int events, List<PlaceNode> children) {}

    public record PlacesPage(List<PlaceNode> roots, int total) {}

    public record SourceEntry(String url, String title, String author, int citations) {}

    /** A heading of the sources index, such as "B", with its sources. */
    public record SourceLetter(String label, List<SourceEntry> sources) {}

    public record SourcesPage(List<SourceLetter> letters, int total) {}

    public record SearchPage(String query, List<PersonLink> people, List<PlaceLink> places, boolean more) {}

    /** A child in a family, with the relation to the parents if it is not by birth. */
    public record ChildView(PersonLink person, String relation) {}

    /**
     * A family in the family diagram: a partner and their children.
     *
     * @param label        e.g. "Married 1875", or the relationship type when there is no marriage
     * @param partner      the other parent, or {@code null} if unknown
     * @param partnerRole  e.g. "Wife", "Husband" or "Partner"
     * @param moreChildren children not shown in the diagram, listed on the family page
     */
    public record FamilyBlock(
            String url,
            String label,
            PersonLink partner,
            String partnerRole,
            List<ChildView> children,
            int childCount,
            int moreChildren) {}

    /** Parents above, the person, and one block per family below. Parents may be {@code null}. */
    public record Diagram(PersonLink father, PersonLink mother, PersonLink self, List<FamilyBlock> families) {}

    /**
     * A person's name for the page heading, split so the surname can be emphasised.
     *
     * @param before the given names, or everything when the name has no surname
     */
    public record Heading(String before, String surname, String after) {}

    /** @param portrait the URL of the person's picture, or empty to show initials */
    /** A link in a row of choices, such as the tabs of a person or the number of generations of a chart. */
    public record Tab(String label, String url, boolean active) {}

    /**
     * The heading of every page about a person, with the tabs between them.
     *
     * @param portrait the URL of the person's picture, or empty to show initials
     */
    public record PersonHero(
            String title,
            Heading heading,
            String portrait,
            String initials,
            String gender,
            boolean living,
            String summary,
            String details,
            String surnameUrl,
            String surnameLink,
            List<Tab> tabs) {}

    /** A year on the axis of the lifespan chart. */
    public record Tick(int x, String label) {}

    /**
     * A row of the lifespan chart: a group heading, or a person's bar.
     *
     * @param open  no known end: the bar fades out
     * @param self  the person the page is about
     * @param after whether the years go after the bar, or before it when there is no room
     */
    public record LifespanRow(
            int y,
            String heading,
            PersonLink person,
            String name,
            int x,
            int width,
            boolean open,
            boolean self,
            String years,
            boolean after) {}

    /** Lifespans of a family on one time axis, in SVG coordinates; the band marks the person's lifetime. */
    public record LifespanChart(
            int width, int height, int left, int bandX, int bandWidth, List<Tick> ticks, List<LifespanRow> rows) {}

    /**
     * @param familyEvents whether the timeline includes events of the family
     * @param familyToggle the URL that shows or hides them
     * @param lifespans    the lifespans of the family, or {@code null} if there are too few
     */
    public record PersonPage(
            PersonHero hero,
            List<EventRow> timeline,
            boolean familyEvents,
            String familyToggle,
            LifespanChart lifespans,
            Diagram diagram,
            List<PersonLink> siblings,
            List<Row> otherNames,
            List<Row> attributes,
            List<NoteView> notes,
            List<SourceUse> sources,
            List<MediaTile> media) {}

    /**
     * A person in an ancestor tree chart, at SVG coordinates.
     *
     * @param person   {@code null} for an unknown ancestor
     * @param root     the person the chart is about
     * @param name     the name, shortened to fit the box
     * @param earlier  the URL of the chart continued from this person, or empty when there is nothing further
     * @param compact  a narrow box without initials
     */
    public record ChartBox(
            int x, int y, int width, boolean compact, PersonLink person, boolean root, String name, String earlier) {}

    /** A connector line of a chart. */
    public record ChartLine(int x1, int y1, int x2, int y2) {}

    /**
     * A segment of a fan chart: an SVG path, and its label rotated to fit.
     *
     * @param person {@code null} for an unknown ancestor
     */
    public record FanSegment(
            String path, PersonLink person, String transform, int fontSize, String name, String years) {}

    /**
     * One generation of ancestors as a list, for small screens: the father's side and the mother's side.
     *
     * @param sides whether to label the two halves; not for parents
     */
    public record AncestorGeneration(
            String label,
            boolean sides,
            List<PersonLink> fatherSide,
            int fatherUnknown,
            List<PersonLink> motherSide,
            int motherUnknown) {}

    public record AncestorsPage(
            PersonHero hero,
            boolean fan,
            List<Tab> generations,
            Tab otherView,
            String heading,
            List<String> columns,
            int width,
            int height,
            List<ChartBox> boxes,
            List<ChartLine> lines,
            List<FanSegment> segments,
            List<AncestorGeneration> list) {}

    /**
     * A person in a descendant chart with their families.
     *
     * @param relation e.g. "adopted", or empty for a birth child
     * @param note     e.g. "4 children", when the children are beyond the generations shown
     * @param moreUrl  the descendant chart of this person, when their children are not shown
     */
    public record DescendantNode(
            PersonLink person, String relation, List<DescendantFamily> families, String note, String moreUrl) {}

    /** A family in a descendant chart: e.g. "1875", the partner, and the children. */
    public record DescendantFamily(String label, PersonLink partner, String familyUrl, List<DescendantNode> children) {}

    public record DescendantsPage(PersonHero hero, List<Tab> generations, String heading, DescendantNode root) {}

    /**
     * A marker on a map, passed to the page's script as JSON.
     *
     * @param label     the number on a pin, or empty
     * @param note      a line under the title in the marker's popup, e.g. "12 events"
     * @param size      the radius of a circle marker in pixels
     * @param secondary drawn less prominently, e.g. places of relatives
     */
    public record MapMarker(
            double lat, double lon, String label, String title, String url, String note, int size, boolean secondary) {}

    /**
     * A place in a person's life, numbered as on the map.
     *
     * @param events  what happened there, e.g. "Birth 1855 · Burial 1911"
     * @param shownAt the enclosing place where it is shown on the map, when it has no coordinates of its own
     * @param mapped  whether it is on the map at all
     */
    public record PlaceStop(String number, String name, String url, String events, String shownAt, boolean mapped) {}

    public record PersonMapPage(
            PersonHero hero, String markers, List<PlaceStop> stops, boolean family, String familyToggle) {}

    /** The map of the whole tree, with filters for kinds of events and periods. */
    public record TreeMapPage(String markers, int mapped, int unmapped, List<Tab> kinds, List<Tab> periods) {}

    public record FamilyPage(
            String title,
            String relationship,
            PersonLink father,
            PersonLink mother,
            List<EventRow> timeline,
            List<ChildView> children,
            List<Row> attributes,
            List<NoteView> notes,
            List<SourceUse> sources,
            List<MediaTile> media) {}

    /**
     * A photo or other media file in a list.
     *
     * @param thumbnail the URL of its thumbnail, or empty if the file cannot be shown as an image
     * @param caption   e.g. "1897 · marked in this photo"
     */
    public record MediaTile(String url, String thumbnail, String title, String caption) {}

    /**
     * A person marked in a photo, with the region in pixels of the displayed image.
     *
     * @param number shown on the photo and beside the name
     */
    public record MarkedPerson(int number, PersonLink person, int x, int y, int width, int height) {}

    /**
     * A media object.
     *
     * @param image    the URL of the image to show, or empty if the file cannot be shown as one
     * @param original the URL of the file itself, or empty if it is not served
     * @param width    of the displayed image in pixels, the coordinate system of {@code marked}
     * @param marker   the radius of the number markers in the same pixels
     * @param linkedFrom other objects that use the media, besides the marked people
     */
    public record MediaPage(
            String title,
            String group,
            String groupUrl,
            String summary,
            String image,
            String original,
            int width,
            int height,
            int marker,
            List<MarkedPerson> marked,
            List<Row> details,
            List<Cited> linkedFrom,
            List<NoteView> notes,
            List<SourceUse> sources) {}

    /** Photos of a decade, or undated ones. */
    public record PhotoGroup(String id, String label, List<MediaTile> tiles) {}

    public record PhotosPage(List<PhotoGroup> groups, int total) {}

    public record SurnameEntry(String surname, String url, int count) {}

    /** A heading of the surname index, such as "Č" or "Ch" in Czech, with its surnames. */
    public record Letter(String label, List<SurnameEntry> surnames) {}

    public record SurnameIndex(List<Letter> letters, int withoutSurname) {}

    public record SurnamePage(String surname, List<PersonLink> people) {}

    public record HomePage(
            PersonLink homePerson,
            int people,
            int families,
            int events,
            int places,
            int sources,
            int media,
            int surnames,
            String exported) {}
}
