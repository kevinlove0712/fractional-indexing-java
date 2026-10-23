// SPDX-License-Identifier: CC0-1.0
package dev.fractionalindexing;

import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** A final length check alone must not replace rejection before output materialization. */
class RejectedKeyAllocationTest {
    @Test
    void oversizedOutputRejectsBeforeMaterializingTheFullKey() throws Exception {
        Object bean = ManagementFactory.getThreadMXBean();
        Class<?> allocationBean;
        try {
            allocationBean = Class.forName("com.sun.management.ThreadMXBean");
        } catch (ClassNotFoundException unavailable) {
            assumeTrue(false, "Thread allocation counters are unavailable on this JVM");
            return;
        }
        assumeTrue(allocationBean.isInstance(bean), "Thread allocation counters are unavailable");
        assumeTrue((Boolean) allocationBean.getMethod("isThreadAllocatedMemorySupported").invoke(bean),
                "Thread allocation counters are unsupported");
        boolean wasEnabled = (Boolean) allocationBean.getMethod("isThreadAllocatedMemoryEnabled").invoke(bean);
        Method enable = allocationBean.getMethod("setThreadAllocatedMemoryEnabled", boolean.class);
        Method allocated = allocationBean.getMethod("getThreadAllocatedBytes", long.class);
        if (!wasEnabled) enable.invoke(bean, true);
        try {
            char[] fraction = new char[100_000];
            Arrays.fill(fraction, 'z');
            String left = "a0" + new String(fraction);
            String expected = left + "V";
            for (int i = 0; i < 10; i++) {
                measure(bean, allocated, left, expected, true);
                measure(bean, allocated, left, expected, false);
            }
            long rejected = Long.MAX_VALUE;
            long successful = Long.MAX_VALUE;
            for (int i = 0; i < 5; i++) {
                rejected = Math.min(rejected, measure(bean, allocated, left, expected, true));
                successful = Math.min(successful, measure(bean, allocated, left, expected, false));
            }
            // Rejecting before the final append/string materialization saves substantial allocation.
            // A late final String length check has the same allocation as the successful call.
            assertTrue(rejected < successful * 0.8,
                    "over-limit output was materialized: rejected=" + rejected + ", successful=" + successful);
        } finally {
            if (!wasEnabled) enable.invoke(bean, false);
        }
    }

    private static long measure(Object bean, Method allocated, String left, String expected,
                                boolean reject) throws Exception {
        long thread = Thread.currentThread().getId();
        int limit = left.length() + (reject ? 0 : 1);
        String result = null;
        IllegalArgumentException failure = null;
        long before = (Long) allocated.invoke(bean, thread);
        try {
            result = FractionalIndexing.generateKeyBetween(left, "a1", limit);
        } catch (IllegalArgumentException exception) {
            failure = exception;
        }
        long bytes = (Long) allocated.invoke(bean, thread) - before;
        assertTrue(bytes >= 0, "allocation counter must be available");
        if (reject) {
            assertNotNull(failure, "an over-limit result must fail");
            assertEquals(KeyLengthExceededException.class, failure.getClass());
            assertNull(result);
        } else {
            assertNull(failure);
            assertEquals(expected, result);
        }
        return bytes;
    }
}
