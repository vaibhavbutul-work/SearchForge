/**
 * SearchForge Search Service
 *
 * Dedicated domain service orchestrating communication between UI components
 * and the Spring Boot search backend via the centralized apiClient.
 *
 * Enforces strict backend truth:
 * - Zero mocked search results.
 * - Preserves backend scoring and rank order.
 * - Normalizes response contracts defensively.
 */

import apiClient from '../api/apiClient';

class SearchService {
  /**
   * Unified search dispatcher routing requests based on selected search mode:
   * - 'ranked': Free-text Okapi BM25 ranked search (/api/v1/search)
   * - 'boolean': Abstract Syntax Tree Boolean retrieval (/api/v1/search/ranked-boolean)
   * - 'filtered': Free-text BM25 constrained by a Boolean filter (/api/v1/search/filtered)
   *
   * @param {string} query - Query string
   * @param {Object} options - Search options { mode, filter, limit, explain }
   * @returns {Promise<Object>} Normalized RankedSearchResponse
   */
  async search(query, { mode = 'ranked', filter = '', limit = 10, explain = false } = {}) {
    switch (mode) {
      case 'boolean':
        return this.searchRankedBoolean(query, { limit, explain });
      case 'filtered':
        return this.searchFiltered(query, filter, { limit, explain });
      case 'ranked':
      default:
        return this.searchFreeText(query, { limit, explain });
    }
  }

  /**
   * Free-text BM25 ranked retrieval (Mode A) enriched with spelling suggestions,
   * synonym expansions, and dynamic highlighted snippets.
   *
   * @param {string} query - Free-text search terms
   * @param {Object} options - Search options { limit, explain }
   * @returns {Promise<Object>} Normalized RankedSearchResponse
   */
  async searchFreeText(query, { limit = 10, explain = false } = {}) {
    if (!query || !query.trim()) {
      return this.emptyResponse('');
    }

    const response = await apiClient.get('/api/v1/search', {
      params: {
        q: query.trim(),
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
   * Defensively normalizes the backend REST response while strictly preserving ranking order and scores.
   */
  normalizeSearchResponse(raw, originalQuery) {
    if (!raw) {
      return this.emptyResponse(originalQuery);
    }

    const results = (raw.results || []).map((hit, index) => ({
      docId: hit.docId,
      url: hit.url || '#',
      title: hit.title || hit.url || 'Untitled Document',
      domain: hit.domain || '',
      snippet: hit.snippet || '',
      highlightedSnippet: hit.highlightedSnippet || null,
      score: hit.score,
      rank: hit.rank ?? index + 1,
      matchedTerms: hit.matchedTerms || [],
      explanation: hit.explanation || null,
    }));

    return {
      query: raw.query || originalQuery,
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
