package me.hejl.gramps.xml;

import java.util.List;
import me.hejl.gramps.model.GrampsDatabase;

/**
 * @param warnings things the parser skipped or could not interpret, such as elements from a newer schema
 */
public record ParseResult(GrampsDatabase database, List<String> warnings) {

    public ParseResult {
        warnings = List.copyOf(warnings);
    }
}
