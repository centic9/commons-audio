package org.dstadler.audio.download;

import org.dstadler.commons.testing.MemoryLeakVerifier;
import org.dstadler.commons.testing.TestHelpers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class RangeDownloadFileTest {
    private static final File SAMPLE_FILE = new File("src/test/resources/test.bin");

    private static final long EXPECTED_LENGTH = SAMPLE_FILE.length();

    private final MemoryLeakVerifier verifier = new MemoryLeakVerifier();

    @AfterEach
    public void tearDown() {
        verifier.assertGarbageCollected();
    }

    @Test
    public void testLength() throws IOException {
        try (RangeDownload download = new RangeDownloadFile(SAMPLE_FILE)) {
            assertEquals(EXPECTED_LENGTH, download.getLength());

            verifier.addObject(download);
        }
    }

    @Test
    public void testLengthClosed() throws IOException {
        RangeDownload download = new RangeDownloadFile(SAMPLE_FILE);
        download.close();

        assertEquals(EXPECTED_LENGTH, download.getLength());

        verifier.addObject(download);
    }

    @Test
    public void testClosed() throws IOException {
        RangeDownload download = new RangeDownloadFile(SAMPLE_FILE);
        download.close();

        verifier.addObject(download);

        assertThrows(IllegalStateException.class, () -> download.readRange(0, 1));
    }

    @Test
    public void testReadRange() throws Exception {
        try (RangeDownload download = new RangeDownloadFile(SAMPLE_FILE)) {
            byte[] bytes = download.readRange(0, 200);
            assertEquals(200, bytes.length);
            assertEquals("imp", new String(Arrays.copyOfRange(bytes, 1, 4)));

            verifier.addObject(download);
        }
    }

    @Test
    public void testToString() throws IOException {
        try (RangeDownload download = new RangeDownloadFile(SAMPLE_FILE)) {
            TestHelpers.ToStringTest(download);

            assertTrue(download.toString().contains(Long.toString(EXPECTED_LENGTH)),
                    "Expected " + EXPECTED_LENGTH + ", but had: " + download);

            verifier.addObject(download);
        }
    }

    @Test
    public void readWithStartBeyondLength() throws IOException {
        try (RangeDownload download = new RangeDownloadFile(SAMPLE_FILE)) {
            verifier.addObject(download);

            assertThrows(IllegalArgumentException.class, () -> download.readRange(99999999L, 200));
        }
    }

    @Test
    public void readBeyondLength() throws IOException {
        try (RangeDownload download = new RangeDownloadFile(SAMPLE_FILE)) {
            byte[] bytes = download.readRange(download.getLength() - 100, 200);
            assertEquals(100, bytes.length);

            verifier.addObject(download);
        }
    }

    @Test
    public void readWithInvalidSizeAt0() throws IOException {
        try (RangeDownload download = new RangeDownloadFile(SAMPLE_FILE)) {
            verifier.addObject(download);

            assertThrows(IllegalArgumentException.class, () -> download.readRange(0, 0));
        }
    }

    @Test
    public void readWithInvalidSizeAt100() throws IOException {
        try (RangeDownload download = new RangeDownloadFile(SAMPLE_FILE)) {
            verifier.addObject(download);

            assertThrows(IllegalArgumentException.class, () -> download.readRange(100, 0));
        }
    }

    @Test
    public void readWithInvalidSizeAtMinus100() throws IOException {
        try (RangeDownload download = new RangeDownloadFile(SAMPLE_FILE)) {
            verifier.addObject(download);

            assertThrows(IllegalArgumentException.class, () -> download.readRange(-100, 10));
        }
    }
}
