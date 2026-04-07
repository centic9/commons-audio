package org.dstadler.audio.player;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class AudioUtilsTest {

    @Test
    public void testConvertToSupportedAudioPCM() {
        // PCM signed 16-bit little-endian is natively supported,
        // so the stream should be returned unchanged
        AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
        byte[] data = new byte[4 * 100];
        AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(data), pcmFormat, 100);

        AudioInputStream result = AudioUtils.convertToSupportedAudio(stream);
        assertSame(stream, result, "PCM audio should be returned as-is");
    }

    @Test
    public void testConvertToSupportedAudioPCMMono() {
        AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, 22050, 16, 1, 2, 22050, false);
        byte[] data = new byte[2 * 50];
        AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(data), pcmFormat, 50);

        AudioInputStream result = AudioUtils.convertToSupportedAudio(stream);
        assertSame(stream, result, "PCM mono audio should be returned as-is");
    }

    @Test
    public void testConvertToMonoAlreadyMono() {
        AudioFormat monoFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false);
        byte[] data = new byte[2 * 100];
        AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(data), monoFormat, 100);

        AudioInputStream result = AudioUtils.convertToMono(stream);
        assertSame(stream, result, "Mono audio should be returned as-is");
    }

    @Test
    public void testConvertToMonoStereoToMono() throws IOException {
        AudioFormat stereoFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
        byte[] data = new byte[4 * 100];
        AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(data), stereoFormat, 100);

        try (AudioInputStream result = AudioUtils.convertToMono(stream)) {
            assertNotSame(stream, result, "Stereo audio should be converted");
            assertEquals(1, result.getFormat().getChannels(), "Result should be mono");
            assertEquals(44100, result.getFormat().getSampleRate(), 0.01);
            assertEquals(16, result.getFormat().getSampleSizeInBits());
        }
    }

    @Test
    public void testConvertToMonoStereoToMonoDifferentSampleRate() throws IOException {
        AudioFormat stereoFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, 22050, 16, 2, 4, 22050, false);
        byte[] data = new byte[4 * 50];
        AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(data), stereoFormat, 50);

        try (AudioInputStream result = AudioUtils.convertToMono(stream)) {
            assertNotSame(stream, result);
            assertEquals(1, result.getFormat().getChannels());
            assertEquals(22050, result.getFormat().getSampleRate(), 0.01);
        }
    }

    @Test
    public void testConvertToMonoPreservesEncoding() throws IOException {
        AudioFormat stereoFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, 48000, 16, 2, 4, 48000, false);
        byte[] data = new byte[4 * 100];
        AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(data), stereoFormat, 100);

        try (AudioInputStream result = AudioUtils.convertToMono(stream)) {
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, result.getFormat().getEncoding());
            // Frame size should be halved: stereo 4 bytes → mono 2 bytes
            assertEquals(2, result.getFormat().getFrameSize());
        }
    }
}
