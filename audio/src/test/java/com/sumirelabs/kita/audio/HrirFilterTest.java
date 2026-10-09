package com.sumirelabs.kita.audio;

import static org.junit.jupiter.api.Assertions.*;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import java.util.ArrayList;
import java.util.Random;
import org.junit.jupiter.api.Test;

class HrirFilterTest {
    @Test void partitionedConvolutionMatchesDirectStereoMatrixAndFlushesTail() throws Exception {
        var random = new Random(42);
        float[][] input = new float[2][1503];
        float[][] impulses = new float[4][903];
        for (var channel : input) for (int i = 0; i < channel.length; i++) channel[i] = (random.nextFloat() - .5f) * .01f;
        for (var route : impulses) for (int i = 0; i < route.length; i++) route[i] = (random.nextFloat() - .5f) * .01f;
        var sink = new Sink();
        var filter = new HrirFilter(new ConvolutionKernel(impulses), sink);
        for (int offset = 0; offset < input[0].length;) {
            int count = Math.min(137, input[0].length - offset);
            filter.process(input, offset, count); offset += count;
        }
        filter.flush();
        assertEquals(input[0].length + impulses[0].length - 1, sink.left.size());
        for (int out = 0; out < 2; out++) {
            var actual = out == 0 ? sink.left : sink.right;
            for (int sample = 0; sample < actual.size(); sample++) {
                double expected = 0;
                for (int index = 0; index < impulses[0].length; index++) {
                    int source = sample - index;
                    if (source >= 0 && source < input[0].length) {
                        expected += input[0][source] * impulses[out][index] + input[1][source] * impulses[2 + out][index];
                    }
                }
                assertEquals(expected, actual.get(sample), 1e-6, "channel=" + out + " sample=" + sample);
            }
        }
    }
    @Test void seekDiscardsHistoryAndPartialSamples() throws Exception {
        var sink = new Sink();
        var filter = new HrirFilter(new ConvolutionKernel(new float[][]{{1}, {0}, {0}, {1}}), sink);
        filter.process(new float[][]{{.1f, .2f}, {.3f, .4f}}, 0, 2);
        filter.seekPerformed(1000, 1000);
        filter.process(new float[][]{{.5f}, {.6f}}, 0, 1);
        filter.flush();
        assertEquals(java.util.List.of(.5f), sink.left);
        assertEquals(java.util.List.of(.6f), sink.right);
    }
    private static final class Sink implements FloatPcmAudioFilter {
        final ArrayList<Float> left = new ArrayList<>(); final ArrayList<Float> right = new ArrayList<>();
        @Override public void process(float[][] data, int offset, int length) {
            for (int i = offset; i < offset + length; i++) { left.add(data[0][i]); right.add(data[1][i]); }
        }
        @Override public void seekPerformed(long requested, long provided) {}
        @Override public void flush() {}
        @Override public void close() {}
    }
}
