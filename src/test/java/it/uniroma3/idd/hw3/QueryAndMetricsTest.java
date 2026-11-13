package it.uniroma3.idd.hw3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.jupiter.api.Test;

class QueryAndMetricsTest {

    private final QueryFactory factory = new QueryFactory(Analyzers.perField());

    @Test
    void keywordChoosesTheField() throws ParseException {
        assertEquals("nome:graphics", factory.parse("nome graphics").toString());
        assertEquals("contenuto:wing contenuto:slipstream", factory.parse("contenuto wings slipstream").toString());
    }

    @Test
    void quotesMakeAPhraseQuery() throws ParseException {
        assertEquals("contenuto:\"boundari layer\"", factory.parse("contenuto \"boundary layer\"").toString());
    }

    @Test
    void splitNameBecomesAPhrase() throws ParseException {
        assertEquals("nome:\"comp graphics\"", factory.parse("nome comp.graphics").toString());
    }

    @Test
    void booleanOperatorsAndDefaultFields() throws ParseException {
        assertEquals("+contenuto:shock -contenuto:tunnel", factory.parse("contenuto +shock -tunnel").toString());
        assertEquals("nome:graphics contenuto:graphic", factory.parse("graphics").toString());
        assertThrows(ParseException.class, () -> factory.parse("nome"));
    }

    @Test
    void rankedMetrics() {
        List<Integer> ranking = List.of(1, 9, 2, 8, 3);
        Set<Integer> relevant = Set.of(1, 2, 3, 4);
        assertEquals(0.6, Metrics.precisionAt(ranking, relevant, 5), 1e-9);
        assertEquals(0.75, Metrics.recallAt(ranking, relevant, 5), 1e-9);
        // AP = (1/1 + 2/3 + 3/5) / 4
        assertEquals((1 + 2.0 / 3 + 3.0 / 5) / 4, Metrics.averagePrecision(ranking, relevant), 1e-9);
        assertEquals(1.0, Metrics.reciprocalRank(ranking, relevant), 1e-9);
        assertEquals(0.5, Metrics.rPrecision(ranking, relevant), 1e-9); // 2 relevant in top 4
        assertEquals(1.0, Metrics.ndcgAt(List.of(1, 2), Map.of(1, 1, 2, 2), 10), 1e-9);
    }

    @Test
    void setMetrics() {
        Metrics.SetScores s = Metrics.setScores(Set.of(1, 2, 3, 4), Set.of(1, 2, 5, 6, 7, 8));
        assertEquals(0.5, s.precision(), 1e-9);
        assertEquals(1.0 / 3, s.recall(), 1e-9);
        assertEquals(0.4, s.f1(), 1e-9);
    }
}
