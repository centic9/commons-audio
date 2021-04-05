package org.dstadler.audio.player;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assume;
import org.junit.Test;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class TarsosDSPPlayerTest {
    private static final File SAMPLE_FILE = new File("src/test/resources/1-second-of-silence.mp3");
    private static final File BIN_FILE = new File("src/test/resources/test.bin");

    @Test
    public void testSetOption() throws IOException {
        try (TarsosDSPPlayer player = new TarsosDSPPlayer(new ByteArrayInputStream("test".getBytes()))) {
            player.setOptions("");
            player.setOptions(null);
            player.setOptions("1.0");
        }
    }

    @Test
    public void testPlay() throws IOException, UnsupportedAudioFileException {
        try (InputStream stream = new FileInputStream(SAMPLE_FILE)) {
            try (TarsosDSPPlayer player = new TarsosDSPPlayer(stream)) {
                player.setOptions("2.0");
                player.play();
            } catch (IllegalArgumentException e) {
                if(ExceptionUtils.getStackTrace(e).contains("No line matching interface SourceDataLine supporting format")) {
                    Assume.assumeNoException("No audio-device available", e);
                }

                throw e;
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

    @Test
    public void testGetAudioInputStreamMP3() throws IOException, UnsupportedAudioFileException {
        try (InputStream stream = new FileInputStream(SAMPLE_FILE)) {
            try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(stream)) {
                assertNotNull(audioStream);
            }
        }
    }

    @Test
    public void testGetAudioInputStreamBIN() throws IOException {
        try (InputStream stream = new FileInputStream(BIN_FILE)) {
            // fails because of "mark/reset not supported", the Java Audio System
            // relies on it to be available for some of the supported audio-formats!
            assertThrows(IOException.class, () -> AudioSystem.getAudioInputStream(stream));
        }
    }

    @Test
    public void testGetAudioInputStreamMarkSupported() throws IOException {
        try (InputStream stream = new BufferedInputStream(new FileInputStream(BIN_FILE), 4096)) {
            assertThrows(UnsupportedAudioFileException.class, () -> AudioSystem.getAudioInputStream(stream));
        }
    }
}