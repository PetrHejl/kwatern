package me.hejl.kwatern.web;

/**
 * The versions of the site served together, each built from the export by the privacy filter with its own
 * options; which one a request sees depends on whether its visitor is signed in. Keeping them apart means a
 * page never has to check who views it: the public version does not contain what it must not show.
 *
 * @param everyone what visitors who are not signed in see, or {@code null} if the site is private
 * @param members  what signed-in members see, or {@code null} if the site has no login
 */
public record Sites(Site everyone, Site members) {

    public Sites {
        if (everyone == null && members == null) {
            throw new IllegalArgumentException("no site");
        }
    }

    /** A site without a login. */
    public static Sites open(Site site) {
        return new Sites(site, null);
    }

    /** Either version, for what they share: the options. */
    public Site any() {
        return everyone != null ? everyone : members;
    }
}
