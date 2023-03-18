package org.dstadler.audio.example;

import org.dstadler.audio.buffer.Chunk;
import org.dstadler.audio.buffer.SeekableRingBuffer;
import org.dstadler.audio.util.ClearablePipedInputStream;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.dstadler.audio.buffer.Chunk.CHUNK_SIZE;

/**
 * A thread which fetches data from the buffer and populates
 * a PipedInputStream that is always filled to let the actual
 * audio-player take data whenever needed.
 *
 * This is part of the simple example audio player.
 */
class AudioWriter implements Runnable {
    private final static Logger log = LoggerFactory.make();

    private final SeekableRingBuffer<Chunk> buffer;
    private final Runnable stopper;
    private final BooleanSupplier shouldStop;

    private PipedOutputStream out;
    private ClearablePipedInputStream in;
    private PlayerThread player;

    public AudioWriter(SeekableRingBuffer<Chunk> buffer, Runnable stopper, BooleanSupplier shouldStop) throws IOException {
        this.buffer = buffer;
        this.stopper = stopper;
        this.shouldStop = shouldStop;
        createPipe();
    }

    private synchronized void createPipe() throws IOException {
        this.out = new PipedOutputStream();

        // configure enough buffer for a few chunks in the pipe to avoid flaky sound output
        in = new ClearablePipedInputStream(out, 5 * CHUNK_SIZE);
    }

    @Override
    public void run() {
        try {
            //player.setOptions("");

            player = new PlayerThread(in, stopper);
            Thread playerThread = new Thread(player, "Player thread");
            playerThread.setDaemon(true);
            playerThread.start();

            long chunks = writeLoop();

            log.info("Stopping playing after " + chunks + " chunks");

            // wait for all data to be read by the playing thread
            in.waitAllConsumed();
        } catch (IOException | InterruptedException e) {
            log.log(Level.WARNING, "Caught unexpected exception", e);
        }
    }

    private long writeLoop() throws IOException {
        long chunks = 0;
        while (!shouldStop.getAsBoolean()) {
            // get the next chunk from the buffer, this might block if no more data is
            // available
            Chunk chunk = buffer.next();
            if (chunk == null) {
                log.info("Buffer is closed, maybe we reached the end of the stream?");
                break;
            }

            log.fine("Write chunk " + chunk + " with " + chunk.getData().length + " bytes");

            // pass on the chunk to the stream for playing
            synchronized (this) {
                out.write(chunk.getData());
            }

            chunks++;
            if (chunks % 200 == 0) {
                log.info("Writing " + chunk.size() + " bytes, having chunk number: " + chunks);
            }
        }
        return chunks;
    }

    public void clearBuffer() throws IOException {
        in.clearBuffer();

        if (player != null) {
            // we also need to re-create the pipe as it is closed
            // when the AudioPlayer stops
            createPipe();

            // ask the player to restart to clear the buffer and start
            // playing data from the new position
            player.triggerRestart(in);
        }
    }
}
