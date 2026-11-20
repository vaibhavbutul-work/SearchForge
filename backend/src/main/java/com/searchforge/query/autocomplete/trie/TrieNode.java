package com.searchforge.query.autocomplete.trie;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Node in the custom SearchForge Prefix Trie.
 *
 * Supports thread-safe character navigation and terminal word metadata.
 */
public class TrieNode {

    private final Map<Character, TrieNode> children = new ConcurrentHashMap<>();
    private volatile boolean endOfWord;
    private volatile String term;
    private volatile long ctf;
    private volatile int df;
    private volatile double score;

    public TrieNode() {
        this.endOfWord = false;
    }

    public Map<Character, TrieNode> getChildren() {
        return Collections.unmodifiableMap(children);
    }

    public TrieNode getChild(char ch) {
        return children.get(ch);
    }

    public TrieNode getOrCreateChild(char ch) {
        return children.computeIfAbsent(ch, c -> new TrieNode());
    }

    public boolean hasChild(char ch) {
        return children.containsKey(ch);
    }

    public boolean isEndOfWord() {
        return endOfWord;
    }

    public void setEndOfWord(boolean endOfWord) {
        this.endOfWord = endOfWord;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public long getCtf() {
        return ctf;
    }

    public void setCtf(long ctf) {
        this.ctf = ctf;
    }

    public int getDf() {
        return df;
    }

    public void setDf(int df) {
        this.df = df;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }
}
