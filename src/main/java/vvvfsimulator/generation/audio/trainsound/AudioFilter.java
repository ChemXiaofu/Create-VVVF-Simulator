package vvvfsimulator.generation.audio.trainsound;

import vvvfsimulator.audiofilter.AudioFFT;
import vvvfsimulator.audiofilter.FFTConvolver;
import vvvfsimulator.audiofilter.Utilities;

public final class AudioFilter {
    private AudioFilter() {
    }

    public interface SampleFilter {
        float apply(float input);
    }

    public static class IdentityFilter implements SampleFilter {
        @Override
        public float apply(float input) {
            return input;
        }
    }

    public static final class CppConvolutionFilter {
        private final FFTConvolver convolver = new FFTConvolver();
        private final float[] input;
        private final float[] output;

        public CppConvolutionFilter(int blockSize, float[] response) {
            this.input = new float[blockSize];
            this.output = new float[blockSize];
            convolver.init(blockSize, response, response.length);
        }

        public void reset() {
            convolver.reset();
        }

        public void process(float[] in, int inOffset, float[] out, int outOffset, int len) {
            convolver.process(in, inOffset, out, outOffset, len);
        }

        public void process(float[] inOut, int len) {
            if (len > input.length) {
                throw new IllegalArgumentException("len exceeds block size");
            }
            System.arraycopy(inOut, 0, input, 0, len);
            convolver.process(input, 0, output, 0, len);
            System.arraycopy(output, 0, inOut, 0, len);
        }

        public static void stereo2monaural(float[] input, int len, float[] outputL, float[] outputR) {
            int frames = len / 2;
            for (int i = 0; i < frames; i++) {
                outputL[i] = input[2 * i];
                outputR[i] = input[2 * i + 1];
            }
        }

        public static void monaural2stereo(float[] inputL, float[] inputR, float[] output, int len) {
            int frames = len / 2;
            for (int i = 0; i < frames; i++) {
                output[2 * i] = inputL[i];
                output[2 * i + 1] = inputR[i];
            }
        }
    }
}
