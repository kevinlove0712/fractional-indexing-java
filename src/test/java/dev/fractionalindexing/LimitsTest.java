// SPDX-License-Identifier: CC0-1.0
package dev.fractionalindexing;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static dev.fractionalindexing.FractionalIndexing.*;
import static org.junit.jupiter.api.Assertions.*;

class LimitsTest {
    @Test
    void defaultRejectsLongInputIncludingEmptyBatches() {
        String key = "a0" + repeat('V', 127);
        assertEquals(129, key.length());
        assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(key, "a1"));
        assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween("a0", key));
        assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween(key, null, 0));
        assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween(null, key, 0));
    }

    @Test
    void acceptsExactLengthAndRejectsGeneratedOverflowWithoutTruncation() {
        String left = "a0" + repeat('z', 125);
        assertEquals(128, generateKeyBetween(left, "a1").length());
        String full = "a0" + repeat('z', 126);
        assertEquals(128, full.length());
        assertEquals("a1", generateKeyBetween(full, null));
        assertTrue(generateNKeysBetween(full, null, 0).isEmpty());
        assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(full, "a1"));
        assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween(full, "a1", 3));
        assertEquals(full + "V", generateKeyBetween(full, "a1", 129));
    }

    @Test
    void defaultBatchBoundaryIsInclusiveAndCheckedBeforeAllocation() {
        assertEquals(10_000, generateNKeysBetween(null, null, 10_000).size());
        assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, 10_001));
        assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, -1));
        assertEquals(10_001, generateNKeysBetween(null, null, 10_001, 128, 10_001).size());
    }

    @Test
    void customLimitsCanBeLoweredOrRaisedWithoutChangingDefaults() {
        assertEquals("a0", generateKeyBetween(null, null, 2));
        assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween("a0", "a1", 2));
        assertEquals("a0V", generateKeyBetween("a0", "a1", 3));
        assertEquals(Arrays.asList("a0G", "a0V", "a0l"), generateNKeysBetween("a0", "a1", 3, 3, 3));
        assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, 4, 128, 3));
        String longKey = "a0" + repeat('z', 254);
        assertEquals(longKey + "V", generateKeyBetween(longKey, "a1", 257));
        assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(longKey, "a1"));
        assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, 10_001));
    }

    @Test
    void validatesConfigurationEvenForEmptyBatches() {
        for (int max : new int[] {Integer.MIN_VALUE, -1, 0, 1}) {
            assertThrows(IllegalArgumentException.class, () -> generateKeyBetween(null, null, max));
            assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, 0, max, 1));
        }
        for (int max : new int[] {Integer.MIN_VALUE, -1, 0}) {
            assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, 0, 128, max));
        }
        assertTrue(generateNKeysBetween(null, null, 0, 2, 1).isEmpty());
    }

    @Test
    void limitsAlsoCoverIntegerTransitionsAndReservedMinimumFix() {
        assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween("az", null, 2));
        assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(null, "Z0", 2));
        assertEquals("b00", generateKeyBetween("az", null, 3));
        assertEquals("Yzz", generateKeyBetween(null, "Z0", 3));
        String first = "A" + repeat('0', 25) + "1";
        assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(null, first, 27));
        assertEquals(28, generateKeyBetween(null, first, 28).length());
        String max = repeat('z', 27);
        assertThrows(KeyLengthExceededException.class, () -> generateKeyBetween(max, null, 27));
        assertEquals(max + "V", generateKeyBetween(max, null, 28));
    }

    @Test
    void aBatchFailsIfAnyIntermediateKeyExceedsTheLimit() {
        assertEquals(Arrays.asList("ay", "az"), generateNKeysBetween("ax", null, 2, 2, 3));
        assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween("ax", null, 3, 2, 3));
        assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween(null, "Z2", 3, 2, 3));
        assertThrows(KeyLengthExceededException.class, () -> generateNKeysBetween("a0", "a1", 100, 3, 100));
        assertEquals("a1", generateKeyBetween("a0", null));
    }

    @Test
    void limitChecksDoNotChangeValidOutputAcrossAllAllocationPaths() {
        for (String[] bounds : Arrays.asList(new String[]{null, null}, new String[]{null, "a0"},
                new String[]{"a0", null}, new String[]{"a0", "a1"}, new String[]{"az", "b00"})) {
            for (int count : new int[] {0, 1, 2, 3, 100}) {
                List<String> expected = generateNKeysBetween(bounds[0], bounds[1], count, 256, 100);
                int length = Math.max(bounds[0] == null ? 2 : bounds[0].length(),
                        bounds[1] == null ? 2 : bounds[1].length());
                for (String key : expected) length = Math.max(length, key.length());
                assertEquals(expected, generateNKeysBetween(bounds[0], bounds[1], count, length, 100));
            }
        }
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
