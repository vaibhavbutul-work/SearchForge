import React from 'react';
import styles from './RankingExplanation.module.css';

export default function RankingExplanation({
  explanation = null,
  score = 0,
  rank = 1,
  docId = null,
  matchedTerms = [],
}) {
  const hasDetails = explanation && Object.keys(explanation).length > 0;

  return (
    <div
      id={`explain-panel-${docId}`}
      className={styles.explanationPanel}
      role="region"
      aria-label={`Ranking explanation for result ${rank}`}
    >
      <div className={styles.panelHeader}>
        <span className={styles.panelTitle}>
          <span>&#9881;</span> Okapi BM25 Ranking Decomposition
        </span>
        <span className={styles.scoreSummary}>
          Final Score: {typeof score === 'number' ? score.toFixed(4) : score}
        </span>
      </div>

      {hasDetails ? (
        <>
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

          <div className={styles.formulaNote}>
            <strong>Scoring Invariant:</strong> Robertson-Sp&auml;rck Jones non-negative IDF rewards rare discriminative terms, while sublinear saturation (<em>k&#8321; = 1.2</em>) and document length normalization (<em>b = 0.75</em>) prevent keyword stuffing.
            <span className={styles.formulaMath}>
              Score(D, Q) = &sum; IDF(q) &times; [ tf &times; (k&#8321; + 1) / (tf + K) ] + PhraseBoost
            </span>
          </div>
        </>
      ) : (
        <div className={styles.formulaNote}>
          <p>
            Ranked at <strong>#{rank}</strong> with an aggregate BM25 score of <strong>{score}</strong>.
          </p>
          {matchedTerms && matchedTerms.length > 0 && (
            <p style={{ marginTop: '4px' }}>
              Matched query terms: <code>{matchedTerms.join(', ')}</code>
            </p>
          )}
        </div>
      )}
    </div>
  );
}
