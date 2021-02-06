package org.dstadler.audio.util;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * An extended {@link PipedInputStream} which supports
 * clearing the current buffer and waiting for the buffer
 * to be finished.
 *
 * These operations are useful when a PipedInput/OutputStream is
 * used for sending audio data to the Javva Audio System, see
 * e.g. the {@link org.dstadler.audio.example.ExamplePlayer} for
 * how this can be used.
 */
public class ClearablePipedInputStream extends PipedInputStream {
    public ClearablePipedInputStream(PipedOutputStream out, int pipeSize) throws IOException {
        super(out, pipeSize);
    }

    /**
     * Read all bytes that are currently available, i.e.
     * until available() returns 0.
     *
     * This method does not block.
     *
     * @throws IOException if an error occurs when calling available() or read()
     */
    public synchronized void clearBuffer() throws IOException {
        // Note: This method is synchronized to avoid possible
        // blocking read() if another thread tries to read
        // between calling available() and read() here.

        // read data until none are available any more
        while (available() > 0) {
            //noinspection ResultOfMethodCallIgnored
            read();
        }
    }

    /**
     * Wait until all data is consumed.
     *
     * This call will block until another thread has read all bytes
     * from the buffer.
     *
     * @throws IOException  If available() throws an exception
     * @throws InterruptedException If the thread is interrupted
     */
    public void waitAllConsumed() throws IOException, InterruptedException {
        while (available() > 0) {
            // tried to use wait() but the piped-stream only notifies readers
            // when data is becoming available, but we would like to wait
            // for data to have been consumed
            Thread.sleep(100);
        }
    }

    @Override
    public String toString() {
        return "ClearablePipedInputStream{" +
                "buffer=" + buffer.length +
                ", in=" + in +
                ", out=" + out +
                '}';
    }
}
