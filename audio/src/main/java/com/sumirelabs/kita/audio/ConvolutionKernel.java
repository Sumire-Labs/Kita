package com.sumirelabs.kita.audio;

import org.jtransforms.fft.FloatFFT_1D;

public final class ConvolutionKernel {
    static final int BLOCK = 512;
    static final int FFT_SIZE = BLOCK * 2;
    final float[][][] spectra;
    final int frames;
    final int partitions;

    public ConvolutionKernel(float[][] impulses) {
        if (impulses.length != 4 || impulses[0].length == 0) throw new IllegalArgumentException("Expected four nonempty impulses");
        frames = impulses[0].length;
        partitions = (frames + BLOCK - 1) / BLOCK;
        spectra = new float[4][partitions][FFT_SIZE * 2];
        var fft = new FloatFFT_1D(FFT_SIZE);
        for (int route = 0; route < 4; route++) {
            if (impulses[route].length != frames) throw new IllegalArgumentException("Impulse lengths differ");
            for (int part = 0; part < partitions; part++) {
                for (int index = 0; index < BLOCK && part * BLOCK + index < frames; index++) {
                    spectra[route][part][index * 2] = impulses[route][part * BLOCK + index];
                }
                fft.complexForward(spectra[route][part]);
            }
        }
    }
}
