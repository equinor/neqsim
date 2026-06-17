package neqsim.process.fielddevelopment.tieback.capacity;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Capacity, holdback, and bottleneck result for one host tie-in planning period.
 *
 * @author ESOL
 * @version 1.0
 */
public final class TieInPeriodResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Period name used in reports. */
  private final String periodName;

  /** Calendar year for the period. */
  private final int year;

  /** Requested base-host production. */
  private final ProductionLoad baseRequest;

  /** Accepted base-host production. */
  private final ProductionLoad acceptedBase;

  /** Satellite production scheduled for this period before deferred backlog. */
  private final ProductionLoad scheduledSatellite;

  /** Deferred satellite backlog entering this period. */
  private final ProductionLoad deferredIntoPeriod;

  /** Satellite request including scheduled and deferred volumes. */
  private final ProductionLoad satelliteRequest;

  /** Accepted satellite production. */
  private final ProductionLoad acceptedSatellite;

  /** Satellite production not accepted in this period. */
  private final ProductionLoad heldBackSatellite;

  /** Satellite backlog carried to the next period. */
  private final ProductionLoad deferredToNextPeriod;

  /** Nameplate allocation scale applied to the satellite request. */
  private final double satelliteAllocationScale;

  /** Nameplate bottleneck category. */
  private final String nameplateBottleneck;

  /** True if an attached process model was used. */
  private final boolean processModelUsed;

  /** True if the process model was feasible after any process holdback. */
  private final boolean processCapacityAvailable;

  /** Process-model bottleneck name. */
  private final String processBottleneck;

  /** Process-model bottleneck utilization fraction. */
  private final double processBottleneckUtilization;

  /** Equipment utilization summary from the process model. */
  private final Map<String, Double> processUtilizationSummary;

  /** Undiscounted deferred or curtailed value in million USD. */
  private final double deferredValueMusd;

  /** Discounted deferred or curtailed value in million USD. */
  private final double deferredValueNpvMusd;

  /** Concise result summary. */
  private final String summary;

  /**
   * Creates a period result.
   *
   * @param periodName period name used in reports
   * @param year calendar year
   * @param baseRequest requested base-host production
   * @param acceptedBase accepted base-host production
   * @param scheduledSatellite scheduled satellite production before deferred backlog
   * @param deferredIntoPeriod deferred satellite backlog entering the period
   * @param satelliteRequest satellite request including scheduled and deferred production
   * @param acceptedSatellite accepted satellite production
   * @param heldBackSatellite satellite production not accepted in this period
   * @param deferredToNextPeriod satellite backlog carried to the next period
   * @param satelliteAllocationScale allocation scale applied to satellite production
   * @param nameplateBottleneck nameplate bottleneck category
   * @param processModelUsed true if an attached process model was checked
   * @param processCapacityAvailable true if process model capacity is available
   * @param processBottleneck process-model bottleneck name
   * @param processBottleneckUtilization process-model bottleneck utilization fraction
   * @param processUtilizationSummary process utilization summary by equipment name in percent
   * @param deferredValueMusd deferred value in MUSD before discounting
   * @param deferredValueNpvMusd deferred value in MUSD after discounting
   * @param summary concise text summary
   */
  public TieInPeriodResult(String periodName, int year, ProductionLoad baseRequest,
      ProductionLoad acceptedBase, ProductionLoad scheduledSatellite,
      ProductionLoad deferredIntoPeriod, ProductionLoad satelliteRequest,
      ProductionLoad acceptedSatellite, ProductionLoad heldBackSatellite,
      ProductionLoad deferredToNextPeriod, double satelliteAllocationScale,
      String nameplateBottleneck, boolean processModelUsed, boolean processCapacityAvailable,
      String processBottleneck, double processBottleneckUtilization,
      Map<String, Double> processUtilizationSummary, double deferredValueMusd,
      double deferredValueNpvMusd, String summary) {
    this.periodName = periodName;
    this.year = year;
    this.baseRequest = baseRequest;
    this.acceptedBase = acceptedBase;
    this.scheduledSatellite = scheduledSatellite;
    this.deferredIntoPeriod = deferredIntoPeriod;
    this.satelliteRequest = satelliteRequest;
    this.acceptedSatellite = acceptedSatellite;
    this.heldBackSatellite = heldBackSatellite;
    this.deferredToNextPeriod = deferredToNextPeriod;
    this.satelliteAllocationScale = satelliteAllocationScale;
    this.nameplateBottleneck = nameplateBottleneck;
    this.processModelUsed = processModelUsed;
    this.processCapacityAvailable = processCapacityAvailable;
    this.processBottleneck = processBottleneck;
    this.processBottleneckUtilization = processBottleneckUtilization;
    this.processUtilizationSummary = new LinkedHashMap<String, Double>(processUtilizationSummary);
    this.deferredValueMusd = deferredValueMusd;
    this.deferredValueNpvMusd = deferredValueNpvMusd;
    this.summary = summary;
  }

  /**
   * Gets the period name.
   *
   * @return period name
   */
  public String getPeriodName() {
    return periodName;
  }

  /**
   * Gets the year.
   *
   * @return calendar year
   */
  public int getYear() {
    return year;
  }

  /**
   * Gets requested base production.
   *
   * @return base production request
   */
  public ProductionLoad getBaseRequest() {
    return baseRequest;
  }

  /**
   * Gets accepted base production.
   *
   * @return accepted base production
   */
  public ProductionLoad getAcceptedBase() {
    return acceptedBase;
  }

  /**
   * Gets scheduled satellite production before deferred backlog.
   *
   * @return scheduled satellite production
   */
  public ProductionLoad getScheduledSatellite() {
    return scheduledSatellite;
  }

  /**
   * Gets deferred production entering the period.
   *
   * @return deferred satellite backlog entering the period
   */
  public ProductionLoad getDeferredIntoPeriod() {
    return deferredIntoPeriod;
  }

  /**
   * Gets satellite request including scheduled and deferred production.
   *
   * @return satellite production request
   */
  public ProductionLoad getSatelliteRequest() {
    return satelliteRequest;
  }

  /**
   * Gets accepted satellite production.
   *
   * @return accepted satellite production
   */
  public ProductionLoad getAcceptedSatellite() {
    return acceptedSatellite;
  }

  /**
   * Gets satellite production held back in the period.
   *
   * @return held-back satellite production
   */
  public ProductionLoad getHeldBackSatellite() {
    return heldBackSatellite;
  }

  /**
   * Gets satellite production deferred to the next period.
   *
   * @return deferred satellite production
   */
  public ProductionLoad getDeferredToNextPeriod() {
    return deferredToNextPeriod;
  }

  /**
   * Gets the satellite allocation scale.
   *
   * @return scale from zero to one for accepted satellite production
   */
  public double getSatelliteAllocationScale() {
    return satelliteAllocationScale;
  }

  /**
   * Gets the nameplate bottleneck.
   *
   * @return nameplate bottleneck category
   */
  public String getNameplateBottleneck() {
    return nameplateBottleneck;
  }

  /**
   * Checks whether a process model was used.
   *
   * @return true if a process model was used
   */
  public boolean isProcessModelUsed() {
    return processModelUsed;
  }

  /**
   * Checks whether process capacity is available.
   *
   * @return true if process model capacity is available
   */
  public boolean isProcessCapacityAvailable() {
    return processCapacityAvailable;
  }

  /**
   * Gets the process bottleneck.
   *
   * @return process bottleneck name, or null if none exists
   */
  public String getProcessBottleneck() {
    return processBottleneck;
  }

  /**
   * Gets the process bottleneck utilization.
   *
   * @return process bottleneck utilization as a fraction
   */
  public double getProcessBottleneckUtilization() {
    return processBottleneckUtilization;
  }

  /**
   * Gets the primary bottleneck, preferring process constraints over nameplate categories.
   *
   * @return primary bottleneck name
   */
  public String getPrimaryBottleneck() {
    if (processBottleneck != null && !processBottleneck.trim().isEmpty()
        && !"None".equalsIgnoreCase(processBottleneck)) {
      return processBottleneck;
    }
    return nameplateBottleneck;
  }

  /**
   * Gets process utilization summary.
   *
   * @return unmodifiable utilization summary in percent
   */
  public Map<String, Double> getProcessUtilizationSummary() {
    return Collections.unmodifiableMap(processUtilizationSummary);
  }

  /**
   * Gets deferred value before discounting.
   *
   * @return deferred value in MUSD
   */
  public double getDeferredValueMusd() {
    return deferredValueMusd;
  }

  /**
   * Gets discounted deferred value.
   *
   * @return discounted deferred value in MUSD
   */
  public double getDeferredValueNpvMusd() {
    return deferredValueNpvMusd;
  }

  /**
   * Gets the period summary.
   *
   * @return summary text
   */
  public String getSummary() {
    return summary;
  }
}
