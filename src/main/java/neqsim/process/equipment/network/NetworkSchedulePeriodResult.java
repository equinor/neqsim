package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hydraulic, inventory, quality, and constraint state for one planning period.
 */
public class NetworkSchedulePeriodResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final NetworkPeriod period;
  private final boolean feasible;
  private final Map<String, GasLinepackState> openingLinepack;
  private final Map<String, GasLinepackState> closingLinepack;
  private final Map<String, double[]> pipeFlowsKgS;
  private final Map<String, Double> constraintResiduals;
  private final Map<String, NetworkQualityComplianceReport> qualityReports;

  /**
   * Create a period result.
   *
   * @param period period
   * @param feasible feasibility
   * @param openingLinepack opening states
   * @param closingLinepack closing states
   * @param pipeFlowsKgS pipe to [inlet, outlet, fuel, losses] kg/s
   * @param constraintResiduals named non-negative residuals
   * @param qualityReports point-specific quality reports
   */
  public NetworkSchedulePeriodResult(NetworkPeriod period, boolean feasible,
      Map<String, GasLinepackState> openingLinepack, Map<String, GasLinepackState> closingLinepack,
      Map<String, double[]> pipeFlowsKgS, Map<String, Double> constraintResiduals,
      Map<String, NetworkQualityComplianceReport> qualityReports) {
    this.period = period;
    this.feasible = feasible;
    this.openingLinepack = new LinkedHashMap<String, GasLinepackState>(openingLinepack);
    this.closingLinepack = new LinkedHashMap<String, GasLinepackState>(closingLinepack);
    this.pipeFlowsKgS = new LinkedHashMap<String, double[]>(pipeFlowsKgS);
    this.constraintResiduals = new LinkedHashMap<String, Double>(constraintResiduals);
    this.qualityReports = new LinkedHashMap<String, NetworkQualityComplianceReport>(qualityReports);
  }

  /** @return period */
  public NetworkPeriod getPeriod() {
    return period;
  }

  /** @return true when all period constraints pass */
  public boolean isFeasible() {
    return feasible;
  }

  /** @return opening linepack */
  public Map<String, GasLinepackState> getOpeningLinepack() {
    return Collections.unmodifiableMap(openingLinepack);
  }

  /** @return closing linepack */
  public Map<String, GasLinepackState> getClosingLinepack() {
    return Collections.unmodifiableMap(closingLinepack);
  }

  /** @return pipe [inlet,outlet,fuel,loss] mass rates in kg/s */
  public Map<String, double[]> getPipeFlowsKgS() {
    return Collections.unmodifiableMap(pipeFlowsKgS);
  }

  /** @return named constraint residuals */
  public Map<String, Double> getConstraintResiduals() {
    return Collections.unmodifiableMap(constraintResiduals);
  }

  /** @return point-specific quality reports */
  public Map<String, NetworkQualityComplianceReport> getQualityReports() {
    return Collections.unmodifiableMap(qualityReports);
  }
}
