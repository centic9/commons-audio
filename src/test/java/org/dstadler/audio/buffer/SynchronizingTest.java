package org.dstadler.audio.buffer;

import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.testing.ThreadTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * A special test which tries to trigger some synchronization problem
 * that did show up sometimes.
 */
public class SynchronizingTest {
    private final static Logger log = LoggerFactory.make();

    private static final String SAMPLE_FILE_URL = "file://" + new File("src/test/resources/10-minutes-of-silence.mp3").getAbsolutePath();

    private static volatile boolean shouldStop = false;
    private final AtomicReference<Exception> exc = new AtomicReference<>();

    @AfterEach
    public void tearDown() throws Exception {
        if (exc.get() != null) {
            throw exc.get();
        }

        ThreadTestHelper.waitForThreadToFinishSubstring("SyncTest");
        ThreadTestHelper.assertNoThreadLeft("Should not have any started threads still running", "SyncTest");
    }

    @Test
    public void test() throws Exception {
        log.info("Playing file " + SAMPLE_FILE_URL);
        RangeDownloadingBuffer buffer = new RangeDownloadingBuffer(SAMPLE_FILE_URL, "", null, 1000,
                Chunk.CHUNK_SIZE, p -> null);

        // one thread consumes the produces chunks
        Thread writer = new Thread(() -> {
            try {
                while (!shouldStop) {
                    Chunk chunk = buffer.next();
                    if (chunk == null) {
                        break;
                    }

                    Thread.sleep(20);
                }
            } catch (InterruptedException e) {
                exc.set(e);
            }
        }, "SyncTest: Writer thread");
        writer.start();

        // one thread constantly access buffer.size() and buffer.fill()
        Thread checkSize = new Thread(() -> {
            try {
                while (!shouldStop) {
                    int size = buffer.size();
                    int fill = buffer.fill();
                    float maxChunksBackwards = (fill - size);
                    if (maxChunksBackwards < 0) {
                        exc.set(new Exception("Had higher size '%d' than fill '%d' in the buffer, but this should not be possible: %s".formatted(
                                size, fill, buffer)));
                    }
                }
            } catch (Exception e) {
                exc.set(e);
            }
        }, "SyncTest: Check Size Thread");
        checkSize.start();

        // then read and populate the buffer until we have read everything
        while (!buffer.empty()) {
            try {
                int fetched = buffer.fillupBuffer(15, 50);
                if (fetched > 0) {
                    log.info("Downloaded " + fetched + " chunks, having buffer: " + buffer);
                }

                Thread.sleep(20);
            } catch (InterruptedException e) {
                exc.set(e);
            }
        }

        // indicate that no more data is read and thus playing should stop
        shouldStop = true;

        // wait for threads to stop
        writer.join();
        checkSize.join();
    }
}
