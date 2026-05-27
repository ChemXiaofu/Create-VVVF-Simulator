package vvvfsimulator.audiofilter;

import java.util.Arrays;

public class TwoStageFFTConvolver {
    private int headBlockSize;
    private int tailBlockSize;
    private final FFTConvolver headConvolver = new FFTConvolver();
    private final FFTConvolver tailConvolver0 = new FFTConvolver();
    private float[] tailOutput0 = new float[0];
    private float[] tailPrecalculated0 = new float[0];
    private final FFTConvolver tailConvolver = new FFTConvolver();
    private float[] tailOutput = new float[0];
    private float[] tailPrecalculated = new float[0];
    private float[] tailInput = new float[0];
    private int tailInputFill;
    private int precalculatedPos;
    private float[] backgroundProcessingInput = new float[0];

    public boolean init(int headBlockSize, int tailBlockSize, float[] ir, int irLen) {
        reset();
        if (headBlockSize == 0 || tailBlockSize == 0) {
            return false;
        }
        headBlockSize = Math.max(1, headBlockSize);
        if (headBlockSize > tailBlockSize) {
            int tmp = headBlockSize;
            headBlockSize = tailBlockSize;
            tailBlockSize = tmp;
        }
        while (irLen > 0 && Math.abs(ir[irLen - 1]) < 0.000001f) {
            irLen--;
        }
        if (irLen == 0) {
            return true;
        }
        this.headBlockSize = Utilities.NextPowerOf2(headBlockSize);
        this.tailBlockSize = Utilities.NextPowerOf2(tailBlockSize);

        int headIrLen = Math.min(irLen, this.tailBlockSize);
        headConvolver.init(this.headBlockSize, ir, headIrLen);

        if (irLen > this.tailBlockSize) {
            int conv1IrLen = Math.min(irLen - this.tailBlockSize, this.tailBlockSize);
            float[] tailIr0 = Arrays.copyOfRange(ir, this.tailBlockSize, this.tailBlockSize + conv1IrLen);
            tailConvolver0.init(this.headBlockSize, tailIr0, conv1IrLen);
            tailOutput0 = new float[this.tailBlockSize];
            tailPrecalculated0 = new float[this.tailBlockSize];
        }
        if (irLen > 2 * this.tailBlockSize) {
            int tailIrLen = irLen - 2 * this.tailBlockSize;
            float[] tailIr = Arrays.copyOfRange(ir, 2 * this.tailBlockSize, 2 * this.tailBlockSize + tailIrLen);
            tailConvolver.init(this.tailBlockSize, tailIr, tailIrLen);
            tailOutput = new float[this.tailBlockSize];
            tailPrecalculated = new float[this.tailBlockSize];
            backgroundProcessingInput = new float[this.tailBlockSize];
        }
        if (tailPrecalculated0.length > 0 || tailPrecalculated.length > 0) {
            tailInput = new float[this.tailBlockSize];
        }
        tailInputFill = 0;
        precalculatedPos = 0;
        return true;
    }

    public void process(float[] input, int inputOffset, float[] output, int outputOffset, int len) {
        headConvolver.process(input, inputOffset, output, outputOffset, len);
        if (tailInput.length == 0) {
            return;
        }
        int processed = 0;
        while (processed < len) {
            int remaining = len - processed;
            int processing = Math.min(remaining, headBlockSize - (tailInputFill % headBlockSize));
            int sumBegin = processed;
            int sumEnd = processed + processing;

            if (tailPrecalculated0.length > 0) {
                int pos = precalculatedPos;
                for (int i = sumBegin; i < sumEnd; i++) {
                    output[outputOffset + i] += tailPrecalculated0[pos++];
                }
            }
            if (tailPrecalculated.length > 0) {
                int pos = precalculatedPos;
                for (int i = sumBegin; i < sumEnd; i++) {
                    output[outputOffset + i] += tailPrecalculated[pos++];
                }
            }
            System.arraycopy(input, inputOffset + processed, tailInput, tailInputFill, processing);
            tailInputFill += processing;

            if (tailPrecalculated0.length > 0 && tailInputFill % headBlockSize == 0) {
                int blockOffset = tailInputFill - headBlockSize;
                tailConvolver0.process(tailInput, blockOffset, tailOutput0, blockOffset, headBlockSize);
                if (tailInputFill == tailBlockSize) {
                    float[] tmp = tailPrecalculated0;
                    tailPrecalculated0 = tailOutput0;
                    tailOutput0 = tmp;
                }
            }
            if (tailPrecalculated.length > 0 && tailInputFill == tailBlockSize
                    && backgroundProcessingInput.length == tailBlockSize && tailOutput.length == tailBlockSize) {
                waitForBackgroundProcessing();
                float[] tmp = tailPrecalculated;
                tailPrecalculated = tailOutput;
                tailOutput = tmp;
                System.arraycopy(tailInput, 0, backgroundProcessingInput, 0, tailBlockSize);
                startBackgroundProcessing();
            }
            if (tailInputFill == tailBlockSize) {
                tailInputFill = 0;
                precalculatedPos = 0;
            } else {
                precalculatedPos += processing;
            }
            processed += processing;
        }
    }

    public void process(float[] input, float[] output, int len) {
        process(input, 0, output, 0, len);
    }

    public void reset() {
        headBlockSize = 0;
        tailBlockSize = 0;
        headConvolver.reset();
        tailConvolver0.reset();
        tailOutput0 = new float[0];
        tailPrecalculated0 = new float[0];
        tailConvolver.reset();
        tailOutput = new float[0];
        tailPrecalculated = new float[0];
        tailInput = new float[0];
        tailInputFill = 0;
        precalculatedPos = 0;
        backgroundProcessingInput = new float[0];
    }

    protected void startBackgroundProcessing() {
        doBackgroundProcessing();
    }

    protected void waitForBackgroundProcessing() {
    }

    protected void doBackgroundProcessing() {
        if (backgroundProcessingInput.length == tailBlockSize && tailOutput.length == tailBlockSize) {
            tailConvolver.process(backgroundProcessingInput, tailOutput, tailBlockSize);
        }
    }
}
