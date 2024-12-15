package org.dstadler.audio.player;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class AudioSPIPlayerTest {
    private static final File SAMPLE_FILE = new File("src/test/resources/1-second-of-silence.mp3");

    @Test
    public void testSetOption() throws IOException {
        try (InputStream stream = new FileInputStream(SAMPLE_FILE)) {
            try (AudioSPIPlayer player = new AudioSPIPlayer(stream)) {
                player.setOptions("");
                player.setOptions(null);
                player.setOptions("1.0");
            }
        }
    }

    @Test
    public void testPlay() throws IOException {
        try (InputStream stream = new FileInputStream(SAMPLE_FILE)) {
            try (AudioSPIPlayer player = new AudioSPIPlayer(stream)) {
                player.play();
            }
        } catch (IllegalArgumentException e) {
            if(ExceptionUtils.getStackTrace(e).contains("No line matching interface SourceDataLine supporting format")) {
                Assumptions.abort("No audio-device available\n" + ExceptionUtils.getStackTrace(e));
            }

            throw e;
        }
    }

    @Test
    public void testCloseTwice() throws IOException {
        try (InputStream stream = new FileInputStream(SAMPLE_FILE)) {
            try (AudioSPIPlayer player = new AudioSPIPlayer(stream)) {
                player.close();
                //noinspection RedundantExplicitClose
                player.close();
            }
        }
    }

    @Test
    public void testException() {
        assertThrows(IOException.class, () -> {
            try (AudioSPIPlayer player = new AudioSPIPlayer(new ByteArrayInputStream("test".getBytes()))) {
                player.setOptions("-0.1");
            }
        });
    }
}
