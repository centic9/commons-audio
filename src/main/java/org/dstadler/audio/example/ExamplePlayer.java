package org.dstadler.audio.example;

import org.dstadler.audio.buffer.Chunk;
import org.dstadler.audio.buffer.RangeDownloadingBuffer;
import org.dstadler.audio.player.AudioPlayer;
import org.dstadler.audio.player.TarsosDSPPlayer;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.dstadler.audio.buffer.Chunk.CHUNK_SIZE;

/**
 * A simple commandline audio player to show usage
 * of some of the components that are contained in this project.
 *
 * You can specify a filename, a local file-url or a remote URL of a
 * download-able audio-stream.
 */
public class ExamplePlayer {
    private final static Logger log = LoggerFactory.make();

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            System.err.println("Usage: ExamplePlayer <url>");
            System.exit(1);
        }

        run(args[0]);
    }

    private static void run(String url) throws IOException, InterruptedException {
        log.info("Playing file " + url);

        // the ring-buffer handles decoupling of downloading the audio and playing it
        // it will buffer up to chunkSize*bufferedChunks bytes in memory and will
        // download more data whenever needed
        RangeDownloadingBuffer buffer = new RangeDownloadingBuffer(url, "", null, 1000,
                Chunk.CHUNK_SIZE, p -> null);

        // play audio in a separate thread
        AtomicBoolean shouldStop = new AtomicBoolean();
        Thread writer = new Thread(new AudioWriter(shouldStop, buffer), "Writer thread");
        writer.start();

        // then read and populate the buffer until we have read everything
        while (!buffer.empty()) {
            try {
                // this just ensures that we fill the buffer in parallel
                // to the writer thread so that playing audio never runs
                // out of data to play
                int fetched = buffer.fillupBuffer(15, 50);
                if (fetched > 0) {
                    log.info("Downloaded " + fetched + " chunks, having buffer: " + buffer);
                }

                Thread.sleep(1000);
            } catch (/*IOException |*/ InterruptedException e) {
                log.log(Level.WARNING, "Caught unexpected exception", e);
            }
        }

        // indicate that no more data is read and thus playing should stop
        shouldStop.set(true);
        writer.join();
    }

    /**
     * A thread which fetches data from the buffer and populates
     * a PipedInputStream that is always filled to let the actual
     * audio-player take data whenever needed.
     */
    private static class AudioWriter implements Runnable {
        private final PipedOutputStream out;
        private final AtomicBoolean shouldStop;
        private final RangeDownloadingBuffer buffer;
        private InputStream inputStream;

        public AudioWriter(AtomicBoolean shouldStop, RangeDownloadingBuffer buffer) {
            this.shouldStop = shouldStop;
            this.buffer = buffer;
            out = new PipedOutputStream();
        }
        @Override
        public void run() {
            try {
                // allow buffer for a few chunks in the pipe to avoid flaky sound output
                // some Audio classes try to use mark()/reset(), thus we use a wrapping BufferedInputStream()
                // here to provide this functionality as PipedInputStream does not support it
                inputStream = new BufferedInputStream(new PipedInputStream(out, 5*CHUNK_SIZE), CHUNK_SIZE);

                //player.setOptions("");
                Thread playerThread = new Thread(new PlayerThread(), "Player thread");
                playerThread.setDaemon(true);
                playerThread.start();

                long chunks = writeLoop();

                log.info("Stopping playing after " + chunks + " chunks");
            } catch (IOException e) {
                log.log(Level.WARNING, "Caught unexpected exception", e);
            }
        }

        private long writeLoop() throws IOException {
            long chunks = 0;
            while (!shouldStop.get()) {
                // get the next chunk from the buffer, this might block if no more data is
                // available
                Chunk chunk = buffer.next();
                if (chunk == null) {
                    log.info("Buffer is closed, maybe we reached the end of the stream?");
                    break;
                }

                // pass on the chunk to the stream for playing
                out.write(chunk.getData());

                chunks++;
                if (chunks % 200 == 0) {
                    log.info("Writing " + chunk.size() + " bytes, having chunk number: " + chunks);
                }
            }
            return chunks;
        }

        /**
         * The audio-system in Java needs a thread for actually
         * running the player
         */
        private class PlayerThread implements Runnable {
            @Override
            public void run() {
                try {
                    AudioPlayer player = createPlayer(inputStream);

                    //player.setOptions("");

                    player.play();
                } catch (Throwable e) {
                    log.log(Level.WARNING, "Caught unexpected exception", e);
                }
            }
        }

        public static AudioPlayer createPlayer(InputStream inputStream) {
            // any of the implementations will play .mp3 streams
            return new TarsosDSPPlayer(inputStream);
//        return new MP3SPIPlayer(inputStream);
//        return new JLayerPlayer(inputStream);
        }
    }
}
