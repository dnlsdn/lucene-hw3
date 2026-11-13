package it.uniroma3.idd.hw3;

import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.EnglishMinimalStemFilter;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.KStemFilter;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * Analyzers for the two fields of the index.
 *
 * <p>The final choice is {@link #FILENAME} for the "nome" field and {@link #ENGLISH}
 * (Porter stemming) for the "contenuto" field. The other analyzers exist only for the
 * comparison in {@link Evaluate}.
 */
public final class Analyzers {

    public static final String NAME_FIELD = "nome";
    public static final String CONTENT_FIELD = "contenuto";

    // --- analyzers for the content field ---
    public static final String WHITESPACE = "whitespace";
    public static final String STANDARD_NO_STOP = "standard-nostop";
    public static final String STANDARD = "standard";
    public static final String ENGLISH_MINIMAL = "english-minimal";
    public static final String ENGLISH_KSTEM = "english-kstem";
    public static final String ENGLISH = "english";

    // --- analyzers for the name field ---
    public static final String KEYWORD = "keyword";
    public static final String FILENAME = "filename";

    public static final List<String> CONTENT_CHOICES =
            List.of(WHITESPACE, STANDARD_NO_STOP, STANDARD, ENGLISH_MINIMAL, ENGLISH_KSTEM, ENGLISH);
    public static final List<String> NAME_CHOICES = List.of(KEYWORD, WHITESPACE, STANDARD, FILENAME);

    public static final String DEFAULT_NAME = FILENAME;
    public static final String DEFAULT_CONTENT = ENGLISH;

    private Analyzers() {}

    public static Analyzer byName(String name) {
        return switch (name) {
            case KEYWORD -> new KeywordAnalyzer();
            case WHITESPACE -> new WhitespaceAnalyzer();
            case STANDARD_NO_STOP -> new StandardAnalyzer(CharArraySet.EMPTY_SET);
            case STANDARD -> new StandardAnalyzer(EnglishAnalyzer.ENGLISH_STOP_WORDS_SET);
            case ENGLISH_MINIMAL -> new EnglishStemming(Stemmer.MINIMAL);
            case ENGLISH_KSTEM -> new EnglishStemming(Stemmer.KSTEM);
            case ENGLISH -> new EnglishAnalyzer(); // standard tokenizer, possessive, lowercase, stop, Porter
            case FILENAME -> new FilenameAnalyzer();
            default -> throw new IllegalArgumentException("unknown analyzer: " + name);
        };
    }

    /** One analyzer per field: the same wrapper is used for indexing and for querying. */
    public static Analyzer perField(String nameAnalyzer, String contentAnalyzer) {
        return new PerFieldAnalyzerWrapper(
                byName(contentAnalyzer),
                Map.of(NAME_FIELD, byName(nameAnalyzer), CONTENT_FIELD, byName(contentAnalyzer)));
    }

    public static Analyzer perField() {
        return perField(DEFAULT_NAME, DEFAULT_CONTENT);
    }

    /**
     * File names such as {@code news-comp.graphics-38851.txt} or
     * {@code cran-0001_experimental-investigation-of-the-aerodynamics.txt}:
     * no spaces, words joined by '-', '_', '.' or by case changes (camelCase).
     *
     * <ul>
     *   <li>WhitespaceTokenizer keeps the whole name as one token;</li>
     *   <li>WordDelimiterGraphFilter splits it on punctuation, case changes and
     *       letter/digit boundaries (news, comp, graphics, 38851);</li>
     *   <li>LowerCaseFilter makes the search case-insensitive;</li>
     *   <li>StopFilter drops "txt", which every file has and which says nothing.</li>
     * </ul>
     * No stemming: a name is an identifier, the user types the words it contains.
     * Without the catenate/preserve options the filter produces a linear token graph,
     * so no FlattenGraphFilter is needed at index time.
     */
    static final class FilenameAnalyzer extends Analyzer {
        private static final CharArraySet EXTENSIONS = new CharArraySet(List.of("txt"), true);
        private static final int FLAGS = WordDelimiterGraphFilter.GENERATE_WORD_PARTS
                | WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS
                | WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE
                | WordDelimiterGraphFilter.SPLIT_ON_NUMERICS;

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new WhitespaceTokenizer();
            TokenStream stream = new WordDelimiterGraphFilter(source, FLAGS, null);
            stream = new LowerCaseFilter(stream);
            stream = new StopFilter(stream, EXTENSIONS);
            return new TokenStreamComponents(source, stream);
        }
    }

    enum Stemmer { MINIMAL, KSTEM }

    /** Same chain as EnglishAnalyzer, with a lighter stemmer in place of Porter. */
    static final class EnglishStemming extends Analyzer {
        private final Stemmer stemmer;

        EnglishStemming(Stemmer stemmer) {
            this.stemmer = stemmer;
        }

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new StandardTokenizer();
            TokenStream stream = new EnglishPossessiveFilter(source);
            stream = new LowerCaseFilter(stream);
            stream = new StopFilter(stream, EnglishAnalyzer.ENGLISH_STOP_WORDS_SET);
            stream = switch (stemmer) {
                case MINIMAL -> new EnglishMinimalStemFilter(stream); // plurals only
                case KSTEM -> new KStemFilter(stream);                // dictionary-based, conservative
            };
            return new TokenStreamComponents(source, stream);
        }
    }
}
