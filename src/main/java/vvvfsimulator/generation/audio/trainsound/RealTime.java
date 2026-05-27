package vvvfsimulator.generation.audio.trainsound;
import vvvfsimulator.data.vvvf.Analyze;
public final class RealTime {
    private RealTime() {
    }

    public static void generate(vvvfsimulator.generation.audio.RealTime.TrainSoundParameter parameter) {
        // Placeholder: runtime stream output for train sound will be integrated with createvvvfsim.VVVFSoundEngine.
        // We keep this entry point so the vvvfsimulator.generation/data port has a stable API surface.
        while (!parameter.quit) {
            int flag = vvvfsimulator.generation.audio.RealTime.realTimeFrequencyControl(parameter.control, parameter, 1.0 / 44100.0);
            if (flag != -1) {
                break;
            }
            Analyze.calculate(parameter.control, parameter.vvvfSoundData);
            parameter.control.addTimeAll(1.0 / 44100.0);
            Audio.calculateTrainSound(parameter.control, parameter.trainSoundData);
        }
    }
}


