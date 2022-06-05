package org.dstadler.audio.example;

import org.dstadler.audio.buffer.BlockingSeekableRingBuffer;
import org.dstadler.audio.buffer.CountingSeekableRingBuffer;
import org.dstadler.audio.buffer.CountingSeekableRingBufferImpl;
import org.dstadler.audio.buffer.RangeDownloadingBuffer;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.util.SuppressForbidden;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * A simple commandline audio player which plays live streams
 * This shows usage of some of the components that are contained
 * in this project.
 *
 * You should specify an URL of a live audio stream.
 */
public class ExampleLivePlayer {
    private final static Logger log = LoggerFactory.make();

    private static volatile boolean shouldStop = false;

    @SuppressForbidden(reason = "Uses System.exit()")
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            System.err.println("Usage: ExampleLivePlayer <live-url>");
            System.exit(1);
        }

        LoggerFactory.initLogging();

        run(args[0]);
    }

    private static void run(String url) throws IOException, InterruptedException {
        log.info("Playing Live-URL " + url);

        // The ring-buffer handles decoupling of downloading the audio and playing.
        // It will buffer up to chunkSize*bufferedChunks bytes in memory and will
        // download more data whenever needed
        CountingSeekableRingBuffer buffer = new CountingSeekableRingBufferImpl(new BlockingSeekableRingBuffer(3000));

        // play audio in a separate thread
        AudioWriter audioWriter = new AudioWriter(buffer, () -> shouldStop = true, () -> shouldStop);
        Thread writer = new Thread(audioWriter, "Writer thread");
        writer.start();

        //int seeked = -1;

        // then read and populate the buffer continuously
        try (StreamReader reader = new StreamReader(60_000, () -> shouldStop)) {
            reader.connectAndRead(url, buffer);
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
