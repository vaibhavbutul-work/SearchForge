package com.searchforge.query.synonym.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Boot configuration properties for synonym dictionary and query expansion.
 */
@Component
@ConfigurationProperties(prefix = "search.synonym")
public class SynonymProperties {

    /**
     * Mode governing synonym expansion (DISABLED, ONE_WAY, BIDIRECTIONAL). Default: BIDIRECTIONAL.
     */
    private SynonymExpansionMode mode = SynonymExpansionMode.BIDIRECTIONAL;

    /**
     * Weight multiplier applied to expanded synonyms (0.0 < discount <= 1.0). Default: 0.5.
     */
    private double discount = 0.5;

    /**
     * Maximum number of synonyms expanded per query term to prevent query explosion. Default: 3.
     */
    private int maxSynonymsPerTerm = 3;

    /**
     * Unidirectional synonym mappings (e.g., k8s -> [kubernetes], js -> [javascript]).
     */
    private Map<String, List<String>> oneWay = new HashMap<>();

    /**
     * Bidirectional synonym mappings (e.g., db <-> [database, rdbms]).
     */
    private Map<String, List<String>> bidirectional = new HashMap<>();

    public SynonymProperties() {
        // Pre-configure standard software engineering tech synonyms out of the box
        bidirectional.put("db", List.of("database"));
        bidirectional.put("postgres", List.of("postgresql"));
        oneWay.put("k8s", List.of("kubernetes"));
        oneWay.put("js", List.of("javascript"));
    }

    public SynonymExpansionMode getMode() {
        return mode;
    }

    public void setMode(SynonymExpansionMode mode) {
        if (mode != null) {
            this.mode = mode;
        }
    }

    public double getDiscount() {
        return discount;
    }

    public void setDiscount(double discount) {
        if (discount > 0.0 && discount <= 1.0) {
            this.discount = discount;
        }
    }

    public int getMaxSynonymsPerTerm() {
        return maxSynonymsPerTerm;
    }

    public void setMaxSynonymsPerTerm(int maxSynonymsPerTerm) {
        if (maxSynonymsPerTerm > 0) {
            this.maxSynonymsPerTerm = maxSynonymsPerTerm;
        }
    }

    public Map<String, List<String>> getOneWay() {
        return oneWay;
    }

    public void setOneWay(Map<String, List<String>> oneWay) {
        this.oneWay = (oneWay != null) ? oneWay : new HashMap<>();
    }

    public Map<String, List<String>> getBidirectional() {
        return bidirectional;
    }

    public void setBidirectional(Map<String, List<String>> bidirectional) {
        this.bidirectional = (bidirectional != null) ? bidirectional : new HashMap<>();
    }
}
