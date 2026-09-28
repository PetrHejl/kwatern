package me.hejl.gramps.xml;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import me.hejl.gramps.model.Address;
import me.hejl.gramps.model.Attribute;
import me.hejl.gramps.model.Bookmark;
import me.hejl.gramps.model.ChildRef;
import me.hejl.gramps.model.Citation;
import me.hejl.gramps.model.DateValue;
import me.hejl.gramps.model.Event;
import me.hejl.gramps.model.EventRef;
import me.hejl.gramps.model.Family;
import me.hejl.gramps.model.Gender;
import me.hejl.gramps.model.GrampsCalendar;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.GrampsDate;
import me.hejl.gramps.model.GrampsDate.Modifier;
import me.hejl.gramps.model.GrampsDate.Quality;
import me.hejl.gramps.model.Header;
import me.hejl.gramps.model.LdsOrdinance;
import me.hejl.gramps.model.Location;
import me.hejl.gramps.model.Media;
import me.hejl.gramps.model.MediaRef;
import me.hejl.gramps.model.Name;
import me.hejl.gramps.model.NameFormat;
import me.hejl.gramps.model.NameMap;
import me.hejl.gramps.model.Note;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.PersonRef;
import me.hejl.gramps.model.Place;
import me.hejl.gramps.model.PlaceName;
import me.hejl.gramps.model.PlaceRef;
import me.hejl.gramps.model.Region;
import me.hejl.gramps.model.RepoRef;
import me.hejl.gramps.model.Repository;
import me.hejl.gramps.model.Researcher;
import me.hejl.gramps.model.Source;
import me.hejl.gramps.model.StyledText;
import me.hejl.gramps.model.Surname;
import me.hejl.gramps.model.Tag;
import me.hejl.gramps.model.Url;

/**
 * Streaming parser for the Gramps XML format (schema 1.x).
 *
 * <p>It is lenient: unknown elements are skipped and unknown attribute values fall back to defaults,
 * each reported once as a warning, so exports from newer Gramps versions still load.
 *
 * <p>Every element method is entered positioned on the element's start tag and returns positioned on
 * its end tag.
 */
final class GrampsXmlParser {

    private static final String NAMESPACE_PREFIX = "http://gramps-project.org/xml/";

    private final XMLStreamReader r;
    private final Map<String, Integer> warningCounts = new TreeMap<>();

    // Attributes of the current start tag not read yet; reported when the reader moves on.
    private final Set<String> unread = new HashSet<>();
    private String unreadElement;

    // Names of the open elements, innermost first, to say where an unknown element was found.
    private final Deque<String> open = new ArrayDeque<>();

    private GrampsXmlParser(XMLStreamReader reader) {
        this.r = reader;
    }

    static ParseResult parse(InputStream in) throws GrampsParseException {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        // The DOCTYPE points to a DTD on the web; never fetch it, and never resolve external entities.
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(in, "UTF-8");
            try {
                return new GrampsXmlParser(reader).document();
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new GrampsParseException("Malformed Gramps XML: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new GrampsParseException("Invalid Gramps XML: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- document

    private ParseResult document() throws XMLStreamException, GrampsParseException {
        while (r.hasNext() && r.next() != START_ELEMENT) {
            // skip prolog
        }
        if (!r.isStartElement() || !"database".equals(r.getLocalName())) {
            throw new GrampsParseException("Not a Gramps XML export: missing <database> root element");
        }
        begin();
        String schemaVersion = schemaVersion(r.getNamespaceURI());

        Header header = null;
        List<Person> people = new ArrayList<>();
        List<Family> families = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        List<Place> places = new ArrayList<>();
        List<Source> sources = new ArrayList<>();
        List<Citation> citations = new ArrayList<>();
        List<Media> media = new ArrayList<>();
        List<Repository> repositories = new ArrayList<>();
        List<Note> notes = new ArrayList<>();
        List<Tag> tags = new ArrayList<>();
        List<Bookmark> bookmarks = new ArrayList<>();
        List<NameFormat> nameFormats = new ArrayList<>();
        List<NameMap> nameMaps = new ArrayList<>();
        String homePerson = null;

        while (nextChild()) {
            switch (r.getLocalName()) {
                case "header" -> header = header();
                case "name-formats" -> each("format", () -> nameFormats.add(nameFormat()));
                case "tags" -> each("tag", () -> tags.add(tag()));
                case "events" -> each("event", () -> events.add(event()));
                case "people" -> {
                    homePerson = attr("home");
                    ignore("default"); // Gramps' "default person" setting, not needed for viewing
                    each("person", () -> people.add(person()));
                }
                case "families" -> each("family", () -> families.add(family()));
                case "citations" -> each("citation", () -> citations.add(citation()));
                case "sources" -> each("source", () -> sources.add(source()));
                case "places" -> each("placeobj", () -> places.add(place()));
                case "objects" -> each("object", () -> media.add(media()));
                case "repositories" -> each("repository", () -> repositories.add(repository()));
                case "notes" -> each("note", () -> notes.add(note()));
                case "bookmarks" ->
                    each("bookmark", () -> {
                        bookmarks.add(new Bookmark(attr("target"), attr("hlink")));
                        skip();
                    });
                case "namemaps" ->
                    each("map", () -> {
                        nameMaps.add(new NameMap(attr("type"), attr("key"), attr("value")));
                        skip();
                    });
                default -> unknown();
            }
        }
        if (header == null) {
            header = new Header(null, null, null, null);
        }
        var database = new GrampsDatabase(
                schemaVersion,
                header,
                people,
                families,
                events,
                places,
                sources,
                citations,
                media,
                repositories,
                notes,
                tags,
                homePerson,
                bookmarks,
                nameFormats,
                nameMaps);
        return new ParseResult(database, warnings());
    }

    private String schemaVersion(String namespace) throws GrampsParseException {
        if (namespace == null || !namespace.startsWith(NAMESPACE_PREFIX)) {
            throw new GrampsParseException("Not a Gramps XML export: unexpected namespace " + namespace);
        }
        String version = namespace.substring(NAMESPACE_PREFIX.length()).replaceAll("/+$", "");
        if (!version.startsWith("1.")) {
            throw new GrampsParseException("Unsupported Gramps XML schema version " + version);
        }
        return version;
    }

    private Header header() throws XMLStreamException {
        String created = null;
        String version = null;
        Researcher researcher = null;
        String mediaPath = null;
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "created" -> {
                    created = attr("date");
                    version = attr("version");
                    skip();
                }
                case "researcher" -> researcher = researcher();
                case "mediapath" -> mediaPath = text();
                default -> unknown();
            }
        }
        return new Header(created, version, researcher, mediaPath);
    }

    private Researcher researcher() throws XMLStreamException {
        String name = null, address = null, locality = null, city = null, state = null;
        String country = null, postal = null, phone = null, email = null;
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "resname" -> name = text();
                case "resaddr" -> address = text();
                case "reslocality" -> locality = text();
                case "rescity" -> city = text();
                case "resstate" -> state = text();
                case "rescountry" -> country = text();
                case "respostal" -> postal = text();
                case "resphone" -> phone = text();
                case "resemail" -> email = text();
                default -> unknown();
            }
        }
        return new Researcher(name, address, locality, city, state, country, postal, phone, email);
    }

    private NameFormat nameFormat() throws XMLStreamException {
        var format = new NameFormat(intAttr("number", 0), attr("name"), attr("fmt_str"), flag("active"));
        skip();
        return format;
    }

    private Tag tag() throws XMLStreamException {
        var tag = new Tag(attr("handle"), change(), attr("name"), attr("color"), intAttr("priority", 0));
        skip();
        return tag;
    }

    // ---------------------------------------------------------------- primary objects

    private Person person() throws XMLStreamException {
        String handle = attr("handle"), id = attr("id");
        long change = change();
        boolean priv = flag("priv");
        Gender gender = Gender.UNKNOWN;
        List<Name> names = new ArrayList<>();
        List<EventRef> eventRefs = new ArrayList<>();
        List<LdsOrdinance> lds = new ArrayList<>();
        List<MediaRef> media = new ArrayList<>();
        List<Address> addresses = new ArrayList<>();
        List<Attribute> attributes = new ArrayList<>();
        List<Url> urls = new ArrayList<>();
        List<String> parentFamilies = new ArrayList<>();
        List<String> families = new ArrayList<>();
        List<PersonRef> associations = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> citations = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "gender" -> gender = gender(text());
                case "name" -> names.add(name());
                case "eventref" -> eventRefs.add(eventRef());
                case "lds_ord" -> lds.add(lds());
                case "objref" -> media.add(mediaRef());
                case "address" -> addresses.add(address());
                case "attribute" -> attributes.add(attribute());
                case "url" -> urls.add(url());
                case "childof" -> parentFamilies.add(hlink());
                case "parentin" -> families.add(hlink());
                case "personref" -> associations.add(personRef());
                case "noteref" -> notes.add(hlink());
                case "citationref" -> citations.add(hlink());
                case "tagref" -> tags.add(hlink());
                default -> unknown();
            }
        }
        return new Person(
                handle,
                id,
                change,
                priv,
                gender,
                names,
                eventRefs,
                lds,
                media,
                addresses,
                attributes,
                urls,
                parentFamilies,
                families,
                associations,
                notes,
                citations,
                tags);
    }

    private Family family() throws XMLStreamException {
        String handle = attr("handle"), id = attr("id");
        long change = change();
        boolean priv = flag("priv");
        String relationship = null, father = null, mother = null;
        List<EventRef> eventRefs = new ArrayList<>();
        List<LdsOrdinance> lds = new ArrayList<>();
        List<MediaRef> media = new ArrayList<>();
        List<ChildRef> children = new ArrayList<>();
        List<Attribute> attributes = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> citations = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "rel" -> {
                    relationship = attr("type");
                    skip();
                }
                case "father" -> father = hlink();
                case "mother" -> mother = hlink();
                case "eventref" -> eventRefs.add(eventRef());
                case "lds_ord" -> lds.add(lds());
                case "objref" -> media.add(mediaRef());
                case "childref" -> children.add(childRef());
                case "attribute" -> attributes.add(attribute());
                case "noteref" -> notes.add(hlink());
                case "citationref" -> citations.add(hlink());
                case "tagref" -> tags.add(hlink());
                // The schema allows a date on a family, but Gramps does not write one and the model has none.
                case "dateval", "daterange", "datespan", "datestr" -> ignoreElement();
                default -> unknown();
            }
        }
        return new Family(
                handle,
                id,
                change,
                priv,
                relationship,
                father,
                mother,
                eventRefs,
                lds,
                media,
                children,
                attributes,
                notes,
                citations,
                tags);
    }

    private Event event() throws XMLStreamException {
        String handle = attr("handle"), id = attr("id");
        long change = change();
        boolean priv = flag("priv");
        String type = null, place = null, description = null;
        GrampsDate date = null;
        List<Attribute> attributes = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> citations = new ArrayList<>();
        List<MediaRef> media = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "type" -> type = text();
                case "dateval", "daterange", "datespan", "datestr" -> date = date();
                case "place" -> place = hlink();
                case "description" -> description = text();
                case "attribute" -> attributes.add(attribute());
                case "noteref" -> notes.add(hlink());
                case "citationref" -> citations.add(hlink());
                case "objref" -> media.add(mediaRef());
                case "tagref" -> tags.add(hlink());
                default -> unknown();
            }
        }
        return new Event(
                handle, id, change, priv, type, date, place, description, attributes, notes, citations, media, tags);
    }

    private Place place() throws XMLStreamException {
        String handle = attr("handle"), id = attr("id");
        long change = change();
        boolean priv = flag("priv");
        String type = attr("type");
        String title = null, code = null, latitude = null, longitude = null;
        List<PlaceName> names = new ArrayList<>();
        List<PlaceRef> enclosedBy = new ArrayList<>();
        List<Location> locations = new ArrayList<>();
        List<MediaRef> media = new ArrayList<>();
        List<Url> urls = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> citations = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "ptitle" -> title = text();
                case "code" -> code = text();
                case "pname" -> names.add(placeName());
                case "coord" -> {
                    latitude = attr("lat");
                    longitude = attr("long");
                    skip();
                }
                case "placeref" -> enclosedBy.add(placeRef());
                case "location" -> {
                    locations.add(new Location(
                            attr("street"),
                            attr("locality"),
                            attr("city"),
                            attr("parish"),
                            attr("county"),
                            attr("state"),
                            attr("country"),
                            attr("postal"),
                            attr("phone")));
                    skip();
                }
                case "objref" -> media.add(mediaRef());
                case "url" -> urls.add(url());
                case "noteref" -> notes.add(hlink());
                case "citationref" -> citations.add(hlink());
                case "tagref" -> tags.add(hlink());
                default -> unknown();
            }
        }
        return new Place(
                handle,
                id,
                change,
                priv,
                type,
                title,
                code,
                names,
                latitude,
                longitude,
                enclosedBy,
                locations,
                media,
                urls,
                notes,
                citations,
                tags);
    }

    private Source source() throws XMLStreamException {
        String handle = attr("handle"), id = attr("id");
        long change = change();
        boolean priv = flag("priv");
        String title = null, author = null, pubInfo = null, abbreviation = null;
        List<String> notes = new ArrayList<>();
        List<MediaRef> media = new ArrayList<>();
        List<Attribute> attributes = new ArrayList<>();
        List<RepoRef> repositories = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "stitle" -> title = text();
                case "sauthor" -> author = text();
                case "spubinfo" -> pubInfo = text();
                case "sabbrev" -> abbreviation = text();
                case "noteref" -> notes.add(hlink());
                case "objref" -> media.add(mediaRef());
                case "srcattribute" -> attributes.add(attribute());
                case "reporef" -> repositories.add(repoRef());
                case "tagref" -> tags.add(hlink());
                default -> unknown();
            }
        }
        return new Source(
                handle,
                id,
                change,
                priv,
                title,
                author,
                pubInfo,
                abbreviation,
                notes,
                media,
                attributes,
                repositories,
                tags);
    }

    private Citation citation() throws XMLStreamException {
        String handle = attr("handle"), id = attr("id");
        long change = change();
        boolean priv = flag("priv");
        GrampsDate date = null;
        String page = null, source = null;
        int confidence = 2;
        List<String> notes = new ArrayList<>();
        List<MediaRef> media = new ArrayList<>();
        List<Attribute> attributes = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "dateval", "daterange", "datespan", "datestr" -> date = date();
                case "page" -> page = text();
                case "confidence" -> confidence = parseInt("confidence", text(), 2);
                case "noteref" -> notes.add(hlink());
                case "objref" -> media.add(mediaRef());
                case "srcattribute" -> attributes.add(attribute());
                case "sourceref" -> source = hlink();
                case "tagref" -> tags.add(hlink());
                default -> unknown();
            }
        }
        return new Citation(handle, id, change, priv, date, page, confidence, notes, media, attributes, source, tags);
    }

    private Media media() throws XMLStreamException {
        String handle = attr("handle"), id = attr("id");
        long change = change();
        boolean priv = flag("priv");
        String path = null, mime = null, checksum = null, description = null;
        GrampsDate date = null;
        List<Attribute> attributes = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> citations = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "file" -> {
                    path = attr("src");
                    mime = attr("mime");
                    checksum = attr("checksum");
                    description = attr("description");
                    skip();
                }
                case "attribute" -> attributes.add(attribute());
                case "dateval", "daterange", "datespan", "datestr" -> date = date();
                case "noteref" -> notes.add(hlink());
                case "citationref" -> citations.add(hlink());
                case "tagref" -> tags.add(hlink());
                default -> unknown();
            }
        }
        return new Media(
                handle, id, change, priv, path, mime, checksum, description, attributes, date, notes, citations, tags);
    }

    private Repository repository() throws XMLStreamException {
        String handle = attr("handle"), id = attr("id");
        long change = change();
        boolean priv = flag("priv");
        String name = null, type = null;
        List<Address> addresses = new ArrayList<>();
        List<Url> urls = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "rname" -> name = text();
                case "type" -> type = text();
                case "address" -> addresses.add(address());
                case "url" -> urls.add(url());
                case "noteref" -> notes.add(hlink());
                case "tagref" -> tags.add(hlink());
                default -> unknown();
            }
        }
        return new Repository(handle, id, change, priv, name, type, addresses, urls, notes, tags);
    }

    private Note note() throws XMLStreamException {
        String handle = attr("handle"), id = attr("id");
        long change = change();
        boolean priv = flag("priv");
        String type = attr("type");
        boolean preformatted = flag("format");
        String text = "";
        List<StyledText.Style> styles = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "text" -> text = text();
                case "style" -> styles.add(style());
                case "tagref" -> tags.add(hlink());
                default -> unknown();
            }
        }
        return new Note(handle, id, change, priv, type, preformatted, new StyledText(text, styles), tags);
    }

    private StyledText.Style style() throws XMLStreamException {
        String name = attr("name"), value = attr("value");
        List<StyledText.Range> ranges = new ArrayList<>();
        while (nextChild()) {
            if ("range".equals(r.getLocalName())) {
                ranges.add(new StyledText.Range(intAttr("start", 0), intAttr("end", 0)));
                skip();
            } else {
                unknown();
            }
        }
        return new StyledText.Style(name, value, ranges);
    }

    // ---------------------------------------------------------------- secondary objects

    private Name name() throws XMLStreamException {
        boolean alternate = flag("alt");
        boolean priv = flag("priv");
        String type = attr("type");
        int sortAs = intAttr("sort", 0), displayAs = intAttr("display", 0);
        String first = null, call = null, suffix = null, title = null, nick = null, familyNick = null, group = null;
        GrampsDate date = null;
        List<Surname> surnames = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> citations = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "first" -> first = text();
                case "call" -> call = text();
                case "surname" -> surnames.add(surname());
                case "suffix" -> suffix = text();
                case "title" -> title = text();
                case "nick" -> nick = text();
                case "familynick" -> familyNick = text();
                case "group" -> group = text();
                case "dateval", "daterange", "datespan", "datestr" -> date = date();
                case "noteref" -> notes.add(hlink());
                case "citationref" -> citations.add(hlink());
                default -> unknown();
            }
        }
        return new Name(
                alternate,
                type,
                first,
                call,
                surnames,
                suffix,
                title,
                nick,
                familyNick,
                group,
                sortAs,
                displayAs,
                date,
                priv,
                citations,
                notes);
    }

    private Surname surname() throws XMLStreamException {
        String prefix = attr("prefix");
        // Gramps omits prim="1" on the primary surname and writes prim="0" on the others.
        boolean primary = !"0".equals(attr("prim"));
        String origin = attr("derivation"), connector = attr("connector");
        return new Surname(text(), prefix, primary, origin, connector);
    }

    private EventRef eventRef() throws XMLStreamException {
        String event = attr("hlink");
        String role = attrOr("role", "Primary");
        boolean priv = flag("priv");
        List<Attribute> attributes = new ArrayList<>();
        List<String> citations = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "attribute" -> attributes.add(attribute());
                case "citationref" -> citations.add(hlink());
                case "noteref" -> notes.add(hlink());
                default -> unknown();
            }
        }
        return new EventRef(event, role, priv, attributes, citations, notes);
    }

    private MediaRef mediaRef() throws XMLStreamException {
        String media = attr("hlink");
        boolean priv = flag("priv");
        Region region = null;
        List<Attribute> attributes = new ArrayList<>();
        List<String> citations = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "region" -> {
                    region = new Region(
                            intAttr("corner1_x", 0),
                            intAttr("corner1_y", 0),
                            intAttr("corner2_x", 100),
                            intAttr("corner2_y", 100));
                    skip();
                }
                case "attribute" -> attributes.add(attribute());
                case "citationref" -> citations.add(hlink());
                case "noteref" -> notes.add(hlink());
                default -> unknown();
            }
        }
        return new MediaRef(media, priv, region, attributes, citations, notes);
    }

    private ChildRef childRef() throws XMLStreamException {
        String child = attr("hlink");
        String fatherRelation = attrOr("frel", "Birth"), motherRelation = attrOr("mrel", "Birth");
        boolean priv = flag("priv");
        List<String> citations = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        citationsAndNotes(citations, notes);
        return new ChildRef(child, fatherRelation, motherRelation, priv, citations, notes);
    }

    private PersonRef personRef() throws XMLStreamException {
        String person = attr("hlink"), relation = attr("rel");
        boolean priv = flag("priv");
        List<String> citations = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        citationsAndNotes(citations, notes);
        return new PersonRef(person, relation, priv, citations, notes);
    }

    private RepoRef repoRef() throws XMLStreamException {
        String repository = attr("hlink"), callNumber = attr("callno"), medium = attr("medium");
        boolean priv = flag("priv");
        List<String> notes = new ArrayList<>();
        while (nextChild()) {
            if ("noteref".equals(r.getLocalName())) {
                notes.add(hlink());
            } else {
                unknown();
            }
        }
        return new RepoRef(repository, callNumber, medium, priv, notes);
    }

    private PlaceRef placeRef() throws XMLStreamException {
        String place = attr("hlink");
        GrampsDate date = null;
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "dateval", "daterange", "datespan", "datestr" -> date = date();
                default -> unknown();
            }
        }
        return new PlaceRef(place, date);
    }

    private PlaceName placeName() throws XMLStreamException {
        String value = attr("value"), lang = attr("lang");
        GrampsDate date = null;
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "dateval", "daterange", "datespan", "datestr" -> date = date();
                default -> unknown();
            }
        }
        return new PlaceName(value, lang, date);
    }

    private LdsOrdinance lds() throws XMLStreamException {
        String type = attr("type");
        boolean priv = flag("priv");
        GrampsDate date = null;
        String temple = null, place = null, status = null, sealedTo = null;
        List<String> citations = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "dateval", "daterange", "datespan", "datestr" -> date = date();
                case "temple" -> {
                    temple = attr("val");
                    skip();
                }
                case "place" -> place = hlink();
                case "status" -> {
                    status = attr("val");
                    skip();
                }
                case "sealed_to" -> sealedTo = hlink();
                case "citationref" -> citations.add(hlink());
                case "noteref" -> notes.add(hlink());
                default -> unknown();
            }
        }
        return new LdsOrdinance(type, date, temple, place, status, sealedTo, priv, citations, notes);
    }

    private Address address() throws XMLStreamException {
        boolean priv = flag("priv");
        GrampsDate date = null;
        String street = null, locality = null, city = null, county = null, state = null;
        String country = null, postal = null, phone = null;
        List<String> citations = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "dateval", "daterange", "datespan", "datestr" -> date = date();
                case "street" -> street = text();
                case "locality" -> locality = text();
                case "city" -> city = text();
                case "county" -> county = text();
                case "state" -> state = text();
                case "country" -> country = text();
                case "postal" -> postal = text();
                case "phone" -> phone = text();
                case "citationref" -> citations.add(hlink());
                case "noteref" -> notes.add(hlink());
                default -> unknown();
            }
        }
        return new Address(date, street, locality, city, county, state, country, postal, phone, priv, citations, notes);
    }

    private Attribute attribute() throws XMLStreamException {
        String type = attr("type"), value = attr("value");
        boolean priv = flag("priv");
        List<String> citations = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        citationsAndNotes(citations, notes);
        return new Attribute(type, value, priv, citations, notes);
    }

    private Url url() throws XMLStreamException {
        var url = new Url(attr("href"), attr("type"), attr("description"), flag("priv"));
        skip();
        return url;
    }

    private void citationsAndNotes(List<String> citations, List<String> notes) throws XMLStreamException {
        while (nextChild()) {
            switch (r.getLocalName()) {
                case "citationref" -> citations.add(hlink());
                case "noteref" -> notes.add(hlink());
                default -> unknown();
            }
        }
    }

    // ---------------------------------------------------------------- dates

    private GrampsDate date() throws XMLStreamException {
        String element = r.getLocalName();
        Quality quality = quality(attr("quality"));
        GrampsCalendar calendar = calendar(attr("cformat"));
        String newYear = attr("newyear");
        boolean dualDated = flag("dualdated");
        GrampsDate date = switch (element) {
            case "dateval" -> {
                String val = attr("val");
                DateValue value = DateValue.parse(val);
                yield value == null
                        ? invalidDate(val)
                        : new GrampsDate(
                                modifier(attr("type")), quality, calendar, value, null, null, newYear, dualDated);
            }
            case "daterange", "datespan" -> {
                String start = attr("start"), stop = attr("stop");
                DateValue startValue = DateValue.parse(start), stopValue = DateValue.parse(stop);
                Modifier modifier = element.equals("daterange") ? Modifier.RANGE : Modifier.SPAN;
                yield startValue == null || stopValue == null
                        ? invalidDate(start + " - " + stop)
                        : new GrampsDate(modifier, quality, calendar, startValue, stopValue, null, newYear, dualDated);
            }
            default -> GrampsDate.textOnly(attr("val"));
        };
        skip();
        return date;
    }

    private GrampsDate invalidDate(String text) {
        warn("Unparseable date value \"" + text + "\" kept as text");
        return GrampsDate.textOnly(text);
    }

    private Modifier modifier(String type) {
        if (type == null) {
            return Modifier.NONE;
        }
        return switch (type) {
            case "before" -> Modifier.BEFORE;
            case "after" -> Modifier.AFTER;
            case "about" -> Modifier.ABOUT;
            case "from" -> Modifier.FROM;
            case "to" -> Modifier.TO;
            default -> {
                warn("Unknown date type \"" + type + "\" treated as a plain date");
                yield Modifier.NONE;
            }
        };
    }

    private GrampsCalendar calendar(String name) {
        if (name == null) {
            return GrampsCalendar.GREGORIAN;
        }
        GrampsCalendar calendar = GrampsCalendar.fromXmlName(name);
        if (calendar == null) {
            warn("Unknown calendar \"" + name + "\" treated as Gregorian");
            return GrampsCalendar.GREGORIAN;
        }
        return calendar;
    }

    private Quality quality(String quality) {
        if (quality == null) {
            return Quality.REGULAR;
        }
        return switch (quality) {
            case "estimated" -> Quality.ESTIMATED;
            case "calculated" -> Quality.CALCULATED;
            default -> {
                warn("Unknown date quality \"" + quality + "\" ignored");
                yield Quality.REGULAR;
            }
        };
    }

    private Gender gender(String value) {
        return switch (value.strip()) {
            case "M" -> Gender.MALE;
            case "F" -> Gender.FEMALE;
            case "X" -> Gender.OTHER;
            case "U" -> Gender.UNKNOWN;
            default -> {
                warn("Unknown gender \"" + value + "\" treated as unknown");
                yield Gender.UNKNOWN;
            }
        };
    }

    // ---------------------------------------------------------------- StAX helpers

    /** Advances to the next child start tag; returns {@code false} at the parent's end tag. */
    private boolean nextChild() throws XMLStreamException {
        while (true) {
            int event = next();
            if (event == START_ELEMENT) {
                begin();
                return true;
            }
            if (event == END_ELEMENT) {
                open.pop();
                return false;
            }
        }
    }

    /** Runs {@code action} for each child named {@code name}, skipping and reporting any others. */
    private void each(String name, ElementAction action) throws XMLStreamException {
        while (nextChild()) {
            if (name.equals(r.getLocalName())) {
                action.run();
            } else {
                unknown();
            }
        }
    }

    /** Skips the rest of the current element, including its children. */
    private void skip() throws XMLStreamException {
        int depth = 1;
        while (depth > 0) {
            int event = next();
            if (event == START_ELEMENT) {
                depth++;
            } else if (event == END_ELEMENT) {
                depth--;
            }
        }
        open.pop();
    }

    private void unknown() throws XMLStreamException {
        String name = open.pop();
        warn("Skipped unknown element <" + name + "> in <" + open.peek() + ">");
        open.push(name);
        unread.clear(); // reported as a whole
        skip();
    }

    /** Advances the reader, first reporting attributes of the start tag being left that were not read. */
    private int next() throws XMLStreamException {
        reportUnread();
        return r.next();
    }

    /** Starts tracking which attributes of the current start tag get read. */
    private void begin() {
        unreadElement = r.getLocalName();
        open.push(unreadElement);
        unread.clear();
        for (int i = 0; i < r.getAttributeCount(); i++) {
            unread.add(r.getAttributeLocalName(i));
        }
    }

    private void reportUnread() {
        for (String name : unread) {
            warn("Ignored unknown attribute " + name + " on <" + unreadElement + ">");
        }
        unread.clear();
    }

    /** Skips an element the parser deliberately does not use, without reporting it or its attributes. */
    private void ignoreElement() throws XMLStreamException {
        unread.clear();
        skip();
    }

    /** Marks attributes that are deliberately not used, so they are not reported. */
    private void ignore(String... names) {
        for (String name : names) {
            unread.remove(name);
        }
    }

    /** Reads the {@code hlink} attribute of the current element and skips to its end. */
    private String hlink() throws XMLStreamException {
        String handle = attr("hlink");
        skip();
        return handle;
    }

    private String text() throws XMLStreamException {
        reportUnread();
        String text = r.getElementText();
        open.pop();
        return text;
    }

    private String attr(String name) {
        unread.remove(name);
        return r.getAttributeValue(null, name);
    }

    private String attrOr(String name, String fallback) {
        String value = attr(name);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private boolean flag(String name) {
        return "1".equals(attr(name));
    }

    private long change() {
        String value = attr("change");
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            warn("Invalid change timestamp \"" + value + "\"");
            return 0;
        }
    }

    private int intAttr(String name, int fallback) {
        return parseInt(name, attr(name), fallback);
    }

    private int parseInt(String what, String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            warn("Invalid " + what + " \"" + value + "\"");
            return fallback;
        }
    }

    private void warn(String message) {
        warningCounts.merge(message, 1, Integer::sum);
    }

    private List<String> warnings() {
        List<String> result = new ArrayList<>(warningCounts.size());
        warningCounts.forEach((message, count) -> result.add(count == 1 ? message : message + " (" + count + "x)"));
        return result;
    }

    @FunctionalInterface
    private interface ElementAction {
        void run() throws XMLStreamException;
    }
}
