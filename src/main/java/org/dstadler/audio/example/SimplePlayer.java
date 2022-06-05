package org.dstadler.audio.example;

import org.dstadler.audio.player.AudioPlayer;
import org.dstadler.audio.player.AudioSPIPlayer;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.util.SuppressForbidden;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.dstadler.audio.buffer.Chunk.CHUNK_SIZE;

/**
 * A very simple commandline audio player to show usage
 * of some of the components that are contained in this project.
 *
 * Here you can only specify a local filename and seek is not possible,
 * look at {@link ExamplePlayer} for a slightly more advanced example.
 */
public class SimplePlayer {
    private final static Logger log = LoggerFactory.make();

    @SuppressForbidden(reason = "Uses System.exit()")
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: SimplePlayer <file>\n\nBut had: " + args.length + " arguments");
            System.exit(1);
        }

        LoggerFactory.initLogging();

        run(args[0]);
    }

    private static void run(String file) {
        log.info("Playing file " + file);

        try (final InputStream input = new BufferedInputStream(Files.newInputStream(Paths.get(file)), CHUNK_SIZE)) {
            AudioPlayer player = createPlayer(input);

            //player.setOptions("");

            player.play();
        } catch (Throwable e) {
            log.log(Level.WARNING, "Caught unexpected exception", e);
        }

        log.info("Waiting for player to stop");
    }

    private static AudioPlayer createPlayer(InputStream inputStream) throws IOException {
        // any of the implementations will play .mp3 streams
        // the SPI-based one can play OggVorbis as well

//        return new TarsosDSPPlayer(inputStream);
        return new AudioSPIPlayer(inputStream);
//        return new JLayerPlayer(inputStream);
    }
}
