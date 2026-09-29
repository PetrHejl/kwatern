package me.hejl.kwatern.view;

import me.hejl.gramps.model.Family;
import me.hejl.gramps.model.Media;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.Place;
import me.hejl.gramps.model.Source;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.kwatern.view.Pages.AncestorsPage;
import me.hejl.kwatern.view.Pages.DescendantsPage;
import me.hejl.kwatern.view.Pages.FamilyPage;
import me.hejl.kwatern.view.Pages.HomePage;
import me.hejl.kwatern.view.Pages.MediaPage;
import me.hejl.kwatern.view.Pages.PersonMapPage;
import me.hejl.kwatern.view.Pages.PersonPage;
import me.hejl.kwatern.view.Pages.PhotosPage;
import me.hejl.kwatern.view.Pages.PlacePage;
import me.hejl.kwatern.view.Pages.PlacesPage;
import me.hejl.kwatern.view.Pages.SearchPage;
import me.hejl.kwatern.view.Pages.SourcePage;
import me.hejl.kwatern.view.Pages.SourcesPage;
import me.hejl.kwatern.view.Pages.SurnameIndex;
import me.hejl.kwatern.view.Pages.SurnamePage;
import me.hejl.kwatern.view.Pages.TreeMapPage;
import me.hejl.kwatern.web.MediaImages;
import me.hejl.kwatern.web.Ui;

/**
 * Builds page models from the published database for one page language and one request. The pages are made by
 * the parts: {@link ListingViews}, {@link PersonViews}, {@link ChartViews}, {@link PlaceViews} and
 * {@link MediaViews}, which share their helpers through {@link ViewPart}.
 */
public final class Views {

    /** The tabs of the pages about a person. */
    public enum Tabs {
        OVERVIEW,
        ANCESTORS,
        DESCENDANTS,
        MAP
    }

    /** Generations of the ancestor chart, including the person. */
    public static final int ANCESTOR_GENERATIONS = 4;

    /** Generations of the descendant chart, including the person. */
    public static final int DESCENDANT_GENERATIONS = 3;

    private final ListingViews listings;
    private final PersonViews people;
    private final ChartViews charts;
    private final PlaceViews placeViews;
    private final MediaViews mediaViews;

    public Views(PublicDatabase data, TreeIndex index, Ui ui, MediaImages images) {
        this.listings = new ListingViews(data, index, ui, images);
        this.people = new PersonViews(data, index, ui, images);
        this.charts = new ChartViews(data, index, ui, images, people);
        this.placeViews = new PlaceViews(data, index, ui, images);
        this.mediaViews = new MediaViews(data, index, ui, images);
    }

    public HomePage home() {
        return listings.home();
    }

    /** All surnames under the letters of the page language's alphabet; the same on every request. */
    public SurnameIndex surnames() {
        return listings.surnames();
    }

    /** The people listed under a surname, or {@code null} if there are none. */
    public SurnamePage surname(String surname) {
        return listings.surname(surname);
    }

    /** All sources, alphabetically under letter headings; the same on every request. */
    public SourcesPage sources() {
        return listings.sources();
    }

    public SourcePage source(Source source) {
        return listings.source(source);
    }

    public SearchPage search(String query, SearchIndex index) {
        return listings.search(query, index);
    }

    /** A person's overview page, with or without the events of their family in the timeline. */
    public PersonPage person(Person person, boolean familyEvents) {
        return people.person(person, familyEvents);
    }

    public FamilyPage family(Family family) {
        return people.family(family);
    }

    /** The ancestor chart of a person: a tree or a fan of {@code generations} generations including the person. */
    public AncestorsPage ancestors(Person person, int generations, boolean fan) {
        return charts.ancestors(person, generations, fan);
    }

    /** The descendants of a person over {@code generations} generations including the person. */
    public DescendantsPage descendants(Person person, int generations) {
        return charts.descendants(person, generations);
    }

    /** The places of a person's life, numbered in the order of the timeline, and optionally of their family. */
    public PersonMapPage personMap(Person person, boolean family) {
        return charts.personMap(person, family);
    }

    public PlacePage place(Place place) {
        return placeViews.place(place);
    }

    /** All places as a tree under the places that are not within another; the same on every request. */
    public PlacesPage places() {
        return placeViews.places();
    }

    /**
     * All places with events on one map, as circles by the number of events; the same on every request.
     *
     * @param kind   {@code births}, {@code marriages}, {@code deaths}, or anything else for all events
     * @param period {@code before1800}, {@code 1800s}, {@code 1900s}, or anything else for any time
     */
    public TreeMapPage treeMap(String kind, String period) {
        return placeViews.treeMap(kind, period);
    }

    /** The media page: the image with the people marked in it, and everything that uses it. */
    public MediaPage media(Media media) {
        return mediaViews.media(media);
    }

    /** All media, by decade of their date, undated last; the same on every request. */
    public PhotosPage photos() {
        return mediaViews.photos();
    }
}
