package com.searchforge.ranking.evaluation;

import java.util.*;

/**
 * Authoritative evaluation dataset for Phase 12 Search Quality Evaluation.
 *
 * Spans 48 categorized queries across 8 Information Retrieval archetypes:
 * 1. EXACT_KEYWORD (Q01 - Q06)
 * 2. BROAD_TOPICAL (Q07 - Q12)
 * 3. CONCEPTUAL (Q13 - Q18)
 * 4. MULTI_CONCEPT (Q19 - Q24)
 * 5. AMBIGUOUS (Q25 - Q30)
 * 6. NATURAL_LANGUAGE (Q31 - Q36)
 * 7. RARE_TERM (Q37 - Q42)
 * 8. SYNONYM (Q43 - Q48)
 *
 * Ground-truth relevance judgments adhere to a 4-level graded scale [0, 3]:
 * - Grade 3: Highly Relevant (authoritative, primary target)
 * - Grade 2: Relevant (substantive topical coverage)
 * - Grade 1: Slightly Relevant (topically adjacent or mentions sub-concepts)
 * - Grade 0: Not Relevant (unjudged or irrelevant)
 *
 * Partitioned into three strictly isolated splits:
 * - DEV (24 queries, 50%): Exploratory analysis and baseline benchmarking.
 * - TUNING (12 queries, 25%): Hyperparameter optimization and parameter tuning.
 * - EVAL (12 queries, 25%): Held-out test set evaluated strictly once to report final generalization.
 */
public final class SearchForgeEvaluationDataset {

    private static final List<EvaluationQueryV2> ALL_QUERIES;
    private static final Map<EvaluationDatasetSplit, List<EvaluationQueryV2>> SPLIT_QUERIES;
    private static final Map<EvaluationCategory, List<EvaluationQueryV2>> CATEGORY_QUERIES;
    private static final Map<String, EvaluationQueryV2> QUERY_MAP;

    static {
        List<EvaluationQueryV2> qList = new ArrayList<>(48);

        // =========================================================================
        // 1. EXACT KEYWORD QUERIES (Q01 - Q06)
        // =========================================================================
        qList.add(new EvaluationQueryV2(
                "Q01", "java 21 virtual threads", EvaluationCategory.EXACT_KEYWORD, EvaluationDatasetSplit.DEV,
                Map.of(101L, 3, 104L, 2, 102L, 1),
                "Doc 101 is the authoritative Java 21 virtual threads guide; Doc 104 covers thread pool utilities; Doc 102 covers reactive concurrency."
        ));
        qList.add(new EvaluationQueryV2(
                "Q02", "postgresql acid transaction isolation mvcc", EvaluationCategory.EXACT_KEYWORD, EvaluationDatasetSplit.DEV,
                Map.of(106L, 3, 110L, 1),
                "Doc 106 specifically covers PostgreSQL MVCC and ACID transaction reliability; Doc 110 covers generic SQL syntax."
        ));
        qList.add(new EvaluationQueryV2(
                "Q03", "raft consensus algorithm distributed state", EvaluationCategory.EXACT_KEYWORD, EvaluationDatasetSplit.DEV,
                Map.of(107L, 3, 108L, 1),
                "Doc 107 explains the Raft distributed consensus algorithm; Doc 108 touches distributed microservices."
        ));
        qList.add(new EvaluationQueryV2(
                "Q04", "supermassive black holes event horizons", EvaluationCategory.EXACT_KEYWORD, EvaluationDatasetSplit.TUNING,
                Map.of(141L, 3, 142L, 1),
                "Doc 141 covers supermassive black holes and event horizons in galaxies; Doc 142 covers stellar evolution collapse."
        ));
        qList.add(new EvaluationQueryV2(
                "Q05", "gutenberg movable type printing press", EvaluationCategory.EXACT_KEYWORD, EvaluationDatasetSplit.TUNING,
                Map.of(151L, 3, 152L, 1),
                "Doc 151 covers Johannes Gutenberg's movable type printing press; Doc 152 covers ancient papyrus scrolls."
        ));
        qList.add(new EvaluationQueryV2(
                "Q06", "sourdough bread wild yeast fermentation", EvaluationCategory.EXACT_KEYWORD, EvaluationDatasetSplit.EVAL,
                Map.of(181L, 3, 183L, 1),
                "Doc 181 covers sourdough wild yeast fermentation and oven baking; Doc 183 covers fresh Italian semolina pasta."
        ));

        // =========================================================================
        // 2. BROAD TOPICAL QUERIES (Q07 - Q12)
        // =========================================================================
        qList.add(new EvaluationQueryV2(
                "Q07", "relational database systems storage and transactions", EvaluationCategory.BROAD_TOPICAL, EvaluationDatasetSplit.DEV,
                Map.of(106L, 3, 109L, 3, 110L, 2),
                "Docs 106 (PostgreSQL transactions) and 109 (B-Tree storage layouts) are highly relevant; Doc 110 covers SQL queries."
        ));
        qList.add(new EvaluationQueryV2(
                "Q08", "human cardiovascular system circulation and blood pressure", EvaluationCategory.BROAD_TOPICAL, EvaluationDatasetSplit.DEV,
                Map.of(121L, 3, 122L, 3, 123L, 1),
                "Docs 121 (heart anatomy) and 122 (arterial blood pressure) are primary; Doc 123 covers aerobic exercise."
        ));
        qList.add(new EvaluationQueryV2(
                "Q09", "astronomical universe galaxies stars and black holes", EvaluationCategory.BROAD_TOPICAL, EvaluationDatasetSplit.DEV,
                Map.of(141L, 3, 142L, 3, 143L, 2, 144L, 1),
                "Docs 141 and 142 are primary targets for deep cosmos astrophysics; Docs 143 and 144 provide adjacent context."
        ));
        qList.add(new EvaluationQueryV2(
                "Q10", "plant biology photosynthesis and carbon absorption", EvaluationCategory.BROAD_TOPICAL, EvaluationDatasetSplit.TUNING,
                Map.of(131L, 3, 132L, 3, 133L, 2, 135L, 1),
                "Docs 131 (photosynthesis) and 132 (forest carbon absorption) directly address botanical energy and carbon cycles."
        ));
        qList.add(new EvaluationQueryV2(
                "Q11", "acoustic and keyboard musical instruments", EvaluationCategory.BROAD_TOPICAL, EvaluationDatasetSplit.EVAL,
                Map.of(161L, 3, 162L, 3, 164L, 1, 165L, 1),
                "Doc 161 covers acoustic guitar; Doc 162 covers grand piano mechanics."
        ));
        qList.add(new EvaluationQueryV2(
                "Q12", "urban transportation and bicycle mobility", EvaluationCategory.BROAD_TOPICAL, EvaluationDatasetSplit.EVAL,
                Map.of(171L, 3, 172L, 2, 175L, 2, 173L, 1),
                "Doc 171 covers urban bicycle commuting; Docs 172 and 175 cover mechanical gearing and micro-mobility."
        ));

        // =========================================================================
        // 3. CONCEPTUAL QUERIES (Q13 - Q18)
        // =========================================================================
        qList.add(new EvaluationQueryV2(
                "Q13", "how can I make software run faster with lightweight concurrency", EvaluationCategory.CONCEPTUAL, EvaluationDatasetSplit.DEV,
                Map.of(101L, 3, 104L, 2, 113L, 1),
                "Doc 101 covers virtual threads reducing concurrency overhead; Doc 104 covers thread pool tuning; Doc 113 covers CPU caches."
        ));
        qList.add(new EvaluationQueryV2(
                "Q14", "preventing lower back strain when sitting at a desk all day", EvaluationCategory.CONCEPTUAL, EvaluationDatasetSplit.DEV,
                Map.of(125L, 3, 123L, 1),
                "Doc 125 covers ergonomic office furniture with lumbar curvature to reduce back strain during computer work."
        ));
        qList.add(new EvaluationQueryV2(
                "Q15", "deep space gravitational attraction bending light beams", EvaluationCategory.CONCEPTUAL, EvaluationDatasetSplit.DEV,
                Map.of(143L, 3, 141L, 2, 142L, 1),
                "Doc 143 covers gravitational lensing bending light beams around massive cosmic structures."
        ));
        qList.add(new EvaluationQueryV2(
                "Q16", "how living organisms turn sunlight into chemical energy", EvaluationCategory.CONCEPTUAL, EvaluationDatasetSplit.TUNING,
                Map.of(131L, 3, 132L, 2),
                "Doc 131 covers plant photosynthesis converting solar sunlight into glucose and oxygen."
        ));
        qList.add(new EvaluationQueryV2(
                "Q17", "preventing data loss when a computer server crashes suddenly", EvaluationCategory.CONCEPTUAL, EvaluationDatasetSplit.TUNING,
                Map.of(106L, 3, 107L, 2, 112L, 1),
                "Doc 106 covers Write-Ahead Logging (WAL) and crash consistency; Doc 107 covers Raft replicated log commit safety."
        ));
        qList.add(new EvaluationQueryV2(
                "Q18", "amplifying string vibrations into warm acoustic tones", EvaluationCategory.CONCEPTUAL, EvaluationDatasetSplit.EVAL,
                Map.of(161L, 3, 164L, 2, 162L, 1),
                "Doc 161 covers soundboards amplifying string vibrations in acoustic guitars; Doc 164 covers violin acoustic dampening."
        ));

        // =========================================================================
        // 4. MULTI-CONCEPT QUERIES (Q19 - Q24)
        // =========================================================================
        qList.add(new EvaluationQueryV2(
                "Q19", "database indexing and crash recovery logs", EvaluationCategory.MULTI_CONCEPT, EvaluationDatasetSplit.DEV,
                Map.of(106L, 3, 109L, 3, 110L, 1),
                "Combines relational indexing (Doc 109) with WAL crash recovery logs (Doc 106)."
        ));
        qList.add(new EvaluationQueryV2(
                "Q20", "microservices communication and distributed consensus", EvaluationCategory.MULTI_CONCEPT, EvaluationDatasetSplit.DEV,
                Map.of(108L, 3, 107L, 3, 106L, 1),
                "Combines decentralized microservices architecture (Doc 108) with Raft distributed consensus (Doc 107)."
        ));
        qList.add(new EvaluationQueryV2(
                "Q21", "cardiac blood circulation and arterial resistance", EvaluationCategory.MULTI_CONCEPT, EvaluationDatasetSplit.DEV,
                Map.of(121L, 3, 122L, 3, 123L, 1),
                "Combines heart chamber circulation (Doc 121) with systemic arterial vascular resistance (Doc 122)."
        ));
        qList.add(new EvaluationQueryV2(
                "Q22", "solid state storage and microprocessor cache memory", EvaluationCategory.MULTI_CONCEPT, EvaluationDatasetSplit.TUNING,
                Map.of(112L, 3, 113L, 3, 111L, 1),
                "Combines PCIe NVMe flash storage (Doc 112) with multi-level CPU cache hierarchies (Doc 113)."
        ));
        qList.add(new EvaluationQueryV2(
                "Q23", "plant water transport and forest carbon storage", EvaluationCategory.MULTI_CONCEPT, EvaluationDatasetSplit.EVAL,
                Map.of(133L, 3, 132L, 3, 131L, 2),
                "Combines xylem water transport (Doc 133) with forest ecosystem carbon sequestration (Doc 132)."
        ));
        qList.add(new EvaluationQueryV2(
                "Q24", "coffee roasting chemistry and bread fermentation", EvaluationCategory.MULTI_CONCEPT, EvaluationDatasetSplit.EVAL,
                Map.of(182L, 3, 181L, 3, 184L, 1),
                "Combines coffee bean roasting chemistry (Doc 182) with sourdough wild yeast fermentation (Doc 181)."
        ));

        // =========================================================================
        // 5. AMBIGUOUS QUERIES (Q25 - Q30)
        // =========================================================================
        qList.add(new EvaluationQueryV2(
                "Q25", "concurrency", EvaluationCategory.AMBIGUOUS, EvaluationDatasetSplit.DEV,
                Map.of(101L, 3, 102L, 3, 104L, 3, 106L, 2),
                "Terse query matching Java virtual threads (Doc 101), reactive streams (Doc 102), and executor thread pools (Doc 104)."
        ));
        qList.add(new EvaluationQueryV2(
                "Q26", "postgres", EvaluationCategory.AMBIGUOUS, EvaluationDatasetSplit.DEV,
                Map.of(106L, 3, 110L, 2, 109L, 1),
                "Single-word query specifically targeting PostgreSQL database engine (Doc 106) and SQL tables (Doc 110)."
        ));
        qList.add(new EvaluationQueryV2(
                "Q27", "scheduler", EvaluationCategory.AMBIGUOUS, EvaluationDatasetSplit.DEV,
                Map.of(111L, 3, 104L, 1),
                "Matches Linux Completely Fair Scheduler (Doc 111) and concurrent task executors (Doc 104)."
        ));
        qList.add(new EvaluationQueryV2(
                "Q28", "photosynthesis", EvaluationCategory.AMBIGUOUS, EvaluationDatasetSplit.TUNING,
                Map.of(131L, 3, 132L, 2),
                "Direct botanical query targeting plant chloroplast energy conversion (Doc 131) and forest canopies (Doc 132)."
        ));
        qList.add(new EvaluationQueryV2(
                "Q29", "black holes", EvaluationCategory.AMBIGUOUS, EvaluationDatasetSplit.TUNING,
                Map.of(141L, 3, 142L, 2, 143L, 1),
                "Broad astrological query targeting supermassive black holes (Doc 141) and stellar collapse (Doc 142)."
        ));
        qList.add(new EvaluationQueryV2(
                "Q30", "derailleur", EvaluationCategory.AMBIGUOUS, EvaluationDatasetSplit.EVAL,
                Map.of(172L, 3, 171L, 2),
                "Targeted bicycle component query for chain drive derailleur mechanics (Doc 172) and urban cycling (Doc 171)."
        ));

        // =========================================================================
        // 6. NATURAL LANGUAGE QUESTIONS (Q31 - Q36)
        // =========================================================================
        qList.add(new EvaluationQueryV2(
                "Q31", "how do green trees absorb atmospheric carbon dioxide from the air", EvaluationCategory.NATURAL_LANGUAGE, EvaluationDatasetSplit.DEV,
                Map.of(132L, 3, 131L, 2),
                "Doc 132 directly addresses forest carbon sequestration; Doc 131 covers chloroplast photosynthesis."
        ));
        qList.add(new EvaluationQueryV2(
                "Q32", "how do microservice architectures communicate across decentralized services", EvaluationCategory.NATURAL_LANGUAGE, EvaluationDatasetSplit.DEV,
                Map.of(108L, 3, 107L, 1),
                "Doc 108 covers autonomous services communicating over lightweight REST APIs or gRPC."
        ));
        qList.add(new EvaluationQueryV2(
                "Q33", "what happens inside the heart chambers during blood circulation", EvaluationCategory.NATURAL_LANGUAGE, EvaluationDatasetSplit.DEV,
                Map.of(121L, 3, 122L, 1),
                "Doc 121 details the four chambers (atria and ventricles) pumping oxygenated blood into the aorta."
        ));
        qList.add(new EvaluationQueryV2(
                "Q34", "how does a bicycle derailleur shift gears across the chain", EvaluationCategory.NATURAL_LANGUAGE, EvaluationDatasetSplit.TUNING,
                Map.of(172L, 3, 171L, 1),
                "Doc 172 details parallel derailleur cages shifting drive chains across front and rear cog cassettes."
        ));
        qList.add(new EvaluationQueryV2(
                "Q35", "how do CPUs use memory caches to prevent slow RAM access", EvaluationCategory.NATURAL_LANGUAGE, EvaluationDatasetSplit.EVAL,
                Map.of(113L, 3, 111L, 1),
                "Doc 113 explains multi-level L1, L2, and L3 cache hierarchies bridging the processor memory speed gap."
        ));
        qList.add(new EvaluationQueryV2(
                "Q36", "how was information printed and distributed before modern computers", EvaluationCategory.NATURAL_LANGUAGE, EvaluationDatasetSplit.EVAL,
                Map.of(151L, 3, 152L, 2, 155L, 1),
                "Doc 151 covers movable metal type printing presses; Doc 152 covers handwritten ancient papyrus scrolls."
        ));

        // =========================================================================
        // 7. RARE TERM / TECHNICAL JARGON QUERIES (Q37 - Q42)
        // =========================================================================
        qList.add(new EvaluationQueryV2(
                "Q37", "cfs red-black tree virtual runtime", EvaluationCategory.RARE_TERM, EvaluationDatasetSplit.DEV,
                Map.of(111L, 3),
                "Doc 111 is the exact and only document describing Linux CFS using red-black trees and virtual runtime."
        ));
        qList.add(new EvaluationQueryV2(
                "Q38", "nvme pcie flash storage controller iops", EvaluationCategory.RARE_TERM, EvaluationDatasetSplit.DEV,
                Map.of(112L, 3),
                "Doc 112 specifically covers NVMe PCIe multi-queue flash storage architectures delivering millions of IOPS."
        ));
        qList.add(new EvaluationQueryV2(
                "Q39", "mvcc write-ahead logging wal", EvaluationCategory.RARE_TERM, EvaluationDatasetSplit.DEV,
                Map.of(106L, 3),
                "Doc 106 specifically covers PostgreSQL Multi-Version Concurrency Control (MVCC) and Write-Ahead Logging (WAL)."
        ));
        qList.add(new EvaluationQueryV2(
                "Q40", "chloroplast thylakoids calvin cycle", EvaluationCategory.RARE_TERM, EvaluationDatasetSplit.TUNING,
                Map.of(131L, 3),
                "Doc 131 specifically covers light-dependent reactions in chloroplast thylakoids and the biochemical Calvin cycle."
        ));
        qList.add(new EvaluationQueryV2(
                "Q41", "xylem tracheids transpiration pull capillary action", EvaluationCategory.RARE_TERM, EvaluationDatasetSplit.TUNING,
                Map.of(133L, 3),
                "Doc 133 covers vascular plant water transport through xylem tracheids driven by transpiration pull."
        ));
        qList.add(new EvaluationQueryV2(
                "Q42", "escapement mechanism hammer strikes piano", EvaluationCategory.RARE_TERM, EvaluationDatasetSplit.EVAL,
                Map.of(162L, 3),
                "Doc 162 covers grand piano action escapement mechanisms and felt hammer strikes on tensioned wire strings."
        ));

        // =========================================================================
        // 8. SYNONYM-BASED QUERIES (Q43 - Q48)
        // =========================================================================
        qList.add(new EvaluationQueryV2(
                "Q43", "espresso bean brewing and hot morning caffeinated beverage", EvaluationCategory.SYNONYM, EvaluationDatasetSplit.DEV,
                Map.of(182L, 3),
                "Doc 182 covers coffee bean roasting chemistry and high-pressure espresso extraction producing crema."
        ));
        qList.add(new EvaluationQueryV2(
                "Q44", "cardiac muscular organ pumping arterial blood", EvaluationCategory.SYNONYM, EvaluationDatasetSplit.DEV,
                Map.of(121L, 3, 122L, 2),
                "Doc 121 covers human heart anatomy pumping oxygenated blood into systemic arterial circulation."
        ));
        qList.add(new EvaluationQueryV2(
                "Q45", "two-wheeled human pedal transit for urban travel", EvaluationCategory.SYNONYM, EvaluationDatasetSplit.DEV,
                Map.of(171L, 3, 172L, 1),
                "Doc 171 covers commuter bicycles providing efficient zero-emission urban pedal transit."
        ));
        qList.add(new EvaluationQueryV2(
                "Q46", "wild yeast sourdough fermentation and baked wheat loaf", EvaluationCategory.SYNONYM, EvaluationDatasetSplit.TUNING,
                Map.of(181L, 3),
                "Doc 181 covers sourdough wild yeast fermentation, gluten networks, and oven baked loaves."
        ));
        qList.add(new EvaluationQueryV2(
                "Q47", "stellar core collapse and catastrophic supernova explosion", EvaluationCategory.SYNONYM, EvaluationDatasetSplit.EVAL,
                Map.of(142L, 3, 141L, 1),
                "Doc 142 covers massive stars ending fusion lifecycles in supernova explosions leaving neutron stars."
        ));
        qList.add(new EvaluationQueryV2(
                "Q48", "ancient papyrus manuscript repository in egypt", EvaluationCategory.SYNONYM, EvaluationDatasetSplit.EVAL,
                Map.of(152L, 3, 151L, 1),
                "Doc 152 covers the ancient Library of Alexandria collecting handwritten papyrus scrolls and treatises."
        ));

        ALL_QUERIES = Collections.unmodifiableList(qList);

        // Populate split mappings
        Map<EvaluationDatasetSplit, List<EvaluationQueryV2>> splitMap = new EnumMap<>(EvaluationDatasetSplit.class);
        for (EvaluationDatasetSplit s : EvaluationDatasetSplit.values()) {
            splitMap.put(s, new ArrayList<>());
        }
        for (EvaluationQueryV2 q : qList) {
            splitMap.get(q.split()).add(q);
        }
        for (Map.Entry<EvaluationDatasetSplit, List<EvaluationQueryV2>> entry : splitMap.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        SPLIT_QUERIES = Collections.unmodifiableMap(splitMap);

        // Populate category mappings
        Map<EvaluationCategory, List<EvaluationQueryV2>> catMap = new EnumMap<>(EvaluationCategory.class);
        for (EvaluationCategory c : EvaluationCategory.values()) {
            catMap.put(c, new ArrayList<>());
        }
        for (EvaluationQueryV2 q : qList) {
            catMap.get(q.category()).add(q);
        }
        for (Map.Entry<EvaluationCategory, List<EvaluationQueryV2>> entry : catMap.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        CATEGORY_QUERIES = Collections.unmodifiableMap(catMap);

        // Populate lookup map by ID
        Map<String, EvaluationQueryV2> idMap = new LinkedHashMap<>();
        for (EvaluationQueryV2 q : qList) {
            idMap.put(q.queryId(), q);
        }
        QUERY_MAP = Collections.unmodifiableMap(idMap);
    }

    private SearchForgeEvaluationDataset() {
        // Utility class
    }

    public static List<EvaluationQueryV2> getAllQueries() {
        return ALL_QUERIES;
    }

    public static List<EvaluationQueryV2> getQueriesBySplit(EvaluationDatasetSplit split) {
        return SPLIT_QUERIES.getOrDefault(split, List.of());
    }

    public static List<EvaluationQueryV2> getQueriesByCategory(EvaluationCategory category) {
        return CATEGORY_QUERIES.getOrDefault(category, List.of());
    }

    public static Optional<EvaluationQueryV2> getQueryById(String queryId) {
        return Optional.ofNullable(QUERY_MAP.get(queryId));
    }

    public static int size() {
        return ALL_QUERIES.size();
    }
}
