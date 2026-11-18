package com.searchforge.indexer.stemmer;

import org.springframework.stereotype.Component;

/**
 * Implementation of the Martin Porter (1980) Stemming Algorithm.
 *
 * Provides algorithmic suffix stripping to reduce inflected or derived words to their common
 * base stem (e.g., "crawling", "crawled", "crawls" -> "crawl").
 *
 * Thread-safe: Operates statelessly per invocation, making it safe for high-concurrency
 * environments powered by Java 21 Virtual Threads.
 */
@Component
public class PorterStemmer implements Stemmer {

    @Override
    public String stem(String word) {
        if (word == null || word.length() <= 2) {
            return word != null ? word : "";
        }
        return new State(word).stem();
    }

    /**
     * Internal stateful helper encapsulating the character buffer and step operations
     * for a single word. Thread-confined to ensure complete thread-safety.
     */
    private static final class State {
        private char[] b;
        private int k; // pointer to end of current word buffer
        private int j; // general pointer to end of stem preceding suffix

        State(String word) {
            this.b = word.toCharArray();
            this.k = word.length() - 1;
        }

        private boolean cons(int i) {
            return switch (b[i]) {
                case 'a', 'e', 'i', 'o', 'u' -> false;
                case 'y' -> (i == 0) || !cons(i - 1);
                default -> true;
            };
        }

        /**
         * Measure m: counts the number of VC sequences between 0 and j.
         */
        private int m() {
            int n = 0;
            int i = 0;
            while (true) {
                if (i > j) return n;
                if (!cons(i)) break;
                i++;
            }
            i++;
            while (true) {
                while (true) {
                    if (i > j) return n;
                    if (cons(i)) break;
                    i++;
                }
                i++;
                n++;
                while (true) {
                    if (i > j) return n;
                    if (!cons(i)) break;
                    i++;
                }
                i++;
            }
        }

        private boolean vowelInStem() {
            for (int i = 0; i <= j; i++) {
                if (!cons(i)) {
                    return true;
                }
            }
            return false;
        }

        private boolean doubleC(int i) {
            if (i < 1) return false;
            if (b[i] != b[i - 1]) return false;
            return cons(i);
        }

        private boolean cvc(int i) {
            if (i < 2 || !cons(i) || cons(i - 1) || !cons(i - 2)) {
                return false;
            }
            char ch = b[i];
            return ch != 'w' && ch != 'x' && ch != 'y';
        }

        private boolean ends(String s) {
            int l = s.length();
            int o = k - l + 1;
            if (o < 0) return false;
            for (int i = 0; i < l; i++) {
                if (b[o + i] != s.charAt(i)) {
                    return false;
                }
            }
            j = k - l;
            return true;
        }

        private void setTo(String s) {
            int l = s.length();
            int o = j + 1;
            if (o + l > b.length) {
                char[] newB = new char[o + l + 8];
                System.arraycopy(b, 0, newB, 0, b.length);
                b = newB;
            }
            for (int i = 0; i < l; i++) {
                b[o + i] = s.charAt(i);
            }
            k = j + l;
        }

        private void r(String s) {
            if (m() > 0) {
                setTo(s);
            }
        }

        /**
         * Step 1: Handles plurals and third person singular verb endings.
         */
        private void step1() {
            if (b[k] == 's') {
                if (ends("sses")) k -= 2;
                else if (ends("ies")) setTo("i");
                else if (b[k - 1] != 's') k--;
            }
            if (ends("eed")) {
                if (m() > 0) k--;
            } else if ((ends("ed") || ends("ing")) && vowelInStem()) {
                k = j;
                if (ends("at")) setTo("ate");
                else if (ends("bl")) setTo("ble");
                else if (ends("iz")) setTo("ize");
                else if (doubleC(k)) {
                    k--;
                    char ch = b[k];
                    if (ch == 'l' || ch == 's' || ch == 'z') k++;
                } else if (m() == 1 && cvc(k)) {
                    setTo("e");
                }
            }
            if (ends("y") && vowelInStem()) {
                b[k] = 'i';
            }
        }

        /**
         * Step 2: Derivational suffixes.
         */
        private void step2() {
            if (k < 1) return;
            switch (b[k - 1]) {
                case 'a':
                    if (ends("ational")) { r("ate"); break; }
                    if (ends("tional")) { r("tion"); break; }
                    break;
                case 'c':
                    if (ends("enci")) { r("ence"); break; }
                    if (ends("anci")) { r("ance"); break; }
                    break;
                case 'e':
                    if (ends("izer")) { r("ize"); break; }
                    break;
                case 'l':
                    if (ends("bli")) { r("ble"); break; }
                    if (ends("alli")) { r("al"); break; }
                    if (ends("entli")) { r("ent"); break; }
                    if (ends("eli")) { r("e"); break; }
                    if (ends("ousli")) { r("ous"); break; }
                    break;
                case 'o':
                    if (ends("ization")) { r("ize"); break; }
                    if (ends("ation")) { r("ate"); break; }
                    if (ends("ator")) { r("ate"); break; }
                    break;
                case 's':
                    if (ends("alism")) { r("al"); break; }
                    if (ends("iveness")) { r("ive"); break; }
                    if (ends("fulness")) { r("ful"); break; }
                    if (ends("ousness")) { r("ous"); break; }
                    break;
                case 't':
                    if (ends("aliti")) { r("al"); break; }
                    if (ends("iviti")) { r("ive"); break; }
                    if (ends("biliti")) { r("ble"); break; }
                    break;
                case 'g':
                    if (ends("logi")) { r("log"); break; }
                    break;
                default:
                    break;
            }
        }

        /**
         * Step 3: Compound suffixes.
         */
        private void step3() {
            if (k < 0) return;
            switch (b[k]) {
                case 'e':
                    if (ends("icate")) { r("ic"); break; }
                    if (ends("ative")) { r(""); break; }
                    if (ends("alize")) { r("al"); break; }
                    break;
                case 'i':
                    if (ends("iciti")) { r("ic"); break; }
                    break;
                case 'l':
                    if (ends("ical")) { r("ic"); break; }
                    if (ends("ful")) { r(""); break; }
                    break;
                case 's':
                    if (ends("ness")) { r(""); break; }
                    break;
                default:
                    break;
            }
        }

        /**
         * Step 4: Residual suffix stripping.
         */
        private void step4() {
            if (k < 1) return;
            switch (b[k - 1]) {
                case 'a': if (ends("al")) break; return;
                case 'c': if (ends("ance") || ends("ence")) break; return;
                case 'e': if (ends("er")) break; return;
                case 'i': if (ends("ic")) break; return;
                case 'l': if (ends("able") || ends("ible")) break; return;
                case 'n': if (ends("ant") || ends("ement") || ends("ment") || ends("ent")) break; return;
                case 'o':
                    if (ends("ion") && j >= 0 && (b[j] == 's' || b[j] == 't')) break;
                    if (ends("ou")) break;
                    return;
                case 's': if (ends("ism")) break; return;
                case 't': if (ends("ate") || ends("iti")) break; return;
                case 'u': if (ends("ous")) break; return;
                case 'v': if (ends("ive")) break; return;
                case 'z': if (ends("ize")) break; return;
                default: return;
            }
            if (m() > 1) {
                k = j;
            }
        }

        /**
         * Step 5: Final cleanup (silent e removal, double l reduction).
         */
        private void step5() {
            j = k;
            if (b[k] == 'e') {
                int a = m();
                if (a > 1 || (a == 1 && !cvc(k - 1))) {
                    k--;
                }
            }
            if (b[k] == 'l' && doubleC(k) && m() > 1) {
                k--;
            }
        }

        String stem() {
            step1();
            step2();
            step3();
            step4();
            step5();
            return new String(b, 0, k + 1);
        }
    }
}
