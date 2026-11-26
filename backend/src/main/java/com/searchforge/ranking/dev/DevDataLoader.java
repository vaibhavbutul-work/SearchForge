package com.searchforge.ranking.dev;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.service.CorpusSyncResult;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.retrieval.DocumentEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Initializes a rich multi-domain sample corpus and indexes it on startup
 * when running with the 'dev' profile.
 *
 * Spans everyday objects (bottle, chair, table, book, clock, car, computer),
 * animals (dog, cat), science, nature, and software engineering.
 *
 * Enables immediate, zero-Docker manual verification of SearchForge
 * on http://localhost:8080.
 */
@Component
@Profile("dev")
public class DevDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataLoader.class);

    private final CrawledDocumentRepository crawledDocumentRepository;
    private final IndexingEngine indexingEngine;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final ActiveDocumentFilter activeDocumentFilter;

    @Autowired
    public DevDataLoader(CrawledDocumentRepository crawledDocumentRepository,
                         IndexingEngine indexingEngine,
                         @Autowired(required = false) DocumentEmbeddingService documentEmbeddingService,
                         @Autowired(required = false) ActiveDocumentFilter activeDocumentFilter) {
        this.crawledDocumentRepository = crawledDocumentRepository;
        this.indexingEngine = indexingEngine;
        this.documentEmbeddingService = documentEmbeddingService;
        this.activeDocumentFilter = activeDocumentFilter;
    }

    @Override
    public void run(String... args) {
        if (crawledDocumentRepository.count() > 0) {
            log.info("Dev database already seeded. Skipping sample corpus insertion.");
            if (documentEmbeddingService != null) {
                documentEmbeddingService.warmupStorageFromDatabase();
            }
            return;
        }

        log.info("==================================================================");
        log.info("🚀 Seeding SearchForge multi-domain dev corpus for interactive testing...");
        log.info("==================================================================");

        List<CrawledDocument> sampleDocs = List.of(
                // 1. Books & Literature
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Book",
                        "wikipedia.org",
                        "History of Books, Printing Press, and World Literature",
                        "A book is a medium for recording information in the form of writing or images, typically composed of many pages bound together and protected by a cover. The invention of the movable type printing press by Johannes Gutenberg in the 15th century revolutionized the distribution of knowledge, enabling mass production of books, novels, dictionaries, encyclopedias, and scientific treatises. Reading books stimulates imagination, expands vocabulary, and preserves human culture across generations in public libraries.",
                        "hash_book_001",
                        200,
                        Instant.now()
                ),

                // 2. Animals: Dog
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Dog",
                        "wikipedia.org",
                        "Domestic Dog Breeds, Canine Behavior, and Training",
                        "The domestic dog is a domesticated descendant of the wolf. Widely referred to as man's best friend, dogs were the first species to be domesticated by humans over fifteen thousand years ago. Dogs perform many roles for people including companionship, hunting, herding sheep and cattle, pulling sleds, guarding property, police detection work, and guiding disabled individuals. Popular dog breeds include Golden Retrievers, German Shepherds, Labradors, Poodles, and Bulldogs. Puppies require obedience training, daily walks, proper nutrition, and veterinary care.",
                        "hash_dog_002",
                        200,
                        Instant.now()
                ),

                // 3. Animals: Cat
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Cat",
                        "wikipedia.org",
                        "Domestic Cat Characteristics, Agility, and Feline Biology",
                        "The cat, commonly referred to as the domestic cat or housecat, is a small carnivorous mammal in the family Felidae. Domestic cats are known for their flexible bodies, quick reflexes, sharp retractable claws, night vision, and acute hearing adapted to hunting mice, rats, and small pests. Cats communicate through meowing, purring, trilling, and tail gestures. Like dogs, cats are among the most popular companion pets in households worldwide.",
                        "hash_cat_003",
                        200,
                        Instant.now()
                ),

                // 4. Everyday Objects: Bottle
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Bottle",
                        "wikipedia.org",
                        "Bottles Glass, Plastic, and Liquid Storage Containers",
                        "A bottle is a narrow-necked container made of an impermeable material such as glass, plastic, stainless steel, or aluminum. Bottles are primarily used to store, transport, and protect liquids such as drinking water, milk, wine, medicine, soda, fruit juice, ink, and cooking oil. The opening at the mouth of the bottle is sealed with an airtight cap, screw top, stopper, or cork to prevent leakage and contamination. Reusable water bottles help reduce single-use plastic waste.",
                        "hash_bottle_004",
                        200,
                        Instant.now()
                ),

                // 5. Everyday Objects: Chair
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Chair",
                        "wikipedia.org",
                        "Chair Furniture Design, History, and Ergonomic Seating",
                        "A chair is a piece of furniture with a raised surface supported by legs, commonly used to seat a single person. Chairs typically feature four legs, a flat seat cushion, and a backrest. Specialized chair designs include armchairs, recliners, rocking chairs, bar stools, dining chairs, and ergonomic office chairs engineered with lumbar support and height adjustment to reduce back fatigue. Modern chairs are crafted from hardwood, steel, molded plastic, and upholstered leather.",
                        "hash_chair_005",
                        200,
                        Instant.now()
                ),

                // 6. Everyday Objects: Table
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Table_(furniture)",
                        "wikipedia.org",
                        "Table Furniture Types, Dining Surfaces, and Desks",
                        "A table is an item of furniture with a flat horizontal upper surface used to support objects for work, dining, food preparation, or storage. Tables are commonly paired with chairs. Notable varieties include dining room tables for sharing meals, low coffee tables in living rooms, bedside nightstands, and study desks designed for computers and writing instruments. Tables are constructed from oak, walnut, glass, marble, and composite wood.",
                        "hash_table_006",
                        200,
                        Instant.now()
                ),

                // 7. Everyday Objects: Clock & Watch
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Clock",
                        "wikipedia.org",
                        "Mechanical Clocks, Quartz Wristwatches, and Timekeeping",
                        "A clock is an instrument used to measure, track, and indicate time. From ancient sundials and hourglasses to modern electronic timekeepers, clocks are essential for organizing society. Mechanical clocks rely on swinging pendulums or balance wheels with gears, while electronic wristwatches utilize vibrating quartz crystals. In scientific institutions, atomic clocks measuring cesium atom oscillations establish Universal Coordinated Time with sub-nanosecond accuracy.",
                        "hash_clock_007",
                        200,
                        Instant.now()
                ),

                // 8. Transportation: Car & Automobile
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Car",
                        "wikipedia.org",
                        "Automobile Engineering, Electric Vehicles, and Transportation",
                        "An automobile or car is a four-wheeled motor vehicle used for passenger transportation on roadways. Modern automobiles are powered by internal combustion gasoline engines, hybrid electric systems, or lithium-ion battery electric motors. Technological advancements in modern cars include electronic stability control, automatic emergency braking, regenerative braking, touch-screen infotainment dashboards, and advanced driver assistance sensors.",
                        "hash_car_008",
                        200,
                        Instant.now()
                ),

                // 9. Transportation: Bicycle
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Bicycle",
                        "wikipedia.org",
                        "Bicycle Mechanics, Gearing, and Sustainable Urban Cycling",
                        "A bicycle, also called a bike or cycle, is a human-powered pedal-driven vehicle with two wheels attached to a rigid frame, one behind the other. Bicycles are widely utilized for daily urban commuting, fitness exercise, sports racing, and delivery services. Key mechanical components include handlebars for steering, a saddle seat, foot pedals, chain drive gears, sprockets, pneumatic rubber tires, and mechanical or hydraulic disc brakes.",
                        "hash_bike_009",
                        200,
                        Instant.now()
                ),

                // 10. Technology: Computer & Smartphone
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Personal_computer",
                        "wikipedia.org",
                        "Personal Computer Hardware, Microprocessors, and Operating Systems",
                        "A computer is an electronic machine capable of storing, manipulating, and retrieving large volumes of digital data at high speed. A personal computer or laptop comprises central processing units (CPUs), graphics processors (GPUs), dynamic random-access memory (RAM), solid-state NVMe storage drives, and input peripherals like mice and keyboards. Computers run operating systems such as Linux, Windows, and macOS to execute software applications.",
                        "hash_pc_010",
                        200,
                        Instant.now()
                ),

                // 11. Food & Drink: Coffee
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Coffee",
                        "wikipedia.org",
                        "Coffee Bean Roasting, Espresso Brewing, and Caffeine",
                        "Coffee is a popular brewed beverage prepared from roasted coffee beans harvested from Coffea plant cherries. Known for its rich aroma, slight bitterness, and energizing caffeine content, coffee is enjoyed globally in cafes and homes. Popular preparations include espresso shots, creamy cappuccinos, caffe lattes, pour-over drip coffee, and cold brew. The two primary commercial coffee species are Arabica and Robusta.",
                        "hash_coffee_011",
                        200,
                        Instant.now()
                ),

                // 12. Food & Drink: Bread
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Bread",
                        "wikipedia.org",
                        "Bread Making, Sourdough Fermentation, and Wheat Flour",
                        "Bread is a foundational staple food prepared from a dough of cereal flour, water, salt, and leavening agents like baker's yeast or sourdough culture. During baking in an oven, yeast ferments sugars to produce carbon dioxide gas bubbles, giving loafs of bread their soft interior crumb and golden crunchy crust. Common types include baguettes, sandwich loaves, sourdough, pita, and whole grain wheat flatbreads.",
                        "hash_bread_012",
                        200,
                        Instant.now()
                ),

                // 13. Space & Astronomy: Galaxy
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Galaxy",
                        "wikipedia.org",
                        "Galaxies, Stars, Black Holes, and the Universe",
                        "A galaxy is a massive gravitationally bound system consisting of stars, stellar remnants, interstellar gas clouds, cosmic dust, and mysterious dark matter. Our Solar System and planet Earth reside within the spiral Milky Way galaxy. The observable universe contains billions of galaxies, ranging from dwarf elliptical galaxies to giant spiral galaxies harboring supermassive black holes at their core.",
                        "hash_galaxy_013",
                        200,
                        Instant.now()
                ),

                // 14. Nature & Earth: Ocean & Water
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Ocean",
                        "wikipedia.org",
                        "Oceans, Marine Ecosystems, and the Global Water Cycle",
                        "The world ocean is a vast continuous body of saline water that covers over seventy percent of the Earth's surface. Oceans stabilize global temperatures, absorb atmospheric carbon, and produce over half of the planet's oxygen through marine phytoplankton. The global water cycle recycles moisture through surface evaporation, cloud condensation, rain precipitation, and river flows returning to the seas.",
                        "hash_ocean_014",
                        200,
                        Instant.now()
                ),

                // 15. Nature & Botany: Trees & Forests
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Tree",
                        "wikipedia.org",
                        "Trees, Forest Ecosystems, and Solar Photosynthesis",
                        "A tree is a perennial woody plant characterized by an elongated trunk supporting branches and foliage leaves. Trees play a vital role in terrestrial ecosystems by absorbing atmospheric carbon dioxide and releasing clean oxygen through solar photosynthesis. Forests provide essential habitats for birds, mammals, and insects, prevent severe soil erosion, and supply timber for construction and furniture manufacturing.",
                        "hash_tree_015",
                        200,
                        Instant.now()
                ),

                // 16. Music: Acoustic Guitar & Piano
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Musical_instrument",
                        "wikipedia.org",
                        "Musical Instruments Acoustic Guitars, Pianos, and Harmony",
                        "Musical instruments produce pleasant acoustic sound vibrations to create songs, rhythm, and harmonic melodies. String instruments such as acoustic and electric guitars produce sound when taut steel or nylon strings are plucked or strummed over a resonance chamber. Keyboards and grand pianos utilize felt-covered mechanical hammers that strike tuned metal strings across a dynamic musical spectrum.",
                        "hash_music_016",
                        200,
                        Instant.now()
                ),

                // 17. Health & Human Body: Heart
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Heart",
                        "wikipedia.org",
                        "Human Heart Anatomy, Blood Circulation, and Health",
                        "The human heart is a muscular organ that pumps vital blood through the circulatory network of arteries and veins. Located in the chest cavity, the heart contracts rhythmically to deliver oxygenated blood and nutrients to vital organs while transporting metabolic carbon dioxide to the lungs. Maintaining cardiovascular health requires regular aerobic exercise, balanced nutrition, and low stress.",
                        "hash_heart_017",
                        200,
                        Instant.now()
                ),

                // 18. Nature & Science: Sun & Solar Energy
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Sun",
                        "wikipedia.org",
                        "The Sun, Solar Radiation, and Clean Renewable Energy",
                        "The Sun is the yellow dwarf star at the center of our Solar System, comprising hot ionized plasma undergoing nuclear fusion. Solar electromagnetic radiation and sunlight provide the thermal energy and daylight necessary to sustain all biological life on Earth. Modern solar photovoltaic panels directly convert solar light into clean, renewable electrical energy for homes, cities, and electric grids.",
                        "hash_sun_018",
                        200,
                        Instant.now()
                ),

                // 19. Tech: Spring Boot
                new CrawledDocument(
                        "https://spring.io/projects/spring-boot",
                        "spring.io",
                        "Spring Boot Microservices and Cloud Native Java",
                        "Spring Boot makes it easy to create stand-alone production-grade Spring based Applications that you can just run. We take an opinionated view of the Spring platform and third-party libraries so you can get started with minimum fuss. Most Spring Boot applications need minimal Spring configuration. Build modern cloud native microservices with Java, Kotlin, and Groovy using Spring Boot.",
                        "hash_sb_019",
                        200,
                        Instant.now()
                ),

                // 20. Tech: Spring Framework Core
                new CrawledDocument(
                        "https://spring.io/projects/spring-framework",
                        "spring.io",
                        "Spring Framework Core and Dependency Injection",
                        "The Spring Framework provides a comprehensive programming and configuration model for modern Java-based enterprise applications on any deployment platform. A key element of Spring is foundational support at the application level: Spring focuses on the plumbing of enterprise applications so teams can focus on application-level business logic, without unnecessary ties to specific deployment environments.",
                        "hash_sf_020",
                        200,
                        Instant.now()
                ),

                // 21. Tech: PostgreSQL
                new CrawledDocument(
                        "https://www.postgresql.org/about/",
                        "postgresql.org",
                        "PostgreSQL The World's Most Advanced Open Source Relational Database",
                        "PostgreSQL is a powerful open source object-relational database system with over 35 years of active development that has earned it a strong reputation for reliability, feature robustness, and performance. PostgreSQL comes with many features aimed to help developers build applications, administrators to protect data integrity, and build fault-tolerant environments, managing data no matter how big or small the dataset.",
                        "hash_pg_021",
                        200,
                        Instant.now()
                ),

                // 22. Tech: BM25 Ranking
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Okapi_BM25",
                        "wikipedia.org",
                        "Okapi BM25 Probabilistic Relevance Ranking and Scoring",
                        "In information retrieval, Okapi BM25 is a ranking function used by search engines to estimate the relevance of documents to a given search query. It is based on the probabilistic retrieval framework developed in the 1970s and 1980s by Stephen E. Robertson, Karen Sparck Jones, and others. The name of the actual ranking function is BM25 Best Matching 25.",
                        "hash_bm_022",
                        200,
                        Instant.now()
                ),

                // 23. Tech: Microservices
                new CrawledDocument(
                        "https://martinfowler.com/articles/microservices.html",
                        "martinfowler.com",
                        "Microservices Guide Architecture and Distributed Systems",
                        "The term microservice architecture has sprung up over the last few years to describe a particular way of designing software applications as suites of independently deployable services. While there is no precise definition of this architectural style, there are certain common characteristics around organization around business capability, automated deployment, endpoint intelligence, and decentralized control of languages and data.",
                        "hash_ms_023",
                        200,
                        Instant.now()
                ),

                // 24. Tech: Java 21 LTS
                new CrawledDocument(
                        "https://docs.oracle.com/en/java/javase/21/",
                        "oracle.com",
                        "Java 21 LTS Documentation and Virtual Threads",
                        "Java Platform Standard Edition 21 is a Long Term Support release. It introduces Virtual Threads for high-throughput lightweight concurrency, Sequenced Collections, Record Patterns, Pattern Matching for switch, and generational ZGC. Virtual threads dramatically reduce the effort of writing, maintaining, and observing high-throughput concurrent applications.",
                        "hash_java_024",
                        200,
                        Instant.now()
                )
        );

        for (CrawledDocument doc : sampleDocs) {
            doc.setStatus(DocumentLifecycleStatus.ACTIVE);
        }
        List<CrawledDocument> savedDocs = crawledDocumentRepository.saveAll(sampleDocs);
        if (activeDocumentFilter != null) {
            activeDocumentFilter.initialize(savedDocs.stream().map(CrawledDocument::getId).toList());
        }
        CorpusSyncResult syncResult = indexingEngine.synchronizeCorpus(100);

        int embeddingsCreated = 0;
        if (documentEmbeddingService != null) {
            embeddingsCreated = documentEmbeddingService.syncCorpusEmbeddings(100);
        }

        log.info("==================================================================");
        log.info("✅ Multi-domain dev corpus seeding complete!");
        log.info("   Indexed documents  : {}", syncResult.documentsIndexed());
        log.info("   Tokens indexed     : {}", syncResult.totalTokensIndexed());
        log.info("   Embeddings indexed : {}", embeddingsCreated);
        log.info("   Try querying now   : 'dog', 'bottle', 'chair', 'book', 'galaxy'");
        log.info("==================================================================");
    }
}
