package com.searchforge.lifecycle.change;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.parser.HtmlParser;
import com.searchforge.crawler.parser.ParsedDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.crawler.url.UrlNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Service responsible for detecting whether crawled document content has changed.
 * Evaluates canonical URL identity and SHA-256 content hashes to distinguish:
 * 1. Brand new documents requiring initial indexing and embedding.
 * 2. Unchanged documents that skip unnecessary CPU/storage reprocessing.
 * 3. Modified documents requiring version increment and incremental reindexing.
 * 4. Duplicate content discovered at different URLs (mirror suppression).
 */
@Service
public class DocumentChangeDetector {

    private static final Logger log = LoggerFactory.getLogger(DocumentChangeDetector.class);

    private final CrawledDocumentRepository crawledDocumentRepository;
    private final UrlNormalizer urlNormalizer;
    private final HtmlParser htmlParser;

    public DocumentChangeDetector(CrawledDocumentRepository crawledDocumentRepository,
                                  UrlNormalizer urlNormalizer,
                                  HtmlParser htmlParser) {
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository, "CrawledDocumentRepository cannot be null");
        this.urlNormalizer = Objects.requireNonNull(urlNormalizer, "UrlNormalizer cannot be null");
        this.htmlParser = Objects.requireNonNull(htmlParser, "HtmlParser cannot be null");
    }

    /**
     * Evaluates change status for a ParsedDocument produced by the crawler.
     */
    public ChangeDetectionResult detectChange(ParsedDocument parsedDoc) {
        if (parsedDoc == null) {
            throw new IllegalArgumentException("ParsedDocument cannot be null");
        }
        return detectChange(parsedDoc.url(), parsedDoc.title(), parsedDoc.cleanText(), parsedDoc.contentHash());
    }

    /**
     * Evaluates change status for raw incoming parameters.
     */
    public ChangeDetectionResult detectChange(String rawUrl, String title, String cleanText, String rawContentHash) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }

        // 1. Canonicalize URL per RFC 3986
        String normalizedUrl = urlNormalizer.normalize(rawUrl, null).orElse(rawUrl.trim());

        // 2. Compute or verify content hash
        String effectiveContentHash = (rawContentHash != null && !rawContentHash.isBlank())
                ? rawContentHash.trim()
                : htmlParser.computeSha256(cleanText != null ? cleanText : "");

        // 3. Check if document exists under this canonical URL
        Optional<CrawledDocument> existingDocOpt = crawledDocumentRepository.findByUrl(normalizedUrl);

        if (existingDocOpt.isPresent()) {
            CrawledDocument existing = existingDocOpt.get();
            boolean hashMatches = Objects.equals(existing.getContentHash(), effectiveContentHash);

            if (hashMatches) {
                boolean titleChanged = !Objects.equals(existing.getTitle(), title);
                log.debug("Document at '{}' is UNCHANGED (hash: {})", normalizedUrl, effectiveContentHash);
                return ChangeDetectionResult.unchanged(
                        normalizedUrl,
                        effectiveContentHash,
                        existing.getId(),
                        existing.getVersion(),
                        titleChanged
                );
            } else {
                log.info("Document at '{}' has been MODIFIED (old hash: {}, new hash: {}, version {} -> {})",
                        normalizedUrl, existing.getContentHash(), effectiveContentHash, existing.getVersion(), existing.getVersion() + 1);
                return ChangeDetectionResult.modified(
                        normalizedUrl,
                        effectiveContentHash,
                        existing.getId(),
                        existing.getVersion()
                );
            }
        }

        // 4. URL is new: check if exact identical content already exists under a different URL
        if (crawledDocumentRepository.existsByContentHash(effectiveContentHash)) {
            log.debug("URL '{}' contains duplicate content hash '{}' existing elsewhere", normalizedUrl, effectiveContentHash);
            return ChangeDetectionResult.duplicateContentDifferentUrl(normalizedUrl, effectiveContentHash);
        }

        // 5. Completely new document
        log.debug("URL '{}' is a NEW_DOCUMENT (hash: {})", normalizedUrl, effectiveContentHash);
        return ChangeDetectionResult.newDocument(normalizedUrl, effectiveContentHash);
    }
}
