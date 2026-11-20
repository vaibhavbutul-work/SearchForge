# SearchForge: Phase 6 Masterclass Guide
## Complete Frontend Web Application Development

This masterclass guide documents the architectural decisions, design tokens, component hierarchy, backend integration contracts, and interview-ready concepts for Phase 6 of SearchForge.

---

## 1. Architectural Blueprint

SearchForge Phase 6 implements an independent, full-featured search engine web application.

```
Browser Client (React 18 + Vite)
      │
      ├── UI Layer: HomePage, SearchPage, SearchHeader, SearchBar
      ├── Domain Layer: searchService.js (Unified Dispatcher & Contract Normalizer)
      ├── Transport Layer: apiClient.js (Fetch API, Timeouts, ApiError Hierarchy)
      └── Storage Layer: localStorage (Recent Searches, capped at 8)
            │
            ▼ HTTP REST (JSON over Port 8080)
[ Spring Boot WebConfig (CORS) ] -> localhost:5173, localhost:4173
      │
      ▼
[ SearchController.java ]
      ├── /api/v1/search (Ranked BM25 + Spelling + Synonyms + Snippets)
      ├── /api/v1/search/ranked-boolean (AST Boolean + BM25)
      ├── /api/v1/search/filtered (Boolean Filter + Ranked Union)
      ├── /api/v1/search/autocomplete (Trie Prefix Suggestions)
      └── /api/v1/search/suggest (Damerau-Levenshtein Spelling Suggestions)
```

---

## 2. Design System Tokens & Usability

The application avoids AI SaaS cliches (purple gradients, glowing cards, glassmorphism) in favor of the timeless, high-utility readability of Google Search and Kagi:

```css
:root {
  --color-bg: #FAFAF8;            /* Minimal off-white canvas */
  --color-surface: #FFFFFF;       /* Pure white elevated elements */
  --color-text-primary: #202124;  /* High contrast primary text (14.8:1) */
  --color-text-secondary: #5F6368;/* WCAG AA compliant secondary text (5.1:1) */
  --color-text-muted: #80868B;    /* Subtle metadata & badges */
  --color-accent: #3157A4;        /* Restrained navy accent */
  --color-border: #E5E5E2;        /* Subtle hairline borders */
  --search-results-width: 680px;  /* Ergonomic reading line length */
}
```

---

## 3. Component Catalog

| Component | File Path | Key Responsibilities |
| :--- | :--- | :--- |
| **`Navbar`** | `src/components/layout/Navbar.jsx` | Minimal header with docs external link and About modal trigger. |
| **`AboutModal`** | `src/components/layout/AboutModal.jsx` | Accessible dialog explaining SearchForge's zero-Lucene architecture. |
| **`SearchBar`** | `src/components/search/SearchBar.jsx` | Omnibox with clear button, shortcut `/` badge, and recent search history dropdown. |
| **`SearchHeader`**| `src/components/search/SearchHeader.jsx` | Sticky SERP header with query retention and compact search bar. |
| **`ExampleQueries`**| `src/components/search/ExampleQueries.jsx` | Technical query chips (`virtual threads`, `dependency injection`, `indexes`). |
| **`SearchStats`** | `src/components/search/SearchStats.jsx` | Telemetry bar (`About X results (Y ms)`) and `didYouMean` spelling prompt. |
| **`SearchModeSelector`**| `src/components/search/SearchModeSelector.jsx` | Compact dropdown switching between Ranked BM25, Boolean AST, and Filtered mode. |
| **`TopKSelector`**| `src/components/search/TopKSelector.jsx` | Per-page limit control (5, 10, 20, 50) directly controlling backend's min-heap. |
| **`SearchResultItem`**| `src/components/search/SearchResultItem.jsx` | Result card with breadcrumbs, title, highlighted snippet, rank, score, and copy button. |
| **`RankingExplanation`**| `src/components/search/RankingExplanation.jsx` | Deconstructs BM25 score into raw TF, RSJ IDF, saturation, and phrase boosts. |
| **`SearchLoadingState`**| `src/components/search/SearchLoadingState.jsx`| Zero-layout-shift shimmer skeleton loader matching result card geometry. |
| **`SearchEmptyState`**| `src/components/search/SearchEmptyState.jsx` | Helpful zero-hit advice (spelling, broader terms, Boolean syntax). |
| **`SearchErrorState`**| `src/components/search/SearchErrorState.jsx` | Masked error banner with interactive retry button. |

---

## 4. Full Technical Verification Audit

- **Frontend Build:** `npm run build` completed cleanly in 675ms (69 modules transformed, 0 errors).
- **Backend Build & Tests:** `.\mvnw.cmd test` completed in 23.8s with **273 tests passing (100%), 0 failures, 0 errors**.
- **Preview Server:** Serves cleanly on port 4173 with HTTP 200 across `/`, `/search?q=...`, and all modes.
- **Accessibility:** Skip to main content link, 5.1:1 text contrast, visible focus rings, reduced-motion queries.
- **Security:** No hardcoded tokens, `.env` git-ignored, and CORS strictly locked to local development origins in `WebConfig.java`.
