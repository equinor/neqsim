package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

/**
 * Spectral diagnostics for slow periodic oscillations in process time series, such as a pressure cycle of several
 * minutes on a gas node.
 *
 * <p>
 * The analyser answers the questions that decide how an oscillation must be treated:
 * </p>
 *
 * <ul>
 * <li>what is the period and how much of the signal variance sits in the band around it ({@link Result#getBandRms()});
 * </li>
 * <li>is it a noise-driven, lightly damped mode or a constant-amplitude limit cycle - the coefficient of variation of
 * the amplitude envelope is about 0.52 for a narrow-band Gaussian signal (Rayleigh envelope) and falls towards 0.1 to
 * 0.2 for a limit cycle ({@link Result#getEnvelopeCv()});</li>
 * <li>how sharp is the peak ({@link Result#getQ()}, {@link Result#getDampingRatioEstimate()}) and are there harmonics
 * ({@link Result#getSecondHarmonicRatio()}) that point to a non-linearity such as a limiter or a burst valve;</li>
 * <li>how does another signal relate to the oscillation - coherence, phase and gain at the peak frequency
 * ({@link #crossSpectrum(double[], double[], double)}).</li>
 * </ul>
 *
 * <p>
 * Spectra are Welch estimates (Hann window, 50 % overlap, linear detrend per segment). The segment length is a power of
 * two and sets the frequency resolution; a peak whose half-power width is below two bins is flagged as
 * resolution-limited, and its Q is then a lower bound.
 * </p>
 *
 * <pre>
 * OscillationAnalyzer a = new OscillationAnalyzer(10.0);
 * OscillationAnalyzer.Result r = a.analyze(pressure, 120.0, 1200.0);
 * double periodMin = r.getPeakPeriodSeconds() / 60.0;
 * OscillationAnalyzer.CrossResult c = a.crossSpectrum(pressure, speed, r.getPeakFrequencyHz());
 * double phase = c.getPhaseDegrees(); // positive: speed leads pressure
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class OscillationAnalyzer implements Serializable {
  /** Serialization version. */
  private static final long serialVersionUID = 1000L;

  /** Sample interval in seconds. */
  private final double dt;

  /** Requested Welch segment length in samples, or 0 for automatic. */
  private int segmentLength = 0;

  /**
   * Creates an analyser for a uniformly sampled signal.
   *
   * @param sampleIntervalSeconds sample interval in seconds, must be positive
   * @throws IllegalArgumentException if the interval is not positive
   */
  public OscillationAnalyzer(double sampleIntervalSeconds) {
    if (!(sampleIntervalSeconds > 0.0)) {
      throw new IllegalArgumentException("sample interval must be positive");
    }
    this.dt = sampleIntervalSeconds;
  }

  /**
   * Sets the Welch segment length. It is rounded down to a power of two. A longer segment gives finer frequency
   * resolution and a more reliable Q, at the cost of fewer averages.
   *
   * @param samples segment length in samples, at least 16; 0 selects an automatic length
   */
  public void setSegmentLength(int samples) {
    this.segmentLength = samples <= 0 ? 0 : Math.max(16, Integer.highestOneBit(samples));
  }

  /**
   * Result of a single-signal analysis.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class Result implements Serializable {
    /** Serialization version. */
    private static final long serialVersionUID = 1000L;
    private double peakFrequencyHz;
    private double bandRms;
    private double totalRms;
    private double q;
    private boolean resolutionLimited;
    private double envelopeCv;
    private double secondHarmonicRatio;
    private double frequencyResolutionHz;
    private int segments;

    /**
     * Frequency of the strongest peak in the search band.
     *
     * @return frequency in Hz
     */
    public double getPeakFrequencyHz() {
      return peakFrequencyHz;
    }

    /**
     * Period of the strongest peak in the search band.
     *
     * @return period in seconds
     */
    public double getPeakPeriodSeconds() {
      return peakFrequencyHz > 0.0 ? 1.0 / peakFrequencyHz : Double.NaN;
    }

    /**
     * RMS of the signal in the search band, in signal units.
     *
     * @return band rms
     */
    public double getBandRms() {
      return bandRms;
    }

    /**
     * RMS of the whole detrended signal, in signal units.
     *
     * @return total rms
     */
    public double getTotalRms() {
      return totalRms;
    }

    /**
     * Quality factor of the peak from the half-power width.
     *
     * @return Q, a lower bound when {@link #isResolutionLimited()} is true
     */
    public double getQ() {
      return q;
    }

    /**
     * Damping ratio of an equivalent second-order mode, 1 / (2 Q).
     *
     * @return damping ratio estimate
     */
    public double getDampingRatioEstimate() {
      return q > 0.0 ? 0.5 / q : Double.NaN;
    }

    /**
     * Whether the half-power width is not resolved by the segment length.
     *
     * @return true if the peak is narrower than two frequency bins
     */
    public boolean isResolutionLimited() {
      return resolutionLimited;
    }

    /**
     * Coefficient of variation of the amplitude envelope of the band-passed signal. A narrow-band Gaussian signal gives
     * about 0.52; a constant-amplitude limit cycle gives a small value.
     *
     * @return envelope coefficient of variation
     */
    public double getEnvelopeCv() {
      return envelopeCv;
    }

    /**
     * Ratio of the spectral density at twice the peak frequency to that at the peak.
     *
     * @return second harmonic ratio, large values indicate a non-linear waveform
     */
    public double getSecondHarmonicRatio() {
      return secondHarmonicRatio;
    }

    /**
     * Frequency resolution of the spectrum.
     *
     * @return resolution in Hz
     */
    public double getFrequencyResolutionHz() {
      return frequencyResolutionHz;
    }

    /**
     * Number of Welch segments averaged.
     *
     * @return segment count
     */
    public int getSegments() {
      return segments;
    }

    /**
     * Interprets the envelope statistics.
     *
     * @return a short text label for the oscillation type
     */
    public String classify() {
      if (envelopeCv >= 0.40) {
        return "noise-driven narrow-band mode (Rayleigh-like envelope)";
      }
      if (envelopeCv <= 0.25) {
        return "constant-amplitude limit cycle";
      }
      return "intermediate: mixed limit cycle and noise";
    }
  }

  /**
   * Result of a cross-spectral comparison at one frequency.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class CrossResult implements Serializable {
    /** Serialization version. */
    private static final long serialVersionUID = 1000L;
    private double coherence;
    private double phaseDegrees;
    private double gain;

    /**
     * Magnitude-squared coherence, 0 to 1.
     *
     * @return coherence
     */
    public double getCoherence() {
      return coherence;
    }

    /**
     * Phase of the second signal relative to the first; positive means the second signal leads.
     *
     * @return phase in degrees, -180 to 180
     */
    public double getPhaseDegrees() {
      return phaseDegrees;
    }

    /**
     * Gain from the first to the second signal, in units of second per first.
     *
     * @return gain
     */
    public double getGain() {
      return gain;
    }
  }

  /**
   * Analyses one signal.
   *
   * @param x uniformly sampled signal
   * @param minPeriodSeconds shortest period of the search band in seconds
   * @param maxPeriodSeconds longest period of the search band in seconds
   * @return the analysis result
   * @throws IllegalArgumentException if the signal is too short or the band is invalid
   */
  public Result analyze(double[] x, double minPeriodSeconds, double maxPeriodSeconds) {
    if (!(minPeriodSeconds > 0.0) || !(maxPeriodSeconds > minPeriodSeconds)) {
      throw new IllegalArgumentException("invalid period band");
    }
    int len = resolveSegmentLength(x.length);
    List<Complex[]> segs = segments(x, len);
    int half = len / 2;
    double df = 1.0 / (len * dt);
    double[] psd = new double[half + 1];
    double winPower = 0.0;
    double[] w = hann(len);
    for (double v : w) {
      winPower += v * v;
    }
    for (Complex[] s : segs) {
      for (int k = 0; k <= half; k++) {
        double a = s[k].abs();
        psd[k] += a * a;
      }
    }
    double scale = 2.0 * dt / (winPower * segs.size());
    for (int k = 0; k <= half; k++) {
      psd[k] *= scale;
    }
    double fLo = 1.0 / maxPeriodSeconds;
    double fHi = 1.0 / minPeriodSeconds;
    int kPk = -1;
    double best = -1.0;
    for (int k = 1; k <= half; k++) {
      double f = k * df;
      if (f >= fLo && f <= fHi && psd[k] > best) {
        best = psd[k];
        kPk = k;
      }
    }
    if (kPk < 0) {
      throw new IllegalArgumentException("no spectral bin inside the period band; use a longer segment");
    }
    Result r = new Result();
    r.frequencyResolutionHz = df;
    r.segments = segs.size();
    r.peakFrequencyHz = refinePeak(psd, kPk, df);
    double bandVar = 0.0;
    double totVar = 0.0;
    for (int k = 1; k <= half; k++) {
      double f = k * df;
      totVar += psd[k] * df;
      if (f >= fLo && f <= fHi) {
        bandVar += psd[k] * df;
      }
    }
    r.bandRms = Math.sqrt(bandVar);
    r.totalRms = Math.sqrt(totVar);
    double lowF = halfPowerCrossing(psd, kPk, -1, df);
    double highF = halfPowerCrossing(psd, kPk, 1, df);
    double width = Math.max(highF - lowF, 1.0e-12);
    r.q = r.peakFrequencyHz / width;
    r.resolutionLimited = width < 2.0 * df;
    int k2 = (int) Math.round(2.0 * r.peakFrequencyHz / df);
    r.secondHarmonicRatio = k2 <= half ? maxAround(psd, k2, 1) / best : 0.0;
    r.envelopeCv = envelopeCv(x, r.peakFrequencyHz);
    return r;
  }

  /**
   * Computes coherence, phase and gain between two signals at a frequency.
   *
   * @param ref reference signal
   * @param y second signal, same length and sampling as the reference
   * @param frequencyHz frequency of interest in Hz
   * @return coherence, phase (positive when y leads ref) and gain
   * @throws IllegalArgumentException if the signals differ in length
   */
  public CrossResult crossSpectrum(double[] ref, double[] y, double frequencyHz) {
    if (ref.length != y.length) {
      throw new IllegalArgumentException("signals must have equal length");
    }
    int len = resolveSegmentLength(ref.length);
    List<Complex[]> sx = segments(ref, len);
    List<Complex[]> sy = segments(y, len);
    int half = len / 2;
    double df = 1.0 / (len * dt);
    int k0 = (int) Math.round(frequencyHz / df);
    int kLo = Math.max(1, k0 - 1);
    int kHi = Math.min(half, k0 + 1);
    Complex pxy = Complex.ZERO;
    double pxx = 0.0;
    double pyy = 0.0;
    for (int s = 0; s < sx.size(); s++) {
      for (int k = kLo; k <= kHi; k++) {
        Complex xx = sx.get(s)[k];
        Complex yy = sy.get(s)[k];
        pxy = pxy.add(xx.conjugate().multiply(yy));
        pxx += xx.abs() * xx.abs();
        pyy += yy.abs() * yy.abs();
      }
    }
    CrossResult c = new CrossResult();
    double mag = pxy.abs();
    c.coherence = pxx > 0.0 && pyy > 0.0 ? mag * mag / (pxx * pyy) : 0.0;
    c.phaseDegrees = Math.toDegrees(Math.atan2(pxy.getImaginary(), pxy.getReal()));
    c.gain = pxx > 0.0 ? mag / pxx : 0.0;
    return c;
  }

  /**
   * Gas-node mass-balance phasor at one frequency: the gas inflow implied by the measured compressor flow and the
   * pressure rate of change, per unit of pressure.
   *
   * <p>
   * With V the gas volume and rho' = d rho / dP, the balance is F_in = m_out + V rho' dP/dt. At angular frequency w the
   * implied inflow per unit pressure is G_m + j w V rho', where G_m is the complex gain from pressure to the compressor
   * mass flow. The phase of the result tells whether the oscillation sits in the inflow and, for a feedback path with
   * negative gain, the delay of that path.
   * </p>
   *
   * @param pressure pressure signal in bar
   * @param massFlowOut total compressor mass flow out of the node in kg/s
   * @param frequencyHz frequency of interest in Hz
   * @param gasVolumeM3 gas volume of the node in m3
   * @param densityPressureDerivative d rho / dP in kg/m3 per bar (isothermal)
   * @return array of {gain magnitude in kg/s per bar, phase in degrees (positive: inflow leads pressure), implied
   * feedback delay in seconds for a negative-gain feedback path}
   */
  public double[] inflowPhasor(double[] pressure, double[] massFlowOut, double frequencyHz, double gasVolumeM3,
      double densityPressureDerivative) {
    CrossResult c = crossSpectrum(pressure, massFlowOut, frequencyHz);
    double w = 2.0 * Math.PI * frequencyHz;
    Complex gm = Complex.valueOf(c.gain * Math.cos(Math.toRadians(c.phaseDegrees)),
        c.gain * Math.sin(Math.toRadians(c.phaseDegrees)));
    Complex f = gm.add(new Complex(0.0, w * gasVolumeM3 * densityPressureDerivative));
    double phase = Math.toDegrees(f.getArgument());
    double delay = ((180.0 - phase) % 360.0 + 360.0) % 360.0 / (360.0 * frequencyHz);
    return new double[] {f.abs(), phase, delay};
  }

  private int resolveSegmentLength(int n) {
    int len = segmentLength;
    if (len <= 0) {
      len = Math.max(16, Integer.highestOneBit(Math.max(16, n / 4)));
    }
    if (len > n) {
      len = Integer.highestOneBit(n);
    }
    if (len < 16) {
      throw new IllegalArgumentException("signal too short for spectral analysis");
    }
    return len;
  }

  private static double[] hann(int len) {
    double[] w = new double[len];
    for (int i = 0; i < len; i++) {
      w[i] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / len);
    }
    return w;
  }

  private List<Complex[]> segments(double[] x, int len) {
    FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
    double[] w = hann(len);
    int step = len / 2;
    List<Complex[]> out = new ArrayList<Complex[]>();
    for (int start = 0; start + len <= x.length; start += step) {
      double[] seg = new double[len];
      double sx = 0.0;
      double sxt = 0.0;
      double st = 0.0;
      double stt = 0.0;
      for (int i = 0; i < len; i++) {
        double v = x[start + i];
        sx += v;
        sxt += v * i;
        st += i;
        stt += (double) i * i;
      }
      double denom = len * stt - st * st;
      double slope = denom != 0.0 ? (len * sxt - st * sx) / denom : 0.0;
      double icpt = (sx - slope * st) / len;
      for (int i = 0; i < len; i++) {
        seg[i] = (x[start + i] - (icpt + slope * i)) * w[i];
      }
      out.add(fft.transform(seg, TransformType.FORWARD));
    }
    if (out.isEmpty()) {
      throw new IllegalArgumentException("signal shorter than one segment");
    }
    return out;
  }

  private static double refinePeak(double[] psd, int k, double df) {
    if (k <= 1 || k >= psd.length - 1) {
      return k * df;
    }
    double a = Math.log(Math.max(psd[k - 1], 1.0e-300));
    double b = Math.log(Math.max(psd[k], 1.0e-300));
    double c = Math.log(Math.max(psd[k + 1], 1.0e-300));
    double den = a - 2.0 * b + c;
    double shift = den != 0.0 ? 0.5 * (a - c) / den : 0.0;
    shift = Math.max(-0.5, Math.min(0.5, shift));
    return (k + shift) * df;
  }

  private static double halfPowerCrossing(double[] psd, int kPk, int dir, double df) {
    double target = psd[kPk] / 2.0;
    int k = kPk;
    while (k + dir > 0 && k + dir < psd.length && psd[k + dir] > target) {
      k += dir;
    }
    int kn = k + dir;
    if (kn <= 0 || kn >= psd.length) {
      return k * df;
    }
    double p0 = psd[k];
    double p1 = psd[kn];
    double frac = p0 != p1 ? (p0 - target) / (p0 - p1) : 0.0;
    return (k + dir * frac) * df;
  }

  private static double maxAround(double[] psd, int k, int w) {
    double m = 0.0;
    for (int i = Math.max(1, k - w); i <= Math.min(psd.length - 1, k + w); i++) {
      m = Math.max(m, psd[i]);
    }
    return m;
  }

  private double envelopeCv(double[] x, double f0) {
    int n = x.length;
    int m = Integer.highestOneBit(n);
    if (m < n) {
      m <<= 1;
    }
    double mean = 0.0;
    for (double v : x) {
      mean += v;
    }
    mean /= n;
    double[] pad = new double[m];
    for (int i = 0; i < n; i++) {
      pad[i] = x[i] - mean;
    }
    FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
    Complex[] spec = fft.transform(pad, TransformType.FORWARD);
    double df = 1.0 / (m * dt);
    double lo = 0.6 * f0;
    double hi = 1.6 * f0;
    for (int k = 0; k < m; k++) {
      double f = k <= m / 2 ? k * df : (m - k) * df;
      boolean inBand = f >= lo && f <= hi;
      if (!inBand || k == 0 || k == m / 2) {
        spec[k] = Complex.ZERO;
      } else if (k < m / 2) {
        spec[k] = spec[k].multiply(2.0);
      } else {
        spec[k] = Complex.ZERO;
      }
    }
    Complex[] analytic = fft.transform(spec, TransformType.INVERSE);
    int trim = (int) Math.min(n / 10, Math.round(2.0 / (f0 * dt)));
    double s = 0.0;
    double s2 = 0.0;
    int cnt = 0;
    for (int i = trim; i < n - trim; i++) {
      double a = analytic[i].abs();
      s += a;
      s2 += a * a;
      cnt++;
    }
    if (cnt == 0 || s == 0.0) {
      return Double.NaN;
    }
    double mu = s / cnt;
    double var = Math.max(0.0, s2 / cnt - mu * mu);
    return Math.sqrt(var) / mu;
  }
}
