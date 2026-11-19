package com.searchforge.ranking.dev;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.service.CorpusSyncResult;
import com.searchforge.indexer.service.IndexingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Initializes realistic sample documents and indexes them on startup
 * when running with the 'dev' profile.
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

    public DevDataLoader(CrawledDocumentRepository crawledDocumentRepository,
                         IndexingEngine indexingEngine) {
        this.crawledDocumentRepository = crawledDocumentRepository;
        this.indexingEngine = indexingEngine;
    }

    @Override
    public void run(String... args) {
        if (crawledDocumentRepository.count() > 0) {
            log.info("Dev database already seeded. Skipping sample corpus insertion.");
            return;
        }

        log.info("==================================================================");
        log.info("🚀 Seeding SearchForge dev corpus for local interactive testing...");
        log.info("==================================================================");

        List<CrawledDocument> sampleDocs = List.of(
                new CrawledDocument(
                        "https://spring.io/projects/spring-boot",
                        "spring.io",
                        "Spring Boot Microservices and Cloud Native Java",
                        "Spring Boot makes it easy to create stand-alone production-grade Spring based Applications that you can just run. We take an opinionated view of the Spring platform and third-party libraries so you can get started with minimum fuss. Most Spring Boot applications need minimal Spring configuration. Build modern cloud native microservices with Java, Kotlin, and Groovy using Spring Boot.",
                        "hash_sb_001",
                        200,
                        Instant.now()
                ),
                new CrawledDocument(
                        "https://spring.io/projects/spring-framework",
                        "spring.io",
                        "Spring Framework Core and Dependency Injection",
                        "The Spring Framework provides a comprehensive programming and configuration model for modern Java-based enterprise applications on any deployment platform. A key element of Spring is foundational support at the application level: Spring focuses on the plumbing of enterprise applications so teams can focus on application-level business logic, without unnecessary ties to specific deployment environments.",
                        "hash_sf_002",
                        200,
                        Instant.now()
                ),
                new CrawledDocument(
                        "https://www.postgresql.org/about/",
                        "postgresql.org",
                        "PostgreSQL The World's Most Advanced Open Source Relational Database",
                        "PostgreSQL is a powerful open source object-relational database system with over 35 years of active development that has earned it a strong reputation for reliability, feature robustness, and performance. PostgreSQL comes with many features aimed to help developers build applications, administrators to protect data integrity, and build fault-tolerant environments, managing data no matter how big or small the dataset.",
                        "hash_pg_003",
                        200,
                        Instant.now()
                ),
                new CrawledDocument(
                        "https://en.wikipedia.org/wiki/Okapi_BM25",
                        "wikipedia.org",
                        "Okapi BM25 Probabilistic Relevance Ranking and Scoring",
                        "In information retrieval, Okapi BM25 is a ranking function used by search engines to estimate the relevance of documents to a given search query. It is based on the probabilistic retrieval framework developed in the 1970s and 1980s by Stephen E. Robertson, Karen Sparck Jones, and others. The name of the actual ranking function is BM25 Best Matching 25.",
                        "hash_bm_004",
                        200,
                        Instant.now()
                ),
                new CrawledDocument(
                        "https://martinfowler.com/articles/microservices.html",
                        "martinfowler.com",
                        "Microservices Guide Architecture and Distributed Systems",
                        "The term microservice architecture has sprung up over the last few years to describe a particular way of designing software applications as suites of independently deployable services. While there is no precise definition of this architectural style, there are certain common characteristics around organization around business capability, automated deployment, endpoint intelligence, and decentralized control of languages and data.",
                        "hash_ms_005",
                        200,
                        Instant.now()
                ),
                new CrawledDocument(
                        "https://docs.oracle.com/en/java/javase/21/",
                        "oracle.com",
                        "Java 21 LTS Documentation and Virtual Threads",
                        "Java Platform Standard Edition 21 is a Long Term Support release. It introduces Virtual Threads for high-throughput lightweight concurrency, Sequenced Collections, Record Patterns, Pattern Matching for switch, and generational ZGC. Virtual threads dramatically reduce the effort of writing, maintaining, and observing high-throughput concurrent applications.",
                        "hash_java_006",
                        200,
                        Instant.now()
                )
        );

        crawledDocumentRepository.saveAll(sampleDocs);
        CorpusSyncResult syncResult = indexingEngine.synchronizeCorpus(100);

        log.info("==================================================================");
        log.info("✅ Dev corpus seeding complete!");
        log.info("   Indexed documents : {}", syncResult.documentsIndexed());
        log.info("   Tokens indexed    : {}", syncResult.totalTokensIndexed());
        log.info("   Try querying now  : http://localhost:8080/api/v1/search?q=spring+boot&explain=true");
        log.info("==================================================================");
    }
}
