package com.searchforge.ranking.evaluation;

import com.searchforge.crawler.model.CrawledDocument;

import java.time.Instant;
import java.util.*;

/**
 * Standardized 120-document evaluation corpus for SearchForge Phase 12.
 *
 * Provides a contiguous, deterministic, multi-domain test collection (IDs 101L - 220L):
 * - Domain 1: Java & Concurrency (Docs 101 - 105)
 * - Domain 2: Distributed Systems & Databases (Docs 106 - 110)
 * - Domain 3: Operating Systems & Hardware Architecture (Docs 111 - 120)
 * - Domain 4: Biomedical & Human Anatomy (Docs 121 - 130)
 * - Domain 5: Botany & Environmental Plant Biology (Docs 131 - 140)
 * - Domain 6: Astronomy & Astrophysics (Docs 141 - 150)
 * - Domain 7: History of Printing & Literature (Docs 151 - 160)
 * - Domain 8: Acoustics & Musical Instruments (Docs 161 - 170)
 * - Domain 9: Urban Bicycles & Commuting (Docs 171 - 180)
 * - Domain 10: Culinary Science & Fermentation (Docs 181 - 185)
 * - Distractor Corpus: Historical & general science background (Docs 186 - 220)
 *
 * Total size: Exactly 120 documents.
 */
public final class SearchForgeEvaluationCorpus {

    private static final List<CrawledDocument> CORPUS;
    private static final Map<Long, CrawledDocument> CORPUS_MAP;

    static {
        List<CrawledDocument> docs = new ArrayList<>(120);

        // Domain 1: Java & Concurrency (Docs 101 - 105)
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

        // Domain 2: Distributed Systems & Databases (Docs 106 - 110)
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
        docs.add(createDoc(116L, "https://hardware.tech/interrupts-dma", "Hardware Interrupt Handling and Direct Memory Access DMA",
                "Direct Memory Access (DMA) controllers offload data transfers between high-speed peripheral devices and system RAM, freeing CPU cores from executing cycle-heavy byte copying loops."));
        docs.add(createDoc(117L, "https://computer-arch.org/risc-cisc", "RISC vs CISC Microprocessor Instruction Set Architectures",
                "Reduced Instruction Set Computers (RISC) prioritize fixed-length instructions and pipelined execution stages, contrasting with Complex Instruction Set (CISC) microcode decoding."));
        docs.add(createDoc(118L, "https://hardware.tech/gpu-parallel-shaders", "Graphics Processing Units GPU Massively Parallel Compute Shaders",
                "GPUs feature thousands of streamlined arithmetic logic units (ALUs) organized into compute workgroups to execute SIMD parallel matrix arithmetic for graphics and machine learning."));
        docs.add(createDoc(119L, "https://kernel.org/memory/paging-tlb", "Virtual Memory Page Tables and Translation Lookaside Buffers TLB",
                "Operating system kernels map virtual addresses to physical RAM frames using hierarchical multi-level page tables cached in hardware translation lookaside buffers."));
        docs.add(createDoc(120L, "https://embedded.org/rtos-scheduling", "Real-Time Embedded Operating Systems RTOS Priority Scheduling",
                "Hard real-time systems employ deterministic preemptive priority scheduling and priority ceiling protocols to prevent unbounded priority inversion in industrial control loops."));

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
        docs.add(createDoc(126L, "https://medical.org/pulmonary/alveoli-gas-exchange", "Pulmonary Alveoli and Respiratory Oxygen Gas Exchange",
                "The lungs facilitate external respiration through millions of microscopic alveoli, where thin epithelial membranes permit passive diffusion of oxygen into pulmonary capillary blood."));
        docs.add(createDoc(127L, "https://neuroscience.org/synaptic-transmission", "Central Nervous System Synaptic Neurotransmitter Transmission",
                "Action potentials propagate along neuronal axons, triggering vesicular exocytosis of chemical neurotransmitters like dopamine and serotonin across the synaptic cleft."));
        docs.add(createDoc(128L, "https://nephrology.org/kidney-filtration", "Renal Nephron Glomerular Filtration and Fluid Osmoregulation",
                "The kidneys filter metabolic waste products from blood plasma through millions of nephron glomeruli, concentrating urine and maintaining systemic electrolyte balance."));
        docs.add(createDoc(129L, "https://immunology.org/adaptive-immune-lymphocytes", "Innate and Adaptive Immune System Lymphocyte Responses",
                "The immune system protects against pathogenic infection through innate phagocytic leukocytes and adaptive clonal selection of antibody-producing B-lymphocytes and cytotoxic T-cells."));
        docs.add(createDoc(130L, "https://physiology.org/muscle-contraction", "Skeletal Muscle Actin Myosin Cross-Bridge Contraction",
                "Skeletal muscle fibers generate mechanical pulling force via sliding filament theory, where calcium ions activate myosin heads to bind actin filaments in ATP-powered power strokes."));

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
        docs.add(createDoc(136L, "https://mycology.org/mycorrhizal-networks", "Soil Mycorrhizal Fungal Networks and Nutrient Symbiosis",
                "Mycorrhizal fungi form mutualistic subterranean associations with plant root systems, exchanging soil phosphorus and nitrogen for photosynthetically fixed host carbohydrates."));
        docs.add(createDoc(137L, "https://botany.org/floral-pollination", "Angiosperm Floral Pollination and Seed Dispersal Strategies",
                "Flowering angiosperms evolve specialized floral morphologies to attract insect and avian pollinators, ensuring genetic cross-fertilization and resilient seed dissemination."));
        docs.add(createDoc(138L, "https://botany.org/auxin-gravitropism", "Plant Hormone Auxin Distribution and Gravitropism Responses",
                "The phytohormone auxin regulates directional plant growth, redistributing along root and shoot stems to mediate phototropic and gravitropic cellular elongation."));
        docs.add(createDoc(139L, "https://ecology.org/autumn-foliage", "Deciduous Forest Autumn Chlorophyll Breakdown and Pigmentation",
                "In autumn, deciduous trees resorb nitrogenous nutrients from leaves, degrading green chlorophyll to unmask yellow carotenoids and red anthocyanin pigments."));
        docs.add(createDoc(140L, "https://ecology.org/mangrove-estuaries", "Wetland Mangrove Ecosystem Salinity Tolerance and Estuarine Buffers",
                "Coastal mangrove forests utilize salt-filtering root ultrafiltration to colonize brackish marine estuaries, stabilizing shorelines against tidal storm erosion."));

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
        docs.add(createDoc(146L, "https://astronomy.org/planetary-magnetospheres", "Planetary Magnetospheres and Solar Wind Plasma Interaction",
                "Planetary magnetic fields generated by molten metal core dynamos deflect charged solar wind plasma, shielding upper atmospheric gases from ion sputtering."));
        docs.add(createDoc(147L, "https://cosmology.org/cosmic-microwave-background", "Cosmic Microwave Background Radiation and Big Bang Nucleosynthesis",
                "The cosmic microwave background is the thermal relic radiation of the early universe, documenting primordial photon decoupling and Big Bang light element abundances."));
        docs.add(createDoc(148L, "https://cosmos.org/hubble-constant-expansion", "Hubble Constant and Accelerating Cosmic Expansion Metrics",
                "Observations of distant Type Ia supernovae demonstrate that the cosmic expansion rate is accelerating, driven by the negative pressure of dark energy."));
        docs.add(createDoc(149L, "https://exoplanets.org/transit-photometry", "Exoplanet Transit Photometry and Kepler Habitable Zone Surveys",
                "Space telescopes detect extrasolar planets by measuring periodic dips in host star brightness during planetary transits, identifying terrestrial worlds in habitable orbital zones."));
        docs.add(createDoc(150L, "https://astronomy.org/asteroid-belt-resonances", "Asteroid Belt Orbital Resonances and Kirkwood Gaps Dynamics",
                "Gravitational perturbations from Jupiter create Kirkwood gaps in the main asteroid belt, ejecting resonant planetesimals into near-Earth intersecting orbits."));

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
        docs.add(createDoc(156L, "https://history.org/illuminated-manuscripts", "Renaissance Manuscript Illumination and Pigment Chemistry",
                "Medieval scriptoria embellished vellum manuscripts with vivid lapis lazuli ultramarine pigments, egg tempera binders, and burnished gold leaf illuminations."));
        docs.add(createDoc(157L, "https://typography.org/punchcutting-typefoundry", "Lead Foundry Metal Casting for Punchcutting Typefounders",
                "Historical typefounders carved steel punches and struck copper matrices to cast interchangeable lead alloy type slugs for letterpress composition."));
        docs.add(createDoc(158L, "https://history.org/rotary-steam-press", "Rotary Steam Press Printing and 19th Century Mass News Media",
                "The introduction of steam-powered rotary cylinder printing presses in the nineteenth century multiplied daily newspaper output, creating mass public journalism."));
        docs.add(createDoc(159L, "https://typography.org/linotype-keyboard", "Linotype Hot Metal Typesetting and Keyboard Newspaper Composing",
                "Ottmar Mergenthaler's Linotype machine allowed operators to compose complete justified lines of molten lead type from a keyboard, accelerating print production."));
        docs.add(createDoc(160L, "https://history.org/monastic-scriptorium", "Monastic Scriptorium Parchment Preparation and Quill Scribes",
                "Before mechanical movable type, medieval monastic scribes painstakingly transcribed classical Latin codices on stretched calfskin parchment using goose quill pens."));

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
        docs.add(createDoc(166L, "https://acoustics.org/room-reverberation", "Architectural Acoustics and Room Reverberation Time Calculations",
                "Concert hall acousticians design wall diffusion panels and calculate Sabine reverberation times to achieve balanced acoustic warmth without speech-blurring echo."));
        docs.add(createDoc(167L, "https://audio-eng.org/microphones-transducers", "Dynamic vs Condenser Studio Microphone Diaphragm Transducers",
                "Studio condenser microphones employ lightweight gold-sputtered mylar diaphragms charged against backplates to capture high-frequency vocal transients with precision."));
        docs.add(createDoc(168L, "https://music.org/brass-acoustics-valves", "Brass Wind Instrument Acoustic Standing Waves and Valve Tuning",
                "Trumpets and trombones amplify lip reed vibrations into standing acoustic air column waves, using rotary valves to alter effective tube resonant lengths."));
        docs.add(createDoc(169L, "https://acoustics.org/percussion-membrane-modes", "Percussion Drumhead Membrane Oscillation and Bessel Mode Physics",
                "Tensioned drumheads oscillate according to circular Bessel function modes, producing non-harmonic inharmonic overtone spectra damped by internal air cavity resonance."));
        docs.add(createDoc(170L, "https://audio-eng.org/nyquist-sampling", "Digital Audio Sampling Nyquist-Shannon Theorem and Quantization",
                "Digital audio converters capture analog waveforms by sampling continuous voltages at twice the highest frequency according to the Nyquist-Shannon theorem."));

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
        docs.add(createDoc(176L, "https://transit.org/subway-automation", "Subway Metro Underground Rail Signal Block Automation",
                "Modern urban metro systems implement communications-based train control (CBTC) and moving block signalling to safely operate driverless underground passenger trains at two-minute headways."));
        docs.add(createDoc(177L, "https://transit.org/light-rail-catenary", "Light Rail Tramway Street Electrification and Overhead Catenary",
                "Urban electric light rail streetcars draw high-voltage direct current through roof pantographs from overhead catenary wires, offering medium-capacity transit."));
        docs.add(createDoc(178L, "https://urban-planning.org/bus-rapid-transit", "Bus Rapid Transit BRT Dedicated Right-of-Way Corridor Design",
                "Bus Rapid Transit systems mimic metro rail efficiency using center-running exclusive bus lanes, off-board fare collection, and traffic signal priority at intersections."));
        docs.add(createDoc(179L, "https://urban-planning.org/pedestrian-intersections", "Pedestrian Walkability Urban Intersections and Traffic Calming",
                "Human-scaled urban design incorporates curb bulb-outs, raised crosswalks, and automated pedestrian countdown signals to reduce vehicular speeds."));
        docs.add(createDoc(180L, "https://automotive.org/ev-regenerative-braking", "Electric Vehicle Regenerative Braking and Inverter Motor Control",
                "Electric vehicles recapture kinetic deceleration energy by operating permanent magnet traction motors as generators, recharging high-voltage lithium battery banks."));

        // Domain 10: Culinary Science & Fermentation (Docs 181 - 185)
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

        // Additional Background Distractors (Docs 186 - 220)
        for (int i = 186; i <= 220; i++) {
            docs.add(createDoc((long) i, "https://synthetic.searchforge.dev/corpus/doc-" + i, "Synthetic Corpus Background Document #" + i,
                    "General encyclopedic reference document #" + i + " providing factual background context on varied historical, geological, industrial, and cultural subjects. Intended as realistic corpus distractor noise to evaluate Information Retrieval precision and recall under competition."));
        }

        CORPUS = Collections.unmodifiableList(docs);
        Map<Long, CrawledDocument> map = new LinkedHashMap<>(docs.size());
        for (CrawledDocument doc : docs) {
            map.put(doc.getId(), doc);
        }
        CORPUS_MAP = Collections.unmodifiableMap(map);
    }

    private SearchForgeEvaluationCorpus() {
        // Utility class
    }

    public static List<CrawledDocument> getCorpus() {
        return CORPUS;
    }

    public static Optional<CrawledDocument> getDocument(long id) {
        return Optional.ofNullable(CORPUS_MAP.get(id));
    }

    public static boolean containsDocument(long id) {
        return CORPUS_MAP.containsKey(id);
    }

    public static int size() {
        return CORPUS.size();
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
