import React, { useState } from 'react';
import RankingExplanation from './RankingExplanation';
import styles from './SearchResultItem.module.css';

export default function SearchResultItem({
  result,
  rank = 1,
  mode = 'hybrid',
  onExplain,
}) {
  const [isExpanded, setIsExpanded] = useState(false);
  const [copied, setCopied] = useState(false);

  const {
    docId,
    documentId,
    title = 'Untitled Document',
    url = '#',
    snippet = '',
    highlightedSnippet = null,
    score = null,
    domain = '',
    explanation = null,
    matchedTerms = [],
    bm25Score = null,
    semanticScore = null,
    bm25Rank = null,
    semanticRank = null,
    rrfScore = null,
  } = result || {};

  const effectiveId = docId ?? documentId;

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

        {/* Multi-Modal Hybrid Badges */}
        {rrfScore != null && (
          <span className={styles.rrfBadge} title="Reciprocal Rank Fusion Combined Score">
            RRF: {typeof rrfScore === 'number' ? rrfScore.toFixed(4) : rrfScore}
          </span>
        )}

        {bm25Score != null && (
          <span className={styles.bm25Badge} title={`BM25 Lexical Score (Rank #${bm25Rank || '-'})`}>
            BM25: {typeof bm25Score === 'number' ? bm25Score.toFixed(2) : bm25Score} {bm25Rank ? `(#${bm25Rank})` : ''}
          </span>
        )}

        {semanticScore != null && (
          <span className={styles.semanticBadge} title={`Dense Vector Cosine Similarity (Rank #${semanticRank || '-'})`}>
            Vector: {typeof semanticScore === 'number' ? `${(semanticScore * 100).toFixed(1)}%` : semanticScore} {semanticRank ? `(#${semanticRank})` : ''}
          </span>
        )}

        {/* Fallback Score Badge for pure lexical/single-mode when sub-scores are not returned */}
        {rrfScore == null && bm25Score == null && semanticScore == null && score != null && (
          <span className={styles.scoreBadge} title="Relevance Score">
            Score: {typeof score === 'number' ? score.toFixed(4) : score}
          </span>
        )}

        <button
          type="button"
          className={styles.explainButton}
          onClick={handleToggleExplain}
          aria-expanded={isExpanded}
          aria-controls={`explain-panel-${effectiveId}`}
          title="Inspect relevance ranking decomposition"
        >
          {isExpanded ? 'Hide ranking breakdown ▴' : 'Why this result? ▾'}
        </button>
      </div>

      {isExpanded && (
        <RankingExplanation
          explanation={explanation}
          score={score}
          rank={rank}
          docId={effectiveId}
          matchedTerms={matchedTerms}
          bm25Score={bm25Score}
          semanticScore={semanticScore}
          bm25Rank={bm25Rank}
          semanticRank={semanticRank}
          rrfScore={rrfScore}
        />
      )}
    </article>
  );
}
