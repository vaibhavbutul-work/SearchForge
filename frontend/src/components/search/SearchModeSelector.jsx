import React, { useState, useEffect } from 'react';
import styles from './SearchModeSelector.module.css';

export const SEARCH_MODES = [
  { id: 'ranked', label: 'Ranked BM25' },
  { id: 'boolean', label: 'Boolean AST (AND/OR/NOT)' },
  { id: 'filtered', label: 'Filtered Search' },
];

export default function SearchModeSelector({
  currentMode = 'ranked',
  currentFilter = '',
  onModeChange,
  onFilterChange,
}) {
  const [filterText, setFilterText] = useState(currentFilter || '');

  useEffect(() => {
    setFilterText(currentFilter || '');
  }, [currentFilter]);

  const handleSelectChange = (e) => {
    const newMode = e.target.value;
    if (onModeChange) {
      onModeChange(newMode);
    }
  };

  const handleFilterSubmit = (e) => {
    e.preventDefault();
    if (onFilterChange) {
      onFilterChange(filterText.trim());
    }
  };

  return (
    <div>
      <div className={styles.selectorContainer}>
        <label htmlFor="search-mode-select" className={styles.selectorLabel}>
          Mode:
        </label>
        <select
          id="search-mode-select"
          className={styles.modeSelect}
          value={currentMode}
          onChange={handleSelectChange}
          aria-label="Select search retrieval mode"
        >
          {SEARCH_MODES.map((mode) => (
            <option key={mode.id} value={mode.id}>
              {mode.label}
            </option>
          ))}
        </select>
      </div>

      {currentMode === 'filtered' && (
        <form onSubmit={handleFilterSubmit} className={styles.filterInputContainer}>
          <label htmlFor="boolean-filter-input" className={styles.filterLabel}>
            Filter (AST):
          </label>
          <input
            id="boolean-filter-input"
            type="text"
            className={styles.filterInput}
            value={filterText}
            onChange={(e) => setFilterText(e.target.value)}
            placeholder="e.g. java OR postgres"
            aria-label="Boolean filter expression"
          />
          <button type="submit" className={styles.modeSelect} style={{ padding: '4px 8px' }}>
            Apply Filter
          </button>
        </form>
      )}
    </div>
  );
}
