package org.dstadler.audio.player;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;

/**
 * Simple interface for providing different implementations
 * of audio playback functionality.
 *
 * The implementations will usually receive a link to the
 * audio-data during construction, the methods in this
 * interface are just used to start and stop playing
 * via play() and close().
 *
 * Some implementations may support setting options,
 * both initially and at runtime to adjust audio playback
 */
public interface AudioPlayer extends AutoCloseable {
    /**
     * Allows to set options which are usually
     * specific for the current implementation.
     *
     * Some implementations also allow to adjust
     * options at runtime by calling this method
     * with changed options while the player keeps
     * playing audio.
     *
     * @param options A string with options specific
     *                for the current implementation
     */
    void setOptions(String options);

    /**
     * Start playing audio.
     *
     * @throws IOException If retrieving or playing audio-data fails
     * @throws UnsupportedAudioFileException If the audio-data
     *          is in an unsupported format
     */
    void play() throws IOException, UnsupportedAudioFileException;

    /**
     * Stop playing audio and free any held resources.
     *
     * @throws IOException If an error occurs while stopping.
     */
    @Override
    void close() throws IOException;
}
