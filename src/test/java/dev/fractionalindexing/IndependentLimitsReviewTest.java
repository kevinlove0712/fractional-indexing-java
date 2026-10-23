// SPDX-License-Identifier: CC0-1.0
package dev.fractionalindexing;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static dev.fractionalindexing.FractionalIndexing.*;
import static org.junit.jupiter.api.Assertions.*;

/** Limits are checked against external expected outputs, not outputs of a less restricted call. */
class IndependentLimitsReviewTest {
    private static final String HEADS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String MIN = "A" + repeat('0', 26);

    @Test
    void allIndependentReferenceVectorsObeyExactAndDefaultLengthLimits() throws Exception {
        int cases = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/independent-rocicorp-vectors.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isEmpty()) continue;
                String[] parts = line.split("\t", -1);
                String left = parts[1].equals("-") ? null : parts[1];
                String right = parts[2].equals("-") ? null : parts[2];
                boolean single = parts[0].equals("single");
                int count = Integer.parseInt(parts[3]);
                List<String> expected = parts[4].isEmpty() ? Collections.emptyList()
                        : Arrays.asList(parts[4].split(","));
                int required = Math.max(2, Math.max(left == null ? 0 : left.length(), right == null ? 0 : right.length()));
                for (String key : expected) required = Math.max(required, key.length());
                assertEquals(expected, call(single, left, right, count, required), "exact limit, vector " + cases);
                if (required > 2) {
                    final int tooSmall = required - 1;
                    assertThrows(KeyLengthExceededException.class, () -> call(single, left, right, count, tooSmall));
                }
                if (required <= 128) {
                    assertEquals(expected, defaults(single, left, right, count), "default limit, vector " + cases);
                } else {
                    assertThrows(KeyLengthExceededException.class, () -> defaults(single, left, right, count));
                }
                if (!single && count > 1) {
                    final int exactLength = required;
                    assertThrowsExactly(IllegalArgumentException.class,
                            () -> generateNKeysBetween(left, right, count, exactLength, count - 1));
                }
                cases++;
            }
        }
        assertEquals(28_350, cases);
    }

    @Test
    void everyIntegerHeadCanGenerateExactly128ButNot129ByDefault() {
        for (int h = 0; h < HEADS.length(); h++) {
            int integerLength = Math.abs(2 * h - 51) / 2 + 2;
            String integer = HEADS.charAt(h) + repeat('V', integerLength - 1);
            String prefix = integer + repeat('V', 126 - integerLength);
            assertEquals(prefix + "1V", generateKeyBetween(prefix + "1", prefix + "2"));
            assertEquals(128, (prefix + "1V").length());
            String full = prefix + "1z";
            assertEquals(128, full.length());
            assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(full, prefix + "2"));
            assertEquals(full + "V", generateKeyBetween(full, prefix + "2", 129));
            assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(null, full + "V"));
            assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(full + "V", null));
            assertTrue(generateNKeysBetween(null, full, 0).isEmpty());
            assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween(null, full + "V", 0));
            assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween(full + "V", null, 0));
        }
    }

    @Test
    void paddingAndExtremeIntegersRespect128And129IncludingGeneratedOverflow() {
        String right = "a0" + repeat('0', 125) + "1";
        assertEquals(128, right.length());
        assertEquals("a0", generateKeyBetween(null, right));
        assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween("a0", right));
        assertEquals("a0" + repeat('0', 126) + "V", generateKeyBetween("a0", right, 129));
        String minimumRight = MIN + repeat('0', 100) + "1";
        assertEquals(128, minimumRight.length());
        assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(null, minimumRight));
        assertEquals(MIN + repeat('0', 101) + "V", generateKeyBetween(null, minimumRight, 129));
        String maximumLeft = repeat('z', 128);
        assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(maximumLeft, null));
        assertEquals(maximumLeft + "V", generateKeyBetween(maximumLeft, null, 129));
    }

    @Test
    void extremeBatchesReachDefaultLengthLimitThenFailOnTheNextKey() {
        String maximum = repeat('z', 27);
        List<String> after = generateNKeysBetween(maximum, null, 505);
        assertOrdered(maximum, null, 505, after, 128);
        assertEquals(repeat('z', 128), after.get(504));
        assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween(maximum, null, 506));
        assertEquals(repeat('z', 128) + "V", generateNKeysBetween(maximum, null, 506, 129, 506).get(505));
        String firstInteger = "A" + repeat('0', 25) + "1";
        List<String> before = generateNKeysBetween(null, firstInteger, 606);
        assertOrdered(null, firstInteger, 606, before, 128);
        assertEquals(MIN + repeat('0', 100) + "1", before.get(0));
        assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween(null, firstInteger, 607));
        assertEquals(MIN + repeat('0', 101) + "V", generateNKeysBetween(null, firstInteger, 607, 129, 607).get(0));
        assertEquals(after, generateNKeysBetween(maximum, null, 505));
        assertEquals(before, generateNKeysBetween(null, firstInteger, 606));
    }

    @Test
    void finiteCapacityAndIntegerTransitionsFailWithoutReturningAPartialBatch() {
        List<String> full = generateNKeysBetween("a0", "a1", 61, 3, 62);
        assertOrdered("a0", "a1", 61, full, 3);
        String digits = "123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < 61; i++) assertEquals("a0" + digits.charAt(i), full.get(i));
        assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween("a0", "a1", 62, 3, 62));
        assertEquals(full, generateNKeysBetween("a0", "a1", 61, 3, 62));
        assertOrdered(null, null, 62, generateNKeysBetween(null, null, 62, 2, 63), 2);
        assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween(null, null, 63, 2, 63));
        assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween(null, "a1", 64, 2, 64));
        String first = "A" + repeat('0', 25) + "1";
        String second = "A" + repeat('0', 25) + "2";
        assertEquals(Collections.singletonList(first), generateNKeysBetween(null, second, 1, 27, 2));
        assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween(null, second, 2, 27, 2));
        assertEquals(Arrays.asList(MIN + "V", first), generateNKeysBetween(null, second, 2, 28, 2));
    }

    @Test
    void invalidCountsAndConfigurationsRejectBeforeAllocatingResults() {
        String oversized = "a0" + repeat('V', 127);
        for (int count : new int[] {Integer.MIN_VALUE, -1, 10_001, Integer.MAX_VALUE}) {
            assertThrowsExactly(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, count));
            assertThrowsExactly(IllegalArgumentException.class, () -> generateNKeysBetween("!", oversized, count));
            assertThrowsExactly(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, count, 2, 1));
        }
        for (int maxLength : new int[] {Integer.MIN_VALUE, -1, 0, 1}) {
            assertThrowsExactly(IllegalArgumentException.class, () -> generateKeyBetween(null, oversized, maxLength));
            assertThrowsExactly(IllegalArgumentException.class, () -> generateNKeysBetween(null, oversized, 0, maxLength, 1));
        }
        for (int maxBatch : new int[] {Integer.MIN_VALUE, -1, 0}) {
            assertThrowsExactly(IllegalArgumentException.class, () -> generateNKeysBetween(null, oversized, 0, 128, maxBatch));
        }
        // Maxima are limits, not allocation capacities: a small request remains small.
        assertEquals("a0", generateKeyBetween(null, null, Integer.MAX_VALUE));
        assertEquals(Arrays.asList("a0", "a1"), generateNKeysBetween(null, null, 2, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertTrue(generateNKeysBetween(null, null, 0, Integer.MAX_VALUE, Integer.MAX_VALUE).isEmpty());
        assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween("!", oversized, 0));
    }

    @Test
    void concurrentCustomLimitsCannotChangeOtherCalls() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> work = new ArrayList<>();
            String full = "a0" + repeat('z', 126);
            for (int worker = 0; worker < 8; worker++) {
                final int maxLength = worker % 2 == 0 ? 128 : 129;
                work.add(() -> {
                    for (int iteration = 0; iteration < 250; iteration++) {
                        if (maxLength == 129) {
                            assertEquals(full + "V", generateKeyBetween(full, "a1", maxLength));
                        } else {
                            assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(full, "a1", maxLength));
                        }
                        assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(full, "a1"));
                        assertThrowsExactly(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, 10_001));
                        assertEquals(Arrays.asList("a0", "a1"), generateNKeysBetween(null, null, 2, 2, 2));
                        assertThrowsExactly(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, 2, 2, 1));
                    }
                    return null;
                });
            }
            for (Future<Void> result : executor.invokeAll(work)) result.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<String> call(boolean single, String left, String right, int count, int maxLength) {
        return single ? Collections.singletonList(generateKeyBetween(left, right, maxLength))
                : generateNKeysBetween(left, right, count, maxLength, Math.max(1, count));
    }

    private static List<String> defaults(boolean single, String left, String right, int count) {
        return single ? Collections.singletonList(generateKeyBetween(left, right)) : generateNKeysBetween(left, right, count);
    }

    private static void assertOrdered(String left, String right, int count, List<String> keys, int maxLength) {
        assertEquals(count, keys.size());
        String previous = left;
        for (String key : keys) {
            assertTrue(previous == null || previous.compareTo(key) < 0);
            assertTrue(right == null || key.compareTo(right) < 0);
            assertTrue(key.length() <= maxLength);
            previous = key;
        }
    }

    private static String repeat(char c, int count) {
        char[] result = new char[count];
        Arrays.fill(result, c);
        return new String(result);
    }
}
