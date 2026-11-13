package it.uniroma3.idd.hw3;

import java.nio.file.Path;
import java.util.List;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Indexes every .txt file under a directory.
 *
 * <pre>
 * java -cp target/lucene-hw3.jar it.uniroma3.idd.hw3.IndexFiles data/docs index
 * </pre>
 */
public final class IndexFiles {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: IndexFiles <docs dir> <index dir> [name analyzer] [content analyzer]");
            System.exit(1);
        }
        Path docs = Path.of(args[0]);
        Path indexPath = Path.of(args[1]);
        String nameAnalyzer = args.length > 2 ? args[2] : Analyzers.DEFAULT_NAME;
        String contentAnalyzer = args.length > 3 ? args[3] : Analyzers.DEFAULT_CONTENT;

        List<Path> files = IndexBuilder.listTxtFiles(docs);
        System.out.printf("Indexing %d .txt files from %s%n", files.size(), docs);
        System.out.printf("Analyzers: nome=%s, contenuto=%s%n", nameAnalyzer, contentAnalyzer);

        try (Directory dir = FSDirectory.open(indexPath)) {
            IndexBuilder.Stats s = IndexBuilder.build(
                    files, dir, Analyzers.perField(nameAnalyzer, contentAnalyzer), null);
            System.out.printf("Indexed %d files (%.1f MB) in %.2f s: %.0f docs/s, %.1f MB/s%n",
                    s.files(), s.bytes() / 1e6, s.totalSeconds(), s.docsPerSecond(), s.megabytesPerSecond());
            System.out.printf("  reading files %.2f s, addDocument %.2f s, commit %.2f s%n",
                    s.readNanos() / 1e9, s.addNanos() / 1e9, s.commitNanos() / 1e9);
            System.out.printf("Index size on disk: %.1f MB, %d segments, in %s%n", s.indexBytes() / 1e6, s.segments(), indexPath);
        }
    }
}
