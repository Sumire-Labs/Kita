package com.sumirelabs.kita.audio;

import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import java.util.Arrays;
import org.jtransforms.fft.FloatFFT_1D;

public final class HrirFilter implements FloatPcmAudioFilter {
    private final ConvolutionKernel kernel;
    private final FloatPcmAudioFilter output;
    private final FloatFFT_1D fft = new FloatFFT_1D(ConvolutionKernel.FFT_SIZE);
    private final float[][][] history;
    private final float[][] input = new float[2][ConvolutionKernel.BLOCK];
    private final float[][] overlap = new float[2][ConvolutionKernel.BLOCK];
    private final float[][] result = new float[2][ConvolutionKernel.BLOCK];
    private final float[][] spectrum = new float[2][ConvolutionKernel.FFT_SIZE * 2];
    private int fill;
    private int cursor;
    private boolean received;

    public HrirFilter(ConvolutionKernel kernel, FloatPcmAudioFilter output) {
        this.kernel = kernel; this.output = output;
        history = new float[2][kernel.partitions][ConvolutionKernel.FFT_SIZE * 2];
    }

    @Override public void process(float[][] data, int offset, int length) throws InterruptedException {
        received |= length > 0;
        int remaining = length;
        while (remaining > 0) {
            int copy = Math.min(remaining, ConvolutionKernel.BLOCK - fill);
            for (int channel = 0; channel < 2; channel++) System.arraycopy(data[channel], offset, input[channel], fill, copy);
            fill += copy; offset += copy; remaining -= copy;
            if (fill == ConvolutionKernel.BLOCK) { processBlock(ConvolutionKernel.BLOCK); fill = 0; }
        }
    }

    private void processBlock(int length) throws InterruptedException {
        for (int channel = 0; channel < 2; channel++) {
            var slot = history[channel][cursor];
            Arrays.fill(slot, 0);
            for (int index = 0; index < ConvolutionKernel.BLOCK; index++) slot[index * 2] = input[channel][index];
            fft.complexForward(slot);
            Arrays.fill(spectrum[channel], 0);
        }
        for (int part = 0; part < kernel.partitions; part++) {
            int index = (cursor - part + kernel.partitions) % kernel.partitions;
            multiply(history[0][index], kernel.spectra[0][part], spectrum[0]);
            multiply(history[0][index], kernel.spectra[1][part], spectrum[1]);
            multiply(history[1][index], kernel.spectra[2][part], spectrum[0]);
            multiply(history[1][index], kernel.spectra[3][part], spectrum[1]);
        }
        for (int channel = 0; channel < 2; channel++) {
            fft.complexInverse(spectrum[channel], true);
            for (int index = 0; index < ConvolutionKernel.BLOCK; index++) {
                // Leave headroom in the supplied IR; clip only the final stereo sum.
                result[channel][index] = Math.clamp(spectrum[channel][index * 2] + overlap[channel][index], -1f, 1f);
                overlap[channel][index] = spectrum[channel][(index + ConvolutionKernel.BLOCK) * 2];
            }
        }
        output.process(result, 0, length);
        cursor = (cursor + 1) % kernel.partitions;
    }

    private static void multiply(float[] signal, float[] impulse, float[] target) {
        for (int index = 0; index < signal.length; index += 2) {
            target[index] += signal[index] * impulse[index] - signal[index + 1] * impulse[index + 1];
            target[index + 1] += signal[index] * impulse[index + 1] + signal[index + 1] * impulse[index];
        }
    }

    @Override public void flush() throws InterruptedException {
        if (received) {
            int remaining = fill + kernel.frames - 1;
            for (var channel : input) Arrays.fill(channel, fill, channel.length, 0);
            while (remaining > 0) {
                int count = Math.min(remaining, ConvolutionKernel.BLOCK);
                processBlock(count); remaining -= count;
                for (var channel : input) Arrays.fill(channel, 0);
            }
        }
        reset();
        output.flush();
    }

    @Override public void seekPerformed(long requested, long provided) { reset(); }
    @Override public void close() {}
    private void reset() {
        fill = 0; cursor = 0; received = false;
        for (var channel : history) for (var block : channel) Arrays.fill(block, 0);
        for (var channel : input) Arrays.fill(channel, 0);
        for (var channel : overlap) Arrays.fill(channel, 0);
    }
}
