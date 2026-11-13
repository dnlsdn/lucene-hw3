# lucene-hw3

Homework 3 of *Ingegneria dei Dati* (Roma Tre, 2026/27): index the `.txt` files of a directory with
Apache Lucene on two fields, the file name (`nome`) and the file content (`contenuto`), each with its
own analyzer, and query the index from the console.

The repository also contains the evaluation used in the report: a test collection with relevance
judgments (Cranfield), standard IR measures, a comparison of analyzers and ranking functions, and
indexing-time measurements on 20,246 files.

## Requirements

- Java 21 or newer
- Maven 3.9+
- Python 3 (only to build the dataset)

## Dataset

```bash
scripts/download.sh
```

Downloads two public collections and writes one `.txt` file per document into `data/docs/`:

| collection | files | file name | content |
|---|---|---|---|
| [Cranfield 1400](http://ir.dcs.gla.ac.uk/resources/test_collections/cran/) | 1,400 | `cran-0001_<title words>.txt` | the abstract (title included) |
| [20 Newsgroups, bydate](http://qwone.com/~jason/20Newsgroups/) | 18,846 | `news-<group>-<id>.txt` | the Usenet post, unchanged |

and the evaluation files into `data/eval/` (225 Cranfield queries, 1,612 relevance judgments, full
titles). These small files are committed, the documents are not.

## Build

```bash
mvn package
```

Runs the unit tests and creates `target/lucene-hw3.jar` with all dependencies.

## Index

```bash
java -cp target/lucene-hw3.jar it.uniroma3.idd.hw3.IndexFiles data/docs index
```

Any directory works: every `.txt` file below it is indexed. Optional third and fourth arguments
choose the name and content analyzers (defaults: `filename` and `english`).

## Search from the console

```bash
java -jar target/lucene-hw3.jar index
```

```
> nome comp.graphics
> contenuto "boundary layer" transition
> contenuto +buckling "cylindrical shells" -thermal
> contenuto "panel flutter" "elastic restraint"~1
```

The first word chooses the field (`nome` or `contenuto`), the rest is read by Lucene's classic
`QueryParser`: quotes make a phrase query, `+` requires a term, `-` excludes it, `~n` sets the slop.
Without a field keyword both fields are searched. An empty line ends the program.

## Analyzers

- **nome**: whitespace tokenizer, `WordDelimiterGraphFilter` (splits on `-`, `_`, `.`, case
  changes, letters/digits), lowercase, stop filter for `txt`. No stemming: names are identifiers.
- **contenuto**: `EnglishAnalyzer` (standard tokenizer, possessive filter, lowercase, English stop
  words, Porter stemmer). Chosen because it gave the best MAP on Cranfield (see the results).

## Experiments

```bash
java -cp target/lucene-hw3.jar it.uniroma3.idd.hw3.Evaluate data results
```

Takes about two minutes and writes [`results/results.md`](results/results.md) plus CSV files:

1. six content analyzers on Cranfield: MAP, P@10, R@100, R@1000, R-precision, MRR, nDCG@10,
   vocabulary size, paired randomization test;
2. BM25, tf-idf and Dirichlet language model;
3. Cranfield queries on the full index, with the newsgroup posts as distractors;
4. four name analyzers on `nome` queries with known answers (precision, recall, F1);
5. twelve console test queries with known answers;
6. indexing time: cost of the analyzer, growth with the number of files, RAM buffer size
   (interleaved rounds after a warm-up).

`results/console-session.txt` is the console output of the twelve test queries.

## Code

| class | role |
|---|---|
| `Analyzers` | analyzers of the two fields and the alternatives used in the comparison |
| `IndexBuilder` | builds the index and measures the indexing time |
| `IndexFiles` | command-line indexer |
| `QueryFactory` | console syntax to Lucene query |
| `SearchFiles` | console search |
| `Metrics` | evaluation measures and the paired randomization test |
| `Evaluate` | all the experiments of the report |
