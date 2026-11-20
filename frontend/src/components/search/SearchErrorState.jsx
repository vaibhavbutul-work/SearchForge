import React from 'react';
import styles from './SearchErrorState.module.css';

export default function SearchErrorState({
  message = 'An unexpected error occurred while communicating with the SearchForge backend.',
  onRetry,
}) {
  return (
    <div className={styles.errorContainer} role="alert">
      <h3 className={styles.title}>Unable to Retrieve Search Results</h3>
      <p className={styles.message}>{message}</p>
      {onRetry && (
        <button type="button" className={styles.retryButton} onClick={onRetry}>
          Try Again
        </button>
      )}
    </div>
  );
}
