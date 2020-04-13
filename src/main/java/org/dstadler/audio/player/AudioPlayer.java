package org.dstadler.audio.player;

import java.io.IOException;

/**
 * Simple interface for providing different implementations
 * of audio playback functionality.
 */
public interface AudioPlayer extends AutoCloseable {

    void setOptions(String options);

    void play() throws IOException;

    @Override
    void close() throws IOException;
}
