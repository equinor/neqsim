package neqsim.process.fielddevelopment.tieback.capacity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregated result from a host tie-in capacity and holdback study.
 *
 * @author ESOL
 * @version 1.0
 */
public final class TieInCapacityResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Host name. */
  private final String hostName;

  /** Allocation policy used. */
  private final CapacityAllocationPolicy allocationPolicy;

  /** Holdback policy used. */
  private final HoldbackPolicy holdbackPolicy;

  /** Period-by-period results. */
  private final List<TieInPeriodResult> periodResults;

  /** Debottleneck decisions. */
  private final List<DebottleneckDecision> debottleneckDecisions;

  /** Text summary. */
  private final String summary;

  /**
   * Creates an aggregated capacity result.
   *
   * @param hostName host facility name
   * @param allocationPolicy allocation policy used
   * @param holdbackPolicy holdback policy used
   * @param periodResults period-by-period results
   * @param debottleneckDecisions debottleneck decisions
   * @param summary summary text
   */
  public TieInCapacityResult(String hostName, CapacityAllocationPolicy allocationPolicy,
      HoldbackPolicy holdbackPolicy, List<TieInPeriodResult> periodResults,
      List<DebottleneckDecision> debottleneckDecisions, String summary) {
    this.hostName = hostName;
    this.allocationPolicy = allocationPolicy;
    this.holdbackPolicy = holdbackPolicy;
    this.periodResults = new ArrayList<TieInPeriodResult>(periodResults);
    this.debottleneckDecisions = new ArrayList<DebottleneckDecision>(debottleneckDecisions);
    Collections.sort(this.debottleneckDecisions);
    this.summary = summary;
  }

  /**
   * Gets the host name.
   *
   * @return host name
   */
  public String getHostName() {
    return hostName;
  }

  /**
   * Gets the allocation policy.
   *
   * @return allocation policy
   */
  public CapacityAllocationPolicy getAllocationPolicy() {
    return allocationPolicy;
  }

  /**
   * Gets the holdback policy.
   *
   * @return holdback policy
   */
  public HoldbackPolicy getHoldbackPolicy() {
    return holdbackPolicy;
  }

  /**
   * Gets period results.
   *
   * @return unmodifiable period results
   */
  public List<TieInPeriodResult> getPeriodResults() {
    return Collections.unmodifiableList(periodResults);
  }

  /**
   * Gets debottleneck decisions.
   *
   * @return unmodifiable debottleneck decisions
   */
  public List<DebottleneckDecision> getDebottleneckDecisions() {
    return Collections.unmodifiableList(debottleneckDecisions);
  }

  /**
   * Checks whether any satellite production is held back.
   *
   * @return true if held-back gas, oil, water, or liquid exists
   */
  public boolean hasHoldback() {
    return getTotalHeldBackGasMSm3() > 0.0 || getTotalHeldBackOilBbl() > 0.0
        || getTotalHeldBackWaterM3() > 0.0 || getTotalHeldBackLiquidM3() > 0.0;
  }

  /**
   * Gets total accepted satellite gas volume.
   *
   * @return accepted satellite gas in MSm3
   */
  public double getTotalAcceptedGasMSm3() {
    double total = 0.0;
    for (TieInPeriodResult period : periodResults) {
      total += period.getAcceptedSatellite().getGasVolumeMSm3();
    }
    return total;
  }

  /**
   * Gets total held-back satellite gas volume.
   *
   * @return held-back satellite gas in MSm3
   */
  public double getTotalHeldBackGasMSm3() {
    double total = 0.0;
    for (TieInPeriodResult period : periodResults) {
      total += period.getHeldBackSatellite().getGasVolumeMSm3();
    }
    return total;
  }

  /**
   * Gets total accepted satellite oil volume.
   *
   * @return accepted satellite oil in barrels
   */
  public double getTotalAcceptedOilBbl() {
    double total = 0.0;
    for (TieInPeriodResult period : periodResults) {
      total += period.getAcceptedSatellite().getOilVolumeBbl();
    }
    return total;
  }

  /**
   * Gets total held-back satellite oil volume.
   *
   * @return held-back satellite oil in barrels
   */
  public double getTotalHeldBackOilBbl() {
    double total = 0.0;
    for (TieInPeriodResult period : periodResults) {
      total += period.getHeldBackSatellite().getOilVolumeBbl();
    }
    return total;
  }

  /**
   * Gets total held-back satellite water volume.
   *
   * @return held-back satellite water in m3
   */
  public double getTotalHeldBackWaterM3() {
    double total = 0.0;
    for (TieInPeriodResult period : periodResults) {
      total += period.getHeldBackSatellite().getWaterVolumeM3();
    }
    return total;
  }

  /**
   * Gets total held-back satellite liquid volume.
   *
   * @return held-back satellite liquid in m3
   */
  public double getTotalHeldBackLiquidM3() {
    double total = 0.0;
    for (TieInPeriodResult period : periodResults) {
      total += period.getHeldBackSatellite().getLiquidVolumeM3();
    }
    return total;
  }

  /**
   * Gets total deferred value before discounting.
   *
   * @return deferred value in MUSD
   */
  public double getTotalDeferredValueMusd() {
    double total = 0.0;
    for (TieInPeriodResult period : periodResults) {
      total += period.getDeferredValueMusd();
    }
    return total;
  }

  /**
   * Gets total discounted deferred value.
   *
   * @return discounted deferred value in MUSD
   */
  public double getTotalDeferredValueNpvMusd() {
    double total = 0.0;
    for (TieInPeriodResult period : periodResults) {
      total += period.getDeferredValueNpvMusd();
    }
    return total;
  }

  /**
   * Gets the primary bottleneck from the most constrained period.
   *
   * @return primary bottleneck name, or "None" if no period has a bottleneck
   */
  public String getPrimaryBottleneck() {
    TieInPeriodResult mostConstrained = null;
    double highestUtilization = -1.0;
    for (TieInPeriodResult period : periodResults) {
      if (period.getProcessBottleneckUtilization() > highestUtilization) {
        highestUtilization = period.getProcessBottleneckUtilization();
        mostConstrained = period;
      }
    }
    if (mostConstrained != null && mostConstrained.getPrimaryBottleneck() != null) {
      return mostConstrained.getPrimaryBottleneck();
    }
    return "None";
  }

  /**
   * Gets the result summary.
   *
   * @return summary text
   */
  public String getSummary() {
    return summary;
  }

  /**
   * Formats the period results as a Markdown table.
   *
   * @return Markdown table
   */
  public String toMarkdownTable() {
    StringBuilder builder = new StringBuilder();
    builder.append("| Year | Sat req gas | Sat acc gas | Held gas | Bottleneck | Deferred NPV |\n");
    builder.append("|------|-------------|-------------|----------|------------|--------------|\n");
    for (TieInPeriodResult period : periodResults) {
      builder.append(String.format("| %d | %.2f | %.2f | %.2f | %s | %.1f |\n", period.getYear(),
          period.getSatelliteRequest().getGasRateMSm3d(),
          period.getAcceptedSatellite().getGasRateMSm3d(),
          period.getHeldBackSatellite().getGasRateMSm3d(), period.getPrimaryBottleneck(),
          period.getDeferredValueNpvMusd()));
    }
    return builder.toString();
  }

  /**
   * Formats the period results as CSV.
   *
   * @return CSV table
   */
  public String toCsv() {
    StringBuilder builder = new StringBuilder();
    builder.append("year,satellite_request_gas_MSm3d,accepted_satellite_gas_MSm3d,");
    builder.append("held_back_gas_MSm3d,bottleneck,deferred_value_npv_MUSD\n");
    for (TieInPeriodResult period : periodResults) {
      builder.append(String.format("%d,%.6f,%.6f,%.6f,%s,%.6f\n", period.getYear(),
          period.getSatelliteRequest().getGasRateMSm3d(),
          period.getAcceptedSatellite().getGasRateMSm3d(),
          period.getHeldBackSatellite().getGasRateMSm3d(), period.getPrimaryBottleneck(),
          period.getDeferredValueNpvMusd()));
    }
    return builder.toString();
  }
}
