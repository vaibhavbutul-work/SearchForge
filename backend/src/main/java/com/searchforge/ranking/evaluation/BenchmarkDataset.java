package com.searchforge.ranking.evaluation;

import java.util.List;
import java.util.Map;

/**
 * Controlled ground-truth evaluation dataset for Phase 7 Search Quality Evaluation (Task 7A).
 *
 * Spans 8 distinct query archetypes across the multi-domain corpus:
 * 1. Exact Keyword queries (high vocabulary overlap, exact terms)
 * 2. Technical queries (dense domain jargon, acronyms)
 * 3. Conceptual queries (intent-based, minimal keyword overlap)
 * 4. Synonym queries (vocabulary mismatch, paraphrasing)
 * 5. Natural Language questions (conversational queries)
 * 6. Low Overlap queries (descriptive queries without canonical terminology)
 * 7. Exact Phrase queries (quoted positional dependencies)
 * 8. Boolean queries (structured logical constraints)
 */
public final class BenchmarkDataset {

    private BenchmarkDataset() {
        // Utility class
    }

    public static List<CategorizedEvaluationQuery> getStandardDataset() {
        return List.of(
                // 1. Exact Keyword Queries
                new CategorizedEvaluationQuery(
                        "java 21 virtual threads",
                        QueryCategory.EXACT_KEYWORD,
                        Map.of(24L, 2, 19L, 1),
                        "Doc 24 is the authoritative Java 21 virtual threads documentation; Doc 19 mentions Java virtual threads."
                ),
                new CategorizedEvaluationQuery(
                        "spring boot microservices",
                        QueryCategory.EXACT_KEYWORD,
                        Map.of(19L, 2, 23L, 2, 20L, 1),
                        "Doc 19 is Spring Boot Microservices guide; Doc 23 is Martin Fowler's Microservices Architecture guide."
                ),
                new CategorizedEvaluationQuery(
                        "okapi bm25 probabilistic relevance ranking",
                        QueryCategory.EXACT_KEYWORD,
                        Map.of(22L, 2),
                        "Doc 22 is Okapi BM25 probabilistic relevance ranking."
                ),

                // 2. Technical Queries
                new CategorizedEvaluationQuery(
                        "relational database transaction reliability sql",
                        QueryCategory.TECHNICAL,
                        Map.of(21L, 2),
                        "Doc 21 is PostgreSQL open-source relational database covering transactions and reliability."
                ),
                new CategorizedEvaluationQuery(
                        "microprocessors dynamic ram solid-state nvme operating systems",
                        QueryCategory.TECHNICAL,
                        Map.of(10L, 2),
                        "Doc 10 covers personal computer hardware, CPUs, GPUs, RAM, NVMe, and operating systems."
                ),
                new CategorizedEvaluationQuery(
                        "cardiovascular circulation blood pressure arteries",
                        QueryCategory.TECHNICAL,
                        Map.of(17L, 2),
                        "Doc 17 covers human heart anatomy, blood pumping, arteries, and veins."
                ),

                // 3. Conceptual Queries (Zero/Low Direct Keyword Overlap)
                new CategorizedEvaluationQuery(
                        "how can I make software run faster with lightweight concurrency",
                        QueryCategory.CONCEPTUAL,
                        Map.of(24L, 2, 10L, 1),
                        "Doc 24 covers virtual threads reducing overhead for high-throughput concurrency; Doc 10 covers CPU computing."
                ),
                new CategorizedEvaluationQuery(
                        "what helps prevent back pain when sitting at a desk all day",
                        QueryCategory.CONCEPTUAL,
                        Map.of(5L, 2, 6L, 1),
                        "Doc 5 covers ergonomic office chairs with lumbar support to reduce back fatigue; Doc 6 covers desks."
                ),
                new CategorizedEvaluationQuery(
                        "celestial bodies and supermassive gravity in deep cosmos",
                        QueryCategory.CONCEPTUAL,
                        Map.of(13L, 2, 18L, 1),
                        "Doc 13 covers galaxies, black holes, dark matter, and cosmic dust; Doc 18 covers stars."
                ),

                // 4. Synonym Queries
                new CategorizedEvaluationQuery(
                        "canine obedience instruction and puppy breeds",
                        QueryCategory.SYNONYM,
                        Map.of(2L, 2),
                        "Doc 2 is domestic dogs, canine behavior, puppy obedience training, and breeds."
                ),
                new CategorizedEvaluationQuery(
                        "feline agility and nocturnal rodent hunting",
                        QueryCategory.SYNONYM,
                        Map.of(3L, 2),
                        "Doc 3 is domestic cats, feline biology, quick reflexes, and night hunting."
                ),
                new CategorizedEvaluationQuery(
                        "espresso bean brewing and hot morning beverage",
                        QueryCategory.SYNONYM,
                        Map.of(11L, 2),
                        "Doc 11 is coffee bean roasting, espresso brewing, and caffeine drinks."
                ),

                // 5. Natural Language Questions
                new CategorizedEvaluationQuery(
                        "how was information printed and distributed before modern computers",
                        QueryCategory.NATURAL_LANGUAGE,
                        Map.of(1L, 2),
                        "Doc 1 covers history of books, Johannes Gutenberg movable type printing press, and libraries."
                ),
                new CategorizedEvaluationQuery(
                        "how do plants turn sunlight into oxygen and absorb carbon",
                        QueryCategory.NATURAL_LANGUAGE,
                        Map.of(15L, 2, 18L, 1),
                        "Doc 15 covers trees absorbing carbon dioxide and releasing oxygen via photosynthesis; Doc 18 covers solar energy."
                ),
                new CategorizedEvaluationQuery(
                        "what is the mechanism used to measure time accurately",
                        QueryCategory.NATURAL_LANGUAGE,
                        Map.of(7L, 2),
                        "Doc 7 covers mechanical clocks, quartz wristwatches, atomic clocks, and timekeeping."
                ),

                // 6. Low Overlap Queries (Paraphrased / Descriptive)
                new CategorizedEvaluationQuery(
                        "two-wheeled pedal transportation for city travel",
                        QueryCategory.LOW_OVERLAP,
                        Map.of(9L, 2),
                        "Doc 9 covers bicycles, urban commuting, cycling, and pedals."
                ),
                new CategorizedEvaluationQuery(
                        "liquid beverage storage glass or plastic vessel",
                        QueryCategory.LOW_OVERLAP,
                        Map.of(4L, 2),
                        "Doc 4 covers bottles, glass, plastic, and liquid storage containers."
                ),
                new CategorizedEvaluationQuery(
                        "fermented wheat dough baked in an oven",
                        QueryCategory.LOW_OVERLAP,
                        Map.of(12L, 2),
                        "Doc 12 covers bread making, sourdough fermentation, wheat flour, and baking."
                ),

                // 7. Phrase Queries
                new CategorizedEvaluationQuery(
                        "\"spring framework\"",
                        QueryCategory.PHRASE,
                        Map.of(20L, 2, 19L, 1),
                        "Doc 20 is Spring Framework Core; Doc 19 is Spring Boot based on Spring Framework."
                ),
                new CategorizedEvaluationQuery(
                        "\"acoustic guitar\"",
                        QueryCategory.PHRASE,
                        Map.of(16L, 2),
                        "Doc 16 covers musical instruments, acoustic guitars, and pianos."
                ),

                // 8. Boolean Queries
                new CategorizedEvaluationQuery(
                        "microservices AND NOT legacy",
                        QueryCategory.BOOLEAN,
                        Map.of(19L, 2, 23L, 2),
                        "Doc 19 and 23 cover modern microservices architectures."
                ),
                new CategorizedEvaluationQuery(
                        "guitar OR piano",
                        QueryCategory.BOOLEAN,
                        Map.of(16L, 2),
                        "Doc 16 explicitly covers guitars and pianos."
                )
        );
    }
}
