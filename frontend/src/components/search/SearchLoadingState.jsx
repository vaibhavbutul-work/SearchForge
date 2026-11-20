import React from 'react';
import styles from './SearchLoadingState.module.css';

export default function SearchLoadingState({ count = 3 }) {
  const items = Array.from({ length: count });

  return (
    <div className={styles.loadingContainer} aria-busy="true" aria-label="Loading search results">
      {items.map((_, i) => (
        <div key={i} className={styles.skeletonItem}>
          <div className={`${styles.skeletonLine} ${styles.skeletonUrl}`} />
          <div className={`${styles.skeletonLine} ${styles.skeletonTitle}`} />
          <div className={`${styles.skeletonLine} ${styles.skeletonSnippetLine1}`} />
          <div className={`${styles.skeletonLine} ${styles.skeletonSnippetLine2}`} />
        </div>
      ))}
    </div>
  );
}
