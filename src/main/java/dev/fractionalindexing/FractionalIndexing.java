// SPDX-License-Identifier: CC0-1.0
// Derived from rocicorp/fractional-indexing, commit 648d2e60705bb6c7786f5f071d4e3f46deb74067.
package dev.fractionalindexing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates case-sensitive, lexicographically sortable keys using rocicorp's default encoding.
 * Null boundaries denote the beginning/end of a list. Use String.compareTo for ordering.
 * This stateless class enforces configurable limits; persistence and concurrency belong to callers.
 */
public final class FractionalIndexing {
    /** Default maximum length of each input and generated key. */
    public static final int DEFAULT_MAX_KEY_LENGTH = 128;
    /** Default maximum number of keys returned by one batch call. */
    public static final int DEFAULT_MAX_BATCH_SIZE = 10_000;

    private static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String MIN_INTEGER = "A" + repeat('0', 26);

    private FractionalIndexing() {
    }

    /**
     * Generates a key with the default 128-character limit.
     *
     * @param left lower boundary, or null for the beginning
     * @param right upper boundary, or null for the end
     * @return a key strictly between the boundaries
     * @throws KeyLengthExceededException if an input or generated key exceeds the limit
     * @throws IllegalArgumentException if a key is invalid or left is not less than right
     */
    public static String generateKeyBetween(String left, String right) {
        return generateKeyBetween(left, right, DEFAULT_MAX_KEY_LENGTH);
    }

    /**
     * Generates a key with an explicit input/output length limit. No truncation is performed.
     *
     * @param left lower boundary, or null for the beginning
     * @param right upper boundary, or null for the end
     * @param maxKeyLength maximum characters per key, at least 2
     * @return a key strictly between the boundaries
     * @throws KeyLengthExceededException if an input or generated key exceeds the limit
     * @throws IllegalArgumentException if configuration or boundaries are invalid
     */
    public static String generateKeyBetween(String left, String right, int maxKeyLength) {
        validateMaxKeyLength(maxKeyLength);
        validateBounds(left, right, maxKeyLength);
        return between(left, right, maxKeyLength);
    }

    /**
     * Generates an ascending batch with the default length and count limits.
     *
     * @param left lower boundary, or null for the beginning
     * @param right upper boundary, or null for the end
     * @param count number of keys, including zero
     * @return a new list of distinct keys; no partial result is returned on failure
     * @throws KeyLengthExceededException if an input or generated key exceeds the limit
     * @throws IllegalArgumentException if boundaries or count are invalid
     */
    public static List<String> generateNKeysBetween(String left, String right, int count) {
        return generateNKeysBetween(left, right, count, DEFAULT_MAX_KEY_LENGTH, DEFAULT_MAX_BATCH_SIZE);
    }

    /**
     * Generates an ascending batch with explicit limits. Count is checked before list allocation.
     * Finite intervals use balanced allocation; unbounded intervals use consecutive integers.
     *
     * @param left lower boundary, or null for the beginning
     * @param right upper boundary, or null for the end
     * @param count number of keys, between zero and maxBatchSize inclusive
     * @param maxKeyLength maximum characters per input/output key, at least 2
     * @param maxBatchSize maximum keys in one call, at least 1
     * @return a new list of distinct keys; no partial result is returned on failure
     * @throws KeyLengthExceededException if an input or generated key exceeds the limit
     * @throws IllegalArgumentException if configuration, boundaries or count are invalid
     */
    public static List<String> generateNKeysBetween(String left, String right, int count,
                                                   int maxKeyLength, int maxBatchSize) {
        validateMaxKeyLength(maxKeyLength);
        if (maxBatchSize < 1) {
            throw new IllegalArgumentException("maxBatchSize must be >= 1");
        }
        if (count < 0 || count > maxBatchSize) {
            throw new IllegalArgumentException("count must be between 0 and maxBatchSize (" + maxBatchSize + ")");
        }
        validateBounds(left, right, maxKeyLength);
        List<String> result = new ArrayList<>(count);
        appendKeys(left, right, count, maxKeyLength, result);
        return result;
    }

    private static void validateMaxKeyLength(int maxKeyLength) {
        if (maxKeyLength < 2) {
            throw new IllegalArgumentException("maxKeyLength must be >= 2");
        }
    }

    private static void checkLength(int length, int maxKeyLength) {
        if (length > maxKeyLength) {
            throw new KeyLengthExceededException(maxKeyLength);
        }
    }

    private static void validateBounds(String left, String right, int maxKeyLength) {
        // Check both lengths before scanning either key or comparing the boundaries.
        if (left != null) checkLength(left.length(), maxKeyLength);
        if (right != null) checkLength(right.length(), maxKeyLength);
        if (left != null) {
            validateKey(left);
        }
        if (right != null) {
            validateKey(right);
        }
        if (left != null && right != null && left.compareTo(right) >= 0) {
            throw new IllegalArgumentException("left must be less than right");
        }
    }

    private static void validateKey(String key) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        int integerLength = integerLength(key.charAt(0));
        if (key.length() < integerLength || key.equals(MIN_INTEGER)) {
            throw new IllegalArgumentException("invalid order key: " + key);
        }
        for (int i = 1; i < key.length(); i++) {
            if (DIGITS.indexOf(key.charAt(i)) < 0) {
                throw new IllegalArgumentException("key contains a non-base62 character");
            }
        }
        if (key.length() > integerLength && key.endsWith("0")) {
            throw new IllegalArgumentException("fractional part must not end in 0");
        }
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }

    private static int integerLength(char head) {
        if (head >= 'a' && head <= 'z') {
            return head - 'a' + 2;
        }
        if (head >= 'A' && head <= 'Z') {
            return 'Z' - head + 2;
        }
        throw new IllegalArgumentException("invalid integer head: " + head);
    }

    private static String integerPart(String key) {
        return key.substring(0, integerLength(key.charAt(0)));
    }

    private static String between(String left, String right, int maxKeyLength) {
        String key = createKey(left, right, maxKeyLength);
        checkLength(key.length(), maxKeyLength);
        return key;
    }

    private static String createKey(String left, String right, int maxKeyLength) {
        if (left == null) {
            if (right == null) {
                return "a0";
            }
            String integer = integerPart(right);
            if (integer.equals(MIN_INTEGER)) {
                return integer + midpoint("", right.substring(integer.length()), integer.length(), maxKeyLength);
            }
            if (integer.length() < right.length()) {
                return integer;
            }
            String previous = decrement(integer);
            // The minimum integer is reserved: choose a valid fraction above it instead.
            return previous.equals(MIN_INTEGER) ? previous + midpoint("", null, previous.length(), maxKeyLength) : previous;
        }
        String leftInteger = integerPart(left);
        String leftFraction = left.substring(leftInteger.length());
        if (right == null) {
            String next = increment(leftInteger);
            return next == null ? leftInteger + midpoint(leftFraction, null, leftInteger.length(), maxKeyLength) : next;
        }
        String rightInteger = integerPart(right);
        if (leftInteger.equals(rightInteger)) {
            return leftInteger + midpoint(leftFraction, right.substring(rightInteger.length()), leftInteger.length(), maxKeyLength);
        }
        String next = increment(leftInteger);
        if (next != null && next.compareTo(right) < 0) {
            return next;
        }
        return leftInteger + midpoint(leftFraction, null, leftInteger.length(), maxKeyLength);
    }

    private static String increment(String integer) {
        char[] chars = integer.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int digit = DIGITS.indexOf(chars[i]);
            if (digit < 61) {
                chars[i] = DIGITS.charAt(digit + 1);
                return new String(chars);
            }
            chars[i] = '0';
        }
        char head = chars[0];
        if (head == 'z') {
            return null;
        }
        if (head == 'Z') {
            return "a0";
        }
        char nextHead = (char) (head + 1);
        return nextHead + repeat('0', integerLength(nextHead) - 1);
    }

    private static String decrement(String integer) {
        char[] chars = integer.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int digit = DIGITS.indexOf(chars[i]);
            if (digit > 0) {
                chars[i] = DIGITS.charAt(digit - 1);
                return new String(chars);
            }
            chars[i] = 'z';
        }
        char head = chars[0];
        if (head == 'a') {
            return "Zz";
        }
        char nextHead = (char) (head - 1);
        return nextHead + repeat('z', integerLength(nextHead) - 1);
    }

    // Iterative midpoint using offsets: no recursive stack or repeated suffix copies on long keys.
    private static String midpoint(String left, String right, int integerLength, int maxKeyLength) {
        int remaining = maxKeyLength - integerLength;
        StringBuilder prefix = new StringBuilder();
        int leftOffset = 0;
        int rightOffset = 0;
        while (true) {
            if (right != null) {
                int start = rightOffset;
                while (rightOffset < right.length()
                        && (leftOffset < left.length() ? left.charAt(leftOffset) : '0')
                        == right.charAt(rightOffset)) {
                    rightOffset++;
                    if (leftOffset < left.length()) {
                        leftOffset++;
                    }
                }
                if (rightOffset - start > remaining - prefix.length()) {
                    throw new KeyLengthExceededException(maxKeyLength);
                }
                prefix.append(right, start, rightOffset);
            }
            // Every exit/continuation appends one more digit; reject before allocating it.
            if (prefix.length() >= remaining) {
                throw new KeyLengthExceededException(maxKeyLength);
            }
            int low = leftOffset == left.length() ? 0 : DIGITS.indexOf(left.charAt(leftOffset));
            int high = right == null ? 62 : DIGITS.indexOf(right.charAt(rightOffset));
            if (high - low > 1) {
                return prefix.append(DIGITS.charAt((low + high + 1) / 2)).toString();
            }
            if (right != null && right.length() - rightOffset > 1) {
                return prefix.append(right.charAt(rightOffset)).toString();
            }
            prefix.append(DIGITS.charAt(low));
            if (leftOffset < left.length()) {
                leftOffset++;
            }
            right = null;
        }
    }

    private static void appendKeys(String left, String right, int count, int maxKeyLength, List<String> result) {
        if (count == 0) {
            return;
        }
        if (count == 1) {
            result.add(between(left, right, maxKeyLength));
        } else if (right == null) {
            for (int i = 0; i < count; i++) {
                left = between(left, null, maxKeyLength);
                result.add(left);
            }
        } else if (left == null) {
            int start = result.size();
            for (int i = 0; i < count; i++) {
                right = between(null, right, maxKeyLength);
                result.add(right);
            }
            Collections.reverse(result.subList(start, result.size()));
        } else {
            int middle = count / 2;
            String key = between(left, right, maxKeyLength);
            appendKeys(left, key, middle, maxKeyLength, result);
            result.add(key);
            appendKeys(key, right, count - middle - 1, maxKeyLength, result);
        }
    }
}
