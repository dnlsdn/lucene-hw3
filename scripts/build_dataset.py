#!/usr/bin/env python3
"""Build the .txt corpus used by the homework from two public collections.

1. Cranfield 1400 (ir.dcs.gla.ac.uk/resources/test_collections/cran/):
   1400 abstracts on aerodynamics, 225 queries and relevance judgments.
   Each document becomes   docs/cranfield/cran-NNNN_<title-slug>.txt
   whose content is the abstract (the .W section, which repeats the title).
   The queries and judgments are written to eval/ in a simple TSV format.

2. 20 Newsgroups, "bydate" version (qwone.com/~jason/20Newsgroups/):
   18,846 Usenet posts from 20 groups. Each post becomes
   docs/20news/news-<group>-<id>.txt   (content copied unchanged).
   The group in the file name gives a known answer set for "nome" queries.

Usage: python3 scripts/build_dataset.py data/raw data
"""
import re
import sys
import unicodedata
from pathlib import Path


def slug(text: str, max_len: int = 70) -> str:
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    words = re.findall(r"[a-z0-9]+", text.lower())
    out = ""
    for w in words:
        if len(out) + len(w) + 1 > max_len:
            break
        out = f"{out}-{w}" if out else w
    return out or "untitled"


def parse_trec_like(path: Path):
    """Parse the SMART format used by Cranfield (.I id, then .T/.A/.B/.W sections)."""
    records, current, field = [], None, None
    for line in path.read_text(encoding="latin-1").splitlines():
        m = re.match(r"^\.([ITABW])\s*(.*)$", line)
        if m:
            tag, rest = m.groups()
            if tag == "I":
                current = {"id": rest.strip(), "T": [], "A": [], "B": [], "W": []}
                records.append(current)
                field = None
            else:
                field = tag
            continue
        if current is not None and field:
            current[field].append(line)
    return records


def clean(lines) -> str:
    return re.sub(r"\s+", " ", " ".join(lines)).strip()


def build_cranfield(raw: Path, out: Path):
    docs_dir = out / "docs" / "cranfield"
    eval_dir = out / "eval"
    docs_dir.mkdir(parents=True, exist_ok=True)
    eval_dir.mkdir(parents=True, exist_ok=True)

    docs = parse_trec_like(raw / "cran" / "cran.all.1400")
    empty = 0
    titles = open(eval_dir / "cranfield-titles.tsv", "w", encoding="utf-8")
    for d in docs:
        num = int(d["id"])
        title = clean(d["T"])
        body = "\n".join(l.strip() for l in d["W"]).strip()
        if not body:
            empty += 1
        name = f"cran-{num:04d}_{slug(title)}.txt"
        (docs_dir / name).write_text(body + "\n", encoding="utf-8")
        titles.write(f"{num}\t{title}\n")  # full title: the file name keeps only ~70 chars
    titles.close()

    # Query ids in cran.qry are not consecutive (001, 002, 004, 008, ...),
    # while cranqrel numbers the queries 1..225 in file order: renumber by position.
    queries = parse_trec_like(raw / "cran" / "cran.qry")
    with open(eval_dir / "cranfield-queries.tsv", "w", encoding="utf-8") as f:
        for pos, q in enumerate(queries, start=1):
            f.write(f"{pos}\t{clean(q['W'])}\n")

    # Relevance codes 1..4 (1 = complete answer, 4 = minimum interest).
    # Lines with code -1 are an artefact of the file (one per query) and are dropped.
    kept = 0
    with open(eval_dir / "cranfield-qrels.tsv", "w", encoding="utf-8") as f:
        for line in (raw / "cran" / "cranqrel").read_text().splitlines():
            parts = line.split()
            if len(parts) != 3:
                continue
            qid, did, rel = parts
            if int(rel) < 1:
                continue
            f.write(f"{int(qid)}\t{int(did)}\t{int(rel)}\n")
            kept += 1
    print(f"cranfield: {len(docs)} docs ({empty} empty), {len(queries)} queries, {kept} judgments")


def build_20news(raw: Path, out: Path):
    docs_dir = out / "docs" / "20news"
    docs_dir.mkdir(parents=True, exist_ok=True)
    count = 0
    for split in ("20news-bydate-train", "20news-bydate-test"):
        for group_dir in sorted((raw / "20news" / split).iterdir()):
            if not group_dir.is_dir():
                continue
            for post in sorted(group_dir.iterdir()):
                text = post.read_bytes().decode("latin-1")
                (docs_dir / f"news-{group_dir.name}-{post.name}.txt").write_text(text, encoding="utf-8")
                count += 1
    print(f"20news: {count} docs")


if __name__ == "__main__":
    raw_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    build_cranfield(raw_dir, out_dir)
    build_20news(raw_dir, out_dir)
