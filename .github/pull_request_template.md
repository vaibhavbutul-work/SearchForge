## 🛠️ Description of Changes
A clear summary of what this pull request modifies, adds, or fixes in SearchForge.

## 🎯 Related Subsystem
- [ ] Web Crawler (`com.searchforge.crawler`)
- [ ] Text Processing & Porter Stemmer (`com.searchforge.indexer.token` / `stem`)
- [ ] Dual-Tier Inverted Index (`com.searchforge.indexer.index`)
- [ ] Boolean Retrieval AST Engine (`com.searchforge.indexer.retrieval`)
- [ ] BM25 Scoring & Ranking Engine (`com.searchforge.ranking`)
- [ ] Query Intelligence & Search Experience (`com.searchforge.query`)
- [ ] REST API & Telemetry (`com.searchforge.*.controller`)
- [ ] Documentation / Visualizations (`docs/` / `docs-site/`)

## 🧪 Testing Checklist
- [ ] Maven test suite executed locally (`./mvnw test`)
- [ ] All 273+ automated tests pass with 0 failures
- [ ] No regression in sub-millisecond search latencies
- [ ] No secrets or `.env` credentials committed
