#!/usr/bin/env bash
# Downloads the two public collections and builds data/docs (20,246 .txt files) and data/eval.
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p data/raw/cran data/raw/20news
curl -sSL -o data/raw/cran/cran.tar.gz \
  http://ir.dcs.gla.ac.uk/resources/test_collections/cran/cran.tar.gz
curl -sSL -o data/raw/20news/20news-bydate.tar.gz \
  http://qwone.com/~jason/20Newsgroups/20news-bydate.tar.gz
tar xzf data/raw/cran/cran.tar.gz -C data/raw/cran
tar xzf data/raw/20news/20news-bydate.tar.gz -C data/raw/20news

python3 scripts/build_dataset.py data/raw data
