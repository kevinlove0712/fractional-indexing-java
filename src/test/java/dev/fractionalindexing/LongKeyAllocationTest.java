// SPDX-License-Identifier: CC0-1.0
package dev.fractionalindexing;

import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static dev.fractionalindexing.FractionalIndexing.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Allocation regression, not a wall-clock benchmark. No management dependency in the library. */
class LongKeyAllocationTest {
    @Test
    void singleAndBatchAllocationsScaleLinearlyWithKeyLength() throws Exception {
        Object bean = ManagementFactory.getThreadMXBean();
        Class<?> allocationBean;
        try {
            allocationBean = Class.forName("com.sun.management.ThreadMXBean");
        } catch (ClassNotFoundException unavailable) {
            assumeTrue(false, "Thread allocation counters are unavailable on this JVM");
            return;
        }
        assumeTrue(allocationBean.isInstance(bean), "Thread allocation counters are unavailable");
        assumeTrue((Boolean) allocationBean.getMethod("isThreadAllocatedMemorySupported").invoke(bean));
        boolean wasEnabled = (Boolean) allocationBean.getMethod("isThreadAllocatedMemoryEnabled").invoke(bean);
        Method enable = allocationBean.getMethod("setThreadAllocatedMemoryEnabled", boolean.class);
        Method allocated = allocationBean.getMethod("getThreadAllocatedBytes", long.class);
        if (!wasEnabled) enable.invoke(bean, true);
        try {
            for (int count : new int[] {1, 3}) {
                String warmup = left(1000);
                for (int i = 0; i < 100; i++) generate(warmup, count);
                long small = measure(bean, allocated, 10_000, count);
                long large = measure(bean, allocated, 100_000, count);
                // 10x input must not cause quadratic (~100x) allocation. Leave room for JVM variation.
                assertTrue(large <= small * 15 + 65_536,
                        "superlinear allocation: small=" + small + ", large=" + large + ", count=" + count);
            }
        } finally {
            if (!wasEnabled) enable.invoke(bean, false);
        }
    }

    private static long measure(Object bean, Method allocated, int length, int count) throws Exception {
        String left = left(length);
        long thread = Thread.currentThread().getId();
        long before = (Long) allocated.invoke(bean, thread);
        List<String> keys = generate(left, count);
        long bytes = (Long) allocated.invoke(bean, thread) - before;
        assertTrue(bytes >= 0, "allocation counter must be available");
        // Broad per-character budget catches suffix copying without asserting platform-specific byte counts.
        assertTrue(bytes < (long) length * count * 128 + 65_536,
                "excessive allocation: length=" + length + ", count=" + count + ", bytes=" + bytes);
        assertEquals(count, keys.size());
        String previous = left;
        for (String key : keys) {
            assertTrue(previous.compareTo(key) < 0);
            assertTrue(key.compareTo("a1") < 0);
            previous = key;
        }
        if (count == 1) assertEquals(left + "V", keys.get(0));
        return bytes;
    }

    private static List<String> generate(String left, int count) {
        return count == 1 ? Collections.singletonList(generateKeyBetween(left, "a1", left.length() + 8))
                : generateNKeysBetween(left, "a1", count, left.length() + 8, 3);
    }

    private static String left(int length) {
        char[] chars = new char[length];
        Arrays.fill(chars, 'z');
        return "a0" + new String(chars);
    }
}
