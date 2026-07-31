package neqsim.process.diagnostics;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Condition-specific statistical and temporal evidence for one process-data window.
 *
 * <p>
 * The evidence mirrors the auditable tools described by AgentRCA: directional mean shifts, variance changes,
 * correlation discrepancies and short-horizon temporal signatures. It intentionally contains no fault labels.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class RcaEvidence implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final String matchedRegimeId;
  private final double regimeDistance;
  private final Map<String, SignalEvidence> signals;
  private final List<CorrelationEvidence> correlations;
  private final double overallAnomalyScore;

  RcaEvidence(String matchedRegimeId, double regimeDistance, Map<String, SignalEvidence> signals,
      List<CorrelationEvidence> correlations, double overallAnomalyScore) {
    this.matchedRegimeId = matchedRegimeId;
    this.regimeDistance = regimeDistance;
    this.signals = Collections.unmodifiableMap(new LinkedHashMap<String, SignalEvidence>(signals));
    List<CorrelationEvidence> sorted = new ArrayList<CorrelationEvidence>(correlations);
    Collections.sort(sorted, new Comparator<CorrelationEvidence>() {
      @Override
      public int compare(CorrelationEvidence first, CorrelationEvidence second) {
        return Double.compare(Math.abs(second.getDifference()), Math.abs(first.getDifference()));
      }
    });
    this.correlations = Collections.unmodifiableList(sorted);
    this.overallAnomalyScore = overallAnomalyScore;
  }

  /**
   * Returns the matched normal operating regime.
   *
   * @return regime identifier
   */
  public String getMatchedRegimeId() {
    return matchedRegimeId;
  }

  /**
   * Returns the standardized Euclidean distance to the matched regime.
   *
   * @return regime distance
   */
  public double getRegimeDistance() {
    return regimeDistance;
  }

  /**
   * Returns evidence for one signal.
   *
   * @param signalName signal name
   * @return signal evidence
   */
  public SignalEvidence getSignalEvidence(String signalName) {
    SignalEvidence evidence = signals.get(signalName);
    if (evidence == null) {
      throw new IllegalArgumentException("unknown signal evidence: " + signalName);
    }
    return evidence;
  }

  /**
   * Returns all signal evidence in window order.
   *
   * @return unmodifiable map
   */
  public Map<String, SignalEvidence> getSignalEvidence() {
    return signals;
  }

  /**
   * Returns correlation discrepancies sorted by decreasing absolute magnitude.
   *
   * @return unmodifiable correlation-evidence list
   */
  public List<CorrelationEvidence> getCorrelationEvidence() {
    return correlations;
  }

  /**
   * Returns the correlation discrepancy for a signal pair.
   *
   * @param firstSignal first signal
   * @param secondSignal second signal
   * @return observed minus normal correlation
   */
  public double getCorrelationDifference(String firstSignal, String secondSignal) {
    for (CorrelationEvidence evidence : correlations) {
      if (evidence.matches(firstSignal, secondSignal)) {
        return evidence.getDifference();
      }
    }
    throw new IllegalArgumentException("unknown signal pair: " + firstSignal + ", " + secondSignal);
  }

  /**
   * Returns a dimensionless aggregate anomaly score.
   *
   * <p>
   * Values near zero indicate agreement with the matched normal regime. A value near or above one means at least one
   * directional, variance, range, trend or correlation feature crossed its nominal screening scale.
   * </p>
   *
   * @return anomaly score
   */
  public double getOverallAnomalyScore() {
    return overallAnomalyScore;
  }

  /**
   * Serializes the complete evidence report for an external reasoning agent or audit log.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(this);
  }

  /**
   * Evidence calculated for one signal.
   */
  public static final class SignalEvidence implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final String signalName;
    private final double observedMean;
    private final double normalMean;
    private final double meanZScore;
    private final double logVarianceRatio;
    private final double logRangeRatio;
    private final double normalizedSlope;
    private final double lagOneCorrelationDifference;

    SignalEvidence(String signalName, double observedMean, double normalMean, double meanZScore,
        double logVarianceRatio, double logRangeRatio, double normalizedSlope, double lagOneCorrelationDifference) {
      this.signalName = signalName;
      this.observedMean = observedMean;
      this.normalMean = normalMean;
      this.meanZScore = meanZScore;
      this.logVarianceRatio = logVarianceRatio;
      this.logRangeRatio = logRangeRatio;
      this.normalizedSlope = normalizedSlope;
      this.lagOneCorrelationDifference = lagOneCorrelationDifference;
    }

    /**
     * Returns the signal name.
     *
     * @return signal name
     */
    public String getSignalName() {
      return signalName;
    }

    /**
     * Returns the observed window mean.
     *
     * @return observed mean in engineering units
     */
    public double getObservedMean() {
      return observedMean;
    }

    /**
     * Returns the mean of the matched normal regime.
     *
     * @return normal mean in engineering units
     */
    public double getNormalMean() {
      return normalMean;
    }

    /**
     * Returns the signed condition-specific mean shift.
     *
     * @return mean shift in normal-regime standard deviations
     */
    public double getMeanZScore() {
      return meanZScore;
    }

    /**
     * Returns the natural logarithm of observed variance divided by normal variance.
     *
     * @return log variance ratio
     */
    public double getLogVarianceRatio() {
      return logVarianceRatio;
    }

    /**
     * Returns the natural logarithm of observed range divided by normal range.
     *
     * @return log range ratio
     */
    public double getLogRangeRatio() {
      return logRangeRatio;
    }

    /**
     * Returns the within-window linear trend normalized by normal signal variation.
     *
     * @return normalized end-to-end slope
     */
    public double getNormalizedSlope() {
      return normalizedSlope;
    }

    /**
     * Returns observed lag-one correlation minus its normal value.
     *
     * @return lag-one correlation difference
     */
    public double getLagOneCorrelationDifference() {
      return lagOneCorrelationDifference;
    }
  }

  /**
   * Correlation discrepancy for one unordered pair of signals.
   */
  public static final class CorrelationEvidence implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final String firstSignal;
    private final String secondSignal;
    private final double normalCorrelation;
    private final double observedCorrelation;
    private final double difference;

    CorrelationEvidence(String firstSignal, String secondSignal, double normalCorrelation, double observedCorrelation) {
      this.firstSignal = firstSignal;
      this.secondSignal = secondSignal;
      this.normalCorrelation = normalCorrelation;
      this.observedCorrelation = observedCorrelation;
      this.difference = observedCorrelation - normalCorrelation;
    }

    /**
     * Returns the first signal.
     *
     * @return signal name
     */
    public String getFirstSignal() {
      return firstSignal;
    }

    /**
     * Returns the second signal.
     *
     * @return signal name
     */
    public String getSecondSignal() {
      return secondSignal;
    }

    /**
     * Returns the matched-regime correlation.
     *
     * @return normal Pearson correlation
     */
    public double getNormalCorrelation() {
      return normalCorrelation;
    }

    /**
     * Returns the observed-window correlation.
     *
     * @return observed Pearson correlation
     */
    public double getObservedCorrelation() {
      return observedCorrelation;
    }

    /**
     * Returns observed minus normal correlation.
     *
     * @return correlation difference
     */
    public double getDifference() {
      return difference;
    }

    private boolean matches(String first, String second) {
      return (firstSignal.equals(first) && secondSignal.equals(second))
          || (firstSignal.equals(second) && secondSignal.equals(first));
    }
  }
}
