import React, { useState, useEffect } from 'react';
import { SEARCH_MODES, resolveSearchMode } from './searchModes.js';
import styles from './SearchModeSelector.module.css';

export { SEARCH_MODES, resolveSearchMode };

export default function SearchModeSelector({
  currentMode = 'hybrid',
  currentFilter = '',
  onModeChange,
  onFilterChange,
}) {
  const [filterText, setFilterText] = useState(currentFilter || '');

  // Normalize legacy 'ranked' alias to 'lexical'
  const normalizedMode = currentMode === 'ranked' ? 'lexical' : currentMode;

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

  const activeModeObj = SEARCH_MODES.find((m) => m.id === normalizedMode) || SEARCH_MODES[0];

  return (
    <div className={styles.wrapper}>
      <div className={styles.selectorContainer}>
        <label htmlFor="search-mode-select" className={styles.selectorLabel}>
          Mode:
        </label>
        <select
          id="search-mode-select"
          className={styles.modeSelect}
          value={normalizedMode}
          onChange={handleSelectChange}
          aria-label="Select search retrieval mode"
        >
          {SEARCH_MODES.map((mode) => (
            <option key={mode.id} value={mode.id}>
              {mode.label}
            </option>
          ))}
        </select>
        <span className={styles.modeBadge} title={activeModeObj.description}>
          {activeModeObj.badge}
        </span>
      </div>

      <div className={styles.modeDescription} title={activeModeObj.description}>
        {activeModeObj.description}
      </div>

      {normalizedMode === 'filtered' && (
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
