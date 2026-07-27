package neqsim.process.equipment.reactor.sulfurrecovery;

import java.io.Serializable;

/** Immutable set of material-balance, recovery, emissions, and operating KPIs for an SRU run. */
public final class SulfurRecoveryPerformance implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final double feedSulfurKgPerHour;
  private final double recoveredSulfurKgPerHour;
  private final double sulfurRecoveryPercent;
  private final double overallSulfurRecoveryPercent;
  private final double sulfurBalanceRelativeError;
  private final double tailGasH2SToSO2Ratio;
  private final double oxygenDemandMoles;
  private final double furnaceTemperatureK;
  private final double stackSO2KgPerHour;
  private final int airControlIterations;
  private final int recycleIterations;
  private final boolean airControlConverged;
  private final boolean recycleConverged;

  /** Package-private construction by the integrated SRU model. */
  SulfurRecoveryPerformance(double feedSulfurKgPerHour, double recoveredSulfurKgPerHour, double sulfurRecoveryPercent,
      double overallSulfurRecoveryPercent, double sulfurBalanceRelativeError, double tailGasH2SToSO2Ratio,
      double oxygenDemandMoles, double furnaceTemperatureK, double stackSO2KgPerHour, int airControlIterations,
      int recycleIterations, boolean airControlConverged, boolean recycleConverged) {
    this.feedSulfurKgPerHour = feedSulfurKgPerHour;
    this.recoveredSulfurKgPerHour = recoveredSulfurKgPerHour;
    this.sulfurRecoveryPercent = sulfurRecoveryPercent;
    this.overallSulfurRecoveryPercent = overallSulfurRecoveryPercent;
    this.sulfurBalanceRelativeError = sulfurBalanceRelativeError;
    this.tailGasH2SToSO2Ratio = tailGasH2SToSO2Ratio;
    this.oxygenDemandMoles = oxygenDemandMoles;
    this.furnaceTemperatureK = furnaceTemperatureK;
    this.stackSO2KgPerHour = stackSO2KgPerHour;
    this.airControlIterations = airControlIterations;
    this.recycleIterations = recycleIterations;
    this.airControlConverged = airControlConverged;
    this.recycleConverged = recycleConverged;
  }

  /** Return sulfur entering with the fresh acid gas [kg/h]. */
  public double getFeedSulfurKgPerHour() {
    return feedSulfurKgPerHour;
  }

  /** Return condensed elemental sulfur product [kg/h]. */
  public double getRecoveredSulfurKgPerHour() {
    return recoveredSulfurKgPerHour;
  }

  /** Return Claus-section recovery relative to fresh-feed sulfur [%]. */
  public double getSulfurRecoveryPercent() {
    return sulfurRecoveryPercent;
  }

  /** Return recovery including sulfur captured by the tail-gas unit [%]. */
  public double getOverallSulfurRecoveryPercent() {
    return overallSulfurRecoveryPercent;
  }

  /** Return relative sulfur-atom closure error. */
  public double getSulfurBalanceRelativeError() {
    return sulfurBalanceRelativeError;
  }

  /** Return the final Claus tail-gas H2S/SO2 molar ratio. */
  public double getTailGasH2SToSO2Ratio() {
    return tailGasH2SToSO2Ratio;
  }

  /** Return calculated oxygen demand on the stream molar-flow basis. */
  public double getOxygenDemandMoles() {
    return oxygenDemandMoles;
  }

  /** Return furnace outlet temperature [K]. */
  public double getFurnaceTemperatureK() {
    return furnaceTemperatureK;
  }

  /** Return incinerator stack SO2 rate [kg/h]. */
  public double getStackSO2KgPerHour() {
    return stackSO2KgPerHour;
  }

  /** Return number of air-demand convergence iterations. */
  public int getAirControlIterations() {
    return airControlIterations;
  }

  /** Return number of tail-gas recycle convergence iterations. */
  public int getRecycleIterations() {
    return recycleIterations;
  }

  /** Return whether the final air-demand solve met its ratio tolerance. */
  public boolean isAirControlConverged() {
    return airControlConverged;
  }

  /** Return whether the acid-gas recycle met its tolerance, or true when disabled. */
  public boolean isRecycleConverged() {
    return recycleConverged;
  }
}
