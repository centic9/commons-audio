package org.dstadler.audio.player;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.WaveformSimilarityBasedOverlapAdd;
import be.tarsos.dsp.io.TarsosDSPAudioInputStream;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;

/**
 * AudioPlayer implementation which uses the TarsosDSP functionality for playback
 * and potentially for slowing down or speeding up the played audio in order to
 * allow to build up more buffer for seeking forward or backwards.
 */
public class TarsosDSPPlayer implements AudioPlayer {
    private final InputStream stream;
    private AudioInputStream ain;
    private AudioDispatcher dispatcher;
    private float tempo = 1.0f;

    public TarsosDSPPlayer(InputStream stream) {
        this.stream = stream;
    }

    @Override
    public void setOptions(String options) {
        if(StringUtils.isNotEmpty(options)) {
            tempo = Float.parseFloat(options);
            Preconditions.checkState(tempo > 0,
                    "Cannot play at speed zero or less, but had: %s",
                    tempo);
        }
    }

    @Override
    public void play() throws IOException, UnsupportedAudioFileException {
        try {
            // first create the AudioInputStream for the incoming audio data (usually MP3!)
            ain = AudioSystem.getAudioInputStream(stream);

            // wrap it so that it is decoded to PCM using the MP3SPI support
            ain = AudioUtils.convertToSupportedAudio(ain);

            // transform the stream to mono as TarsosDSP can only process Mono currently
            AudioInputStream monoStream = AudioUtils.convertToMono(ain);
            AudioFormat monoFormat = monoStream.getFormat();

            // then define the time stretching step
            WaveformSimilarityBasedOverlapAdd wsola = new WaveformSimilarityBasedOverlapAdd(
                    WaveformSimilarityBasedOverlapAdd.Parameters.musicDefaults(tempo, monoFormat.getSampleRate()));

            // then start up the TarsosDSP audio system
            TarsosDSPAudioInputStream audioStream = new JVMAudioInputStream(monoStream);
            dispatcher = new AudioDispatcher(audioStream,
                    wsola.getInputBufferSize() * monoFormat.getChannels(),
                    wsola.getOverlap() * monoFormat.getChannels());

            // if we should speed up or slow down audio playback, add the WSOLA time stretcher
            if(tempo != 1.0f) {
                dispatcher.addAudioProcessor(wsola);
            }
            //  the audio-output processor provides the actual audio playback in the pipeline
            dispatcher.addAudioProcessor(new be.tarsos.dsp.io.jvm.AudioPlayer(dispatcher.getFormat()));

            // finally run the audio pipeline directly, no need for a separate thread
            // here because the player runs in its own thread already anyway
            dispatcher.run();
        } catch (LineUnavailableException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        if(dispatcher != null) {
            dispatcher.stop();
            dispatcher = null;
        }

        if(ain != null) {
            ain.close();
            ain = null;
        }

        if(stream != null) {
            stream.close();
        }
    }
}
