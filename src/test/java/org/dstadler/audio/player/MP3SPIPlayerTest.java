package org.dstadler.audio.player;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MP3SPIPlayerTest {
    private static final File SAMPLE_FILE = new File("src/test/resources/1-second-of-silence.mp3");

    @Test
    public void testSetOption() throws IOException {
        try (InputStream stream = new FileInputStream(SAMPLE_FILE)) {
            try (MP3SPIPlayer player = new MP3SPIPlayer(stream)) {
                player.setOptions("");
                player.setOptions(null);
                player.setOptions("1.0");
            }
        }
    }

    @Test
    public void testPlay() throws IOException {
        try (InputStream stream = new FileInputStream(SAMPLE_FILE)) {
            try (MP3SPIPlayer player = new MP3SPIPlayer(stream)) {
                player.play();
            }
        } catch (IllegalArgumentException e) {
            if(ExceptionUtils.getStackTrace(e).contains("No line matching interface SourceDataLine supporting format")) {
                Assume.assumeNoException("No audio-device available", e);
            }

            throw e;
        }
    }

    @Test
    public void testCloseTwice() throws IOException {
        try (InputStream stream = new FileInputStream(SAMPLE_FILE)) {
            try (MP3SPIPlayer player = new MP3SPIPlayer(stream)) {
                player.close();
                //noinspection RedundantExplicitClose
                player.close();
            }
        }
    }

    @Test(expected = IOException.class)
    public void testException() throws IOException {
        try (MP3SPIPlayer player = new MP3SPIPlayer(new ByteArrayInputStream("test".getBytes()))) {
            player.setOptions("-0.1");
        }
    }
}
