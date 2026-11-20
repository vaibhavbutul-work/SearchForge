import React from 'react';
import styles from './TopKSelector.module.css';

const LIMIT_OPTIONS = [5, 10, 20, 50];

export default function TopKSelector({ limit = 10, onLimitChange }) {
  const handleChange = (e) => {
    const newLimit = parseInt(e.target.value, 10);
    if (onLimitChange && !isNaN(newLimit)) {
      onLimitChange(newLimit);
    }
  };

  return (
    <div className={styles.container}>
      <label htmlFor="top-k-select" className={styles.label}>
        Results:
      </label>
      <select
        id="top-k-select"
        className={styles.select}
        value={limit}
        onChange={handleChange}
        aria-label="Select number of top results to return"
      >
        {LIMIT_OPTIONS.map((val) => (
          <option key={val} value={val}>
            {val} per page
          </option>
        ))}
      </select>
    </div>
  );
}
