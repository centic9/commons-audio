package org.dstadler.audio.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class RuntimeInterruptedExceptionTest {
    @Test
    public void test() {
        RuntimeInterruptedException ex = new RuntimeInterruptedException(new IllegalStateException());
        assertNotNull(ex.getCause());
        assertNotSame(ex, ex.getCause());
    }
}
