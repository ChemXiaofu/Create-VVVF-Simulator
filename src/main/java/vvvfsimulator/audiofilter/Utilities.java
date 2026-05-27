package vvvfsimulator.audiofilter;

import java.util.Arrays;

public final class Utilities {
    private Utilities() {
    }

    public static boolean SSEEnabled() {
        return false;
    }

    public static int NextPowerOf2(int val) {
        int next = 1;
        while (next < val) {
            next <<= 1;
        }
        return next;
    }

    public static void Sum(float[] result, int resultOffset, float[] a, int aOffset, float[] b, int bOffset, int len) {
        for (int i = 0; i < len; i++) {
            result[resultOffset + i] = a[aOffset + i] + b[bOffset + i];
        }
    }

    public static void CopyAndPad(float[] dest, float[] src, int srcOffset, int srcSize) {
        System.arraycopy(src, srcOffset, dest, 0, srcSize);
        if (dest.length > srcSize) {
            Arrays.fill(dest, srcSize, dest.length, 0f);
        }
    }

    public static void ComplexMultiplyAccumulate(
            float[] re,
            float[] im,
            float[] reA,
            float[] imA,
            float[] reB,
            float[] imB,
            int len
    ) {
        for (int i = 0; i < len; i++) {
            re[i] += reA[i] * reB[i] - imA[i] * imB[i];
            im[i] += reA[i] * imB[i] + imA[i] * reB[i];
        }
    }
}
