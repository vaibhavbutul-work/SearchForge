package com.searchforge.semantic.tokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * High-performance, zero-external-dependency WordPiece tokenizer for pre-trained BERT and MiniLM models
 * (such as all-MiniLM-L6-v2).
 *
 * Implements:
 * 1. Basic text normalization (whitespace cleaning, lowercase transformation).
 * 2. Unicode and ASCII punctuation separation.
 * 3. Greedy longest-match subword segmentation with continuation prefixes ("##").
 * 4. Model-compliant special token placement ([CLS] at start, [SEP] at termination).
 * 5. Dynamic sequence sizing without unnecessary padding.
 */
public class WordPieceTokenizer implements Tokenizer {

    private static final Logger log = LoggerFactory.getLogger(WordPieceTokenizer.class);

    public static final String CLS_TOKEN = "[CLS]";
    public static final String SEP_TOKEN = "[SEP]";
    public static final String PAD_TOKEN = "[PAD]";
    public static final String UNK_TOKEN = "[UNK]";
    public static final String MASK_TOKEN = "[MASK]";

    public static final int DEFAULT_MAX_SEQUENCE_LENGTH = 256;
    public static final int MAX_INPUT_CHARS_PER_WORD = 100;

    private final Map<String, Integer> tokenToId;
    private final String[] idToToken;
    private final int padTokenId;
    private final int clsTokenId;
    private final int sepTokenId;
    private final int unkTokenId;

    /**
     * Constructs tokenizer by loading vocabulary from classpath resource or file path.
     *
     * @param vocabPath path to vocab.txt file (or resource path on classpath if preceded by "classpath:" or relative)
     */
    public WordPieceTokenizer(String vocabPath) {
        this.tokenToId = new HashMap<>(32000);
        List<String> vocabList = new ArrayList<>(32000);

        try (InputStream is = openVocabStream(vocabPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int id = 0;
            while ((line = reader.readLine()) != null) {
                String token = line.trim();
                if (!token.isEmpty()) {
                    tokenToId.put(token, id);
                    vocabList.add(token);
                    id++;
                }
            }
        } catch (IOException e) {
            throw new TokenizerException("Failed to load vocabulary from: " + vocabPath, e);
        }

        if (vocabList.isEmpty()) {
            throw new TokenizerException("Loaded vocabulary is empty from: " + vocabPath);
        }

        this.idToToken = vocabList.toArray(new String[0]);
        this.padTokenId = tokenToId.getOrDefault(PAD_TOKEN, 0);
        this.clsTokenId = tokenToId.getOrDefault(CLS_TOKEN, 101);
        this.sepTokenId = tokenToId.getOrDefault(SEP_TOKEN, 102);
        this.unkTokenId = tokenToId.getOrDefault(UNK_TOKEN, 100);

        log.info("Initialized WordPieceTokenizer: {} unique tokens loaded from {}", vocabList.size(), vocabPath);
    }

    /**
     * Direct constructor for in-memory vocabulary mapping (ideal for lightweight unit tests).
     */
    public WordPieceTokenizer(Map<String, Integer> vocabulary) {
        if (vocabulary == null || vocabulary.isEmpty()) {
            throw new TokenizerException("Vocabulary cannot be null or empty");
        }
        this.tokenToId = new HashMap<>(vocabulary);
        int maxId = vocabulary.values().stream().max(Integer::compareTo).orElse(0);
        this.idToToken = new String[maxId + 1];
        for (Map.Entry<String, Integer> entry : vocabulary.entrySet()) {
            if (entry.getValue() >= 0 && entry.getValue() <= maxId) {
                this.idToToken[entry.getValue()] = entry.getKey();
            }
        }
        this.padTokenId = tokenToId.getOrDefault(PAD_TOKEN, 0);
        this.clsTokenId = tokenToId.getOrDefault(CLS_TOKEN, 101);
        this.sepTokenId = tokenToId.getOrDefault(SEP_TOKEN, 102);
        this.unkTokenId = tokenToId.getOrDefault(UNK_TOKEN, 100);
    }

    @Override
    public TokenizationResult tokenize(String text) {
        return tokenize(text, DEFAULT_MAX_SEQUENCE_LENGTH);
    }

    @Override
    public TokenizationResult tokenize(String text, int maxSequenceLength) {
        int maxLen = maxSequenceLength > 2 ? maxSequenceLength : DEFAULT_MAX_SEQUENCE_LENGTH;

        if (text == null || text.isBlank()) {
            // Emits minimal valid sequence: [CLS], [SEP]
            List<String> emptyTokens = List.of(CLS_TOKEN, SEP_TOKEN);
            long[] ids = new long[]{(long) clsTokenId, (long) sepTokenId};
            long[] mask = new long[]{1L, 1L};
            long[] typeIds = new long[]{0L, 0L};
            return new TokenizationResult(emptyTokens, ids, mask, typeIds, 2, false);
        }

        // 1. Basic text normalization and punctuation splitting
        List<String> words = basicTokenize(text);

        // 2. WordPiece subword segmentation
        List<String> subTokens = new ArrayList<>();
        for (String word : words) {
            tokenizeWordPiece(word, subTokens);
        }

        // 3. Truncate if token sequence exceeds maxSequenceLength - 2 (reserve slots for [CLS] and [SEP])
        boolean truncated = false;
        int maxAllowedSubTokens = maxLen - 2;
        if (subTokens.size() > maxAllowedSubTokens) {
            subTokens = subTokens.subList(0, maxAllowedSubTokens);
            truncated = true;
        }

        // 4. Construct final sequence with special tokens: [CLS] + subTokens + [SEP]
        int totalSeqLen = subTokens.size() + 2;
        List<String> finalTokens = new ArrayList<>(totalSeqLen);
        finalTokens.add(CLS_TOKEN);
        finalTokens.addAll(subTokens);
        finalTokens.add(SEP_TOKEN);

        long[] tokenIds = new long[totalSeqLen];
        long[] attentionMask = new long[totalSeqLen];
        long[] tokenTypeIds = new long[totalSeqLen];

        tokenIds[0] = clsTokenId;
        attentionMask[0] = 1L;
        tokenTypeIds[0] = 0L;

        for (int i = 0; i < subTokens.size(); i++) {
            tokenIds[i + 1] = convertTokenToId(subTokens.get(i));
            attentionMask[i + 1] = 1L;
            tokenTypeIds[i + 1] = 0L;
        }

        tokenIds[totalSeqLen - 1] = sepTokenId;
        attentionMask[totalSeqLen - 1] = 1L;
        tokenTypeIds[totalSeqLen - 1] = 0L;

        return new TokenizationResult(finalTokens, tokenIds, attentionMask, tokenTypeIds, totalSeqLen, truncated);
    }

    private List<String> basicTokenize(String text) {
        String clean = text.toLowerCase(Locale.ROOT).trim();
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();

        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);

            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                if (!currentToken.isEmpty()) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else if (isPunctuation(c)) {
                if (!currentToken.isEmpty()) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else {
                currentToken.append(c);
            }
        }

        if (!currentToken.isEmpty()) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }

    private void tokenizeWordPiece(String word, List<String> output) {
        if (word.length() > MAX_INPUT_CHARS_PER_WORD) {
            output.add(UNK_TOKEN);
            return;
        }

        int start = 0;
        int len = word.length();
        boolean isBad = false;
        List<String> subTokens = new ArrayList<>();

        while (start < len) {
            int end = len;
            String curSub = null;

            while (start < end) {
                String sub = (start == 0) ? word.substring(start, end) : "##" + word.substring(start, end);
                if (tokenToId.containsKey(sub)) {
                    curSub = sub;
                    break;
                }
                end--;
            }

            if (curSub == null) {
                isBad = true;
                break;
            }

            subTokens.add(curSub);
            start = end;
        }

        if (isBad) {
            output.add(UNK_TOKEN);
        } else {
            output.addAll(subTokens);
        }
    }

    private boolean isPunctuation(char c) {
        // ASCII punctuation characters
        if ((c >= 33 && c <= 47) || (c >= 58 && c <= 64) || (c >= 91 && c <= 96) || (c >= 123 && c <= 126)) {
            return true;
        }
        // General Unicode punctuation categories
        int type = Character.getType(c);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private InputStream openVocabStream(String path) throws IOException {
        if (path == null || path.isBlank() || "default".equalsIgnoreCase(path)) {
            InputStream defaultStream = getClass().getResourceAsStream("/models/vocab.txt");
            if (defaultStream != null) {
                return defaultStream;
            }
            throw new FileNotFoundException("Default classpath vocabulary resource '/models/vocab.txt' not found.");
        }

        // 1. Try direct file system path
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            return new FileInputStream(file);
        }

        // 2. Try classpath resource
        String cleanPath = path.startsWith("classpath:") ? path.substring(10) : path;
        if (!cleanPath.startsWith("/")) {
            cleanPath = "/" + cleanPath;
        }
        InputStream resourceStream = getClass().getResourceAsStream(cleanPath);
        if (resourceStream != null) {
            return resourceStream;
        }

        throw new FileNotFoundException("Cannot find vocabulary file at path '" + path + "' or in classpath resources.");
    }

    @Override
    public int getVocabSize() {
        return tokenToId.size();
    }

    @Override
    public int getPadTokenId() {
        return padTokenId;
    }

    @Override
    public int getClsTokenId() {
        return clsTokenId;
    }

    @Override
    public int getSepTokenId() {
        return sepTokenId;
    }

    @Override
    public int getUnkTokenId() {
        return unkTokenId;
    }

    @Override
    public long convertTokenToId(String token) {
        if (token == null) {
            return unkTokenId;
        }
        Integer id = tokenToId.get(token);
        return id != null ? (long) id : (long) unkTokenId;
    }

    @Override
    public String convertIdToToken(long id) {
        if (id >= 0 && id < idToToken.length) {
            return idToToken[(int) id];
        }
        return null;
    }
}
