import React from 'react';
import styles from './ExampleQueries.module.css';

const DEFAULT_EXAMPLES = [
  'Java virtual threads',
  'Spring Boot dependency injection',
  'PostgreSQL indexing',
  'b-tree indexes',
];

export default function ExampleQueries({ onSelectQuery, examples = DEFAULT_EXAMPLES }) {
  return (
    <div className={styles.container} aria-label="Example search queries">
      <span className={styles.label}>Try searching</span>
      <div className={styles.queryList} role="list">
        {examples.map((query) => (
          <button
            key={query}
            type="button"
            className={styles.queryChip}
            onClick={() => onSelectQuery && onSelectQuery(query)}
            role="listitem"
            title={`Search for "${query}"`}
          >
            <span className={styles.queryText}>{query}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
