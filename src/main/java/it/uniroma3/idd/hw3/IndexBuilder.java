package it.uniroma3.idd.hw3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;

/** Builds the index of the .txt files of a directory and measures how long it takes. */
public final class IndexBuilder {

    public static final String PATH_FIELD = "percorso";
    public static final String COLLECTION_FIELD = "collezione";

    /** Timings of one indexing run. */
    public record Stats(int files, long bytes, long readNanos, long addNanos, long commitNanos,
                        long totalNanos, long indexBytes, int segments) {
        double totalSeconds() { return totalNanos / 1e9; }
        double docsPerSecond() { return files / totalSeconds(); }
        double megabytesPerSecond() { return bytes / 1e6 / totalSeconds(); }
    }

    private IndexBuilder() {}

    /** All the .txt files under {@code root}, in a fixed order. */
    public static List<Path> listTxtFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".txt"))
                    .sorted()
                    .toList();
        }
    }

    /** A reproducible random sample of {@code n} files (used for the scaling experiment). */
    public static List<Path> sample(List<Path> files, int n, long seed) {
        List<Path> copy = new ArrayList<>(files);
        Collections.shuffle(copy, new Random(seed));
        return copy.subList(0, Math.min(n, copy.size()));
    }

    public static Stats build(List<Path> files, Directory directory, Analyzer analyzer,
                              Similarity similarity) throws IOException {
        return build(files, directory, analyzer, similarity, IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB);
    }

    /**
     * @param ramBufferMB memory used to buffer documents before a segment is flushed
     *                    to disk (Lucene default: 16 MB); more segments mean more merges
     */
    public static Stats build(List<Path> files, Directory directory, Analyzer analyzer,
                              Similarity similarity, double ramBufferMB) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer)
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE) // rebuild from scratch every time
                .setRAMBufferSizeMB(ramBufferMB);
        if (similarity != null) {
            config.setSimilarity(similarity);
        }

        long bytes = 0, readNanos = 0, addNanos = 0, commitNanos;
        long start = System.nanoTime();
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (Path file : files) {
                long t0 = System.nanoTime();
                byte[] raw = Files.readAllBytes(file);
                String content = new String(raw, StandardCharsets.UTF_8);
                long t1 = System.nanoTime();
                writer.addDocument(toDocument(file, content));
                long t2 = System.nanoTime();
                bytes += raw.length;
                readNanos += t1 - t0;
                addNanos += t2 - t1;
            }
            long t3 = System.nanoTime();
            writer.commit(); // without commit the searcher would not see the documents
            commitNanos = System.nanoTime() - t3;
        }
        long total = System.nanoTime() - start;
        int segments;
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            segments = reader.leaves().size();
        }
        return new Stats(files.size(), bytes, readNanos, addNanos, commitNanos, total, sizeOf(directory), segments);
    }

    static Document toDocument(Path file, String content) {
        String fileName = file.getFileName().toString();
        Path parent = file.getParent();
        Document doc = new Document();
        // both fields are analyzed (TextField) and stored, so results can show them
        doc.add(new TextField(Analyzers.NAME_FIELD, fileName, Field.Store.YES));
        doc.add(new TextField(Analyzers.CONTENT_FIELD, content, Field.Store.YES));
        doc.add(new StoredField(PATH_FIELD, file.toString()));
        // one exact term: lets the evaluation filter one collection
        doc.add(new StringField(COLLECTION_FIELD,
                parent == null ? "" : parent.getFileName().toString(), Field.Store.YES));
        return doc;
    }

    static long sizeOf(Directory directory) throws IOException {
        long size = 0;
        for (String name : directory.listAll()) {
            size += directory.fileLength(name);
        }
        return size;
    }
}
