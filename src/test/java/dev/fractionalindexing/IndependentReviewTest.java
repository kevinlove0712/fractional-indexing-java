// SPDX-License-Identifier: CC0-1.0
package dev.fractionalindexing;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import static dev.fractionalindexing.FractionalIndexing.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent review: constructed inputs, arithmetic oracle and pinned JS differential cases. */
class IndependentReviewTest {
    private static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String HEADS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String MIN = "A" + repeat('0', 26);

    @Test
    void matchesIndependentlyConstructedUpstreamCases() throws Exception {
        int cases = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/independent-rocicorp-vectors.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isEmpty()) continue;
                String[] columns = line.split("\t", -1);
                String left = columns[1].equals("-") ? null : columns[1];
                String right = columns[2].equals("-") ? null : columns[2];
                int count = Integer.parseInt(columns[3]);
                List<String> expected = columns[4].isEmpty() ? Collections.emptyList()
                        : Arrays.asList(columns[4].split(","));
                List<String> actual = columns[0].equals("single")
                        ? Collections.singletonList(generateKeyBetween(left, right, 2048))
                        : generateNKeysBetween(left, right, count, 2048, 10_000);
                assertEquals(expected, actual, "independent vector " + cases);
                assertKeys(left, right, count, actual);
                cases++;
            }
        }
        assertTrue(cases >= 10_000, "independent fixture set is incomplete");
    }

    @Test
    void integerCarriesAndBorrowsAgreeWithBigIntegerRanksAcrossAllHeads() {
        for (int h = 0; h < HEADS.length(); h++) {
            char head = HEADS.charAt(h);
            int width = Math.abs(2 * h - 51) / 2 + 1;
            String low = head + repeat('0', width);
            String high = head + repeat('z', width);
            if (h > 0) {
                List<String> before = generateNKeysBetween(null, low, 65);
                assertKeys(null, low, 65, before);
                for (int i = 0; i < before.size(); i++) {
                    assertEquals(integerRank(low).subtract(BigInteger.valueOf(65 - i)), integerRank(before.get(i)));
                }
            }
            if (h < HEADS.length() - 1) {
                List<String> after = generateNKeysBetween(high, null, 65);
                assertKeys(high, null, 65, after);
                for (int i = 0; i < after.size(); i++) {
                    assertEquals(integerRank(high).add(BigInteger.valueOf(i + 1)), integerRank(after.get(i)));
                }
            }
        }
    }

    @Test
    void allPairsOfShortConstructedFractionsSupportBalancedBatchSizes() {
        TreeSet<String> boundaries = new TreeSet<>();
        boundaries.add("a0");
        boundaries.add("a1");
        for (int i = 0; i < DIGITS.length(); i++) {
            if (i > 0) boundaries.add("a0" + DIGITS.charAt(i));
            for (char tail : new char[] {'1', 'V', 'z'}) boundaries.add("a0" + DIGITS.charAt(i) + tail);
        }
        List<String> sorted = new ArrayList<>(boundaries);
        for (int i = 0; i < sorted.size(); i++) {
            for (int j = i + 1; j < sorted.size(); j++) {
                String left = sorted.get(i), right = sorted.get(j);
                assertKeys(left, right, 1, Collections.singletonList(generateKeyBetween(left, right)));
                for (int n : new int[] {0, 1, 2, 7, 8}) assertKeys(left, right, n, generateNKeysBetween(left, right, n));
            }
        }
    }

    @Test
    void arbitraryValidKeysSupportSingleAndBatchAllocation() {
        Random random = new Random(0x1D3E53L);
        for (int trial = 0; trial < 10_000; trial++) {
            String left = randomKey(random), right = randomKey(random);
            if (left.equals(right)) continue;
            if (left.compareTo(right) > 0) { String swap = left; left = right; right = swap; }
            if (trial % 3 == 0) left = null;
            if (trial % 3 == 1) right = null;
            int n = trial % 17;
            assertKeys(left, right, n, generateNKeysBetween(left, right, n));
            assertEquals(Collections.singletonList(generateKeyBetween(left, right)), generateNKeysBetween(left, right, 1));
        }
    }

    @Test
    void rejectsEveryNonAlphabetUtf16CodeUnitInHeadsIntegersAndFractions() {
        for (int code = 0; code <= Character.MAX_VALUE; code++) {
            char c = (char) code;
            if (HEADS.indexOf(c) < 0) assertRejected(c + repeat('1', 26));
            if (DIGITS.indexOf(c) < 0) {
                assertRejected("a" + c);
                assertRejected("a0" + c + "V");
            }
        }
    }

    @Test
    void validatesLengthsTrailingZerosOrderAndCountsEvenForEmptyBatches() {
        assertRejected("");
        assertRejected(MIN);
        for (int h = 0; h < HEADS.length(); h++) {
            char head = HEADS.charAt(h);
            int width = Math.abs(2 * h - 51) / 2 + 1;
            for (int length = 0; length < width; length++) assertRejected(head + repeat('1', length));
            assertRejected(head + repeat('1', width) + "0");
            assertRejected(head + repeat('1', width) + "V0");
        }
        for (int count : new int[] {0, 1, 2, 7}) {
            assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween("a0", "a0", count));
            assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween("a1", "a0", count));
        }
        for (int count : new int[] {-1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, count));
            assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween("a0", "a1", count));
        }
        assertThrows(IllegalArgumentException.class, () -> generateKeyBetween("a0", "a0"));
        assertThrows(IllegalArgumentException.class, () -> generateKeyBetween("a1", "a0"));
    }

    @Test
    void minIntegerFixRemainsValidWhenABatchCrossesTheReservedInteger() {
        String first = "A" + repeat('0', 25) + "1";
        String second = "A" + repeat('0', 25) + "2";
        assertEquals(Arrays.asList(MIN + "8", MIN + "G", MIN + "V", first), generateNKeysBetween(null, second, 4));
        String max = repeat('z', 27);
        for (int count : new int[] {0, 1, 2, 3, 63, 64, 65, 257}) {
            List<String> before = generateNKeysBetween(null, second, count);
            assertKeys(null, second, count, before);
            assertReusable(before);
            List<String> after = generateNKeysBetween(max, null, count);
            assertKeys(max, null, count, after);
            assertReusable(after);
            assertKeys(MIN + "1", first, count, generateNKeysBetween(MIN + "1", first, count));
        }
    }

    @Test
    void longPrefixesZeroPaddingAndMaxDigitRunsRemainCorrect() {
        String common = "a0" + repeat('V', 100_000);
        assertEquals(common + "V", generateKeyBetween(common + "1", common + "z", 200_000));
        assertKeys(common + "1", common + "z", 33, generateNKeysBetween(common + "1", common + "z", 33, 200_000, 10_000));
        String zeros = repeat('0', 100_000);
        assertEquals("a0" + zeros + "0V", generateKeyBetween("a0", "a0" + zeros + "1", 200_000));
        assertEquals(MIN + zeros + "0V", generateKeyBetween(null, MIN + zeros + "1", 200_000));
        String maxRun = "a0" + repeat('z', 20_000);
        assertEquals(maxRun + "V", generateKeyBetween(maxRun, "a1", 200_000));
        assertKeys(maxRun, "a1", 7, generateNKeysBetween(maxRun, "a1", 7, 200_000, 10_000));
    }

    private static void assertRejected(String key) {
        assertThrows(IllegalArgumentException.class, () -> generateKeyBetween(key, null));
        assertThrows(IllegalArgumentException.class, () -> generateKeyBetween(null, key));
        assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween(key, null, 0));
        assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween(null, key, 0));
    }

    private static void assertKeys(String left, String right, int count, List<String> keys) {
        assertEquals(count, keys.size());
        String previous = left;
        for (String key : keys) {
            assertValid(key);
            assertTrue(previous == null || previous.compareTo(key) < 0);
            assertTrue(right == null || key.compareTo(right) < 0);
            previous = key;
        }
    }

    private static void assertValid(String key) {
        assertFalse(key.isEmpty());
        int h = HEADS.indexOf(key.charAt(0));
        assertTrue(h >= 0);
        int length = Math.abs(2 * h - 51) / 2 + 2;
        assertTrue(key.length() >= length);
        assertNotEquals(MIN, key);
        for (int i = 1; i < key.length(); i++) assertTrue(DIGITS.indexOf(key.charAt(i)) >= 0);
        assertTrue(key.length() == length || !key.endsWith("0"));
    }

    private static void assertReusable(List<String> keys) {
        for (String key : keys) {
            assertTrue(generateKeyBetween(null, key).compareTo(key) < 0);
            assertTrue(generateKeyBetween(key, null).compareTo(key) > 0);
        }
    }

    private static BigInteger integerRank(String key) {
        int h = HEADS.indexOf(key.charAt(0));
        int width = Math.abs(2 * h - 51) / 2 + 1;
        assertEquals(width + 1, key.length(), "expected integer key");
        BigInteger offset = BigInteger.ZERO, value = BigInteger.ZERO, radix = BigInteger.valueOf(62);
        for (int i = 0; i < h; i++) offset = offset.add(radix.pow(Math.abs(2 * i - 51) / 2 + 1));
        for (int i = 1; i < key.length(); i++) value = value.multiply(radix).add(BigInteger.valueOf(DIGITS.indexOf(key.charAt(i))));
        return offset.add(value);
    }

    private static String randomKey(Random random) {
        int h = random.nextInt(52);
        int width = Math.abs(2 * h - 51) / 2 + 1;
        StringBuilder key = new StringBuilder().append(HEADS.charAt(h));
        for (int i = 0; i < width; i++) key.append(DIGITS.charAt(random.nextInt(62)));
        int fractionalLength = random.nextInt(80);
        for (int i = 0; i < fractionalLength; i++) key.append(DIGITS.charAt(i == fractionalLength - 1 ? 1 + random.nextInt(61) : random.nextInt(62)));
        if (key.toString().equals(MIN)) key.append('V');
        return key.toString();
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
