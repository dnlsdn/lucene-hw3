package it.uniroma3.idd.hw3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;

class AnalyzersTest {

    static List<String> tokens(Analyzer analyzer, String text) throws IOException {
        List<String> out = new ArrayList<>();
        try (TokenStream ts = analyzer.tokenStream("f", text)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) out.add(term.toString());
            ts.end();
        }
        return out;
    }

    @Test
    void filenameAnalyzerSplitsOnPunctuationAndDropsExtension() throws IOException {
        Analyzer a = Analyzers.byName(Analyzers.FILENAME);
        assertEquals(List.of("news", "comp", "graphics", "37261"), tokens(a, "news-comp.graphics-37261.txt"));
        assertEquals(List.of("cran", "0001", "wing", "in", "a", "slipstream"),
                tokens(a, "cran-0001_wing-in-a-slipstream.txt"));
        assertEquals(List.of("my", "report", "2024", "final"), tokens(a, "MyReport2024_FINAL.TXT"));
    }

    @Test
    void standardAnalyzerKeepsDottedGroupNamesTogether() throws IOException {
        // why StandardAnalyzer is a bad choice for file names: "graphics" alone is not a token
        assertEquals(List.of("news", "comp.graphics", "37261", "txt"),
                tokens(Analyzers.byName(Analyzers.STANDARD), "news-comp.graphics-37261.txt"));
    }

    @Test
    void englishAnalyzerRemovesStopWordsAndStems() throws IOException {
        assertEquals(List.of("experiment", "investig", "aerodynam", "wing"),
                tokens(Analyzers.byName(Analyzers.ENGLISH), "Experimental investigation of the aerodynamics of a wing"));
    }
}
