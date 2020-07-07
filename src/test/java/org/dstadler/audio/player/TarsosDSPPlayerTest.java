package org.dstadler.audio.player;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class TarsosDSPPlayerTest {
    private static final File SAMPLE_FILE = new File("src/test/resources/1-second-of-silence.mp3");

    @Test
    public void testSetOption() throws IOException {
        try (TarsosDSPPlayer player = new TarsosDSPPlayer(new ByteArrayInputStream("test".getBytes()))) {
            player.setOptions("");
            player.setOptions(null);
            player.setOptions("1.0");
        }
    }

    @Test
    public void testPlay() throws IOException {
        try (InputStream stream = new FileInputStream(SAMPLE_FILE)) {
            try (TarsosDSPPlayer player = new TarsosDSPPlayer(stream)) {
                player.setOptions("2.0");
                player.play();
            }
        }
    }

    @Test
    public void testCloseTwice() throws IOException {
        try (TarsosDSPPlayer player = new TarsosDSPPlayer(new ByteArrayInputStream("test".getBytes()))) {
            player.close();
            //noinspection RedundantExplicitClose
            player.close();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testException() throws IOException {
        try (TarsosDSPPlayer player = new TarsosDSPPlayer(new ByteArrayInputStream("test".getBytes()))) {
            player.setOptions("-0.1");
        }
    }
}