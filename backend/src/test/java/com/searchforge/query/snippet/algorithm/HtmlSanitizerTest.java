package com.searchforge.query.snippet.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    @Test
    @DisplayName("Should escape all HTML control characters (&, <, >, \", ')")
    void shouldEscapeHtmlEntities() {
        String input = "Tom & Jerry <cartoon> \"classic\" '1940'";
        String escaped = HtmlSanitizer.escapeHtml(input);

        assertThat(escaped).isEqualTo("Tom &amp; Jerry &lt;cartoon&gt; &quot;classic&quot; &#39;1940&#39;");
    }

    @Test
    @DisplayName("Should neutralize executable script injection tags")
    void shouldNeutralizeScriptInjection() {
        String xssPayload = "<script>alert('XSS Attack!')</script>";
        String safe = HtmlSanitizer.escapeHtml(xssPayload);

        assertThat(safe).isEqualTo("&lt;script&gt;alert(&#39;XSS Attack!&#39;)&lt;/script&gt;");
        assertThat(safe).doesNotContain("<script>");
        assertThat(safe).doesNotContain("</script>");
    }

    @Test
    @DisplayName("Should leave regular alphanumeric text and punctuation unaltered")
    void shouldNotAlterCleanText() {
        String clean = "PostgreSQL B-Tree indexes provide logarithmic O(log N) lookups.";
        assertThat(HtmlSanitizer.escapeHtml(clean)).isEqualTo(clean);
    }

    @Test
    @DisplayName("Should handle null and empty string boundaries")
    void shouldHandleNullAndEmpty() {
        assertThat(HtmlSanitizer.escapeHtml(null)).isEmpty();
        assertThat(HtmlSanitizer.escapeHtml("")).isEmpty();
    }
}
