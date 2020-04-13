package org.dstadler.audio.player;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * Some general purpose helper methods for transcoding MP3 to WAV or
 * converting stereo audio data to mono.
 *
 * These mostly us the Java Audio System and the MP3 SPI provided by the
 * jlayer libraries.
 *
 * If you use convertToSupportedAudio() with MP3 input data you will
 * need the MP3SPI, e.g. via the following in build.gradle:
 *
 *     // MP3 SPI, used for playback
 *     compile group: 'com.googlecode.soundlibs', name: 'mp3spi', version: '1.9.5.4'
 *
 * For OggVorbis support, the following should work (untested!)
 *
 *     compile group: 'com.googlecode.soundlibs', name: 'vorbisspi', version: '1.0.3.3'
 */
public class AudioUtils {

    public static AudioInputStream convertToSupportedAudio(AudioInputStream audioStream) {
        AudioFormat format = audioStream.getFormat( );
        DataLine.Info info = new DataLine.Info(SourceDataLine.class,format);

        // If the format is not supported directly (i.e. if it is not PCM
        // encoded), then try to transcode it to PCM.
        if (!AudioSystem.isLineSupported(info)) {// This is the PCM format we want to transcode to.
            // The parameters here are audio format details that you
            // shouldn't need to understand for casual use.
            AudioFormat pcm =
                    new AudioFormat(format.getSampleRate( ), 16,
                            format.getChannels( ), true, false);

            // Get a wrapper stream around the input stream that does the
            // transcoding for us.
            return AudioSystem.getAudioInputStream(pcm, audioStream);
        }

        return audioStream;
    }

    // see https://www.experts-exchange.com/questions/26925195/java-stereo-to-mono-conversion-unsupported-conversion-error.html
    public static AudioInputStream convertToMono(AudioInputStream sourceStream) {
        AudioFormat sourceFormat = sourceStream.getFormat();

        // is already mono?
        if(sourceFormat.getChannels() == 1) {
            return sourceStream;
        }

        AudioFormat targetFormat = new AudioFormat(
                sourceFormat.getEncoding(),
                sourceFormat.getSampleRate(),
                sourceFormat.getSampleSizeInBits(),
                1,
                // this is the important bit, the framesize needs to change as well,
                // for framesize 4, this calculation leads to new framesize 2
                (sourceFormat.getSampleSizeInBits() + 7) / 8,
                sourceFormat.getFrameRate(),
                sourceFormat.isBigEndian());
        return AudioSystem.getAudioInputStream(targetFormat, sourceStream);
    }
}
