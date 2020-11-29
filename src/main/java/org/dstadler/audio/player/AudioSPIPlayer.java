package org.dstadler.audio.player;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;

/**
 * AudioPlayer implementation which uses the Java Audio System for playback
 * together with the MP3 SPI for transcoding the MP3 audio data to PCM.
 */
public class AudioSPIPlayer implements AudioPlayer {
    private final InputStream stream;
    private AudioInputStream ain;

    private SourceDataLine line = null;
    // We haven't started the line yet.
    boolean started = false;

    public AudioSPIPlayer(InputStream stream) throws IOException {
        this.stream = stream;

        try {
            ain = AudioSystem.getAudioInputStream(stream);
        } catch (UnsupportedAudioFileException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void setOptions(String options) {
        // no options supported here
    }

    @Override
    public void play() throws IOException {
        // Get an audio input stream from the URL
        try {
            ain = AudioUtils.convertToSupportedAudio(ain);

            // Get information about the format of the stream
            AudioFormat format = ain.getFormat( );
            DataLine.Info info = new DataLine.Info(SourceDataLine.class,format);

            // If not done before, open the line through which we'll play the streaming audio
            if(line == null) {
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format);
            }

            // Allocate a buffer for reading from the input stream and writing
            // to the line.  Make it large enough to hold 4k audio frames.
            // Note that the SourceDataLine also has its own internal buffer.
            int framesize = format.getFrameSize( );
            byte[  ] buffer = new byte[4 * 1024 * framesize]; // the buffer
            int numbytes = 0;                               // how many bytes

            for(;;) {  // We'll exit the loop when we reach the end of stream
                // First, read some bytes from the input stream.
                int bytesread = ain.read(buffer, numbytes,buffer.length-numbytes);

                if (bytesread == -1) {
                    break;
                }

                numbytes += bytesread;

                // Now that we've got some audio data to write to the line,
                // start the line, so it will play that data as we write it.
                if (!started) {
                    line.start( );
                    started = true;
                }

                // We must write bytes to the line in an integer multiple of
                // the framesize.  So figure out how many bytes we'll write.
                int bytestowrite = (numbytes/framesize)*framesize;

                // bail out if the player was closed
                if(ain == null) {
                    break;
                }

                // Now write the bytes. The line will buffer them and play
                // them. This call will block until all bytes are written.
                line.write(buffer, 0, bytestowrite);

                // If we didn't have an integer multiple of the frame size,
                // then copy the remaining bytes to the start of the buffer.
                int remaining = numbytes - bytestowrite;
                if (remaining > 0)
                    System.arraycopy(buffer,bytestowrite,buffer,0,remaining);
                numbytes = remaining;
            }

            // Now block until all buffered sound finishes playing.
            line.drain( );
        } catch (LineUnavailableException e) {
            throw new IOException(e);
        }

    }

    @Override
    public void close() throws IOException {
        if(line != null) {
            line.close();
            line = null;
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
