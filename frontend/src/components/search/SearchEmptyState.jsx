import React from 'react';
import styles from './SearchEmptyState.module.css';

export default function SearchEmptyState({ query = '' }) {
  return (
    <div className={styles.emptyContainer} role="region" aria-label="No results">
      <p className={styles.heading}>
        Your search {query ? <span>for <strong className={styles.queryText}>&ldquo;{query}&rdquo;</strong></span> : ''} did not match any indexed documents.
      </p>

      <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', marginBottom: 'var(--space-2)' }}>
        Suggestions:
      </p>

      <ul className={styles.suggestionList}>
        <li>Make sure that all words are spelled correctly.</li>
        <li>Try different, more general, or alternative technical keywords.</li>
        <li>Try fewer search terms to broaden candidate retrieval.</li>
        <li>If using Boolean operators (<code>AND</code>, <code>OR</code>, <code>NOT</code>), check parentheses and syntax.</li>
      </ul>
    </div>
  );
}
