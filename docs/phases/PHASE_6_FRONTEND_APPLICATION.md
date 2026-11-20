# Phase 6: Complete Frontend Web Application Development

## 1. Overview & Architectural Vision

SearchForge Phase 6 transforms the foundational Java 21 / Spring Boot search engine into a complete, independent, professional web application.

Following the core philosophy **"What I cannot create, I do not understand"**, SearchForge's web interface is built from first principles:
- **No Third-Party Search Engines:** The frontend connects exclusively to our custom Spring Boot backend. Zero Lucene, zero Elasticsearch, zero external search APIs.
- **Hybrid Search Engine Design:** Combines the minimal, content-first clarity of Google Search and Kagi with the developer-centric transparency of modern information retrieval tools.
- **Zero SaaS / AI Clichés:** No artificial chatbot bubbles, glowing gradients, floating decorations, or fake metrics. Pure search engine utility.
- **Decoupled Architecture:** Built as a standalone React 18 + Vite application in `frontend/`, communicating with Spring Boot REST endpoints on port 8080.

---

## 2. Visual Design System & Tokens

SearchForge implements a restrained, accessible design system defined in `src/styles/variables.css` and `src/styles/globals.css`:

| Token | CSS Variable | Hex / Value | Purpose | Contrast Ratio (WCAG) |
| :--- | :--- | :--- | :--- | :---: |
| **Background** | `--color-bg` | `#FAFAF8` | Off-white canvas reducing eye strain | — |
| **Surface** | `--color-surface` | `#FFFFFF` | Cards, search bars, modals | — |
| **Primary Text** | `--color-text-primary` | `#202124` | Headings, titles, query text | **14.8:1** (AAA) |
| **Secondary Text** | `--color-text-secondary`| `#5F6368` | Snippets, metadata, breadcrumbs | **5.1:1** (AA) |
| **Muted Text** | `--color-text-muted` | `#80868B` | Subtle labels, keyboard badges | **3.8:1** (Large text AA) |
| **Accent** | `--color-accent` | `#3157A4` | Brand marks, links, active pills | **6.3:1** (AA) |
| **Accent Hover** | `--color-accent-hover`| `#264482` | Link hover states | **8.1:1** (AAA) |
| **Accent Subtle**| `--color-accent-subtle`| `rgba(49, 87, 164, 0.08)` | `<mark>` tag highlighting | — |
| **Border** | `--color-border` | `#E5E5E2` | Dividers, search input borders | — |
| **Border Focus** | `--color-border-focus`| `#3157A4` | Accessible 2px `:focus-visible` ring | — |

---

## 3. Frontend Architecture

```
frontend/
├── public/
│   └── favicon.svg                       # Minimalist search lens brand icon
├── src/
│   ├── app/
│   │   ├── App.jsx                       # Root layout, skip-link, persistent footer
│   │   └── router.jsx                    # React Router (createBrowserRouter)
│   ├── components/
│   │   ├── layout/
│   │   │   ├── Navbar.jsx & .module.css  # Top navigation bar
│   │   │   └── AboutModal.jsx & .css     # First-principles architectural modal
│   │   └── search/
│   │       ├── SearchBar.jsx & .module.css       # Omnibox with history & '/' badge
│   │       ├── SearchHeader.jsx & .module.css    # Sticky SERP header with query retention
│   │       ├── ExampleQueries.jsx & .module.css  # Example topic chips
│   │       ├── SearchStats.jsx & .module.css     # Telemetry & didYouMean prompts
│   │       ├── SearchModeSelector.jsx & .css     # Mode switcher (Ranked, Boolean, Filtered)
│   │       ├── TopKSelector.jsx & .module.css    # Results per page selector (5, 10, 20, 50)
│   │       ├── SearchResultItem.jsx & .css       # Individual document card with 'Why this result?'
│   │       ├── SearchResultsList.jsx & .css      # Result items container
│   │       ├── RankingExplanation.jsx & .css     # BM25 mathematical score decomposition
│   │       ├── SearchLoadingState.jsx & .css     # Shimmer skeleton loader
│   │       ├── SearchEmptyState.jsx & .css       # Zero-hit actionable tips
│   │       └── SearchErrorState.jsx & .css       # Graceful error banner with retry
│   ├── pages/
│   │   ├── HomePage.jsx & .module.css    # Minimalist centered homepage
│   │   ├── SearchPage.jsx & .module.css  # Full search results page orchestrator
│   │   └── NotFoundPage.jsx              # 404 error page
│   ├── services/
│   │   ├── api/
│   │   │   └── apiClient.js              # Fetch API transport with timeouts & ApiError
│   │   └── search/
│   │       └── searchService.js          # Domain search service with unified dispatcher
│   ├── hooks/
│   │   ├── useSearchHistory.js           # LocalStorage query persistence & deduplication
│   │   └── useGlobalShortcuts.js         # '/' search focus & 'Escape' dismiss listeners
│   └── styles/
│       ├── variables.css                 # Design tokens (colors, typography, radii)
│       └── globals.css                   # Global CSS reset, a11y, scrollbar
├── .env.example                          # VITE_API_BASE_URL=http://localhost:8080
├── package.json                          # React 18, React Router v6, Vite 6
└── vite.config.js                        # Port 5173 (dev) & 4173 (preview)
```

---

## 4. End-to-End Request Data Lifecycle

```
User Query (e.g. "virtual threads")
      │
      ▼
[ React SearchBar / SearchHeader ]
      │  Reads/updates window.location.search (?q=virtual+threads&mode=ranked&limit=10)
      ▼
[ SearchPage.jsx ]
      │  Invokes searchService.search(query, { mode, filter, limit, explain: true })
      ▼
[ searchService.js ]
      │  Formats parameters and dispatches to appropriate endpoint
      ▼
[ apiClient.js ]
      │  HTTP GET http://localhost:8080/api/v1/search?q=virtual+threads&limit=10&explain=true
      ▼
[ Spring Boot WebConfig (CORS) ]
      │  Validates Origin against http://localhost:5173
      ▼
[ SearchController.java ]
      ├── 1. SpellingCorrectionService: Detects spelling errors -> didYouMean
      ├── 2. SynonymExpansionService: Expands query terms (discount factor δ = 0.5)
      ├── 3. RankedRetrievalEngine: Postings union (TAAT) + Okapi BM25 Scorer
      ├── 4. Bounded Min-Heap: Extracts Top-K candidates in O(C log K)
      ├── 5. SnippetGeneratorService: Word-boundary sliding window + <mark> tags
      └── 6. Explainability Engine: Deconstructs rawTf, idf, saturation, termScore
      │
      ▼
[ RankedSearchResponse JSON Payload ]
      │  { totalHits, executionTimeMs, didYouMean, results: [...] }
      ▼
[ searchService.normalizeSearchResponse() ]
      │  Defensively normalizes attributes preserving rank order and scores
      ▼
[ SearchPage.jsx ]
      ├── <SearchStats />: "About 3 results (0.26 ms)" + "Did you mean: ...?"
      └── <SearchResultsList />
            └── <SearchResultItem />: Title, URL, <mark> snippet, Score, Rank
                  └── <RankingExplanation />: "Why this result?" breakdown
```

---

## 5. Feature Highlights

### 1. Multi-Modal Retrieval Engine Support
- **Ranked BM25 (Default):** Bag-of-words retrieval scoring documents through asymptotic term saturation and document length normalization.
- **Ranked Boolean:** Abstract Syntax Tree (AST) evaluator resolving `AND`, `OR`, `NOT`, and parenthetical grouping, ranking matching documents with BM25.
- **Filtered Search:** Constrains candidate scoring to documents satisfying an explicit secondary Boolean filter (e.g. `postgres OR oracle`).

### 2. Ranking Transparency ("Why this result?")
Users can click the inline `"Why this result? ▾"` toggle on any search hit to inspect the exact mathematical terms that determined its ranking:
- **Raw Term Frequency ($tf$):** Count of occurrences in the document.
- **RSJ IDF Weight:** Non-negative Robertson-Spärck Jones Inverse Document Frequency $\ln\left(1 + \frac{N - df + 0.5}{df + 0.5}\right)$.
- **Saturation Factor $S(tf)$:** Sublinear saturation divisor $\frac{tf \cdot (k_1 + 1)}{tf + k_1 \cdot (1 - b + b \cdot |D|/\text{avgdl})}$.
- **Net Contribution:** $S(tf) \times \text{IDF}$.
- **Exact Phrase Boost:** Additive bonus highlighted with an amber **Phrase** badge when adjacent token matches occur ($pos_{i+1} - pos_i = 1$).

### 3. Keyboard Shortcuts & Search History
- **Press `/`:** Instantly focuses the search bar from anywhere on the page (bypasses when already typing in an input).
- **Press `Escape`:** Clears search input or closes dropdowns/modals.
- **Search History:** Persists recent queries in browser `localStorage`, automatically deduplicates, caps at 8 items, and allows single-item deletion or full clearance.

### 4. Accessibility & Responsive Polish
- **Skip Link:** Accessible `<a href="#main-content" className="skipLink">Skip to main content</a>`.
- **WCAG AA Compliance:** 5.1:1 text contrast ratio on secondary text, 14.8:1 on primary text.
- **Reduced Motion:** Disables keyframe shimmers and animations when `@media (prefers-reduced-motion: reduce)` is detected.
- **Defensive Wrapping:** `overflow-wrap: anywhere; word-break: break-all;` guarantees long technical URLs never break mobile layout.

---

## 6. Development & Build Verification

```bash
# 1. Start Spring Boot backend (dev profile)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 2. Start React + Vite frontend
cd ../frontend
npm install
npm run dev
# Open http://localhost:5173

# 3. Production build test
npm run build
# Built 69 modules in ~700ms with 0 errors
```
