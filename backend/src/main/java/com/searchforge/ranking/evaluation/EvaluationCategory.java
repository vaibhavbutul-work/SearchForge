package com.searchforge.ranking.evaluation;

/**
 * Information Retrieval query archetypes specified for Phase 12 Search Quality Evaluation:
 *
 * 1. EXACT_KEYWORD: High lexical overlap, exact canonical technical terms.
 * 2. BROAD_TOPICAL: Wide subject areas spanning multiple relevant documents.
 * 3. CONCEPTUAL: Intent-based queries with minimal or zero direct literal keyword overlap.
 * 4. MULTI_CONCEPT: Queries requiring intersection of multiple distinct technical domains.
 * 5. AMBIGUOUS: Short, underspecified queries with multiple valid interpretations.
 * 6. NATURAL_LANGUAGE: Full conversational questions and natural sentence queries.
 * 7. RARE_TERM: Highly specialized domain jargon and low-frequency technical tokens.
 * 8. SYNONYM: Queries where user expresses intent using vocabulary absent from the target text.
 */
public enum EvaluationCategory {
    EXACT_KEYWORD,
    BROAD_TOPICAL,
    CONCEPTUAL,
    MULTI_CONCEPT,
    AMBIGUOUS,
    NATURAL_LANGUAGE,
    RARE_TERM,
    SYNONYM
}
