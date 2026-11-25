package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reusable, scale-aware comparison metrics for two-fluid benchmark and acceptance suites. */
public final class TwoFluidBenchmarkMetrics {
  private TwoFluidBenchmarkMetrics() {
  }

  /** Fit the least-squares log-log exponent in {@code response = constant * rate^n}. */
  public static double fitRateExponent(double[] rates, double[] responses) {
    requireSameLength(rates, responses, 2);
    double meanX = 0.0;
    double meanY = 0.0;
    for (int index = 0; index < rates.length; index++) {
      requirePositiveFinite(rates[index], "Rate");
      requirePositiveFinite(responses[index], "Response");
      meanX += Math.log(rates[index]);
      meanY += Math.log(responses[index]);
    }
    meanX /= rates.length;
    meanY /= rates.length;
    double covariance = 0.0;
    double variance = 0.0;
    for (int index = 0; index < rates.length; index++) {
      double dx = Math.log(rates[index]) - meanX;
      covariance += dx * (Math.log(responses[index]) - meanY);
      variance += dx * dx;
    }
    if (!(variance > 0.0)) {
      throw new IllegalArgumentException("Rate sweep must contain at least two distinct rates");
    }
    return covariance / variance;
  }

  /** Return maximum value divided by the sample median of a positive profile. */
  public static double maximumToMedianRatio(double[] profile) {
    requireFiniteValues(profile, 1, "Profile");
    double maximum = -Double.MAX_VALUE;
    for (double value : profile) {
      if (value < 0.0) {
        throw new IllegalArgumentException("Profile values must be non-negative");
      }
      maximum = Math.max(maximum, value);
    }
    double median = percentile(profile, 0.5);
    if (!(median > 0.0)) {
      throw new IllegalArgumentException("Profile median must be positive");
    }
    return maximum / median;
  }

  /** Symmetric relative spread using the larger absolute result as scale. */
  public static double relativeMeshSpread(double first, double second) {
    if (!Double.isFinite(first) || !Double.isFinite(second)) {
      throw new IllegalArgumentException("Mesh results must be finite");
    }
    double scale = Math.max(Math.abs(first), Math.abs(second));
    return scale == 0.0 ? 0.0 : Math.abs(first - second) / scale;
  }

  /**
   * Analyze a settled signal with robust P10-P90 amplitude and completed median-upcrossing cycles.
   *
   * @param timeSeconds strictly increasing sample times
   * @param signal sampled pressure, flow, or holdup signal
   * @param settledWindowStartSeconds first time included in the settled window
   * @return immutable limit-cycle metrics
   */
  public static LimitCycleMetrics analyzeLimitCycle(double[] timeSeconds, double[] signal,
      double settledWindowStartSeconds) {
    requireSameLength(timeSeconds, signal, 3);
    if (!Double.isFinite(settledWindowStartSeconds)) {
      throw new IllegalArgumentException("Settled-window start must be finite");
    }
    List<Double> selectedTimes = new ArrayList<Double>();
    List<Double> selectedSignal = new ArrayList<Double>();
    double previousTime = -Double.MAX_VALUE;
    for (int index = 0; index < timeSeconds.length; index++) {
      double time = timeSeconds[index];
      double value = signal[index];
      if (!Double.isFinite(time) || !Double.isFinite(value) || time <= previousTime) {
        throw new IllegalArgumentException("Times must be finite and strictly increasing; signal must be finite");
      }
      previousTime = time;
      if (time >= settledWindowStartSeconds) {
        selectedTimes.add(time);
        selectedSignal.add(value);
      }
    }
    if (selectedTimes.size() < 3) {
      throw new IllegalArgumentException("Settled window must contain at least three samples");
    }

    double[] values = new double[selectedSignal.size()];
    for (int index = 0; index < values.length; index++) {
      values[index] = selectedSignal.get(index);
    }
    double p10 = percentile(values, 0.10);
    double median = percentile(values, 0.50);
    double p90 = percentile(values, 0.90);
    double robustBand = p90 - p10;

    List<Double> crossingTimes = new ArrayList<Double>();
    List<Integer> crossingUpperIndices = new ArrayList<Integer>();
    if (robustBand > Math.ulp(Math.max(1.0, Math.abs(median)))) {
      for (int index = 1; index < values.length; index++) {
        double before = values[index - 1] - median;
        double after = values[index] - median;
        if (before < 0.0 && after >= 0.0 && after > before) {
          double fraction = -before / (after - before);
          double crossing = selectedTimes.get(index - 1)
              + fraction * (selectedTimes.get(index) - selectedTimes.get(index - 1));
          crossingTimes.add(crossing);
          crossingUpperIndices.add(index);
        }
      }
    }
    int completedCycles = Math.max(0, crossingTimes.size() - 1);
    double period = Double.NaN;
    double medianCyclePeakToPeak = Double.NaN;
    double relativeCycleAmplitudeSpread = Double.NaN;
    double relativeCyclePeriodSpread = Double.NaN;
    if (completedCycles > 0) {
      double[] intervals = new double[completedCycles];
      double[] amplitudes = new double[completedCycles];
      for (int index = 0; index < intervals.length; index++) {
        intervals[index] = crossingTimes.get(index + 1) - crossingTimes.get(index);
        double cycleMinimum = median;
        double cycleMaximum = median;
        int first = crossingUpperIndices.get(index);
        int last = crossingUpperIndices.get(index + 1) - 1;
        for (int sample = first; sample <= last; sample++) {
          cycleMinimum = Math.min(cycleMinimum, values[sample]);
          cycleMaximum = Math.max(cycleMaximum, values[sample]);
        }
        amplitudes[index] = cycleMaximum - cycleMinimum;
      }
      period = percentile(intervals, 0.5);
      medianCyclePeakToPeak = percentile(amplitudes, 0.5);
      relativeCycleAmplitudeSpread = relativeRange(amplitudes, medianCyclePeakToPeak);
      relativeCyclePeriodSpread = relativeRange(intervals, period);
    }
    return new LimitCycleMetrics(p10, median, p90, period, medianCyclePeakToPeak, relativeCycleAmplitudeSpread,
        relativeCyclePeriodSpread, completedCycles, values.length, selectedTimes.get(0),
        selectedTimes.get(selectedTimes.size() - 1), toPrimitive(crossingTimes));
  }

  /**
   * Compare the repeatability of the phase offset between two settled limit-cycle signals.
   *
   * <p>
   * Each reference median upcrossing is paired with the nearest unused response upcrossing. Phase offsets are wrapped
   * onto one cycle before their circular mean and maximum deviation are evaluated. A constant nonzero lag is therefore
   * accepted, while drifting, subharmonic or unrelated oscillations are not.
   * </p>
   *
   * @param reference settled reference signal, normally pressure
   * @param response settled response signal, normally outlet liquid production
   * @return immutable phase-lock summary
   */
  public static PhaseLockMetrics analyzePhaseLock(LimitCycleMetrics reference, LimitCycleMetrics response) {
    if (reference == null || response == null) {
      throw new IllegalArgumentException("Phase-lock signals cannot be null");
    }
    double period = reference.getPeriodSeconds();
    double[] referenceCrossings = reference.getMedianUpcrossingTimesSeconds();
    double[] responseCrossings = response.getMedianUpcrossingTimesSeconds();
    if (!(period > 0.0) || !Double.isFinite(period) || referenceCrossings.length == 0
        || responseCrossings.length == 0) {
      return new PhaseLockMetrics(Double.NaN, Double.NaN, 0, referenceCrossings.length, responseCrossings.length,
          Double.NaN);
    }
    double sin = 0.0;
    double cos = 0.0;
    double[] offsets = new double[Math.min(referenceCrossings.length, responseCrossings.length)];
    boolean[] usedResponseCrossings = new boolean[responseCrossings.length];
    int count = 0;
    for (double referenceCrossing : referenceCrossings) {
      int nearestIndex = -1;
      for (int index = 0; index < responseCrossings.length; index++) {
        if (!usedResponseCrossings[index]
            && (nearestIndex < 0 || Math.abs(responseCrossings[index] - referenceCrossing) < Math
                .abs(responseCrossings[nearestIndex] - referenceCrossing))) {
          nearestIndex = index;
        }
      }
      if (nearestIndex < 0) {
        break;
      }
      usedResponseCrossings[nearestIndex] = true;
      double nearest = responseCrossings[nearestIndex];
      double offset = wrapCycles((nearest - referenceCrossing) / period);
      offsets[count++] = offset;
      double angle = 2.0 * Math.PI * offset;
      sin += Math.sin(angle);
      cos += Math.cos(angle);
    }
    double meanAngle = Math.atan2(sin, cos);
    double maximumDeviation = 0.0;
    for (int index = 0; index < count; index++) {
      maximumDeviation = Math.max(maximumDeviation, Math.abs(wrapCycles(offsets[index] - meanAngle / (2.0 * Math.PI))));
    }
    double resultant = Math.sqrt(sin * sin + cos * cos) / count;
    return new PhaseLockMetrics(meanAngle / (2.0 * Math.PI), maximumDeviation, count, referenceCrossings.length,
        responseCrossings.length, resultant);
  }

  /** Calculate the full relative range around a positive finite median. */
  private static double relativeRange(double[] values, double median) {
    if (!(median > 0.0) || !Double.isFinite(median)) {
      return Double.NaN;
    }
    double minimum = Double.POSITIVE_INFINITY;
    double maximum = Double.NEGATIVE_INFINITY;
    for (double value : values) {
      minimum = Math.min(minimum, value);
      maximum = Math.max(maximum, value);
    }
    return (maximum - minimum) / median;
  }

  /** Wrap a phase offset to [-0.5, 0.5). */
  private static double wrapCycles(double cycles) {
    return cycles - Math.floor(cycles + 0.5);
  }

  /** Convert boxed samples to a defensive primitive array. */
  private static double[] toPrimitive(List<Double> values) {
    double[] result = new double[values.size()];
    for (int index = 0; index < values.size(); index++) {
      result[index] = values.get(index);
    }
    return result;
  }

  private static double percentile(double[] values, double fraction) {
    double[] sorted = Arrays.copyOf(values, values.length);
    Arrays.sort(sorted);
    double position = fraction * (sorted.length - 1);
    int lower = (int) Math.floor(position);
    int upper = (int) Math.ceil(position);
    if (lower == upper) {
      return sorted[lower];
    }
    double weight = position - lower;
    return sorted[lower] * (1.0 - weight) + sorted[upper] * weight;
  }

  private static void requireSameLength(double[] first, double[] second, int minimumLength) {
    requireFiniteValues(first, minimumLength, "First array");
    requireFiniteValues(second, minimumLength, "Second array");
    if (first.length != second.length) {
      throw new IllegalArgumentException("Arrays must have the same length");
    }
  }

  private static void requireFiniteValues(double[] values, int minimumLength, String label) {
    if (values == null || values.length < minimumLength) {
      throw new IllegalArgumentException(label + " must contain at least " + minimumLength + " values");
    }
    for (double value : values) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException(label + " values must be finite");
      }
    }
  }

  private static void requirePositiveFinite(double value, String label) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(label + " must be positive and finite");
    }
  }

  /** Immutable transient limit-cycle summary. */
  public static final class LimitCycleMetrics implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double p10;
    private final double median;
    private final double p90;
    private final double periodSeconds;
    private final double medianCyclePeakToPeak;
    private final double relativeCycleAmplitudeSpread;
    private final double relativeCyclePeriodSpread;
    private final int completedCycleCount;
    private final int sampleCount;
    private final double windowStartSeconds;
    private final double windowEndSeconds;
    private final double[] medianUpcrossingTimesSeconds;

    private LimitCycleMetrics(double p10, double median, double p90, double periodSeconds, double medianCyclePeakToPeak,
        double relativeCycleAmplitudeSpread, double relativeCyclePeriodSpread, int completedCycleCount, int sampleCount,
        double windowStartSeconds, double windowEndSeconds, double[] medianUpcrossingTimesSeconds) {
      this.p10 = p10;
      this.median = median;
      this.p90 = p90;
      this.periodSeconds = periodSeconds;
      this.medianCyclePeakToPeak = medianCyclePeakToPeak;
      this.relativeCycleAmplitudeSpread = relativeCycleAmplitudeSpread;
      this.relativeCyclePeriodSpread = relativeCyclePeriodSpread;
      this.completedCycleCount = completedCycleCount;
      this.sampleCount = sampleCount;
      this.windowStartSeconds = windowStartSeconds;
      this.windowEndSeconds = windowEndSeconds;
      this.medianUpcrossingTimesSeconds = medianUpcrossingTimesSeconds.clone();
    }

    public double getP10() {
      return p10;
    }

    public double getMedian() {
      return median;
    }

    public double getP90() {
      return p90;
    }

    public double getP10ToP90Band() {
      return p90 - p10;
    }

    public double getPeriodSeconds() {
      return periodSeconds;
    }

    /** @return median peak-to-peak amplitude of complete settled cycles, or NaN without a complete cycle */
    public double getMedianCyclePeakToPeak() {
      return medianCyclePeakToPeak;
    }

    /** @return full range of complete-cycle amplitudes divided by their median, or NaN without a cycle */
    public double getRelativeCycleAmplitudeSpread() {
      return relativeCycleAmplitudeSpread;
    }

    /** @return full range of complete-cycle periods divided by their median, or NaN without a cycle */
    public double getRelativeCyclePeriodSpread() {
      return relativeCyclePeriodSpread;
    }

    public int getCompletedCycleCount() {
      return completedCycleCount;
    }

    public int getSampleCount() {
      return sampleCount;
    }

    public double getWindowStartSeconds() {
      return windowStartSeconds;
    }

    public double getWindowEndSeconds() {
      return windowEndSeconds;
    }

    public boolean hasRepeatedCycle() {
      return completedCycleCount > 0 && Double.isFinite(periodSeconds);
    }

    /** @return defensive median-upcrossing times in s */
    public double[] getMedianUpcrossingTimesSeconds() {
      return medianUpcrossingTimesSeconds.clone();
    }
  }

  /** Immutable phase-lock summary for two settled periodic signals. */
  public static final class PhaseLockMetrics implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double meanPhaseOffsetCycles;
    private final double maximumPhaseDeviationCycles;
    private final int matchedCrossings;
    private final int referenceCrossings;
    private final int responseCrossings;
    private final double circularResultant;

    private PhaseLockMetrics(double meanPhaseOffsetCycles, double maximumPhaseDeviationCycles, int matchedCrossings,
        int referenceCrossings, int responseCrossings, double circularResultant) {
      this.meanPhaseOffsetCycles = meanPhaseOffsetCycles;
      this.maximumPhaseDeviationCycles = maximumPhaseDeviationCycles;
      this.matchedCrossings = matchedCrossings;
      this.referenceCrossings = referenceCrossings;
      this.responseCrossings = responseCrossings;
      this.circularResultant = circularResultant;
    }

    /** @return circular mean response lag in reference cycles */
    public double getMeanPhaseOffsetCycles() {
      return meanPhaseOffsetCycles;
    }

    /** @return maximum wrapped deviation from the mean lag in cycles */
    public double getMaximumPhaseDeviationCycles() {
      return maximumPhaseDeviationCycles;
    }

    /** @return number of reference crossings paired to the response */
    public int getMatchedCrossings() {
      return matchedCrossings;
    }

    /** @return median upcrossings observed in the reference window */
    public int getReferenceCrossings() {
      return referenceCrossings;
    }

    /** @return median upcrossings observed in the response window */
    public int getResponseCrossings() {
      return responseCrossings;
    }

    /** @return circular concentration from zero to one, or NaN without matched crossings */
    public double getCircularResultant() {
      return circularResultant;
    }

    /** @return true when at least two one-to-one crossings cover both signals and define a repeatable phase relation */
    public boolean hasRepeatedPhaseLock() {
      return matchedCrossings >= 2 && matchedCrossings >= Math.max(referenceCrossings, responseCrossings) - 1
          && Double.isFinite(maximumPhaseDeviationCycles);
    }
  }
}
