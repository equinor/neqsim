package neqsim.process.fastsimulation;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Timing and conservation summary for a K-value proxy benchmark against rigorous process reruns.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class KValueProcessBenchmarkResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Number of benchmark cases. */
  private final int caseCount;

  /** Total rigorous rerun time in nanoseconds. */
  private final long rigorousTotalTimeNanos;

  /** Total proxy rerun time in nanoseconds. */
  private final long proxyTotalTimeNanos;

  /** Maximum relative terminal mass deviation between proxy and rigorous runs. */
  private final double maxTerminalMassDeviation;

  /**
   * Creates a benchmark result.
   *
   * @param caseCount number of benchmark cases
   * @param rigorousTotalTimeNanos total rigorous rerun time in nanoseconds
   * @param proxyTotalTimeNanos total proxy rerun time in nanoseconds
   * @param maxTerminalMassDeviation maximum relative terminal mass deviation
   */
  KValueProcessBenchmarkResult(int caseCount, long rigorousTotalTimeNanos, long proxyTotalTimeNanos,
      double maxTerminalMassDeviation) {
    this.caseCount = caseCount;
    this.rigorousTotalTimeNanos = rigorousTotalTimeNanos;
    this.proxyTotalTimeNanos = proxyTotalTimeNanos;
    this.maxTerminalMassDeviation = maxTerminalMassDeviation;
  }

  /**
   * Gets the number of benchmark cases.
   *
   * @return case count
   */
  public int getCaseCount() {
    return caseCount;
  }

  /**
   * Gets the total rigorous time.
   *
   * @return total rigorous time in nanoseconds
   */
  public long getRigorousTotalTimeNanos() {
    return rigorousTotalTimeNanos;
  }

  /**
   * Gets the total proxy time.
   *
   * @return total proxy time in nanoseconds
   */
  public long getProxyTotalTimeNanos() {
    return proxyTotalTimeNanos;
  }

  /**
   * Gets the average rigorous time.
   *
   * @return average rigorous time in nanoseconds
   */
  public double getRigorousAverageTimeNanos() {
    return caseCount == 0 ? 0.0 : ((double) rigorousTotalTimeNanos) / caseCount;
  }

  /**
   * Gets the average proxy time.
   *
   * @return average proxy time in nanoseconds
   */
  public double getProxyAverageTimeNanos() {
    return caseCount == 0 ? 0.0 : ((double) proxyTotalTimeNanos) / caseCount;
  }

  /**
   * Gets the measured speedup.
   *
   * @return rigorous average time divided by proxy average time
   */
  public double getSpeedup() {
    double proxyAverage = getProxyAverageTimeNanos();
    return proxyAverage <= 0.0 ? Double.POSITIVE_INFINITY : getRigorousAverageTimeNanos() / proxyAverage;
  }

  /**
   * Gets the maximum terminal mass deviation.
   *
   * @return maximum relative terminal mass deviation
   */
  public double getMaxTerminalMassDeviation() {
    return maxTerminalMassDeviation;
  }

  /**
   * Serializes the benchmark result to JSON.
   *
   * @return pretty-printed JSON
   */
  public String toJson() {
    Map<String, Object> root = new LinkedHashMap<String, Object>();
    root.put("schemaVersion", KValueProcessResult.SCHEMA_VERSION);
    root.put("method", "cached-k-value-process-simulation-benchmark");
    root.put("caseCount", caseCount);
    root.put("rigorousTotalTimeNanos", rigorousTotalTimeNanos);
    root.put("proxyTotalTimeNanos", proxyTotalTimeNanos);
    root.put("rigorousAverageTimeNanos", getRigorousAverageTimeNanos());
    root.put("proxyAverageTimeNanos", getProxyAverageTimeNanos());
    root.put("speedup", getSpeedup());
    root.put("maxTerminalMassDeviation", maxTerminalMassDeviation);
    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
  }
}
