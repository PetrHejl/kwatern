package me.hejl.gramps.privacy;

/**
 * What {@link PrivacyFilter} hides.
 *
 * @param hideLiving  show people who may be alive only as "Living", without details
 * @param hidePrivate remove objects and details marked private in Gramps
 */
public record PrivacyOptions(boolean hideLiving, boolean hidePrivate) {

    /** Hide both; the safe choice for a public site. */
    public static final PrivacyOptions DEFAULT = new PrivacyOptions(true, true);
}
