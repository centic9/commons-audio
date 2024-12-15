package org.dstadler.audio.player;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class JLayerPlayerTest {
    private static final File SAMPLE_FILE = new File("src/test/resources/1-second-of-silence.mp3");

    @Test
    public void testSetOption() throws IOException {
        try (InputStream stream = new FileInputStream(SAMPLE_FILE)) {
            try (JLayerPlayer player = new JLayerPlayer(stream)) {
                player.setOptions("");
                player.setOptions(null);
                player.setOptions("1.0");
            } catch (IOException e) {
                if(ExceptionUtils.getStackTrace(e).contains("Cannot create AudioDevice")) {
                    Assumptions.abort("No audio-device available\n" + ExceptionUtils.getStackTrace(e));
                }

                throw e;
            }
        }
    }

    @Test
    public void testPlay() throws IOException {
        try (InputStream stream = new FileInputStream(SAMPLE_FILE)) {
            try (JLayerPlayer player = new JLayerPlayer(stream)) {
                player.play();
            } catch (IOException e) {
                if(ExceptionUtils.getStackTrace(e).contains("Cannot create AudioDevice")) {
                    Assumptions.abort("No audio-device available\n" + ExceptionUtils.getStackTrace(e));
                }

                throw e;
            }
        }
    }

    @Test
    public void testCloseTwice() throws IOException {
        try (InputStream stream = new FileInputStream(SAMPLE_FILE)) {
            try (JLayerPlayer player = new JLayerPlayer(stream)) {
                player.close();
                //noinspection RedundantExplicitClose
                player.close();
            } catch (IOException e) {
                if(ExceptionUtils.getStackTrace(e).contains("Cannot create AudioDevice")) {
                    Assumptions.abort("No audio-device available\n" + ExceptionUtils.getStackTrace(e));
                }

                throw e;
            }
        }
    }

    @Test // (expected = IOException.class)
    public void testException() throws IOException {
        // JLayer does not fail on invalid data...
        try (JLayerPlayer player = new JLayerPlayer(new ByteArrayInputStream("test".getBytes()))) {
            player.play();
        } catch (IOException e) {
            if(ExceptionUtils.getStackTrace(e).contains("Cannot create AudioDevice")) {
                Assumptions.abort(("No audio-device available\n" + ExceptionUtils.getStackTrace(e)));
            }

            throw e;
        }
    }
}