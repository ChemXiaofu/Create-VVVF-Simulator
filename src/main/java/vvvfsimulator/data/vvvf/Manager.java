package vvvfsimulator.data.vvvf;

public final class Manager {
    private static final Struct TEMPLATE = new Struct();

    public static Struct loadData;
    public static Struct current = deepClone(TEMPLATE);
    public static String loadPath = "";

    private Manager() {
    }

    public static Struct deepClone(Struct source) {
        Struct copy = new Struct();
        copy.level = source.level;
        copy.jerkSetting = source.jerkSetting;
        copy.minimumFrequency.accelerating = source.minimumFrequency.accelerating;
        copy.minimumFrequency.braking = source.minimumFrequency.braking;
        for (Struct.PulseControlEx ex : source.acceleratePattern) {
            copy.acceleratePattern.add(ex.copyEx());
        }
        for (Struct.PulseControlEx ex : source.brakingPattern) {
            copy.brakingPattern.add(ex.copyEx());
        }
        return copy;
    }

    public static Struct getTemplate() {
        return deepClone(TEMPLATE);
    }

    public static void resetCurrent() {
        current = deepClone(TEMPLATE);
        loadPath = "";
        loadData = null;
    }
}
