import React, { useEffect } from 'react';
import styles from './AboutModal.module.css';

export default function AboutModal({ isOpen, onClose }) {
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className={styles.overlay} onClick={onClose} role="dialog" aria-modal="true" aria-labelledby="about-title">
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <h2 id="about-title" className={styles.title}>About SearchForge</h2>
          <button className={styles.closeButton} onClick={onClose} aria-label="Close dialog">
            &times;
          </button>
        </div>

        <div className={styles.body}>
          <p>
            <strong>SearchForge</strong> is a custom search engine built entirely from first principles
            in modern <strong>Java 21 LTS</strong> and <strong>Spring Boot</strong> without relying on third-party search libraries
            such as Lucene, Solr, or Elasticsearch.
          </p>

          <ul className={styles.highlightList}>
            <li>
              <span className={styles.check}>&bull;</span>
              <span><strong>Web Crawler:</strong> Project Loom virtual threads, RFC 9309 robots compliance, per-domain rate limiting.</span>
            </li>
            <li>
              <span className={styles.check}>&bull;</span>
              <span><strong>Linguistic Processing:</strong> Unicode NFD normalizer, 130+ stop words, Martin Porter stemming cascade.</span>
            </li>
            <li>
              <span className={styles.check}>&bull;</span>
              <span><strong>Dual-Tier Index:</strong> Concurrent in-memory dictionary synchronized with PostgreSQL relational postings.</span>
            </li>
            <li>
              <span className={styles.check}>&bull;</span>
              <span><strong>Relevance Engine:</strong> Okapi BM25 scoring with Robertson-Sp&auml;rck Jones non-negative IDF and phrase boosting.</span>
            </li>
            <li>
              <span className={styles.check}>&bull;</span>
              <span><strong>Query Intelligence:</strong> Weighted Trie prefix autocomplete, Damerau-Levenshtein spelling, sliding snippets, and directional synonyms.</span>
            </li>
          </ul>

          <p>
            SearchForge serves real search rankings directly from memory in sub-millisecond query latencies.
          </p>
        </div>

        <div className={styles.footer}>
          <button className={styles.doneButton} onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
