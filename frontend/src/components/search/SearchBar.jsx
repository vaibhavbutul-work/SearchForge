import React, { useRef, useState, useEffect } from 'react';
import { useGlobalShortcuts } from '../../hooks/useGlobalShortcuts';
import { useSearchHistory } from '../../hooks/useSearchHistory';
import styles from './SearchBar.module.css';

export default function SearchBar({
  value = '',
  onChange,
  onSearch,
  placeholder = 'Search technical documentation or enter keywords...',
  size = 'large',
  showActions = false,
  autoFocus = true,
  onFeelingCurious,
}) {
  const containerRef = useRef(null);
  const inputRef = useRef(null);
  const [isFocused, setIsFocused] = useState(false);
  const { history, addSearch, removeSearch, clearHistory } = useSearchHistory();

  // Connect global keyboard shortcuts: '/' to focus search input, 'Escape' to blur/close
  useGlobalShortcuts({
    searchInputRef: inputRef,
    onEscape: () => {
      setIsFocused(false);
      if (inputRef.current) {
        inputRef.current.blur();
      }
    },
  });

  useEffect(() => {
    if (autoFocus && inputRef.current) {
      inputRef.current.focus();
    }
  }, [autoFocus]);

  // Handle outside clicks to close history dropdown
  useEffect(() => {
    const handleOutsideClick = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setIsFocused(false);
      }
    };
    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, []);

  const handleSubmit = (e) => {
    if (e) e.preventDefault();
    const cleanQuery = value.trim();
    if (cleanQuery) {
      addSearch(cleanQuery);
      setIsFocused(false);
      if (onSearch) {
        onSearch(cleanQuery);
      }
    }
  };

  const handleClear = () => {
    if (onChange) {
      onChange('');
    }
    if (inputRef.current) {
      inputRef.current.focus();
    }
  };

  const handleSelectHistoryItem = (item) => {
    if (onChange) {
      onChange(item);
    }
    addSearch(item);
    setIsFocused(false);
    if (onSearch) {
      onSearch(item);
    }
  };

  const shouldShowHistory = isFocused && history && history.length > 0;

  return (
    <div
      ref={containerRef}
      className={`${styles.searchBarContainer} ${styles[size] || styles.large}`}
    >
      <form onSubmit={handleSubmit} className={styles.searchForm} role="search">
        <span className={styles.searchIcon} aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
        </span>

        <input
          ref={inputRef}
          type="search"
          className={styles.input}
          value={value}
          onChange={(e) => onChange && onChange(e.target.value)}
          onFocus={() => setIsFocused(true)}
          placeholder={placeholder}
          aria-label="Search query"
          autoComplete="off"
          spellCheck="false"
        />

        <div className={styles.actions}>
          {value ? (
            <button
              type="button"
              className={styles.clearButton}
              onClick={handleClear}
              aria-label="Clear search input"
              title="Clear"
            >
              &times;
            </button>
          ) : (
            !isFocused && (
              <span className={styles.shortcutBadge} title="Press / to search">
                /
              </span>
            )
          )}
        </div>

        <button type="submit" className={styles.submitButton} aria-label="Submit search">
          Search
        </button>
      </form>

      {/* Recent Search History Dropdown */}
      {shouldShowHistory && (
        <div className={styles.historyDropdown} role="listbox" aria-label="Recent search history">
          <div className={styles.historyHeader}>
            <span>Recent Searches</span>
            <button
              type="button"
              className={styles.clearAllBtn}
              onClick={(e) => {
                e.stopPropagation();
                clearHistory();
              }}
            >
              Clear all
            </button>
          </div>

          <ul className={styles.historyList}>
            {history.map((item) => (
              <li key={item} className={styles.historyRow}>
                <button
                  type="button"
                  className={styles.historyQueryBtn}
                  onClick={() => handleSelectHistoryItem(item)}
                >
                  <span className={styles.historyIcon} aria-hidden="true">&#9679;</span>
                  <span>{item}</span>
                </button>
                <button
                  type="button"
                  className={styles.deleteItemBtn}
                  onClick={(e) => {
                    e.stopPropagation();
                    removeSearch(item);
                  }}
                  aria-label={`Remove "${item}" from history`}
                  title="Remove from history"
                >
                  &times;
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {showActions && (
        <div className={styles.buttonGroup}>
          <button
            type="button"
            className={`${styles.actionButton} ${styles.primaryActionButton}`}
            onClick={handleSubmit}
          >
            SearchForge
          </button>
          {onFeelingCurious && (
            <button
              type="button"
              className={styles.actionButton}
              onClick={onFeelingCurious}
              title="Try a random technical query"
            >
              Feeling Curious
            </button>
          )}
        </div>
      )}
    </div>
  );
}
