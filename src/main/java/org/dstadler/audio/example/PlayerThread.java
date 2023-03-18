package org.dstadler.audio.example;

import org.dstadler.audio.player.AudioPlayer;
import org.dstadler.audio.player.TarsosDSPPlayer;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.dstadler.audio.buffer.Chunk.CHUNK_SIZE;

/**
 * Runnable for a thread which runs the audio-player of the
 * Java audio-system in the example player implementation.
 *
 * Also automatically tries to restart playing
 */
public class PlayerThread implements Runnable {
    private final static Logger log = LoggerFactory.make();

    private InputStream inputStream;

    private final Runnable stopper;
    private volatile boolean restart = true;
    private AudioPlayer player;

    public PlayerThread(PipedInputStream in, Runnable stopper) {
        // some Audio classes try to use mark()/reset(), thus we use a wrapping BufferedInputStream()
        // here to provide this functionality as PipedInputStream does not support it
        this.inputStream = new BufferedInputStream(in, CHUNK_SIZE);
        this.stopper = stopper;
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

            stopper.run();
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

    private AudioPlayer createPlayer(InputStream inputStream) {
        // any of the implementations will play .mp3 streams
        // the SPI-based one can play OggVorbis as well
        return new TarsosDSPPlayer(inputStream);
//            return new AudioSPIPlayer(inputStream);
//            return new JLayerPlayer(inputStream);
    }
}
