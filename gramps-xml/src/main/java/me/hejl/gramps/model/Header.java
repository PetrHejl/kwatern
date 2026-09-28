package me.hejl.gramps.model;

/**
 * Export metadata.
 *
 * @param created       export date as written by Gramps ({@code YYYY-MM-DD})
 * @param grampsVersion version of Gramps that wrote the export
 * @param mediaPath     base directory for relative media paths; may be {@code null}
 */
public record Header(String created, String grampsVersion, Researcher researcher, String mediaPath) {}
