/**
 * SearchForge Search Service
 *
 * Dedicated domain service orchestrating communication between UI components
 * and the Spring Boot search backend via the centralized apiClient.
 *
 * Enforces strict backend truth:
 * - Zero mocked search results.
 * - Preserves backend multi-modal scoring and rank order (BM25, Dense Semantic, RRF).
 * - Normalizes response contracts defensively.
 */

import apiClient from '../api/apiClient.js';

class SearchService {
  /**
   * Unified search dispatcher routing requests based on selected search mode:
   * - 'hybrid': Multi-modal retrieval combining BM25 + Semantic via Reciprocal Rank Fusion
   * - 'lexical' / 'ranked': Free-text Okapi BM25 ranked search (/api/v1/search?mode=lexical)
   * - 'semantic': Dense neural vector retrieval based on continuous cosine similarity (/api/v1/search?mode=semantic)
   * - 'boolean': Abstract Syntax Tree Boolean retrieval (/api/v1/search/ranked-boolean)
   * - 'filtered': Free-text BM25 constrained by a Boolean filter (/api/v1/search/filtered)
   *
   * @param {string} query - Query string
   * @param {Object} options - Search options { mode, filter, limit, explain }
   * @returns {Promise<Object>} Normalized RankedSearchResponse
   */
  async search(query, { mode = 'hybrid', filter = '', limit = 10, explain = false } = {}) {
    switch (mode) {
      case 'boolean':
        return this.searchRankedBoolean(query, { limit, explain });
      case 'filtered':
        return this.searchFiltered(query, filter, { limit, explain });
      case 'semantic':
        return this.searchFreeText(query, { mode: 'semantic', limit, explain });
      case 'lexical':
      case 'ranked':
        return this.searchFreeText(query, { mode: 'lexical', limit, explain });
      case 'hybrid':
      default:
        return this.searchFreeText(query, { mode: 'hybrid', limit, explain });
    }
  }

  /**
   * Multi-mode search retrieval (hybrid, lexical, semantic) enriched with spelling suggestions,
   * synonym expansions, and dynamic highlighted snippets.
   *
   * @param {string} query - Free-text search terms
   * @param {Object} options - Search options { mode, limit, explain }
   * @returns {Promise<Object>} Normalized RankedSearchResponse
   */
  async searchFreeText(query, { mode = 'hybrid', limit = 10, explain = false } = {}) {
    if (!query || !query.trim()) {
      return this.emptyResponse('');
    }

    const response = await apiClient.get('/api/v1/search', {
      params: {
        q: query.trim(),
        mode,
        limit,
        explain,
      },
    });

    return this.normalizeSearchResponse(response, query.trim());
  }

  /**
   * Evaluates a Boolean query AST and ranks matching candidates via BM25 (Mode C).
   *
   * @param {string} query - Boolean expression (e.g. 'java AND threads')
   * @param {Object} options - Search options { limit, explain }
   * @returns {Promise<Object>} Normalized RankedSearchResponse
   */
  async searchRankedBoolean(query, { limit = 10, explain = false } = {}) {
    if (!query || !query.trim()) {
      return this.emptyResponse('');
    }

    const response = await apiClient.get('/api/v1/search/ranked-boolean', {
      params: {
        q: query.trim(),
        limit,
        explain,
      },
    });

    return this.normalizeSearchResponse(response, query.trim());
  }

  /**
   * Filtered ranked search restricted strictly to documents passing a Boolean filter (Mode B).
   *
   * @param {string} query - Free-text query
   * @param {string} filter - Boolean filter expression
   * @param {Object} options - Search options { limit, explain }
   * @returns {Promise<Object>} Normalized RankedSearchResponse
   */
  async searchFiltered(query, filter, { limit = 10, explain = false } = {}) {
    if (!query || !query.trim() || !filter || !filter.trim()) {
      return this.emptyResponse('');
    }

    const response = await apiClient.get('/api/v1/search/filtered', {
      params: {
        q: query.trim(),
        filter: filter.trim(),
        limit,
        explain,
      },
    });

    return this.normalizeSearchResponse(response, query.trim());
  }

  /**
   * Prefix autocomplete suggestions ranked by corpus popularity.
   *
   * @param {string} prefix - Incomplete term prefix
   * @param {number} limit - Maximum suggestions (default 5)
   * @returns {Promise<Array>} List of autocomplete suggestions
   */
  async getAutocomplete(prefix, limit = 5) {
    if (!prefix || !prefix.trim()) {
      return [];
    }

    try {
      const response = await apiClient.get('/api/v1/search/autocomplete', {
        params: {
          prefix: prefix.trim(),
          limit,
        },
      });
      return response?.suggestions || [];
    } catch {
      // Autocomplete failures should degrade silently without blocking the user
      return [];
    }
  }

  /**
   * Damerau-Levenshtein spelling correction suggestions.
   *
   * @param {string} query - Raw user query
   * @returns {Promise<string|null>} Suggested query or null
   */
  async getSpellingSuggestion(query) {
    if (!query || !query.trim()) {
      return null;
    }

    try {
      const response = await apiClient.get('/api/v1/search/suggest', {
        params: { q: query.trim() },
      });
      return response?.hasCorrection ? response.suggestedQuery : null;
    } catch {
      return null;
    }
  }

  /**
   * Defensively normalizes the backend REST response while strictly preserving ranking order and multi-modal scores.
   */
  normalizeSearchResponse(raw, originalQuery) {
    if (!raw) {
      return this.emptyResponse(originalQuery);
    }

    const results = (raw.results || []).map((hit, index) => ({
      docId: hit.docId ?? hit.documentId,
      documentId: hit.documentId ?? hit.docId,
      url: hit.url || '#',
      title: hit.title || hit.url || 'Untitled Document',
      domain: hit.domain || '',
      snippet: hit.snippet || '',
      highlightedSnippet: hit.highlightedSnippet || null,
      score: hit.score,
      rank: hit.rank ?? hit.finalRank ?? index + 1,
      finalRank: hit.finalRank ?? hit.rank ?? index + 1,
      matchedTerms: hit.matchedTerms || [],
      explanation: hit.explanation || null,
      bm25Score: hit.bm25Score !== undefined ? hit.bm25Score : null,
      semanticScore: hit.semanticScore !== undefined ? hit.semanticScore : null,
      bm25Rank: hit.bm25Rank !== undefined ? hit.bm25Rank : null,
      semanticRank: hit.semanticRank !== undefined ? hit.semanticRank : null,
      rrfScore: hit.rrfScore !== undefined ? hit.rrfScore : null,
    }));

    return {
      query: raw.query || originalQuery,
      mode: raw.mode || 'hybrid',
      fallback: Boolean(raw.fallback),
      fallbackReason: raw.fallbackReason || null,
      didYouMean: raw.didYouMean || null,
      totalHits: raw.totalHits ?? results.length,
      returnedHits: raw.returnedHits ?? results.length,
      executionTimeMs: raw.executionTimeMs ?? 0,
      results,
    };
  }

  emptyResponse(query) {
    return {
      query,
      mode: 'hybrid',
      fallback: false,
      fallbackReason: null,
      didYouMean: null,
      totalHits: 0,
      returnedHits: 0,
      executionTimeMs: 0,
      results: [],
    };
  }
}

export const searchService = new SearchService();
export default searchService;
