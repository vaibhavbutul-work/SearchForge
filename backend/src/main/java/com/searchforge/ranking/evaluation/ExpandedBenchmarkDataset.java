package com.searchforge.ranking.evaluation;

import com.searchforge.crawler.model.CrawledDocument;

import java.time.Instant;
import java.util.*;

/**
 * Synthetic expanded benchmark evaluation dataset for Phase 7.1 (Task 9).
 *
 * Contains 120 curated multi-domain synthetic documents (including relevant targets, near-miss distractors,
 * and background noise) and 40 categorized evaluation queries across all 8 Information Retrieval archetypes.
 *
 * Ground-truth relevance judgments (grades 2 = highly relevant, 1 = partially relevant) are established
 * independently of retrieval engine implementations with documented evaluation rationales.
 */
public final class ExpandedBenchmarkDataset {

    private ExpandedBenchmarkDataset() {
        // Utility class
    }

    /**
     * Generates the 120-document synthetic evaluation corpus.
     */
    public static List<CrawledDocument> getCorpus() {
        List<CrawledDocument> docs = new ArrayList<>(120);

        // Domain 1: Java & Concurrency (Docs 101 - 110)
        docs.add(createDoc(101L, "https://docs.oracle.com/java21/virtual-threads", "Java 21 Virtual Threads Documentation",
                "Virtual threads are lightweight threads introduced in Java 21 that dramatically reduce the overhead of writing, maintaining, and observing high-throughput concurrent applications. They allow servers to handle millions of simultaneous socket connections without exhausting operating system thread pools."));
        docs.add(createDoc(102L, "https://spring.io/guides/reactive-concurrency", "Reactive Concurrency with Project Reactor and Spring",
                "Reactive streams and non-blocking backpressure provide asynchronous concurrency on the JVM using event loops. Mono and Flux types represent asynchronous data sequences without dedicating physical OS threads per connection."));
        docs.add(createDoc(103L, "https://openjdk.org/jeps/jvm-memory-management", "JVM Garbage Collection and Memory Management Architecture",
                "Garbage collectors in the HotSpot JVM including ZGC, G1, and Shenandoah automatically reclaim heap memory. Generation boundaries, mark-and-sweep phases, and low-pause compaction minimize stop-the-world application stalls."));
        docs.add(createDoc(104L, "https://java.com/concurrency/executors", "Java Concurrency Utilities and Thread Pools",
                "The java.util.concurrent package provides ExecutorService, ForkJoinPool, and Semaphore abstractions. Bounded thread pools prevent uncontrolled memory consumption while coordinating parallel task submission."));
        docs.add(createDoc(105L, "https://searchforge.dev/distractor/legacy-java", "Legacy Java 1.4 Serialization Guidelines",
                "Object serialization in early Java releases allows writing object graphs to byte streams. Class version UID and transient fields govern backward compatibility across networks."));

        // Domain 2: Distributed Systems & Databases (Docs 106 - 115)
        docs.add(createDoc(106L, "https://www.postgresql.org/docs/transactions", "PostgreSQL ACID Transaction Isolation and MVCC",
                "PostgreSQL utilizes Multi-Version Concurrency Control (MVCC) to ensure ACID transaction reliability. Write-Ahead Logging (WAL) and write locks guarantee database consistency during server crashes and concurrent queries."));
        docs.add(createDoc(107L, "https://raft.github.io/consensus", "Raft Consensus Algorithm for Distributed Replicated State Machines",
                "Raft decomposes distributed consensus into leader election, log replication, and commit safety. Quorum voting ensures fault-tolerant cluster consensus across split-brain network partitions."));
        docs.add(createDoc(108L, "https://martinfowler.com/articles/microservices", "Microservices Architecture and Distributed Service Decoupling",
                "Microservice architecture decomposes monolithic systems into autonomous, independently deployable domain services. Services communicate over lightweight REST APIs or gRPC with decentralized data management."));
        docs.add(createDoc(109L, "https://database.guide/b-tree-indexing", "B-Tree and Inverted Index Physical Storage Layouts",
                "Relational and search engines organize records using balanced B-Tree structures and posting lists. Page-level disk storage, cache lines, and leaf node pointers accelerate logarithmic index retrieval."));
        docs.add(createDoc(110L, "https://searchforge.dev/distractor/sql-syntax", "Basic SQL Syntax for Relational Tables",
                "Structured Query Language uses SELECT, INSERT, UPDATE, and DELETE statements to query tabular database schemas and foreign key constraints."));

        // Domain 3: Operating Systems & Hardware Architecture (Docs 111 - 120)
        docs.add(createDoc(111L, "https://kernel.org/scheduler/cfs", "Linux Completely Fair Scheduler and Process Management",
                "The Linux kernel schedules task execution using the Completely Fair Scheduler (CFS) backed by a red-black tree. Virtual runtime tracking ensures proportional CPU core distribution across multi-core systems."));
        docs.add(createDoc(112L, "https://hardware.tech/nvme-solid-state", "PCIe NVMe Solid-State Drives and Flash Memory Controller",
                "Non-Volatile Memory Express (NVMe) utilizes PCI Express multi-queue architectures to deliver millions of IOPS and low latency flash storage compared to legacy SATA interfaces."));
        docs.add(createDoc(113L, "https://computer-arch.org/cpu-caches", "CPU Microarchitecture L1 L2 L3 Cache Hierarchy and Paging",
                "Microprocessors use multi-level cache hierarchies to bridge the memory speed gap. Cache coherence protocols like MESI coordinate simultaneous reads across CPU cores and translation lookaside buffers (TLB)."));
        docs.add(createDoc(114L, "https://searchforge.dev/distractor/vintage-pc", "Vintage Personal Computer Hardware from the 1980s",
                "Early personal computers featured 8-bit microprocessors, magnetic floppy disks, CRT monitors, and limited 64-kilobyte random access memory."));
        docs.add(createDoc(115L, "https://searchforge.dev/distractor/keyboard-switches", "Mechanical Keyboard Switches and Tactile Feedback",
                "Mechanical keyboards employ individual spring-loaded physical switches under each keycap to register keystrokes with tactile click or linear smooth actuation."));

        // Domain 4: Biomedical & Human Anatomy (Docs 121 - 130)
        docs.add(createDoc(121L, "https://medical.org/cardiology/heart-anatomy", "Human Heart Anatomy and Cardiovascular Circulation",
                "The human heart is a muscular organ with four chambers: left and right atria and ventricles. It pumps oxygenated blood through the aorta into systemic arterial circulation and receives venous blood return."));
        docs.add(createDoc(122L, "https://health.nih.gov/cardiovascular/hypertension", "Cardiovascular Blood Pressure Regulation and Arterial Resistance",
                "Blood pressure is determined by cardiac output and systemic peripheral vascular resistance. Arterial elasticity, baroreceptor reflexes, and renal hormonal feedback regulate systolic and diastolic arterial pressures."));
        docs.add(createDoc(123L, "https://searchforge.dev/distractor/exercise-cardio", "Aerobic Exercise Benefits for Physical Fitness",
                "Regular aerobic physical exercise enhances stamina, supports resting metabolic rate, and improves general well-being and muscular endurance."));
        docs.add(createDoc(124L, "https://searchforge.dev/distractor/herbal-tea", "Herbal Tea Blends and Caffeine Free Infusions",
                "Herbal infusions made from dried chamomile flowers, peppermint leaves, and lavender petals offer calming caffeine-free evening beverages."));
        docs.add(createDoc(125L, "https://searchforge.dev/distractor/ergonomic-chairs", "Ergonomic Office Furniture and Lumbar Posture",
                "Adjustable office chairs with lumbar curvature support spinal alignment, reducing lower back strain during long hours of computer desk work."));

        // Domain 5: Botany & Environmental Plant Biology (Docs 131 - 140)
        docs.add(createDoc(131L, "https://botany.org/photosynthesis-biochemistry", "Plant Photosynthesis Biochemistry and Chloroplast Energy Conversion",
                "Green plants convert solar sunlight, carbon dioxide, and water into chemical glucose and oxygen through light-dependent reactions in chloroplast thylakoids and the Calvin cycle."));
        docs.add(createDoc(132L, "https://ecology.org/forest-carbon-sequestration", "Forest Canopy Carbon Absorption and Ecosystem Biomass",
                "Forest ecosystems act as major terrestrial carbon sinks. Tree trunks, roots, and forest soils sequester atmospheric carbon dioxide, mitigating greenhouse gas concentrations."));
        docs.add(createDoc(133L, "https://botany.org/vascular-xylem-phloem", "Vascular Plant Water Transport via Xylem and Phloem",
                "Vascular plants transport water and dissolved soil minerals upward from roots through xylem tracheids driven by transpiration pull and capillary action."));
        docs.add(createDoc(134L, "https://searchforge.dev/distractor/indoor-houseplants", "Indoor Houseplant Care and Potting Soil Drainage",
                "Popular indoor houseplants such as snake plants and pothos thrive with indirect sunlight, porous potting soil, and periodic watering schedules."));
        docs.add(createDoc(135L, "https://searchforge.dev/distractor/botanical-gardens", "Botanical Gardens Architecture and Greenhouse Displays",
                "Public botanical gardens showcase curated living plant collections within temperature-controlled glass greenhouses for scientific education."));

        // Domain 6: Astronomy & Astrophysics (Docs 141 - 150)
        docs.add(createDoc(141L, "https://astronomy.org/black-holes/supermassive", "Supermassive Black Holes and Event Horizons in Galaxies",
                "Supermassive black holes reside at the centers of galaxies, possessing millions to billions of solar masses. Their immense gravitational curvature creates an event horizon from which no matter or electromagnetic radiation can escape."));
        docs.add(createDoc(142L, "https://astrophysics.org/stellar-evolution", "Stellar Evolution Neutron Stars and Supernova Remnants",
                "Massive stars end their thermonuclear fusion lifecycles in catastrophic supernova explosions, leaving behind ultra-dense neutron stars or collapsing directly into stellar-mass black holes."));
        docs.add(createDoc(143L, "https://cosmos.org/gravitational-lensing", "Gravitational Lensing and Dark Matter Cosmic Distribution",
                "Einstein's general relativity predicts that massive cosmic structures deflect passing light beams. This gravitational lensing effect allows astronomers to map invisible dark matter halos across deep space."));
        docs.add(createDoc(144L, "https://searchforge.dev/distractor/stargazing-telescopes", "Amateur Stargazing Telescopes and Eyepiece Optics",
                "Refractor and reflector telescopes equipped with optical parabolic mirrors allow backyard astronomy enthusiasts to observe lunar craters and planetary rings."));
        docs.add(createDoc(145L, "https://searchforge.dev/distractor/solar-eclipse", "Solar and Lunar Eclipse Geometry",
                "Astronomical eclipses occur when the moon orbits directly between the Earth and sun, casting an umbral shadow across the terrestrial surface."));

        // Domain 7: History of Printing & Literature (Docs 151 - 160)
        docs.add(createDoc(151L, "https://history.org/gutenberg-printing-press", "Johannes Gutenberg Movable Type Printing Press and Literacy Revolution",
                "The invention of the movable metal type printing press in Mainz around 1440 transformed human communication. It enabled rapid mass distribution of printed books, fostering the European Renaissance and expanding universal literacy."));
        docs.add(createDoc(152L, "https://libraries.org/ancient-alexandria", "The Great Library of Alexandria and Ancient Papyrus Scrolls",
                "The ancient Library of Alexandria was an intellectual center of the Hellenistic world, collecting hundreds of thousands of handwritten papyrus scrolls and philosophical treatises."));
        docs.add(createDoc(153L, "https://searchforge.dev/distractor/modern-e-readers", "Digital E-Readers and Electronic Ink Screen Technology",
                "Electronic ink displays simulate the reflective appearance of physical paper, allowing portable e-reader devices to store thousands of digital books with weeks of battery life."));
        docs.add(createDoc(154L, "https://searchforge.dev/distractor/calligraphy-pens", "Traditional Calligraphy Pens and Fountain Pen Inks",
                "Calligraphy utilizes flexible dip nibs, chiseled edges, and pigmented inks to create decorative handwritten typography and flourish strokes."));
        docs.add(createDoc(155L, "https://searchforge.dev/distractor/book-binding", "Handcrafted Leather Bookbinding and Gold Leaf Embossing",
                "Fine craft bookbinders sew paper signatures with linen thread, encasing finished volumes in Moroccan leather boards with hand-tooled gold leaf titling."));

        // Domain 8: Acoustics & Musical Instruments (Docs 161 - 170)
        docs.add(createDoc(161L, "https://music-acoustics.org/guitar-acoustics", "Acoustic Guitar Resonance Soundboard and Harmonic String Vibration",
                "Acoustic guitars generate rich musical tone through vibrating steel or nylon strings. The bridge transfers mechanical string energy to the spruce soundboard, which amplifies acoustic sound waves through the resonant guitar body cavity."));
        docs.add(createDoc(162L, "https://music.org/grand-piano-mechanics", "Grand Piano Action Hammer Strikes and Cast Iron Soundboard",
                "Pianos produce sound when felt-covered wooden hammers strike tensioned steel wire strings. An escapement mechanism allows repetitive note strikes while dampers control string sustain."));
        docs.add(createDoc(163L, "https://searchforge.dev/distractor/electric-amplifiers", "Electric Guitar Vacuum Tube Audio Amplifiers",
                "Guitar amplifiers use vacuum tubes or transistors to amplify weak electromagnetic pickup signals into dynamic distorted rock and roll tones."));
        docs.add(createDoc(164L, "https://searchforge.dev/distractor/violin-varnish", "Violin Varnish Chemistry and Luthier Construction",
                "Violin makers apply layered natural resin varnishes to spruce tops and flamed maple backs, shaping instrument aesthetics and surface acoustic dampening."));
        docs.add(createDoc(165L, "https://searchforge.dev/distractor/sheet-music", "Standard Music Notation and Clef Signatures",
                "Musical compositions represent pitch and rhythm using five-line staves, treble and bass clefs, time signatures, and note values from quarter notes to sixteenth notes."));

        // Domain 9: Urban Bicycles & Commuting (Docs 171 - 180)
        docs.add(createDoc(171L, "https://cycling.org/urban-commuting", "Urban Bicycle Commuting Infrastructure and City Transit Cycling",
                "Commuter bicycles offer efficient zero-emission city transit. Two-wheeled pedal transportation utilizes lightweight aluminum or steel frames, internal gear hubs, puncture-resistant tires, and dedicated bike lane networks."));
        docs.add(createDoc(172L, "https://cycling.org/bicycle-derailleur-gearing", "Bicycle Derailleur Gearing Chain Drive and Mechanical Efficiency",
                "Bicycle drivetrains transfer pedaling force through a roller chain to front chainrings and rear cog cassettes. Parallel derailleur cages shift the drive chain smoothly across varying gear ratios."));
        docs.add(createDoc(173L, "https://searchforge.dev/distractor/mountain-bike-suspension", "Full-Suspension Mountain Bikes and Hydraulic Shocks",
                "Off-road mountain bicycles employ air-sprung front suspension forks and rear hydraulic shock absorbers to maintain wheel traction over rocky downhill trails."));
        docs.add(createDoc(174L, "https://searchforge.dev/distractor/skateboards", "Skateboard Trucks Polyurethane Wheels and Grip Tape",
                "Skateboards combine laminated maple wood decks with aluminum pivot trucks and polyurethane wheels designed for urban street tricks and skatepark transition carving."));
        docs.add(createDoc(175L, "https://searchforge.dev/distractor/electric-scooters", "Lithium Battery Powered Electric Standing Scooters",
                "Electric kick scooters feature brushless hub motors and lithium-ion battery packs, providing short-distance urban micro-mobility."));

        // Domain 10: Culinary Science & Fermentation (Docs 181 - 190)
        docs.add(createDoc(181L, "https://baking-science.org/sourdough-fermentation", "Sourdough Bread Wild Yeast Fermentation and Wheat Gluten",
                "Artisan sourdough bread relies on a symbiotic starter culture of wild yeasts and lactic acid bacteria. Fermenting wheat flour produces organic acids and carbon dioxide gas, while strong gluten protein matrices trap gas bubbles inside the oven-baked loaf."));
        docs.add(createDoc(182L, "https://culinary.org/coffee-roasting-chemistry", "Coffee Bean Roasting Chemistry and Espresso Extraction",
                "Roasting green coffee beans triggers complex Maillard browning and caramelization reactions. High-pressure espresso extraction forces near-boiling water through finely ground roasted coffee, producing rich crema and intense flavor."));
        docs.add(createDoc(183L, "https://searchforge.dev/distractor/pasta-making", "Fresh Italian Egg Pasta Extrusion and Semolina Flour",
                "Traditional Italian pasta blends durum wheat semolina flour with whole eggs, extruded through brass dies into fettuccine ribbons and boiled in salted water."));
        docs.add(createDoc(184L, "https://searchforge.dev/distractor/cast-iron-cookware", "Seasoning Cast Iron Skillets and Non-Stick Polymerization",
                "Cast iron cookware achieves natural non-stick properties by baking thin layers of unsaturated vegetable oil until the lipids cross-link into a hard polymer surface."));
        docs.add(createDoc(185L, "https://searchforge.dev/distractor/tea-brewing", "Loose Leaf Green Tea Steeping Water Temperature",
                "Delicate green tea leaves require lower water steeping temperatures around eighty degrees Celsius to prevent bitter tannin extraction."));

        // Additional Background Distractors (Docs 186 - 220) to expand corpus to 120 documents
        for (int i = 186; i <= 220; i++) {
            docs.add(createDoc((long) i, "https://synthetic.searchforge.dev/corpus/doc-" + i, "Synthetic Corpus Background Document #" + i,
                    "General encyclopedic reference document #" + i + " providing factual background context on varied historical, geological, industrial, and cultural subjects. Intended as realistic corpus distractor noise to evaluate Information Retrieval precision and recall under competition."));
        }

        return Collections.unmodifiableList(docs);
    }

    /**
     * Curated 40-query evaluation benchmark suite spanning all 8 IR archetypes.
     */
    public static List<CategorizedEvaluationQuery> getQueries() {
        return List.of(
                // 1. EXACT KEYWORD QUERIES (5)
                new CategorizedEvaluationQuery("java 21 virtual threads", QueryCategory.EXACT_KEYWORD,
                        Map.of(101L, 2, 104L, 1), "Doc 101 is authoritative Java 21 virtual threads; Doc 104 covers thread utilities."),
                new CategorizedEvaluationQuery("postgresql acid transaction isolation mvcc", QueryCategory.EXACT_KEYWORD,
                        Map.of(106L, 2), "Doc 106 covers PostgreSQL MVCC and ACID reliability."),
                new CategorizedEvaluationQuery("raft consensus algorithm distributed state", QueryCategory.EXACT_KEYWORD,
                        Map.of(107L, 2), "Doc 107 covers Raft distributed consensus."),
                new CategorizedEvaluationQuery("supermassive black holes event horizons", QueryCategory.EXACT_KEYWORD,
                        Map.of(141L, 2), "Doc 141 covers supermassive black holes and event horizons."),
                new CategorizedEvaluationQuery("gutenberg movable type printing press", QueryCategory.EXACT_KEYWORD,
                        Map.of(151L, 2), "Doc 151 covers Gutenberg's movable type printing press."),

                // 2. TECHNICAL QUERIES (5)
                new CategorizedEvaluationQuery("cfs completely fair scheduler red-black tree", QueryCategory.TECHNICAL,
                        Map.of(111L, 2), "Doc 111 covers Linux CFS process scheduler and red-black tree."),
                new CategorizedEvaluationQuery("pcie nvme solid-state flash memory controller", QueryCategory.TECHNICAL,
                        Map.of(112L, 2), "Doc 112 covers NVMe PCIe architecture and flash controller."),
                new CategorizedEvaluationQuery("cardiovascular arterial blood pressure systolic diastolic", QueryCategory.TECHNICAL,
                        Map.of(122L, 2, 121L, 1), "Doc 122 covers arterial blood pressure regulation; Doc 121 covers heart anatomy."),
                new CategorizedEvaluationQuery("chloroplast thylakoids calvin cycle light reactions", QueryCategory.TECHNICAL,
                        Map.of(131L, 2), "Doc 131 covers chloroplast biochemistry and Calvin cycle."),
                new CategorizedEvaluationQuery("b-tree page-level storage leaf node pointers", QueryCategory.TECHNICAL,
                        Map.of(109L, 2), "Doc 109 covers B-Tree physical disk page layouts."),

                // 3. CONCEPTUAL QUERIES (5 - Minimal keyword overlap)
                new CategorizedEvaluationQuery("how can software handle millions of simultaneous connections efficiently", QueryCategory.CONCEPTUAL,
                        Map.of(101L, 2, 102L, 1), "Doc 101 covers virtual threads for high concurrency; Doc 102 covers reactive streams."),
                new CategorizedEvaluationQuery("what causes astronomical light to curve around invisible cosmic matter", QueryCategory.CONCEPTUAL,
                        Map.of(143L, 2, 141L, 1), "Doc 143 covers gravitational lensing and dark matter; Doc 141 covers black hole curvature."),
                new CategorizedEvaluationQuery("how did written information spread to common people before computers", QueryCategory.CONCEPTUAL,
                        Map.of(151L, 2, 152L, 1), "Doc 151 covers printing press revolution; Doc 152 covers ancient library scrolls."),
                new CategorizedEvaluationQuery("what makes an acoustic musical instrument amplify vibrating strings naturally", QueryCategory.CONCEPTUAL,
                        Map.of(161L, 2, 162L, 1), "Doc 161 covers guitar soundboard resonance; Doc 162 covers piano soundboard."),
                new CategorizedEvaluationQuery("preventing lower back discomfort when sitting at a work desk", QueryCategory.CONCEPTUAL,
                        Map.of(125L, 2), "Doc 125 covers ergonomic chairs and lumbar support."),

                // 4. SYNONYM QUERIES (5)
                new CategorizedEvaluationQuery("espresso bean brewing and hot morning caffeinated beverage", QueryCategory.SYNONYM,
                        Map.of(182L, 2), "Doc 182 covers coffee roasting and espresso extraction."),
                new CategorizedEvaluationQuery("cardiac muscular organ pumping arterial blood", QueryCategory.SYNONYM,
                        Map.of(121L, 2, 122L, 1), "Doc 121 covers heart anatomy pumping blood."),
                new CategorizedEvaluationQuery("two-wheeled human pedal transit for urban travel", QueryCategory.SYNONYM,
                        Map.of(171L, 2), "Doc 171 covers city bicycle commuting."),
                new CategorizedEvaluationQuery("wild yeast sourdough fermentation and baked wheat loaf", QueryCategory.SYNONYM,
                        Map.of(181L, 2), "Doc 181 covers sourdough fermentation and oven baked bread."),
                new CategorizedEvaluationQuery("stellar core collapse and catastrophic supernova explosion", QueryCategory.SYNONYM,
                        Map.of(142L, 2), "Doc 142 covers supernova explosions and neutron star remnants."),

                // 5. NATURAL LANGUAGE QUESTIONS (5)
                new CategorizedEvaluationQuery("how do green trees absorb atmospheric carbon dioxide from the air", QueryCategory.NATURAL_LANGUAGE,
                        Map.of(132L, 2, 131L, 1), "Doc 132 covers forest carbon sequestration; Doc 131 covers photosynthesis."),
                new CategorizedEvaluationQuery("how do microservice architectures communicate across decentralized services", QueryCategory.NATURAL_LANGUAGE,
                        Map.of(108L, 2), "Doc 108 covers microservice architecture communication."),
                new CategorizedEvaluationQuery("what happens inside the heart chambers during blood circulation", QueryCategory.NATURAL_LANGUAGE,
                        Map.of(121L, 2), "Doc 121 covers four chambers of the heart and circulation."),
                new CategorizedEvaluationQuery("how does a bicycle derailleur shift gears across the chain", QueryCategory.NATURAL_LANGUAGE,
                        Map.of(172L, 2), "Doc 172 covers bicycle derailleur shifting mechanics."),
                new CategorizedEvaluationQuery("how do CPUs use memory caches to prevent slow RAM access", QueryCategory.NATURAL_LANGUAGE,
                        Map.of(113L, 2), "Doc 113 covers multi-level CPU cache hierarchies."),

                // 6. LOW OVERLAP QUERIES (5)
                new CategorizedEvaluationQuery("spruce soundboard resonant wooden body cavity", QueryCategory.LOW_OVERLAP,
                        Map.of(161L, 2), "Doc 161 covers acoustic guitar soundboard and body resonance."),
                new CategorizedEvaluationQuery("symbiotic lactic acid bacteria trapped gas bubbles", QueryCategory.LOW_OVERLAP,
                        Map.of(181L, 2), "Doc 181 covers sourdough lactic acid bacteria and gas bubbles."),
                new CategorizedEvaluationQuery("capillary action and upward water movement in stems", QueryCategory.LOW_OVERLAP,
                        Map.of(133L, 2), "Doc 133 covers xylem water transport via capillary action."),
                new CategorizedEvaluationQuery("split-brain network partitions and quorum voting", QueryCategory.LOW_OVERLAP,
                        Map.of(107L, 2), "Doc 107 covers Raft consensus quorum voting during partitions."),
                new CategorizedEvaluationQuery("write-ahead logging crash recovery locks", QueryCategory.LOW_OVERLAP,
                        Map.of(106L, 2), "Doc 106 covers PostgreSQL write-ahead logging (WAL) crash recovery."),

                // 7. PHRASE QUERIES (5)
                new CategorizedEvaluationQuery("\"virtual threads\"", QueryCategory.PHRASE,
                        Map.of(101L, 2), "Doc 101 contains exact phrase 'virtual threads'."),
                new CategorizedEvaluationQuery("\"microservices architecture\"", QueryCategory.PHRASE,
                        Map.of(108L, 2), "Doc 108 contains exact phrase 'microservices architecture'."),
                new CategorizedEvaluationQuery("\"completely fair scheduler\"", QueryCategory.PHRASE,
                        Map.of(111L, 2), "Doc 111 contains exact phrase 'completely fair scheduler'."),
                new CategorizedEvaluationQuery("\"supermassive black holes\"", QueryCategory.PHRASE,
                        Map.of(141L, 2), "Doc 141 contains exact phrase 'supermassive black holes'."),
                new CategorizedEvaluationQuery("\"movable metal type\"", QueryCategory.PHRASE,
                        Map.of(151L, 2), "Doc 151 contains exact phrase 'movable metal type'."),

                // 8. BOOLEAN QUERIES (5)
                new CategorizedEvaluationQuery("concurrency AND threads", QueryCategory.BOOLEAN,
                        Map.of(101L, 2, 104L, 1), "Docs 101 and 104 contain both concurrency and threads."),
                new CategorizedEvaluationQuery("guitar OR piano", QueryCategory.BOOLEAN,
                        Map.of(161L, 2, 162L, 2), "Docs 161 (guitar) and 162 (piano) match union."),
                new CategorizedEvaluationQuery("concurrency AND NOT reactive", QueryCategory.BOOLEAN,
                        Map.of(101L, 2, 104L, 1), "Excludes reactive doc 102; includes virtual threads doc 101."),
                new CategorizedEvaluationQuery("database AND transactions", QueryCategory.BOOLEAN,
                        Map.of(106L, 2), "Doc 106 contains database and transactions."),
                new CategorizedEvaluationQuery("bicycle AND urban", QueryCategory.BOOLEAN,
                        Map.of(171L, 2), "Doc 171 covers urban bicycle commuting.")
        );
    }

    private static CrawledDocument createDoc(Long id, String url, String title, String cleanText) {
        CrawledDocument doc = new CrawledDocument(
                url,
                extractDomain(url),
                title,
                cleanText,
                "hash_" + id,
                200,
                Instant.now()
        );
        try {
            java.lang.reflect.Field field = CrawledDocument.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(doc, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set document id", e);
        }
        return doc;
    }

    private static String extractDomain(String url) {
        try {
            return new java.net.URI(url).getHost();
        } catch (Exception e) {
            return "searchforge.dev";
        }
    }
}
