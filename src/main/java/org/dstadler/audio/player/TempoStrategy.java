package org.dstadler.audio.player;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.Strings;
import org.dstadler.audio.buffer.CountingSeekableRingBuffer;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.dstadler.audio.player.BufferBasedTempoStrategy.DEFAULT_KEEP_AREA_SECONDS;
import static org.dstadler.audio.player.BufferBasedTempoStrategy.DEFAULT_SPEED_STEP;

/**
 * Interface to provide a way to compute what audio-tempo
 * should be used for playback, e.g. based on buffer-position
 * we may decide to slow down a bit to build up buffer
 * at the front of the audio-queue
 */
public interface TempoStrategy {
    String CONSTANT = "constant";
    String CONSTANT_PREFIX = CONSTANT + ":";
    String CONSTANT_1 = CONSTANT_PREFIX + "1.0";
    String DEFAULT = "default";
    String ADAPTIVE = "adaptive";
    String ADAPTIVE_PREFIX = ADAPTIVE + ":";
    Pattern ADAPTIVE_PARAMS_PATTERN = Pattern.compile("(\\d+):([0-9.]+)");

    float calculateTempo();

    String name();

    static TempoStrategy create(@Nonnull String strategyAndTempo, Supplier<CountingSeekableRingBuffer> buffer) {
        if (strategyAndTempo.startsWith(CONSTANT_PREFIX)) {
            return new ConstantTempoStrategy(Float.parseFloat(Strings.CS.removeStart(strategyAndTempo, CONSTANT_PREFIX)));
        }

        // unknown strategy?
        if (!strategyAndTempo.startsWith(ADAPTIVE) && !strategyAndTempo.startsWith(DEFAULT)) {
            LoggerFactory.make().warning("Could not create unknown tempo strategy '" + strategyAndTempo + "'");
        }

        // read parameters of adaptive strategy
        if (strategyAndTempo.startsWith(ADAPTIVE_PREFIX) && !ADAPTIVE_PREFIX.equals(strategyAndTempo)) {
            String params = Strings.CS.removeStart(strategyAndTempo, ADAPTIVE_PREFIX);

            Matcher matcher = ADAPTIVE_PARAMS_PATTERN.matcher(params);
            if (!matcher.matches()) {
                LoggerFactory.make().warning("Could not read parameters of tempo strategy'" + strategyAndTempo +
                        "', expected '" + ADAPTIVE_PREFIX + "<keepAreaSecond>:<speedStepFloat>'");
            } else {
                new BufferBasedTempoStrategy(buffer, Integer.parseInt(matcher.group(1)), Float.parseFloat(matcher.group(2)));
            }
        }

        return new BufferBasedTempoStrategy(buffer, DEFAULT_KEEP_AREA_SECONDS, DEFAULT_SPEED_STEP);
    }

    /**
     * Verify that the given tempoStrategy actually exists.
     *
     * @param tempoStrategy Just the name of the strategy, not including
     * colon or additional parameters.
     */
    static void validate(String tempoStrategy) {
        Preconditions.checkNotNull(tempoStrategy, "Need a tempoStrategy");

        Preconditions.checkState(CONSTANT.equals(tempoStrategy) ||
            DEFAULT.equals(tempoStrategy) || ADAPTIVE.equals(tempoStrategy),
                "Had an unknown tempoStrategy %s, "
                        + "only %s<float>, %s and %s are supported",
                tempoStrategy, CONSTANT_PREFIX, DEFAULT, ADAPTIVE);
    }
}
