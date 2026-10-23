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
import java.util.Random;
import static dev.fractionalindexing.FractionalIndexing.*;
import static org.junit.jupiter.api.Assertions.*;

class FractionalIndexingTest {
    @Test
    void matchesPinnedJavaScriptReference() throws Exception {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/rocicorp-vectors.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) continue;
                String[] parts = line.split("\t", -1);
                String left = parts[1].equals("-") ? null : parts[1];
                String right = parts[2].equals("-") ? null : parts[2];
                if (parts[0].equals("single")) {
                    assertEquals(parts[4], generateKeyBetween(left, right), "vector " + count);
                } else {
                    List<String> expected = parts[4].isEmpty() ? Collections.emptyList()
                            : Arrays.asList(parts[4].split(","));
                    assertEquals(expected, generateNKeysBetween(left, right, Integer.parseInt(parts[3])), "vector " + count);
                }
                count++;
            }
        }
        assertEquals(2457, count);
    }

    @Test
    void batchInsertionPreservesInputOrderAtAllPositions() {
        assertEquals(Arrays.asList("Zx", "Zy", "Zz"), generateNKeysBetween(null, "a0", 3));
        assertEquals(Arrays.asList("a0G", "a0V", "a0l"), generateNKeysBetween("a0", "a1", 3));
        assertEquals(Arrays.asList("a1", "a2", "a3"), generateNKeysBetween("a0", null, 3));
        assertTrue(generateNKeysBetween(null, null, 0).isEmpty());
    }

    @Test
    void rejectsInvalidKeysAndBounds() {
        for (String bad : Arrays.asList("", " ", "A1", "0", "a", "a00", "a!", "aé", "a0\n", "a😀", "A" + repeat('0', 26))) {
            assertThrows(IllegalArgumentException.class, () -> generateKeyBetween(bad, null), bad);
            assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween(null, bad, 0), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> generateKeyBetween("a1", "a0"));
        assertThrows(IllegalArgumentException.class, () -> generateKeyBetween("a0", "a0"));
        assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween("a1", "a0", 3));
        assertThrows(IllegalArgumentException.class, () -> generateNKeysBetween(null, null, -1));
    }

    @Test
    void handlesIntegerLengthTransitions() {
        assertEquals("Yzz", generateKeyBetween(null, "Z0"));
        assertEquals("Xzzz", generateKeyBetween(null, "Y00"));
        assertEquals("Zz", generateKeyBetween(null, "a0"));
        assertEquals("a0", generateKeyBetween("Zz", null));
        assertEquals("b00", generateKeyBetween("az", null));
        assertEquals("c000", generateKeyBetween("bzz", null));
    }

    @Test
    void remainsUsableAtMinimumAndMaximumIntegerBoundaries() {
        String minimum = "A" + repeat('0', 26);
        String right = "A" + repeat('0', 25) + "1";
        String key = generateKeyBetween(null, right);
        assertEquals(minimum + "V", key);
        assertTrue(key.compareTo(right) < 0);
        assertOrdered(null, right, generateNKeysBetween(null, right, 100));
        assertTrue(generateKeyBetween(null, key).compareTo(key) < 0);
        String maximum = repeat('z', 27);
        assertEquals(maximum + "V", generateKeyBetween(maximum, null));
        assertOrdered(maximum, null, generateNKeysBetween(maximum, null, 100));
    }

    @Test
    void reproducesLength32Thresholds() {
        assertEquals(175, firstLength32(true, 1));
        assertEquals(146, firstLength32(false, 1));
        assertEquals(88, firstLength32(true, 3));
        assertEquals(73, firstLength32(false, 3));
    }

    @Test
    void aMillionPrependsAndAppendsKeepShortKeys() {
        String left = "a0", right = "a0";
        for (int i = 0; i < 1_000_000; i++) {
            String nextLeft = generateKeyBetween(null, left);
            String nextRight = generateKeyBetween(right, null);
            assertTrue(nextLeft.compareTo(left) < 0);
            assertTrue(nextRight.compareTo(right) > 0);
            left = nextLeft;
            right = nextRight;
        }
        assertEquals("Wwory", left);
        assertEquals("d3B82", right);
    }

    @Test
    void bulkAllocationIsCompactAndSorted() {
        List<String> keys = generateNKeysBetween("a0", "a1", 100_000, 128, 100_000);
        assertEquals(100_000, keys.size());
        assertOrdered("a0", "a1", keys);
        assertEquals(6, keys.stream().mapToInt(String::length).max().getAsInt());
    }

    @Test
    void randomInsertMoveDeleteOperationsPreserveLogicalOrder() {
        List<String> keys = new ArrayList<>(generateNKeysBetween(null, null, 1000));
        Random random = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            int action = random.nextInt(3);
            if (action != 0 && keys.size() > 1) keys.remove(random.nextInt(keys.size()));
            if (action != 2) {
                int position = random.nextInt(keys.size() + 1);
                String left = position == 0 ? null : keys.get(position - 1);
                String right = position == keys.size() ? null : keys.get(position);
                List<String> added = generateNKeysBetween(left, right, action == 0 ? 3 : 1);
                assertOrdered(left, right, added);
                keys.addAll(position, added);
            }
            assertOrdered(null, null, keys);
        }
    }

    @Test
    void longFractionalKeysDoNotOverflowTheCallStack() {
        String left = "a0" + repeat('z', 10_000);
        String key = generateKeyBetween(left, "a1", left.length() + 1);
        assertTrue(left.compareTo(key) < 0);
        assertTrue(key.compareTo("a1") < 0);
        assertEquals(left + "V", key);
    }

    private static int firstLength32(boolean towardLeft, int count) {
        String left = "a0", right = "a1";
        for (int round = 1; round <= 1000; round++) {
            List<String> keys = generateNKeysBetween(left, right, count);
            assertOrdered(left, right, keys);
            if (keys.stream().anyMatch(k -> k.length() >= 32)) return round;
            if (towardLeft) right = keys.get(0); else left = keys.get(keys.size() - 1);
        }
        fail("threshold not reached");
        return -1;
    }

    private static void assertOrdered(String left, String right, List<String> keys) {
        String previous = left;
        for (String key : keys) {
            assertTrue(previous == null || previous.compareTo(key) < 0);
            assertTrue(right == null || key.compareTo(right) < 0);
            previous = key;
        }
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
