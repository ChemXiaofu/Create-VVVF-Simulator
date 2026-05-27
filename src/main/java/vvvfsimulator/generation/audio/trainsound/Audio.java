package vvvfsimulator.generation.audio.trainsound;

import vvvfsimulator.data.basefrequency.Analyze;
import vvvfsimulator.data.basefrequency.StructCompiled;
import vvvfsimulator.data.trainaudio.Struct;
import vvvfsimulator.generation.GenerateCommon.GenerationParameter;
import vvvfsimulator.generation.audio.WavWriter;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import vvvfsimulator.vvvf.calculation.Common;

public final class Audio {
    private Audio() {
    }

    public static double calculateHarmonicSounds(vvvfsimulator.vvvf.model.Struct.Domain control,List<Struct.HarmonicData> harmonics) {
        double sound = 0;
        for (Struct.HarmonicData harmonic : harmonics) {
            if (harmonic.range.start > control.getBaseWaveFrequency()) {
                continue;
            }
            if (harmonic.range.end >= 0 && harmonic.range.end < control.getBaseWaveFrequency()) {
                continue;
            }
            double harmonicFreq = harmonic.harmonic * control.getBaseWaveFrequency();
            if (harmonic.disappear != -1 && harmonicFreq > harmonic.disappear) {
                continue;
            }
            double sine = Math.sin(control.getBaseWaveTime() * control.getBaseWaveAngleFrequency() * harmonic.harmonic);
            double amp = harmonic.amplitude.startValue
                    + (harmonic.amplitude.endValue - harmonic.amplitude.startValue)
                    / (harmonic.amplitude.end - harmonic.amplitude.start)
                    * (control.getBaseWaveFrequency() - harmonic.amplitude.start);
            amp = Math.min(amp, harmonic.amplitude.maximumValue);
            amp = Math.max(amp, harmonic.amplitude.minimumValue);
            double fade = (harmonic.disappear == -1 || harmonicFreq + 100.0 <= harmonic.disappear)
                    ? 1.0
                    : (harmonic.disappear - harmonicFreq) / 100.0;
            sound += sine * amp * fade;
        }
        return sound;
    }

    public static double calculateTrainSound(vvvfsimulator.vvvf.model.Struct.Domain control,Struct data) {
        Common.calculatePhsaseState(control, 0);
        return calculateTrainSoundFromCurrentState(control, data);
    }

    public static double calculateTrainSoundFromCurrentState(vvvfsimulator.vvvf.model.Struct.Domain control,Struct data) {
        double motorPwm = control.motor.parameter.diffTe * Math.pow(10, data.motorVolumeDb);
        double motor = calculateHarmonicSounds(control, data.harmonicSound);
        double gear = calculateHarmonicSounds(control, data.gearSound);
        return (motorPwm + motor + gear) * Math.pow(10, data.totalVolumeDb);
    }

    public static void exportWavFile(GenerationParameter parameter, int samplingFrequency, String path) throws IOException {
        StructCompiled baseFreq = parameter.baseFrequencyData;
        vvvfsimulator.data.vvvf.Struct vvvfData = parameter.vvvfData;
        Struct trainData = parameter.trainData;
        vvvfsimulator.vvvf.model.Struct.Domain domain = new vvvfsimulator.vvvf.model.Struct.Domain(trainData.motorSpec);
        final int processBlockSize = 512;
        AudioFilter.CppConvolutionFilter convolutionFilter = null;
        if (trainData.useConvolutionFilter && trainData.impulseResponse != null && trainData.impulseResponse.length > 0) {
            convolutionFilter = new AudioFilter.CppConvolutionFilter(processBlockSize, trainData.impulseResponse);
        }
        float[] blockInput = new float[processBlockSize];
        float[] blockOutput = new float[processBlockSize];
        int blockFill = 0;

        double dt = 1.0 / samplingFrequency;
        parameter.progress.total = baseFreq.getEstimatedSteps(dt);

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(path))) {
            WavWriter.writeHeader(out, samplingFrequency, 16, 1, 0);
            int samples = 0;
            while (true) {
                vvvfsimulator.data.vvvf.Analyze.calculate(domain, vvvfData);
                float sound = (float) calculateTrainSound(domain, trainData);
                blockInput[blockFill++] = sound;

                if (blockFill == processBlockSize) {
                    if (convolutionFilter != null) {
                        convolutionFilter.process(blockInput, 0, blockOutput, 0, blockFill);
                    } else {
                        System.arraycopy(blockInput, 0, blockOutput, 0, blockFill);
                    }
                    for (int i = 0; i < blockFill; i++) {
                        short pcm = (short) Math.max(Math.min(blockOutput[i] * Short.MAX_VALUE, Short.MAX_VALUE), Short.MIN_VALUE);
                        out.write(pcm & 0xFF);
                        out.write((pcm >> 8) & 0xFF);
                        samples++;
                    }
                    blockFill = 0;
                }

                parameter.progress.progress++;
                boolean cont = Analyze.checkForFreqChange(domain, baseFreq, vvvfData, dt);
                if (!cont || parameter.progress.cancel) {
                    if (blockFill > 0) {
                        if (convolutionFilter != null) {
                            convolutionFilter.process(blockInput, 0, blockOutput, 0, blockFill);
                        } else {
                            System.arraycopy(blockInput, 0, blockOutput, 0, blockFill);
                        }
                        for (int i = 0; i < blockFill; i++) {
                            short pcm = (short) Math.max(Math.min(blockOutput[i] * Short.MAX_VALUE, Short.MAX_VALUE), Short.MIN_VALUE);
                            out.write(pcm & 0xFF);
                            out.write((pcm >> 8) & 0xFF);
                            samples++;
                        }
                    }
                    WavWriter.patchDataSize(path, samples * 2);
                    break;
                }
            }
        }
    }
}


