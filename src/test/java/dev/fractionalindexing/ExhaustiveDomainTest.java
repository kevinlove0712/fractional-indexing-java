// SPDX-License-Identifier: CC0-1.0
package dev.fractionalindexing;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import static dev.fractionalindexing.FractionalIndexing.*;
import static org.junit.jupiter.api.Assertions.*;

/** Finite-domain enumeration and an exact numeric model independent of the midpoint algorithm. */
class ExhaustiveDomainTest {
    private static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String HEADS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String MIN = "A" + repeat('0', 26);
    private static final BigInteger BASE = BigInteger.valueOf(62);
    private static final BigInteger[] OFFSETS = offsets();

    @Test
    void adjacentKeysInTheCompleteThreeCharacterDomainHaveNoAvailableKey() {
        List<String> domain = allKeysUpToThreeCharacters();
        assertEquals(15_376, domain.size());
        for (int i = 1; i < domain.size(); i++) {
            String left = domain.get(i - 1), right = domain.get(i);
            assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(left, right, 3), left + "/" + right);
            assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween(left, right, 1, 3, 1));
            String longer = generateKeyBetween(left, right);
            assertTrue(longer.length() > 3);
            assertTrue(left.compareTo(longer) < 0 && longer.compareTo(right) < 0);
        }
    }

    @Test
    void everySingleGapInTheCompleteThreeCharacterDomainSelectsTheOnlyKey() {
        List<String> domain = allKeysUpToThreeCharacters();
        for (int i = 1; i < domain.size() - 1; i++) {
            String left = domain.get(i - 1), expected = domain.get(i), right = domain.get(i + 1);
            assertEquals(expected, generateKeyBetween(left, right, 3), left + "/" + right);
            assertEquals(Collections.singletonList(expected), generateNKeysBetween(left, right, 1, 3, 1));
            assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween(left, right, 2, 3, 2));
        }
    }

    @Test
    void generatedKeysStayBetweenBoundsUnderAnExactRationalOrderingModel() {
        for (long seed : new long[] {0x41B3A8L, 0x7612F0L, 0xC0FFEE12L}) {
            Random random = new Random(seed);
            for (int trial = 0; trial < 4_000; trial++) {
                String left = validKey(random), right = validKey(random);
                if ((trial & 1) == 0) right = left.substring(0, integerLength(left.charAt(0))) + fraction(random);
                if (right.equals(MIN)) right += "V";
                if (left.equals(right)) right += "V";
                if (numericCompare(left, right) > 0) { String swap = left; left = right; right = swap; }
                assertTrue(left.compareTo(right) < 0, "lexical/numeric disagreement; seed=" + seed + ", trial=" + trial);
                if (trial % 7 == 0) left = null;
                if (trial % 7 == 1) right = null;
                int count = random.nextInt(33);
                List<String> keys = generateNKeysBetween(left, right, count);
                assertEquals(count, keys.size());
                assertNumericallyOrdered(left, right, keys);
                assertNumericallyOrdered(left, right, Collections.singletonList(generateKeyBetween(left, right)));
            }
        }
    }

    @Test
    void zeroCountValidationAgreesWithIndependentGrammarForMutatedKeys() {
        Random random = new Random(0xD0C5BEEFL);
        int[] limits = {2, 3, 26, 27, 28, 127, 128, 129, 256};
        for (int trial = 0; trial < 100_000; trial++) {
            String base = validKey(random);
            String key;
            switch (trial % 8) {
                case 0: key = base; break;
                case 1: key = base.substring(0, random.nextInt(base.length())); break;
                case 2: key = base.substring(0, 1) + '\uD800' + base.substring(1); break;
                case 3: key = base + "0"; break;
                case 4: key = "0" + base.substring(1); break;
                case 5: key = "a0" + repeat('V', 125 + random.nextInt(6)); break;
                case 6: key = MIN + (random.nextBoolean() ? "" : "1"); break;
                default: key = "a" + (char) random.nextInt(65536); break;
            }
            int limit = limits[random.nextInt(limits.length)];
            if (key.length() > limit) {
                assertThrowsExactly(KeyLengthExceededException.class, () -> generateNKeysBetween(key, null, 0, limit, 1));
                assertThrowsExactly(KeyLengthExceededException.class, () -> generateNKeysBetween(null, key, 0, limit, 1));
            } else if (validGrammar(key)) {
                assertTrue(generateNKeysBetween(key, null, 0, limit, 1).isEmpty());
                assertTrue(generateNKeysBetween(null, key, 0, limit, 1).isEmpty());
            } else {
                assertThrowsExactly(IllegalArgumentException.class, () -> generateNKeysBetween(key, null, 0, limit, 1));
                assertThrowsExactly(IllegalArgumentException.class, () -> generateNKeysBetween(null, key, 0, limit, 1));
            }
        }
    }

    @Test
    void millionCharacterKeysUseOffsetsAcrossDistinctMidpointBranches() {
        String z = repeat('z', 1_000_000);
        assertEquals("a0" + z + "V", generateKeyBetween("a0" + z, "a1", 1_000_003));
        String common = "a0" + repeat('G', 1_000_000);
        assertEquals(common + "V", generateKeyBetween(common + "1", common + "z", 1_000_003));
        String zeros = repeat('0', 1_000_000);
        assertEquals("a0" + zeros + "0V", generateKeyBetween("a0", "a0" + zeros + "1", 1_000_004));
        assertEquals(MIN + zeros + "0V", generateKeyBetween(null, MIN + zeros + "1", MIN.length() + 1_000_002));
        String lower = "a0" + z;
        List<String> batch = generateNKeysBetween(lower, "a1", 3, 1_000_003, 3);
        assertEquals(Arrays.asList(lower + "G", lower + "V", lower + "l"), batch);
    }

    private static List<String> allKeysUpToThreeCharacters() {
        List<String> keys = new ArrayList<>();
        for (char head : new char[] {'Y', 'Z', 'a', 'b'}) {
            for (int first = 0; first < 62; first++) {
                String prefix = "" + head + DIGITS.charAt(first);
                if (head == 'Z' || head == 'a') keys.add(prefix);
                for (int second = head == 'Z' || head == 'a' ? 1 : 0; second < 62; second++) {
                    keys.add(prefix + DIGITS.charAt(second));
                }
            }
        }
        Collections.sort(keys);
        return keys;
    }

    private static boolean validGrammar(String key) {
        if (key.isEmpty() || HEADS.indexOf(key.charAt(0)) < 0 || key.equals(MIN)) return false;
        int length = integerLength(key.charAt(0));
        if (key.length() < length) return false;
        for (int i = 1; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) return false;
        }
        return key.length() == length || key.charAt(key.length() - 1) != '0';
    }

    private static int integerLength(char head) {
        return 2 + Math.abs(2 * HEADS.indexOf(head) - 51) / 2;
    }

    private static BigInteger[] offsets() {
        BigInteger[] result = new BigInteger[52];
        BigInteger sum = BigInteger.ZERO;
        for (int i = 0; i < result.length; i++) {
            result[i] = sum;
            sum = sum.add(BASE.pow(integerLength(HEADS.charAt(i)) - 1));
        }
        return result;
    }

    private static BigInteger numerator(String key) {
        BigInteger value = OFFSETS[HEADS.indexOf(key.charAt(0))];
        BigInteger integer = BigInteger.ZERO;
        int length = integerLength(key.charAt(0));
        for (int i = 1; i < length; i++) integer = integer.multiply(BASE).add(BigInteger.valueOf(DIGITS.indexOf(key.charAt(i))));
        value = value.add(integer);
        for (int i = length; i < key.length(); i++) value = value.multiply(BASE).add(BigInteger.valueOf(DIGITS.indexOf(key.charAt(i))));
        return value;
    }

    private static int numericCompare(String left, String right) {
        int a = left.length() - integerLength(left.charAt(0));
        int b = right.length() - integerLength(right.charAt(0));
        int scale = Math.max(a, b);
        return numerator(left).multiply(BASE.pow(scale - a)).compareTo(numerator(right).multiply(BASE.pow(scale - b)));
    }

    private static void assertNumericallyOrdered(String left, String right, List<String> keys) {
        String previous = left;
        for (String key : keys) {
            assertTrue(validGrammar(key));
            assertTrue(previous == null || numericCompare(previous, key) < 0);
            assertTrue(right == null || numericCompare(key, right) < 0);
            previous = key;
        }
    }

    private static String validKey(Random random) {
        char head = HEADS.charAt(random.nextInt(52));
        StringBuilder key = new StringBuilder().append(head);
        for (int i = 1; i < integerLength(head); i++) key.append(DIGITS.charAt(random.nextInt(62)));
        key.append(fraction(random));
        if (key.toString().equals(MIN)) key.append('V');
        return key.toString();
    }

    private static String fraction(Random random) {
        StringBuilder fraction = new StringBuilder();
        int length = random.nextInt(31);
        for (int i = 0; i < length; i++) fraction.append(DIGITS.charAt(i == length - 1 ? 1 + random.nextInt(61) : random.nextInt(62)));
        return fraction.toString();
    }

    private static String repeat(char c, int length) {
        char[] chars = new char[length];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
