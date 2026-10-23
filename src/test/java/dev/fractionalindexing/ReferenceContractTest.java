// SPDX-License-Identifier: CC0-1.0
package dev.fractionalindexing;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static dev.fractionalindexing.FractionalIndexing.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Reference-contract tests added during independent review. Expected values were produced by the
 * pinned rocicorp reference (reference/rocicorp/index.mjs, commit 648d2e6) and verified to diverge
 * from it only where UPSTREAM.md declares a difference. JS-side behaviors cited in comments were
 * confirmed by running the reference directly.
 */
class ReferenceContractTest {
    private static final String MIN = "A" + repeat('0', 26);
    private static final String MAX = repeat('z', 27);
    private static final int UNBOUNDED = 1_000_000;

    /**
     * UPSTREAM.md differences 1-4. The reference swaps reversed single boundaries
     * (generateKeyBetween("a1","a0") => "a0V"), accepts non-base62 fractions
     * (generateKeyBetween("a0\u00e9", null) => "a1"), skips validation for n == 0
     * (generateNKeysBetween("bogus!","####",0) => []) and returns the reserved minimum integer M
     * for (null, "A"+25*"0"+"1") which it then rejects as an input. Java behaves as documented.
     */
    @Test
    void documentedDifferencesBehaveAsDeclared() {
        assertThrows(IllegalArgumentException.class, () -> generateKeyBetween("a1", "a0"));
        assertThrows(IllegalArgumentException.class, () -> generateKeyBetween("a0", "a0"));
        assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween("a1", "a0", 3));
        // The reference's batch function with reversed bounds returns unsorted keys
        // (["a0U","a0S","a0O","a0M","a0K"] for ("a0V","a0G",5)); Java rejects them instead.
        assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween("a0V", "a0G", 5));
        assertThrows(IllegalArgumentException.class, () -> generateKeyBetween("a0\u00e9", null));
        assertThrows(IllegalArgumentException.class, () -> generateKeyBetween("a0\n", null));
        assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween("bogus!", "####", 0));
        assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, -1));
        // Difference 4: the reference returns M for (null, R); Java returns a usable key below R.
        assertEquals(MIN + "V", generateKeyBetween(null, "A" + repeat('0', 25) + "1"));
        assertEquals("A" + repeat('0', 25) + "1", generateKeyBetween(MIN + "V", null));
    }

    /**
     * Prepends below the reserved minimum integer, starting from M + "1". Checkpoints are the
     * reference's exact outputs at the given depths. The walk uses an explicit length limit so it
     * can pass the default 128-character boundary; within the boundary the default API must agree,
     * beyond it the default API must reject rather than truncate or misorder.
     */
    @Test
    void prependChainBelowTheReservedMinimumMatchesReferenceCheckpoints() {
        int[] depths = {1, 2, 3, 4, 5, 10, 50, 100, 500, 1000};
        char[] tails = {'V', 'G', '8', '4', '2', '4', 'G', '4', 'G', '4'};
        int[] zeroCounts = {27, 27, 27, 27, 27, 28, 35, 43, 110, 193};
        String current = MIN + "1";
        int checkpoint = 0;
        for (int depth = 1; depth <= 1000; depth++) {
            current = generateKeyBetween(null, current, UNBOUNDED);
            if (checkpoint < depths.length && depth == depths[checkpoint]) {
                assertEquals("A" + repeat('0', zeroCounts[checkpoint]) + tails[checkpoint],
                        current, "prepend chain depth " + depth);
                checkpoint++;
            }
            assertTrue(current.compareTo(MIN) > 0, "key must stay above the reserved minimum");
            if (current.length() <= 128) {
                String expected = generateKeyBetween(null, current, UNBOUNDED);
                if (expected.length() <= 128) {
                    assertEquals(expected, generateKeyBetween(null, current));
                } else {
                    final String inBounds = current;
                    assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(null, inBounds));
                }
            } else {
                assertTrue(current.length() > 128);
                final String tooLong = current;
                assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(null, tooLong));
            }
        }
        assertEquals(depths.length, checkpoint);
        for (int depth = 0; depth < 20_000; depth++) {
            String next = generateKeyBetween(null, current, UNBOUNDED);
            assertTrue(next.compareTo(current) < 0);
            assertTrue(next.compareTo(MIN) > 0);
            assertTrue(generateKeyBetween(next, null, UNBOUNDED).compareTo(next) > 0);
            current = next;
        }
    }

    /** Appends above the largest integer: reference checkpoints, then a Java-only walk. */
    @Test
    void appendChainAboveTheLargestIntegerMatchesReferenceCheckpoints() {
        int[] depths = {1, 2, 3, 4, 5, 10, 100, 1000};
        String[] expected = {MAX + "V", MAX + "l", MAX + "t", MAX + "x", MAX + "z",
                MAX + "zz", MAX + repeat('z', 20), MAX + repeat('z', 200)};
        String current = MAX;
        int checkpoint = 0;
        for (int depth = 1; depth <= 1000; depth++) {
            current = generateKeyBetween(current, null, UNBOUNDED);
            if (checkpoint < depths.length && depth == depths[checkpoint]) {
                assertEquals(expected[checkpoint], current, "append chain depth " + depth);
                checkpoint++;
            }
            if (current.length() <= 128) {
                String nextExpected = generateKeyBetween(current, null, UNBOUNDED);
                if (nextExpected.length() <= 128) {
                    assertEquals(nextExpected, generateKeyBetween(current, null));
                } else {
                    final String inBounds = current;
                    assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(inBounds, null));
                }
            } else {
                final String tooLong = current;
                assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(tooLong, null));
            }
        }
        assertEquals(depths.length, checkpoint);
        for (int depth = 0; depth < 20_000; depth++) {
            String next = generateKeyBetween(current, null, UNBOUNDED);
            assertTrue(next.compareTo(current) > 0);
            current = next;
        }
    }

    /** Balanced-batch outputs at length transitions, reserved-integer neighborhoods and the top end. */
    @Test
    void trickyBatchSplitsMatchReferenceExactly() {
        assertEquals(Arrays.asList("Zz8", "ZzG", "ZzO", "ZzV", "Zzd", "Zzl", "Zzt"),
                generateNKeysBetween("Zz", "a0", 7));
        assertEquals(Arrays.asList("a04", "a08", "a0C", "a0G", "a0K", "a0O"),
                generateNKeysBetween("a0", "a0V", 6));
        assertEquals(Arrays.asList(
                        MIN + "X", MIN + "Z", MIN + "d", MIN + "h", MIN + "k",
                        MIN + "m", MIN + "o", MIN + "s", MIN + "w"),
                generateNKeysBetween(MIN + "V", MIN + "z", 9));
        assertEquals(Arrays.asList(
                        MAX + "l", MAX + "t", MAX + "x", MAX + "z", MAX + "zV"),
                generateNKeysBetween(MAX + "V", null, 5));
        List<String> belowZ0 = generateNKeysBetween(null, "Z0", 130);
        assertEquals(130, belowZ0.size());
        assertEquals("Yxu", belowZ0.get(0));
        assertEquals("Yzy", belowZ0.get(128));
        assertEquals("Yzz", belowZ0.get(129), "last key must touch the Z-boundary from below");
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }
}
