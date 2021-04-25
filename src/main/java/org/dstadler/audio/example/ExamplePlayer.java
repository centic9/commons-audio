package org.dstadler.audio.example;

import org.dstadler.audio.buffer.Chunk;
import org.dstadler.audio.buffer.RangeDownloadingBuffer;
import org.dstadler.audio.player.AudioPlayer;
import org.dstadler.audio.player.AudioSPIPlayer;
import org.dstadler.audio.util.ClearablePipedInputStream;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.util.SuppressForbidden;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
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

    private static volatile boolean shouldStop = false;

    @SuppressForbidden(reason = "Uses System.exit()")
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            System.err.println("Usage: ExamplePlayer <url>");
            System.exit(1);
        }

        LoggerFactory.initLogging();

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
        AudioWriter audioWriter = new AudioWriter(buffer);
        Thread writer = new Thread(audioWriter, "Writer thread");
        writer.start();

        //int seeked = -1;

        // then read and populate the buffer until we have read everything
        while (!buffer.empty() && !shouldStop) {
            try {
                // this just ensures that we fill the buffer in parallel
                // to the writer thread so that playing audio never runs
                // out of data to play
                int fetched = buffer.fillupBuffer(15, 50);
                if (fetched > 0) {
                    log.info("Downloaded " + fetched + " chunks, having buffer: " + buffer);
                }

                Thread.sleep(1000);

                /* just for testing seeking
                if (seeked == -1) {
                    seeked = seek(buffer, audioWriter, 0.95);
                }*/
            } catch (/*IOException |*/ InterruptedException e) {
                log.log(Level.WARNING, "Caught unexpected exception", e);
            }
        }

        // indicate that no more data is read and thus playing should stop
        shouldStop = true;
        writer.join();
    }

    @SuppressWarnings("unused")
    private static int seek(RangeDownloadingBuffer buffer, AudioWriter audioWriter, double percentage) throws IOException {
        log.info("Seeking to " + (percentage*100) + "%");

        int availableChunks = buffer.fill();
        int availableBackwardChunks = availableChunks - buffer.size();

        // compute where we need to seek to based on the available number of chunks
        // and the current read-position
        double nrOfChunks = percentage * availableChunks;
        int nrOfChunksRounded = (int)(percentage * availableChunks);

        // when we need to seek backwards, this will become negative
        final int chunksToSeek = nrOfChunksRounded - availableBackwardChunks;

        // ask the buffer to seek that man chunks forward or backwards
        log.info("Seeking " + chunksToSeek + " chunks in the buffer");
        int seeked = buffer.seek(chunksToSeek);

        log.info("Clearing piped-buffer");
        audioWriter.clearBuffer();

        log.info("Seeking " + seeked + " chunks, had request of " + chunksToSeek + " and " + nrOfChunksRounded +
                "/" + nrOfChunks + " chunks because of percentage " + percentage + " and available chunks: " + availableChunks +
                " and available backwards: " + availableBackwardChunks + ": " + buffer);
        return seeked;
    }

    /**
     * A thread which fetches data from the buffer and populates
     * a PipedInputStream that is always filled to let the actual
     * audio-player take data whenever needed.
     */
    private static class AudioWriter implements Runnable {
        private final RangeDownloadingBuffer buffer;

        private PipedOutputStream out;
        private ClearablePipedInputStream in;
        private PlayerThread player;

        public AudioWriter(RangeDownloadingBuffer buffer) throws IOException {
            this.buffer = buffer;
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

                player = new PlayerThread(in);
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
            while (!shouldStop) {
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

    /**
     * The audio-system in Java needs a thread for actually
     * running the player
     */
    private static class PlayerThread implements Runnable {
        private InputStream inputStream;

        private volatile boolean restart = true;
        private AudioPlayer player;

        public PlayerThread(PipedInputStream in) {
            // some Audio classes try to use mark()/reset(), thus we use a wrapping BufferedInputStream()
            // here to provide this functionality as PipedInputStream does not support it
            this.inputStream = new BufferedInputStream(in, CHUNK_SIZE);
        }

        @Override
        public void run() {
            try {
                while (restart) {
                    log.log(Level.INFO, "Starting player");
                    restart = false;

                    player = createPlayer(inputStream);

                    //player.setOptions("");

                    player.play();
                }
            } catch (Throwable e) {
                log.log(Level.WARNING, "Caught unexpected exception", e);

                shouldStop = true;
            }
        }

        public void triggerRestart(InputStream stream) throws IOException {
            if (player != null) {
                log.log(Level.INFO, "Restarting player");

                this.inputStream = stream;
                restart = true;

                player.close();
            }
        }

        private AudioPlayer createPlayer(InputStream inputStream) throws IOException {
            // any of the implementations will play .mp3 streams
            // the SPI-based one can play OggVorbis as well
//            return new TarsosDSPPlayer(inputStream);
            return new AudioSPIPlayer(inputStream);
//            return new JLayerPlayer(inputStream);
        }
    }
}
