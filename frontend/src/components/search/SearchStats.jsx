import React from 'react';
import styles from './SearchStats.module.css';

export default function SearchStats({
  totalHits = 0,
  executionTimeMs = null,
  searchMode = 'Ranked BM25',
  didYouMean = null,
  onDidYouMeanClick,
}) {
  return (
    <div className={styles.statsContainer}>
      <div className={styles.statsBar} aria-live="polite">
        <span>
          About {totalHits} {totalHits === 1 ? 'result' : 'results'}
        </span>
        {executionTimeMs !== null && (
          <span>({executionTimeMs} ms)</span>
        )}
        {searchMode && (
          <span className={styles.modeBadge}>{searchMode}</span>
        )}
      </div>

      {didYouMean && (
        <div className={styles.didYouMean}>
          Did you mean:{' '}
          <button
            type="button"
            className={styles.didYouMeanLink}
            onClick={() => onDidYouMeanClick && onDidYouMeanClick(didYouMean)}
          >
            {didYouMean}
          </button>
          ?
        </div>
      )}
    </div>
  );
}
