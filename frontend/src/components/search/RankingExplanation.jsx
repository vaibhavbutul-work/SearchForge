import React from 'react';
import styles from './RankingExplanation.module.css';

export default function RankingExplanation({
  explanation = null,
  score = 0,
  rank = 1,
  docId = null,
  matchedTerms = [],
  bm25Score = null,
  semanticScore = null,
  bm25Rank = null,
  semanticRank = null,
  rrfScore = null,
}) {
  const isHybrid = rrfScore != null || (bm25Score != null && semanticScore != null);
  const hasBm25Details = explanation && Object.keys(explanation).length > 0;

  const bm25Contribution = bm25Rank ? (1.0 / (60 + bm25Rank)) : 0;
  const semanticContribution = semanticRank ? (1.0 / (60 + semanticRank)) : 0;

  return (
    <div
      id={`explain-panel-${docId}`}
      className={styles.explanationPanel}
      role="region"
      aria-label={`Ranking explanation for result ${rank}`}
    >
      <div className={styles.panelHeader}>
        <span className={styles.panelTitle}>
          <span>&#9881;</span> {isHybrid ? 'Multi-Modal Rank Fusion (RRF)' : 'Relevance Ranking Decomposition'}
        </span>
        <span className={styles.scoreSummary}>
          Final Rank: #{rank} &bull; Score: {typeof (rrfScore ?? score) === 'number' ? (rrfScore ?? score).toFixed(6) : score}
        </span>
      </div>

      {/* Multi-Modal Hybrid Decomposition Cards */}
      {(isHybrid || bm25Score != null || semanticScore != null) && (
        <div className={styles.fusionSection}>
          <div className={styles.fusionCardsGrid}>
            {/* BM25 Lexical Source */}
            <div className={styles.fusionCard}>
              <div className={styles.fusionCardHeader}>
                <span className={styles.fusionCardIcon}>🔤</span>
                <span className={styles.fusionCardTitle}>Lexical BM25</span>
              </div>
              <div className={styles.fusionCardStat}>
                <span className={styles.fusionStatLabel}>Source Rank:</span>
                <span className={styles.fusionStatValue}>
                  {bm25Rank ? `#${bm25Rank}` : <em style={{ color: 'var(--color-text-muted)' }}>Unranked</em>}
                </span>
              </div>
              <div className={styles.fusionCardStat}>
                <span className={styles.fusionStatLabel}>BM25 Score:</span>
                <span className={styles.fusionStatValue}>
                  {bm25Score != null ? bm25Score.toFixed(4) : 'N/A'}
                </span>
              </div>
              <div className={styles.fusionCardStat}>
                <span className={styles.fusionStatLabel}>RRF Contribution:</span>
                <span className={styles.fusionStatFormula}>
                  {bm25Rank ? `1/(60+${bm25Rank}) = +${bm25Contribution.toFixed(6)}` : '+0.000000'}
                </span>
              </div>
            </div>

            {/* Dense Semantic Source */}
            <div className={styles.fusionCard}>
              <div className={styles.fusionCardHeader}>
                <span className={styles.fusionCardIcon}>🧠</span>
                <span className={styles.fusionCardTitle}>Dense Semantic</span>
              </div>
              <div className={styles.fusionCardStat}>
                <span className={styles.fusionStatLabel}>Source Rank:</span>
                <span className={styles.fusionStatValue}>
                  {semanticRank ? `#${semanticRank}` : <em style={{ color: 'var(--color-text-muted)' }}>Unranked</em>}
                </span>
              </div>
              <div className={styles.fusionCardStat}>
                <span className={styles.fusionStatLabel}>Cosine Sim:</span>
                <span className={styles.fusionStatValue}>
                  {semanticScore != null ? `${(semanticScore * 100).toFixed(1)}% (${semanticScore.toFixed(4)})` : 'N/A'}
                </span>
              </div>
              <div className={styles.fusionCardStat}>
                <span className={styles.fusionStatLabel}>RRF Contribution:</span>
                <span className={styles.fusionStatFormula}>
                  {semanticRank ? `1/(60+${semanticRank}) = +${semanticContribution.toFixed(6)}` : '+0.000000'}
                </span>
              </div>
            </div>

            {/* Final Fused Position */}
            <div className={`${styles.fusionCard} ${styles.fusionCardTotal}`}>
              <div className={styles.fusionCardHeader}>
                <span className={styles.fusionCardIcon}>⚡</span>
                <span className={styles.fusionCardTitle}>Fused Rank</span>
              </div>
              <div className={styles.fusionCardStat}>
                <span className={styles.fusionStatLabel}>Final Position:</span>
                <span className={styles.fusionStatValue} style={{ color: 'var(--color-accent)', fontWeight: 700 }}>
                  #{rank}
                </span>
              </div>
              <div className={styles.fusionCardStat}>
                <span className={styles.fusionStatLabel}>Total RRF:</span>
                <span className={styles.fusionStatValue} style={{ color: 'var(--color-accent)', fontWeight: 700 }}>
                  {typeof (rrfScore ?? score) === 'number' ? (rrfScore ?? score).toFixed(6) : score}
                </span>
              </div>
              <div className={styles.fusionCardStat}>
                <span className={styles.fusionStatLabel}>Constant k:</span>
                <span className={styles.fusionStatFormula}>k = 60 (SIGIR 2009)</span>
              </div>
            </div>
          </div>

          {/* Consensus Assessment */}
          <div className={styles.consensusBanner}>
            {bm25Rank && semanticRank ? (
              <span className={styles.consensusTextSuccess}>
                ✓ <strong>Multi-Modal Consensus:</strong> Strong relevance verified by both lexical inverted index matching and 384D dense vector continuous similarity.
              </span>
            ) : bm25Rank ? (
              <span className={styles.consensusTextLexical}>
                🔤 <strong>Lexical-Only Retrieval:</strong> Discovered through exact keyword matches in inverted index; lower semantic representation proximity.
              </span>
            ) : (
              <span className={styles.consensusTextSemantic}>
                🧠 <strong>Semantic-Only Retrieval:</strong> Discovered conceptually through dense neural embeddings despite vocabulary mismatch with query terms.
              </span>
            )}
          </div>
        </div>
      )}

      {/* BM25 Term Decomposition Table */}
      {hasBm25Details && (
        <>
          <div className={styles.subSectionHeader}>
            <span>Okapi BM25 Term-Level Decomposition</span>
          </div>
          <div className={styles.tableWrapper}>
            <table className={styles.scoreTable}>
              <thead>
                <tr>
                  <th>Matched Term</th>
                  <th>Raw TF</th>
                  <th>RSJ IDF</th>
                  <th>Saturation S(tf)</th>
                  <th>Contribution</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(explanation).map(([termKey, termData]) => {
                  const isPhrase = termKey.startsWith('PHRASE:');
                  const displayTerm = isPhrase ? termKey.replace('PHRASE:', '') : termKey;

                  return (
                    <tr key={termKey}>
                      <td>
                        {isPhrase ? (
                          <span className={styles.phraseCell}>
                            <span className={styles.phraseBadge}>Phrase</span>
                            &ldquo;{displayTerm}&rdquo;
                          </span>
                        ) : (
                          <span className={styles.termCell}>{displayTerm}</span>
                        )}
                      </td>
                      <td>{termData.rawTf}</td>
                      <td>{typeof termData.idf === 'number' ? termData.idf.toFixed(4) : termData.idf}</td>
                      <td>{typeof termData.saturation === 'number' ? termData.saturation.toFixed(4) : termData.saturation}</td>
                      <td className={styles.scoreCell}>
                        +{typeof termData.termScore === 'number' ? termData.termScore.toFixed(4) : termData.termScore}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </>
      )}

      <div className={styles.formulaNote}>
        <strong>Ranking Invariant:</strong> Reciprocal Rank Fusion combines ordinal ranks via <span className={styles.formulaMath}>RRF(d) = &sum; [ 1 / (60 + rank_r(d)) ]</span>, eliminating manual score calibration between unbounded BM25 scores and bounded cosine similarities.
      </div>
    </div>
  );
}
