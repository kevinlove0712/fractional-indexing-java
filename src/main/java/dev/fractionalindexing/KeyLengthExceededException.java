// SPDX-License-Identifier: CC0-1.0
package dev.fractionalindexing;

/** An input or generated key exceeds the configured limit; keys are never truncated. */
public final class KeyLengthExceededException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /**
     * @param maxKeyLength configured maximum key length
     */
    public KeyLengthExceededException(int maxKeyLength) {
        super("key length exceeds maxKeyLength (" + maxKeyLength + ")");
    }
}
