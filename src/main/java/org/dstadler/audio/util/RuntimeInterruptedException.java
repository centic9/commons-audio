package org.dstadler.audio.util;

/**
 * Helper to wrap InterruptedException as unchecked exception.
 */
public class RuntimeInterruptedException extends RuntimeException {
    public RuntimeInterruptedException(Throwable cause) {
        super(cause);
    }
}
