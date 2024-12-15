package org.dstadler.audio.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RuntimeInterruptedExceptionTest {
    @Test
    public void test() {
        RuntimeInterruptedException ex = new RuntimeInterruptedException(new IllegalStateException());
        assertNotNull(ex.getCause());
        assertNotSame(ex, ex.getCause());
    }
}
