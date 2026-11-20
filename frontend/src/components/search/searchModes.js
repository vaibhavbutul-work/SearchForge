/**
 * SearchForge Multi-Modal Search Modes Definitions
 */

export const SEARCH_MODES = [
  {
    id: 'hybrid',
    label: 'Hybrid (BM25 + Dense + RRF)',
    shortLabel: 'Hybrid',
    badge: '⚡ Multi-Modal',
    description: 'Fuses lexical BM25 precision with neural semantic vector understanding via Reciprocal Rank Fusion.',
    icon: '⚡',
  },
  {
    id: 'lexical',
    label: 'Keyword Search (BM25)',
    shortLabel: 'Keyword (BM25)',
    badge: '🔤 Lexical',
    description: 'Exact keyword matching with BM25 term frequency saturation and document length normalization.',
    icon: '🔤',
  },
  {
    id: 'semantic',
    label: 'Semantic Search (Vector)',
    shortLabel: 'Semantic (Dense)',
    badge: '🧠 Neural',
    description: 'Discovers conceptual matches using 384-dimensional dense embeddings & continuous cosine similarity.',
    icon: '🧠',
  },
  {
    id: 'boolean',
    label: 'Boolean AST (AND/OR/NOT)',
    shortLabel: 'Boolean AST',
    badge: '⚖️ Logical',
    description: 'Structured Boolean evaluation of exact query Abstract Syntax Trees with hard constraints.',
    icon: '⚖️',
  },
  {
    id: 'filtered',
    label: 'Filtered Search',
    shortLabel: 'Filtered',
    badge: '🔍 Filtered',
    description: 'Free-text BM25 search restricted strictly to documents matching a Boolean filter expression.',
    icon: '🔍',
  },
];

export function resolveSearchMode(modeId) {
  if (!modeId || modeId === 'ranked') {
    return 'lexical';
  }
  return modeId;
}
