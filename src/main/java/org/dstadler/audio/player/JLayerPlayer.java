package org.dstadler.audio.player;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import java.io.IOException;
import java.io.InputStream;

/**
 * AudioPlayer implementation which uses the simple player provided by the
 * jlayer libraries.
 */
public class JLayerPlayer implements AudioPlayer {
    private final Player player;

    public JLayerPlayer(InputStream inputStream) throws IOException {
        try {
            this.player = new Player(inputStream);
        } catch (JavaLayerException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void setOptions(String options) {
        // no options supported here
    }

    @Override
    public void play() throws IOException {
        try {
            player.play();
        } catch (JavaLayerException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() {
        player.close();
    }
}
