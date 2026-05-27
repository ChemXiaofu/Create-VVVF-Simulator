package vvvfsimulator.audiofilter;
public final class AudioFFT {
    private int size;
    private int halfSize;
    private int levels;
    private float[] workRe = new float[0];
    private float[] workIm = new float[0];
    public void init(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("FFT size must be >= 0");
        }
        if (size != 0 && (size & (size - 1)) != 0) {
            throw new IllegalArgumentException("FFT size must be power of 2");
        }
        this.size = size;
        this.halfSize = size / 2;
        this.levels = size == 0 ? 0 : Integer.numberOfTrailingZeros(size);
        this.workRe = new float[size];
        this.workIm = new float[size];
    }
    public void fft(float[] data, float[] re, float[] im) {
        if (size == 0) {
            return;
        }
        if (data.length < size || re.length < halfSize + 1 || im.length < halfSize + 1) {
            throw new IllegalArgumentException("buffer too small for configured FFT size");
        }
        System.arraycopy(data, 0, workRe, 0, size);
        java.util.Arrays.fill(workIm, 0f);
        fftComplex(workRe, workIm, false);
        for (int k = 0; k <= halfSize; k++) {
            re[k] = workRe[k];
            im[k] = workIm[k];
        }
    }
    public void ifft(float[] data, float[] re, float[] im) {
        if (size == 0) {
            return;
        }
        if (data.length < size || re.length < halfSize + 1 || im.length < halfSize + 1) {
            throw new IllegalArgumentException("buffer too small for configured FFT size");
        }
        workRe[0] = re[0];
        workIm[0] = im[0];
        for (int k = 1; k < halfSize; k++) {
            workRe[k] = re[k];
            workIm[k] = im[k];
            int mirror = size - k;
            workRe[mirror] = re[k];
            workIm[mirror] = -im[k];
        }
        if (halfSize > 0) {
            workRe[halfSize] = re[halfSize];
            workIm[halfSize] = 0f;
        }
        fftComplex(workRe, workIm, true);
        for (int n = 0; n < size; n++) {
            data[n] = workRe[n];
        }
    }
    public static int ComplexSize(int size) {
        return size / 2 + 1;
    }
    private void fftComplex(float[] real, float[] imag, boolean inverse) {
        for (int i = 0; i < size; i++) {
            int j = Integer.reverse(i) >>> (32 - levels);
            if (j > i) {
                float tr = real[i];
                real[i] = real[j];
                real[j] = tr;
                float ti = imag[i];
                imag[i] = imag[j];
                imag[j] = ti;
            }
        }
        for (int len = 2; len <= size; len <<= 1) {
            int halfLen = len >>> 1;
            double angle = (inverse ? 2.0 : -2.0) * Math.PI / len;
            double wLenRe = Math.cos(angle);
            double wLenIm = Math.sin(angle);
            for (int i = 0; i < size; i += len) {
                double wRe = 1.0;
                double wIm = 0.0;
                for (int j = 0; j < halfLen; j++) {
                    int even = i + j;
                    int odd = even + halfLen;
                    double uRe = real[even];
                    double uIm = imag[even];
                    double vRe = real[odd] * wRe - imag[odd] * wIm;
                    double vIm = real[odd] * wIm + imag[odd] * wRe;
                    real[even] = (float) (uRe + vRe);
                    imag[even] = (float) (uIm + vIm);
                    real[odd] = (float) (uRe - vRe);
                    imag[odd] = (float) (uIm - vIm);
                    double nextWRe = wRe * wLenRe - wIm * wLenIm;
                    wIm = wRe * wLenIm + wIm * wLenRe;
                    wRe = nextWRe;
                }
            }
        }
        if (inverse) {
            float invSize = 1.0f / size;
            for (int i = 0; i < size; i++) {
                real[i] *= invSize;
                imag[i] *= invSize;
            }
        }
    }
}
