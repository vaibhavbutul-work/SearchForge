package com.searchforge.crawler.parser;

import com.searchforge.crawler.fetcher.FetchedDocument;
import com.searchforge.crawler.url.UrlNormalizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Parses raw HTML into clean, structured content ready for indexing.
 *
 * Responsibilities:
 * 1. Extracts webpage title.
 * 2. Strips boilerplate and non-content tags (<script>, <style>, <nav>, <footer>, etc.).
 * 3. Extracts readable text for search indexing.
 * 4. Extracts outbound hyperlinks (<a href>) with automatic base URL resolution.
 * 5. Generates a 64-character SHA-256 fingerprint of the cleaned text to detect duplicate content.
 */
@Component
public class HtmlParser {

    private static final Logger log = LoggerFactory.getLogger(HtmlParser.class);

    // CSS selector identifying non-content boilerplate elements to strip
    private static final String NOISE_TAGS_SELECTOR = "script, style, nav, footer, header, aside, iframe, noscript, svg";

    private final UrlNormalizer urlNormalizer;

    public HtmlParser(UrlNormalizer urlNormalizer) {
        this.urlNormalizer = urlNormalizer;
    }

    /**
     * Parses a FetchedDocument into a structured ParsedDocument.
     *
     * @param fetchedDoc Raw document from DocumentFetcher.
     * @return Structured ParsedDocument containing clean text and discovered links.
     */
    public ParsedDocument parse(FetchedDocument fetchedDoc) {
        String baseUri = fetchedDoc.url();
        Document doc = Jsoup.parse(fetchedDoc.rawHtml(), baseUri);

        // 1. Extract Title (fallback to <h1> or "Untitled")
        String title = doc.title();
        if (title == null || title.isBlank()) {
            Element h1 = doc.selectFirst("h1");
            title = (h1 != null && !h1.text().isBlank()) ? h1.text() : "Untitled";
        }
        if (title.length() > 512) {
            title = title.substring(0, 509) + "...";
        }

        // 2. Discover Outbound Hyperlinks (before removing tags)
        List<String> outboundLinks = new ArrayList<>();
        Elements anchorElements = doc.select("a[href]");
        for (Element anchor : anchorElements) {
            // "abs:href" automatically resolves relative links using baseUri or <base> tag
            String absoluteUrl = anchor.attr("abs:href");
            if (absoluteUrl != null && !absoluteUrl.isBlank()) {
                outboundLinks.add(absoluteUrl.trim());
            }
        }

        // 3. Strip Boilerplate / Noise Tags
        doc.select(NOISE_TAGS_SELECTOR).remove();

        // 4. Extract Readable Clean Text
        String cleanText = doc.body() != null ? doc.body().text().trim() : "";

        // 5. Generate SHA-256 Content Fingerprint
        String contentHash = computeSha256(cleanText);

        // 6. Extract Domain
        String domain = urlNormalizer.extractDomain(baseUri).orElse("unknown");

        log.debug("Parsed '{}': title='{}', textLength={}, linksFound={}",
                baseUri, title, cleanText.length(), outboundLinks.size());

        return new ParsedDocument(
                baseUri,
                domain,
                title,
                cleanText,
                contentHash,
                fetchedDoc.statusCode(),
                outboundLinks,
                fetchedDoc.fetchedAt()
        );
    }

    /**
     * Computes a 64-character lowercase SHA-256 hex digest of the input string.
     */
    public String computeSha256(String text) {
        if (text == null) {
            text = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable in JVM", e);
        }
    }
}
