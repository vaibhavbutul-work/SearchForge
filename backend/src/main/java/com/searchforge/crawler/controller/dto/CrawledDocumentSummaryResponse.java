package com.searchforge.crawler.controller.dto;

import com.searchforge.crawler.model.CrawledDocument;

import java.time.Instant;

/**
 * Compact summary response for exploring crawled documents without transferring large clean_text payloads.
 */
public record CrawledDocumentSummaryResponse(
        Long id,
        String url,
        String domain,
        String title,
        String contentSnippet,
        String contentHash,
        int httpStatusCode,
        Instant crawledAt
) {
    private static final int MAX_SNIPPET_LENGTH = 200;

    public static CrawledDocumentSummaryResponse from(CrawledDocument doc) {
        String snippet = "";
        if (doc.getCleanText() != null) {
            snippet = doc.getCleanText().length() > MAX_SNIPPET_LENGTH
                    ? doc.getCleanText().substring(0, MAX_SNIPPET_LENGTH) + "..."
                    : doc.getCleanText();
        }

        return new CrawledDocumentSummaryResponse(
                doc.getId(),
                doc.getUrl(),
                doc.getDomain(),
                doc.getTitle(),
                snippet,
                doc.getContentHash(),
                doc.getHttpStatusCode(),
                doc.getCrawledAt()
        );
    }
}
