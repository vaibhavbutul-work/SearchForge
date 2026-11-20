import React, { useState } from 'react';
import RankingExplanation from './RankingExplanation';
import styles from './SearchResultItem.module.css';

export default function SearchResultItem({
  result,
  rank = 1,
  onExplain,
}) {
  const [isExpanded, setIsExpanded] = useState(false);
  const [copied, setCopied] = useState(false);

  const {
    docId,
    title = 'Untitled Document',
    url = '#',
    snippet = '',
    highlightedSnippet = null,
    score = null,
    domain = '',
    explanation = null,
    matchedTerms = [],
  } = result || {};

  // Extract clean breadcrumb from URL if domain is not provided
  let displayDomain = domain;
  let displayPath = '';
  try {
    const parsedUrl = new URL(url);
    displayDomain = parsedUrl.hostname;
    displayPath = parsedUrl.pathname !== '/' ? parsedUrl.pathname.replace(/\/$/, '') : '';
  } catch {
    displayDomain = domain || url;
  }

  const handleCopyUrl = (e) => {
    e.preventDefault();
    if (url && url !== '#') {
      navigator.clipboard.writeText(url).then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }).catch(() => {});
    }
  };

  const handleToggleExplain = () => {
    setIsExpanded((prev) => !prev);
    if (onExplain) {
      onExplain(result);
    }
  };

  return (
    <article className={styles.resultItem} aria-label={`Result ${rank}: ${title}`}>
      <div className={styles.urlBreadcrumb}>
        <span className={styles.faviconPlaceholder} aria-hidden="true">&#9679;</span>
        <span className={styles.domainText}>{displayDomain}</span>
        {displayPath && <span className={styles.pathText}>&rsaquo; {displayPath}</span>}
        <button
          type="button"
          className={styles.copyBtn}
          onClick={handleCopyUrl}
          title="Copy document URL"
          aria-label={`Copy link for ${title}`}
        >
          {copied ? '✓ Copied' : '⧉ Copy'}
        </button>
      </div>

      <h3 className={styles.title}>
        <a
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          className={styles.titleLink}
        >
          {title}
        </a>
      </h3>

      {highlightedSnippet ? (
        <p
          className={styles.snippet}
          dangerouslySetInnerHTML={{ __html: highlightedSnippet }}
        />
      ) : (
        <p className={styles.snippet}>{snippet}</p>
      )}

      <div className={styles.metadataBar}>
        <span className={styles.rankBadge}>#{rank}</span>
        {score !== null && score !== undefined && (
          <span className={styles.scoreBadge}>
            Score: {typeof score === 'number' ? score.toFixed(4) : score}
          </span>
        )}
        <button
          type="button"
          className={styles.explainButton}
          onClick={handleToggleExplain}
          aria-expanded={isExpanded}
          aria-controls={`explain-panel-${docId}`}
          title="Inspect BM25 relevance score breakdown"
        >
          {isExpanded ? 'Hide explanation ▴' : 'Why this result? ▾'}
        </button>
      </div>

      {isExpanded && (
        <RankingExplanation
          explanation={explanation}
          score={score}
          rank={rank}
          docId={docId}
          matchedTerms={matchedTerms}
        />
      )}
    </article>
  );
}
