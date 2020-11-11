package org.dstadler.audio.player;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;

/**
 * Simple interface for providing different implementations
 * of audio playback functionality.
 */
public interface AudioPlayer extends AutoCloseable {

    void setOptions(String options);

    void play() throws IOException, UnsupportedAudioFileException;

    @Override
    void close() throws IOException;
}
