package org.dstadler.audio.example;

import org.dstadler.audio.buffer.Chunk;
import org.dstadler.audio.buffer.RangeDownloadingBuffer;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.util.SuppressForbidden;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple commandline audio player to show usage
 * of some of the components that are contained in this project.
 *
 * You can specify a filename, a local file-url or a remote URL of a
 * download-able audio-stream.
 */
public class ExamplePlayer {
    private final static Logger log = LoggerFactory.make();

    // test-options, set to true to enable some features below
    private final static boolean SEEK = false;
    private final static boolean TEMPO_ADJUST = false;

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

        // The ring-buffer handles decoupling of downloading the audio and playing.
        // It will buffer up to chunkSize*bufferedChunks bytes in memory and will
        // download more data whenever needed
        RangeDownloadingBuffer buffer = new RangeDownloadingBuffer(url, "", null, 3000,
                Chunk.CHUNK_SIZE, p -> null);

        // play audio in a separate thread
        AudioWriter audioWriter = new AudioWriter(buffer, () -> shouldStop = true, () -> shouldStop);
        Thread writer = new Thread(audioWriter, "Writer thread");
        writer.start();

        int seeked = -1;
        int count = 0;

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

                // just for testing seeking
                if (SEEK && seeked == -1) {
                    seeked = seek(buffer, audioWriter, 0.95);
                }

                // for testing adjust tempo on the fly
                if (TEMPO_ADJUST && count % 5 == 0) {
                    // for testing use tempo between 0.6 and 1.5
                    float tempo = 0.6f + ((count % 4) * 0.3f);
                    log.info("Adjusting tempo to " + tempo);
                    audioWriter.setTempo(tempo);
                }
            } catch (/*IOException |*/ InterruptedException e) {
                log.log(Level.WARNING, "Caught unexpected exception", e);
            }

            count++;
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

}
