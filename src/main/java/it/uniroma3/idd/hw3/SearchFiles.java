package it.uniroma3.idd.hw3;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Reads queries from the console and prints the best results.
 *
 * <pre>
 * java -jar target/lucene-hw3.jar index
 * &gt; contenuto "boundary layer" transition
 * </pre>
 * An empty line, "exit" or "quit" ends the program. Lines can also be piped in.
 */
public final class SearchFiles {

    private static final int TOP_K = 10;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: SearchFiles <index dir> [name analyzer] [content analyzer]");
            System.exit(1);
        }
        String nameAnalyzer = args.length > 1 ? args[1] : Analyzers.DEFAULT_NAME;
        String contentAnalyzer = args.length > 2 ? args[2] : Analyzers.DEFAULT_CONTENT;
        QueryFactory factory = new QueryFactory(Analyzers.perField(nameAnalyzer, contentAnalyzer));

        try (Directory dir = FSDirectory.open(Path.of(args[0]));
             DirectoryReader reader = DirectoryReader.open(dir);
             BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            IndexSearcher searcher = new IndexSearcher(reader);
            System.out.printf(Locale.ROOT, "Index: %d documents. Syntax: nome|contenuto <terms>, \"...\" for a phrase.%n",
                    reader.numDocs());
            while (true) {
                System.out.print("> ");
                System.out.flush();
                String line = in.readLine();
                if (line == null || line.isBlank() || line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                    break;
                }
                if (System.console() == null) {
                    System.out.println(line); // echo piped queries so the transcript is readable
                }
                run(searcher, factory, line);
            }
        }
    }

    static void run(IndexSearcher searcher, QueryFactory factory, String line) throws Exception {
        Query query;
        try {
            query = factory.parse(line);
        } catch (ParseException e) {
            System.out.println("  invalid query: " + e.getMessage().lines().findFirst().orElse(""));
            return;
        }
        long t0 = System.nanoTime();
        TopDocs top = searcher.search(query, TOP_K);
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("  Lucene query: %s%n", query);
        System.out.printf(Locale.ROOT, "  %d matching documents (%.1f ms)%n", top.totalHits.value(), ms);

        StoredFields stored = searcher.storedFields();
        int rank = 1;
        for (ScoreDoc hit : top.scoreDocs) {
            Document doc = stored.document(hit.doc);
            System.out.printf(Locale.ROOT, "  %2d. %7.3f  %s%n", rank++, hit.score, doc.get(Analyzers.NAME_FIELD));
            System.out.printf("               %s%n", snippet(doc.get(Analyzers.CONTENT_FIELD)));
        }
        System.out.println();
    }

    static String snippet(String content) {
        String flat = content.replaceAll("\\s+", " ").strip();
        return flat.length() <= 100 ? flat : flat.substring(0, 100) + "...";
    }
}
