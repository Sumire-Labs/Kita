package com.sumirelabs.kita.audio.crossfade;

/** Stereo 48 kHz PCM, with equal-power gains and a soft knee for overlapping peaks. */
public final class CrossfadeMixer {
    private CrossfadeMixer() {}
    public static void mix(float[][] first, float[][] second, float[][] output, long offset, long duration, int frames) {
        for (int sample = 0; sample < frames; sample++) {
            double progress = Math.clamp((offset + sample) / (double) duration, 0, 1);
            double a = Math.cos(progress * Math.PI / 2), b = Math.sin(progress * Math.PI / 2);
            for (int channel = 0; channel < 2; channel++) output[channel][sample] = limit((float) (first[channel][sample] * a + second[channel][sample] * b));
        }
    }
    static float limit(float value) {
        float magnitude = Math.abs(value);
        return magnitude <= .9f ? value : Math.copySign(.9f + .1f * (float) Math.tanh((magnitude - .9f) / .1f), value);
    }
}
