package org.dstadler.audio.player;

import org.dstadler.audio.buffer.CountingSeekableRingBuffer;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of the {@link TempoStrategy} based on the
 * given ring-buffer, calculateTemp() will look at the current
 * playing position in respect to add-position and get-position
 * in the buffer and report tempo so that:
 * * Tempo is 1.0 for half of the buffer in the middle
 * * Tempo is 1.1 or 0.9 for one third of the buffer further out
 * * Tempo is 1.15 or 0.85 for one third of the buffer further out
 * * Tempo is 1.2 or 0.9 for the outer third of the buffer on both sides
 *
 * I.e.:
 *
 *   ------------------------------------------------------------------
 *  | 1.2 |1.15 | 1.1 |           1.0                | 0.9 |0.85 | 0.8 |
 *   ------------------------------------------------------------------
 *
 * The result is that playback at the beginning/end of the buffer is sped up
 * or slowed down so that playback gains some buffer to skip forward/backwards
 * always, e.g. when listening at "now", but ads are coming up.
 *
 * Furthermore the limit is capped at 300 seconds (i.e. 5 minutes) to only
 * build up that much buffer with tempo-adjusted playback.
 */
public class BufferBasedTempoStrategy implements TempoStrategy {
    private final static Logger log = LoggerFactory.make();

    private final Supplier<CountingSeekableRingBuffer> buffer;

    public BufferBasedTempoStrategy(Supplier<CountingSeekableRingBuffer> buffer) {
        this.buffer = buffer;
    }

    @Override
    public float calculateTempo() {
        CountingSeekableRingBuffer bufferLocal = buffer.get();

        double chunksPerSecond = bufferLocal.getChunksPerSecond();

        int fill = bufferLocal.fill();
        int size = bufferLocal.size();
        double maxSecondsBackwards = ((double)(fill - size))/chunksPerSecond;
        double maxSecondsForward = ((double) size)/chunksPerSecond;

        // use a quarter of the current size at both ends for tempo-adjustment
        double limit = ((double) fill)/4/chunksPerSecond;

        // no computation if the area is not very large due to small buffer size
        if(limit < 200) {
            // start adjusting tempo a bit to "swing in" and not have a large tempo drop
            // when the limit is reached

            // use steps of 0.05 from 1.0 down to 0.85, with 200 0.80 will be used
            // by the calculation below
            if(limit < 50) {
                return 1.0f;
            } else if (limit < 100) {
                return 0.95f;
            } else  if (limit < 150) {
                return 0.90f;
            } else if (limit < 200) {
                return 0.85f;
            }
        }

        // no tempo adjustment if we have a reasonable amount of time available
        // thus set the max limit to 5 minutes, so the tempo-steps are then each
        // 100 seconds apart
        if(limit > 300) {
            limit = 300;
        }

        // no adjustment necessary if we have enough buffer in both directions
        if(maxSecondsBackwards >= limit && maxSecondsForward >= limit) {
            return 1.0f;
        }

        // have 3 levels of tempo adjustment
        // with 120%, 115% and 110% speedup
        // and 80%, 85%, 90% slowdown
        double stepSize = limit/4;

        if(log.isLoggable(Level.FINE)) {
            log.fine("Having maxBack: " + maxSecondsBackwards + ", maxForward: " + maxSecondsForward +
                    ", step: " + stepSize);
        }
        if(maxSecondsBackwards < limit) {
            // not enough buffer backwards => play a bit faster to build up more buffer
            if(maxSecondsBackwards < stepSize) {
                return 1.2f;
            } else if(maxSecondsBackwards < 2*stepSize) {
                return 1.15f;
            } else if(maxSecondsBackwards < 3*stepSize) {
                return 1.1f;
            } else {
                return 1.05f;
            }
        } else {
            // not enough buffer forwards => play a bit slower to build up more buffer
            if(maxSecondsForward < stepSize) {
                return 0.8f;
            } else if(maxSecondsForward < 2*stepSize) {
                return 0.85f;
            } else if(maxSecondsForward < 3*stepSize) {
                return 0.9f;
            } else {
                return 0.95f;
            }
        }
    }

    @Override
    public String name() {
        return ADAPTIVE;
    }
}
