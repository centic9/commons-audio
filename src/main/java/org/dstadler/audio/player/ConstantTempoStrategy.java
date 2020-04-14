package org.dstadler.audio.player;

/**
 * A simple tempo strategy which always returns the tempo-value that was defined
 * during construction.
 */
public class ConstantTempoStrategy implements TempoStrategy {
    private final float tempo;

    /**
     * Construct the strategy with the given tempo value.
     *
     * @param tempo The tempo to use, 1.0 for normal speed, lower values for
     *          slower playback, higher values for faster playback. Values
     *          in the range [0.8, 1.2] seem to still sound fairly good.
     */
    public ConstantTempoStrategy(float tempo) {
        this.tempo = tempo;
    }

    @Override
    public float calculateTempo() {
        return tempo;
    }

    @Override
    public String name() {
        return CONSTANT;
    }
}
