/**
 * Immutable in-memory model of a Gramps XML database.
 *
 * <p>References between objects are kept as Gramps handles (the {@code hlink} values in the XML)
 * and resolved through {@link me.hejl.gramps.model.GrampsDatabase}. Optional scalar values are
 * {@code null} when absent; lists are never {@code null}.
 */
package me.hejl.gramps.model;
