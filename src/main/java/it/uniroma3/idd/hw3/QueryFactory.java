package it.uniroma3.idd.hw3;

import java.util.Locale;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;

/**
 * Turns a console line into a Lucene query.
 *
 * <p>Syntax: the first word chooses the field, the rest are the terms.
 * <pre>
 *   nome graphics                 -> files whose name contains "graphics"
 *   contenuto boundary layer      -> content contains boundary OR layer (ranked)
 *   contenuto "boundary layer"    -> phrase query: the two words adjacent, in this order
 *   contenuto +shock -tunnel      -> shock required, tunnel excluded
 * </pre>
 * The terms are read by the classic QueryParser of the slides, so quotes, '+', '-',
 * wildcards and slop ({@code "heat transfer"~3}) work as documented there.
 * Without a field keyword the terms are searched in both fields.
 */
public final class QueryFactory {

    private final Analyzer analyzer;

    public QueryFactory(Analyzer analyzer) {
        this.analyzer = analyzer; // the same per-field analyzer used at indexing time
    }

    /** Field chosen by the first word, or null when the line has no keyword. */
    static String fieldOf(String keyword) {
        return switch (keyword.toLowerCase(Locale.ROOT)) {
            case "nome", "name" -> Analyzers.NAME_FIELD;
            case "contenuto", "content" -> Analyzers.CONTENT_FIELD;
            default -> null;
        };
    }

    public Query parse(String line) throws ParseException {
        String trimmed = line.strip();
        String[] parts = trimmed.split("\\s+", 2);
        String field = fieldOf(parts[0]);
        if (field != null) {
            if (parts.length < 2 || parts[1].isBlank()) {
                throw new ParseException("no terms after '" + parts[0] + "'");
            }
            QueryParser parser = new QueryParser(field, analyzer);
            // a single typed word that the analyzer splits (comp.graphics, boundary-layer)
            // becomes a phrase query instead of an OR of its pieces
            parser.setSplitOnWhitespace(true); // required by autoGeneratePhraseQueries
            parser.setAutoGeneratePhraseQueries(true);
            return parser.parse(parts[1]);
        }
        MultiFieldQueryParser parser = new MultiFieldQueryParser(
                new String[] {Analyzers.NAME_FIELD, Analyzers.CONTENT_FIELD}, analyzer);
        parser.setSplitOnWhitespace(true);
        parser.setAutoGeneratePhraseQueries(true);
        return parser.parse(trimmed);
    }
}
