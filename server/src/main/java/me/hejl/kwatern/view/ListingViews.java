package me.hejl.kwatern.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import me.hejl.gramps.model.Citation;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.gramps.model.RepoRef;
import me.hejl.gramps.model.Source;
import me.hejl.gramps.name.NameOrder;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.kwatern.view.Pages.CitationEntry;
import me.hejl.kwatern.view.Pages.Cited;
import me.hejl.kwatern.view.Pages.HomePage;
import me.hejl.kwatern.view.Pages.Letter;
import me.hejl.kwatern.view.Pages.PlaceLink;
import me.hejl.kwatern.view.Pages.Row;
import me.hejl.kwatern.view.Pages.SearchPage;
import me.hejl.kwatern.view.Pages.SourceEntry;
import me.hejl.kwatern.view.Pages.SourceLetter;
import me.hejl.kwatern.view.Pages.SourcePage;
import me.hejl.kwatern.view.Pages.SourcesPage;
import me.hejl.kwatern.view.Pages.SurnameEntry;
import me.hejl.kwatern.view.Pages.SurnameIndex;
import me.hejl.kwatern.view.Pages.SurnamePage;
import me.hejl.kwatern.web.MediaImages;
import me.hejl.kwatern.web.Ui;
import me.hejl.kwatern.web.Urls;

/** The home page and the listings of the whole tree: surnames, sources, and search results. */
final class ListingViews extends ViewPart {

    /** People and places shown on a search page. */
    private static final int SEARCH_LIMIT = 50;

    ListingViews(PublicDatabase data, TreeIndex index, Ui ui, MediaImages images) {
        super(data, index, ui, images);
    }

    public HomePage home() {
        Map<String, List<Person>> surnames = index.surnames();
        return new HomePage(
                db.homePerson().map(this::link).orElse(null),
                db.people().size(),
                db.families().size(),
                db.events().size(),
                db.places().size(),
                db.sources().size(),
                db.media().size(),
                surnames.size(),
                db.header().created());
    }

    /** All surnames under the letters of the page language's alphabet; the same on every request. */
    public SurnameIndex surnames() {
        return ui.page("surnames", () -> {
            Map<String, List<Person>> surnames = index.surnames();
            List<Letter> letters = new ArrayList<>();
            Set<String> named = new HashSet<>(surnames.keySet());
            named.remove("");
            for (NameOrder.Bucket<String> bucket : ui.order().alphabeticIndex(named, s -> s)) {
                letters.add(new Letter(
                        bucket.label(),
                        bucket.items().stream()
                                .map(s -> new SurnameEntry(
                                        s, Urls.surname(s), surnames.get(s).size()))
                                .toList()));
            }
            return new SurnameIndex(
                    letters, surnames.getOrDefault("", List.of()).size());
        });
    }

    /** The people listed under a surname, or {@code null} if there are none. */
    public SurnamePage surname(String surname) {
        List<Person> people = index.surnames().getOrDefault(surname, List.of()).stream()
                .sorted(Comparator.comparing(Person::primaryName, ui.order().names()))
                .toList();
        return people.isEmpty()
                ? null
                : new SurnamePage(surname, people.stream().map(this::link).toList());
    }

    public SourcePage source(Source source) {
        List<Row> details = new ArrayList<>();
        addRow(details, "source.author", source.author());
        addRow(details, "source.pubinfo", source.pubInfo());
        addRow(details, "source.abbreviation", source.abbreviation());
        details.addAll(attributes(source.attributes()));
        List<Row> repositories = new ArrayList<>();
        for (RepoRef ref : source.repositories()) {
            db.repositories()
                    .get(ref.repository())
                    .ifPresent(r -> repositories.add(new Row(
                            r.name() == null ? ui.t("unknown") : r.name(),
                            ui.join(
                                    ref.callNumber() == null ? "" : ui.t("source.call_number", ref.callNumber()),
                                    ui.type("medium", ref.medium())))));
        }
        List<CitationEntry> citations = db.referrers(source.handle()).stream()
                .filter(Citation.class::isInstance)
                .map(Citation.class::cast)
                .map(c -> new CitationEntry(
                        c.page() == null ? "" : c.page(),
                        ui.date(c.date()),
                        ui.t("confidence." + Math.clamp(c.confidence(), 0, 4)),
                        cited(c),
                        tiles(c.media())))
                .toList();
        return new SourcePage(
                sourceTitle(source), details, repositories, notes(source.notes()), citations, tiles(source.media()));
    }

    /** All sources, alphabetically under letter headings; the same on every request. */
    public SourcesPage sources() {
        return ui.page("sources", this::makeSources);
    }

    private SourcesPage makeSources() {
        List<SourceEntry> entries = new ArrayList<>();
        for (Source source : db.sources().all()) {
            String title = sourceTitle(source);
            int citations = (int) db.referrers(source.handle()).stream()
                    .filter(Citation.class::isInstance)
                    .count();
            entries.add(new SourceEntry(
                    Urls.source(source), title, source.author() == null ? "" : source.author(), citations));
        }
        List<SourceLetter> letters = new ArrayList<>();
        for (NameOrder.Bucket<SourceEntry> bucket : ui.order().alphabeticIndex(entries, SourceEntry::title)) {
            letters.add(new SourceLetter(bucket.label(), bucket.items()));
        }
        return new SourcesPage(letters, entries.size());
    }

    public SearchPage search(String query, SearchIndex index) {
        SearchIndex.Result result = index.find(query, SEARCH_LIMIT);
        return new SearchPage(
                query,
                result.people().stream()
                        .map(h -> db.people().get(h.handle()).map(this::link).orElse(null))
                        .filter(Objects::nonNull)
                        .toList(),
                result.places().stream()
                        .map(h -> db.places().get(h.handle()).orElse(null))
                        .filter(Objects::nonNull)
                        .map(p -> new PlaceLink(
                                Urls.place(p), ui.places().title(p, null), ui.type("placetype", p.type())))
                        .toList(),
                result.more());
    }

    /** What a citation supports: people, their events, families or places. */
    private List<Cited> cited(Citation citation) {
        List<Cited> cited = new ArrayList<>();
        for (PrimaryObject referrer : db.referrers(citation.handle())) {
            if (!(referrer instanceof Source) && !(referrer instanceof Citation)) {
                describe(referrer).ifPresent(cited::add);
            }
        }
        return cited;
    }
}
