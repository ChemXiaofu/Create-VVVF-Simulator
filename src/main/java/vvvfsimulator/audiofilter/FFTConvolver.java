package vvvfsimulator.audiofilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FFTConvolver {
    private int blockSize;
    private int segSize;
    private int segCount;
    private int fftComplexSize;
    private final List<float[]> segmentsRe = new ArrayList<>();
    private final List<float[]> segmentsIm = new ArrayList<>();
    private final List<float[]> segmentsIRRe = new ArrayList<>();
    private final List<float[]> segmentsIRIm = new ArrayList<>();
    private float[] fftBuffer = new float[0];
    private final AudioFFT fft = new AudioFFT();
    private float[] preMultipliedRe = new float[0];
    private float[] preMultipliedIm = new float[0];
    private float[] convRe = new float[0];
    private float[] convIm = new float[0];
    private float[] overlap = new float[0];
    private int current;
    private float[] inputBuffer = new float[0];
    private int inputBufferFill;

    public boolean init(int blockSize, float[] ir, int irLen) {
        reset();
        if (blockSize == 0) {
            return false;
        }
        while (irLen > 0 && Math.abs(ir[irLen - 1]) < 0.000001f) {
            irLen--;
        }
        if (irLen == 0) {
            return true;
        }
        this.blockSize = Utilities.NextPowerOf2(blockSize);
        this.segSize = 2 * this.blockSize;
        this.segCount = (int) Math.ceil((double) irLen / this.blockSize);
        this.fftComplexSize = AudioFFT.ComplexSize(segSize);

        fft.init(segSize);
        fftBuffer = new float[segSize];

        for (int i = 0; i < segCount; i++) {
            segmentsRe.add(new float[fftComplexSize]);
            segmentsIm.add(new float[fftComplexSize]);
        }

        for (int i = 0; i < segCount; i++) {
            float[] re = new float[fftComplexSize];
            float[] im = new float[fftComplexSize];
            int remaining = irLen - i * this.blockSize;
            int copySize = Math.max(0, Math.min(this.blockSize, remaining));
            Utilities.CopyAndPad(fftBuffer, ir, i * this.blockSize, copySize);
            fft.fft(fftBuffer, re, im);
            segmentsIRRe.add(re);
            segmentsIRIm.add(im);
        }

        preMultipliedRe = new float[fftComplexSize];
        preMultipliedIm = new float[fftComplexSize];
        convRe = new float[fftComplexSize];
        convIm = new float[fftComplexSize];
        overlap = new float[this.blockSize];
        inputBuffer = new float[this.blockSize];
        inputBufferFill = 0;
        current = 0;
        return true;
    }

    public void process(float[] input, int inputOffset, float[] output, int outputOffset, int len) {
        if (segCount == 0) {
            Arrays.fill(output, outputOffset, outputOffset + len, 0f);
            return;
        }
        int processed = 0;
        while (processed < len) {
            boolean inputBufferWasEmpty = inputBufferFill == 0;
            int processing = Math.min(len - processed, blockSize - inputBufferFill);
            int inputBufferPos = inputBufferFill;
            System.arraycopy(input, inputOffset + processed, inputBuffer, inputBufferPos, processing);

            Utilities.CopyAndPad(fftBuffer, inputBuffer, 0, blockSize);
            fft.fft(fftBuffer, segmentsRe.get(current), segmentsIm.get(current));

            if (inputBufferWasEmpty) {
                Arrays.fill(preMultipliedRe, 0f);
                Arrays.fill(preMultipliedIm, 0f);
                for (int i = 1; i < segCount; i++) {
                    int indexIr = i;
                    int indexAudio = (current + i) % segCount;
                    Utilities.ComplexMultiplyAccumulate(
                            preMultipliedRe, preMultipliedIm,
                            segmentsIRRe.get(indexIr), segmentsIRIm.get(indexIr),
                            segmentsRe.get(indexAudio), segmentsIm.get(indexAudio),
                            fftComplexSize
                    );
                }
            }

            System.arraycopy(preMultipliedRe, 0, convRe, 0, fftComplexSize);
            System.arraycopy(preMultipliedIm, 0, convIm, 0, fftComplexSize);
            Utilities.ComplexMultiplyAccumulate(
                    convRe, convIm,
                    segmentsRe.get(current), segmentsIm.get(current),
                    segmentsIRRe.get(0), segmentsIRIm.get(0),
                    fftComplexSize
            );

            fft.ifft(fftBuffer, convRe, convIm);
            Utilities.Sum(output, outputOffset + processed, fftBuffer, inputBufferPos, overlap, inputBufferPos, processing);

            inputBufferFill += processing;
            if (inputBufferFill == blockSize) {
                Arrays.fill(inputBuffer, 0f);
                inputBufferFill = 0;
                System.arraycopy(fftBuffer, blockSize, overlap, 0, blockSize);
                current = current > 0 ? current - 1 : segCount - 1;
            }
            processed += processing;
        }
    }

    public void process(float[] input, float[] output, int len) {
        process(input, 0, output, 0, len);
    }

    public void reset() {
        blockSize = 0;
        segSize = 0;
        segCount = 0;
        fftComplexSize = 0;
        segmentsRe.clear();
        segmentsIm.clear();
        segmentsIRRe.clear();
        segmentsIRIm.clear();
        fftBuffer = new float[0];
        fft.init(0);
        preMultipliedRe = new float[0];
        preMultipliedIm = new float[0];
        convRe = new float[0];
        convIm = new float[0];
        overlap = new float[0];
        current = 0;
        inputBuffer = new float[0];
        inputBufferFill = 0;
    }
}
