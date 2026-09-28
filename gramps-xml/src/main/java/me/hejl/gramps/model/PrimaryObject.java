package me.hejl.gramps.model;

/** Common identity of the top-level Gramps objects. */
public sealed interface PrimaryObject permits Person, Family, Event, Place, Source, Citation, Media, Repository, Note {

    /** Internal Gramps handle, stable across exports. */
    String handle();

    /** User-visible Gramps ID such as {@code I0042}; may be {@code null}. */
    String id();

    /** Last change as a Unix timestamp in seconds. */
    long change();

    /** Whether the object is marked private in Gramps. */
    boolean priv();
}
