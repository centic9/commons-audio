package org.dstadler.audio.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedOutputStream;

import static org.dstadler.audio.buffer.Chunk.CHUNK_SIZE;

/**
 * Small wrapper around the two ends of a PipedInput/OutputStream.
 *
 * Encapsulates actions on the two objects and provides utility
 * methods to (re-)create the pipe, clear current buffers and
 * wait for all data to be consumed.
 *
 * These are usually necessary for building audio-related applicatoins:
 *
 * Waiting for consumption is done at the end of a song before starting
 * the next one.
 *
 * Clearing the buffer is done when seeking or stopping playback to
 * stop playing quickly.
 *
 * re-creating the pipe is done initially and whenever the player
 * needs to be changed.
 *
 * Uses {@link ClearablePipedInputStream} to allow clearing
 * the buffer.
 */
public class DataPipe implements AutoCloseable {
    private PipedOutputStream out;
    private ClearablePipedInputStream pipedIn;

    /**
     * @return True if the pipe was created and not closed.
     */
    public boolean isRunning() {
        return out != null;
    }

    /**
     * Provides the {@link InputStream} for reading data off of the pipe
     *
     * @return An InputStream which provides the data that was written to the pipe or null
     *          if the pipe was not created yet.
     */
    public InputStream getInput() {
        return pipedIn;
    }

    /**
     * Write the given bytes into the pipe so that they appear in
     * the InputStream.
     *
     * @param data The bytes to write
     *
     * @throws IOException If writing to the pipe fails.
     */
    public synchronized void write(byte[] data) throws IOException {
        out.write(data);
    }

    /**
     * Allows to either create or re-create the pipe.
     *
     * The pipe cannot be used before it is created initially.
     *
     * Later this method can be called again to shut down any remaining
     * parts of the pipe and create it again.
     *
     * Note: after re-creation, getInput() needs to be called again to
     *      fetch the current InputStream.
     *
     * @throws IOException If closing or creating the parts of the pipe fails.
     */
    public synchronized void createPipe() throws IOException {
        if (out != null) {
            out.close();
        }
        out = new PipedOutputStream();

        if (pipedIn != null) {
            pipedIn.close();
        }

        // allow buffer for a few chunks in the pipe to avoid flaky sound output
        pipedIn = new ClearablePipedInputStream(out, 5 * CHUNK_SIZE);
    }

    /**
     * Remove any remaining data from the pipe so that the InputStream
     * does not provide it any more.
     *
     * This also re-creates the pipe to have a clean state again afterwards
     * and avoid partly-received data.
     *
     * @return True if the pipe could be cleared, false if the pipe is closed
     *
     * @throws IOException If closing, clearing or creating the pipe fails
     */
    public boolean clearBuffer() throws IOException {
        if (pipedIn == null) {
            return false;
        }

        pipedIn.clearBuffer();

        // we also need to re-create the pipe as it is closed
        // when the AudioPlayer stops
        createPipe();

        return true;
    }

    /**
     * Wait until all data that is currently buffered in the InputStream
     * is consumed.
     *
     * @throws IOException  If waiting on the {@link ClearablePipedInputStream} fails
     * @throws InterruptedException If the thread is interrupted
     */
    public void waitAllConsumed() throws IOException, InterruptedException {
        if (pipedIn != null) {
            // make sure all bytes from the pipe are actually sent
            // onwards to the player
            pipedIn.waitAllConsumed();
        }
    }

    /**
     * Returns the number of bytes that can be read from this input
     * stream without blocking.
     *
     * @return the number of bytes that can be read from this input stream
     *         without blocking, or {@code 0} if this input stream has been
     *         closed by invoking its {@link #close()} method.
     *
     * @throws IOException  if an I/O error occurs.
     */
    public int available() throws IOException {
        if (pipedIn == null) {
            return 0;
        }

        return pipedIn.available();
    }

    /**
     * Close both sides of the pipe.
     *
     * @throws IOException If closing fails
     */
    public void close() throws IOException {
        if (out != null) {
            out.close();
            out = null;
        }

        if (pipedIn != null) {
            pipedIn.close();
            pipedIn = null;
        }
    }

    @Override
    public String toString() {
        return "DataPipe{" +
                "out=" + out +
                ", pipedIn=" + pipedIn +
                '}';
    }
}
