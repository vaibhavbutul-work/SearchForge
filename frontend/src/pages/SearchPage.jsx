import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import SearchHeader from '../components/search/SearchHeader';
import SearchStats from '../components/search/SearchStats';
import SearchModeSelector, { SEARCH_MODES } from '../components/search/SearchModeSelector';
import TopKSelector from '../components/search/TopKSelector';
import SearchResultsList from '../components/search/SearchResultsList';
import SearchLoadingState from '../components/search/SearchLoadingState';
import SearchEmptyState from '../components/search/SearchEmptyState';
import SearchErrorState from '../components/search/SearchErrorState';
import searchService from '../services/search/searchService';
import { useSearchHistory } from '../hooks/useSearchHistory';
import styles from './SearchPage.module.css';

export default function SearchPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { addSearch } = useSearchHistory();

  // URL search parameters (Persistence & Shareability)
  const query = searchParams.get('q') || '';
  const rawMode = searchParams.get('mode') || 'hybrid';
  // Normalize legacy 'ranked' alias to 'lexical'
  const mode = rawMode === 'ranked' ? 'lexical' : rawMode;
  const filter = searchParams.get('filter') || '';
  const limit = parseInt(searchParams.get('limit') || '10', 10);

  // Backend response state
  const [searchData, setSearchData] = useState({
    totalHits: 0,
    executionTimeMs: 0,
    mode: 'hybrid',
    fallback: false,
    fallbackReason: null,
    didYouMean: null,
    results: [],
  });
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  // Updates URL parameters preserving shareable state across refreshes
  const updateUrlParams = useCallback((newQuery, newMode, newFilter, newLimit) => {
    const params = new URLSearchParams();
    if (newQuery && newQuery.trim()) {
      params.set('q', newQuery.trim());
    }
    if (newMode && newMode !== 'hybrid') {
      params.set('mode', newMode);
    }
    if (newMode === 'filtered' && newFilter && newFilter.trim()) {
      params.set('filter', newFilter.trim());
    }
    const resolvedLimit = newLimit !== undefined ? newLimit : limit;
    if (resolvedLimit && resolvedLimit !== 10) {
      params.set('limit', resolvedLimit.toString());
    }

    const queryString = params.toString();
    navigate(queryString ? `/search?${queryString}` : '/search');
  }, [navigate, limit]);

  // Execute search via searchService
  const executeSearch = useCallback(async (searchQuery, searchMode, searchFilter, searchLimit) => {
    if (!searchQuery || !searchQuery.trim()) {
      setSearchData({
        totalHits: 0,
        executionTimeMs: 0,
        mode: searchMode,
        fallback: false,
        fallbackReason: null,
        didYouMean: null,
        results: [],
      });
      setIsLoading(false);
      setError(null);
      return;
    }

    if (searchMode === 'filtered' && (!searchFilter || !searchFilter.trim())) {
      setError("Filtered mode requires a Boolean filter expression (e.g. 'java OR postgres'). Please provide a filter above.");
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);

    // Save to local search history
    addSearch(searchQuery.trim());

    try {
      const response = await searchService.search(searchQuery.trim(), {
        mode: searchMode,
        filter: searchFilter ? searchFilter.trim() : '',
        limit: searchLimit,
        explain: true,
      });

      setSearchData({
        totalHits: response.totalHits,
        executionTimeMs: response.executionTimeMs,
        mode: response.mode,
        fallback: response.fallback,
        fallbackReason: response.fallbackReason,
        didYouMean: response.didYouMean,
        results: response.results,
      });
    } catch (err) {
      setError(err.message || 'Unable to connect to SearchForge backend server.');
    } finally {
      setIsLoading(false);
    }
  }, [addSearch]);

  useEffect(() => {
    executeSearch(query, mode, filter, limit);
  }, [query, mode, filter, limit, executeSearch]);

  const handleQuerySubmit = (newQuery) => {
    updateUrlParams(newQuery, mode, filter, limit);
  };

  const handleModeChange = (newMode) => {
    updateUrlParams(query, newMode, filter, limit);
  };

  const handleFilterChange = (newFilter) => {
    updateUrlParams(query, mode, newFilter, limit);
  };

  const handleLimitChange = (newLimit) => {
    updateUrlParams(query, mode, filter, newLimit);
  };

  const handleDidYouMeanClick = (suggestedQuery) => {
    updateUrlParams(suggestedQuery, mode, filter, limit);
  };

  const activeModeObj = SEARCH_MODES.find((m) => m.id === mode) || SEARCH_MODES[0];
  const modeLabel = activeModeObj.label;

  return (
    <div className={styles.pageContainer}>
      <SearchHeader query={query} onSearch={handleQuerySubmit} />

      <main className={styles.contentWrapper}>
        {/* Controls Bar: Stats, Mode Selector & Top-K Limit */}
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-3)', marginBottom: 'var(--space-4)' }}>
          {query.trim() !== '' && !isLoading && !error && (
            <SearchStats
              totalHits={searchData.totalHits}
              executionTimeMs={searchData.executionTimeMs}
              searchMode={modeLabel}
              didYouMean={searchData.didYouMean}
              onDidYouMeanClick={handleDidYouMeanClick}
            />
          )}

          <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
            <SearchModeSelector
              currentMode={mode}
              currentFilter={filter}
              onModeChange={handleModeChange}
              onFilterChange={handleFilterChange}
            />
            <TopKSelector
              limit={limit}
              onLimitChange={handleLimitChange}
            />
          </div>
        </div>

        {/* Fallback Graceful Degradation Banner */}
        {searchData.fallback && (
          <div className={styles.fallbackBanner} role="alert">
            <span className={styles.fallbackIcon}>⚠️</span>
            <div className={styles.fallbackContent}>
              <strong>Notice: Automatic Fallback to Lexical Search</strong>
              <p>
                {searchData.fallbackReason || 'Dense semantic retrieval was temporarily unavailable; results were gracefully retrieved using lexical BM25 search.'}
              </p>
            </div>
          </div>
        )}

        {/* Loading Skeleton */}
        {isLoading && (
          <div className={styles.resultsArea}>
            <SearchLoadingState count={4} />
          </div>
        )}

        {/* Error State */}
        {!isLoading && error && (
          <div className={styles.resultsArea}>
            <SearchErrorState
              message={error}
              onRetry={() => executeSearch(query, mode, filter, limit)}
            />
          </div>
        )}

        {/* Search Results */}
        {!isLoading && !error && query.trim() !== '' && (
          <div className={styles.resultsArea}>
            {searchData.results.length === 0 ? (
              <SearchEmptyState query={query} />
            ) : (
              <SearchResultsList
                results={searchData.results}
                mode={mode}
              />
            )}
          </div>
        )}

        {/* Initial Prompt when visiting /search without query */}
        {!isLoading && !error && query.trim() === '' && (
          <div style={{ textAlign: 'center', padding: '4rem 0', color: 'var(--color-text-secondary)' }}>
            <p style={{ fontSize: 'var(--text-base)', marginBottom: 'var(--space-2)' }}>
              Please enter a search query in the header above or press <kbd style={{ padding: '2px 5px', border: '1px solid var(--color-border)', borderRadius: '3px', background: 'var(--color-surface)', fontFamily: 'var(--font-mono)' }}>/</kbd> to focus.
            </p>
            <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
              Active Mode: <strong>{modeLabel}</strong> &bull; Or return to the <a href="/">homepage</a>.
            </p>
          </div>
        )}
      </main>
    </div>
  );
}
