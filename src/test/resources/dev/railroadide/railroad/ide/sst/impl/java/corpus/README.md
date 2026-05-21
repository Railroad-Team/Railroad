# Java Parser Corpus

This corpus feeds `JavaParserCorpusTest`.

Format:

- `manifest.csv` columns:
  - `relative_path`: file under this corpus directory
  - `kind`: `valid` or `recovery`
  - `features`: short feature tags for coverage tracking

Rules:

- `valid` files are expected to be syntactically valid Java and must parse with no syntax recovery artifacts.
- `recovery` files are intentionally malformed and must still produce syntax trees.
- Every manifest entry must point to a non-empty file.
