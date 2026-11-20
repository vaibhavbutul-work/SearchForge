import React from 'react';
import SearchResultItem from './SearchResultItem';
import styles from './SearchResultsList.module.css';

export default function SearchResultsList({ results = [], mode = 'hybrid', onExplain }) {
  if (!results || results.length === 0) {
    return null;
  }

  return (
    <div className={styles.resultsList} role="feed" aria-label="Search results list">
      {results.map((result, index) => (
        <SearchResultItem
          key={result.docId || result.documentId || result.url || index}
          result={result}
          rank={result.rank || result.finalRank || index + 1}
          mode={mode}
          onExplain={onExplain}
        />
      ))}
    </div>
  );
}
