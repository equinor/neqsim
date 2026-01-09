package neqsim.process.fielddevelopment.tieback;

import java.io.Serializable;
import neqsim.process.fielddevelopment.screening.FlowAssuranceResult;

/**
 * Represents a specific tie-back option evaluated by the TiebackAnalyzer.
 *
 * <p>
 * A TiebackOption encapsulates all the technical and economic parameters for connecting a satellite
 * field to a host facility. It includes:
 * </p>
 * <ul>
 * <li><b>Technical parameters</b>: Distance, pipeline size, flow assurance requirements</li>
 * <li><b>Economic parameters</b>: CAPEX breakdown, NPV, payback</li>
 * <li><b>Constraints</b>: Host capacity limits, pressure requirements</li>
 * <li><b>Flow assurance</b>: Hydrate, wax, and corrosion screening results</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 * @see TiebackAnalyzer
 * @see HostFacility
 */
public class TiebackOption implements Serializable, Comparable<TiebackOption> {
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // IDENTIFICATION
  // ============================================================================

  private String discoveryName;
  private String hostName;
  private String optionId;

  // ============================================================================
  // TECHNICAL PARAMETERS
  // ============================================================================

  /** Tieback distance in kilometers. */
  private double distanceKm;

  /** Pipeline diameter in inches. */
  private double pipelineDiameterInches;

  /** Maximum water depth along route in meters. */
  private double maxWaterDepthM;

  /** Estimated arrival pressure at host in bara. */
  private double arrivalPressureBara;

  /** Estimated arrival temperature at host in Celsius. */
  private double arrivalTemperatureC;

  /** Number of subsea wells. */
  private int wellCount;

  // ============================================================================
  // PRODUCTION PARAMETERS
  // ============================================================================

  /** Maximum production rate constrained by host capacity (gas: MSm3/d, oil: bbl/d). */
  private double maxProductionRate;

  /** Production rate unit. */
  private String rateUnit;

  /** Total recoverable reserves. */
  private double recoverableReserves;

  /** Reserves unit. */
  private String reservesUnit;

  /** Expected field life in years. */
  private double fieldLifeYears;

  // ============================================================================
  // CAPEX BREAKDOWN (MUSD)
  // ============================================================================

  /** Subsea equipment CAPEX (trees, manifolds, controls). */
  private double subseaCapexMusd;

  /** Pipeline CAPEX. */
  private double pipelineCapexMusd;

  /** Umbilical CAPEX. */
  private double umbilicalCapexMusd;

  /** Drilling and completion CAPEX. */
  private double drillingCapexMusd;

  /** Host modifications CAPEX. */
  private double hostModificationCapexMusd;

  /** Total CAPEX. */
  private double totalCapexMusd;

  // ============================================================================
  // ECONOMIC RESULTS
  // ============================================================================

  /** Net Present Value in MUSD. */
  private double npvMusd;

  /** Internal Rate of Return (0-1). */
  private double irr;

  /** Payback period in years. */
  private double paybackYears;

  /** Breakeven price (oil: USD/bbl, gas: USD/Sm3). */
  private double breakevenPrice;

  // ============================================================================
  // FLOW ASSURANCE
  // ============================================================================

  /** Hydrate screening result. */
  private FlowAssuranceResult hydrateResult;

  /** Wax screening result. */
  private FlowAssuranceResult waxResult;

  /** Corrosion screening result. */
  private FlowAssuranceResult corrosionResult;

  /** Hydrate subcooling margin in Celsius. */
  private double hydrateMarginC;

  /** WAT margin in Celsius. */
  private double watMarginC;

  /** Flow assurance notes/recommendations. */
  private String flowAssuranceNotes;

  // ============================================================================
  // FEASIBILITY FLAGS
  // ============================================================================

  /** Whether the option is technically feasible. */
  private boolean feasible;

  /** Reason for infeasibility (if not feasible). */
  private String infeasibilityReason;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new tieback option.
   *
   * @param discoveryName name of the satellite field
   * @param hostName name of the host facility
   */
  public TiebackOption(String discoveryName, String hostName) {
    this.discoveryName = discoveryName;
    this.hostName = hostName;
    this.optionId = discoveryName + " -> " + hostName;
    this.feasible = true;
    this.hydrateResult = FlowAssuranceResult.PASS;
    this.waxResult = FlowAssuranceResult.PASS;
    this.corrosionResult = FlowAssuranceResult.PASS;
  }

  // ============================================================================
  // DERIVED CALCULATIONS
  // ============================================================================

  /**
   * Calculates total CAPEX from breakdown.
   *
   * @return total CAPEX in MUSD
   */
  public double calculateTotalCapex() {
    this.totalCapexMusd = subseaCapexMusd + pipelineCapexMusd + umbilicalCapexMusd
        + drillingCapexMusd + hostModificationCapexMusd;
    return totalCapexMusd;
  }

  /**
   * Estimates pipeline CAPEX based on distance and diameter.
   *
   * @param costPerKmMusd cost per km in MUSD
   * @return pipeline CAPEX in MUSD
   */
  public double estimatePipelineCapex(double costPerKmMusd) {
    this.pipelineCapexMusd = distanceKm * costPerKmMusd;
    return pipelineCapexMusd;
  }

  /**
   * Checks if the option has any critical flow assurance issues.
   *
   * @return true if any flow assurance category fails
   */
  public boolean hasFlowAssuranceIssues() {
    return hydrateResult == FlowAssuranceResult.FAIL || waxResult == FlowAssuranceResult.FAIL
        || corrosionResult == FlowAssuranceResult.FAIL;
  }

  /**
   * Gets the overall flow assurance status.
   *
   * @return worst flow assurance result across all categories
   */
  public FlowAssuranceResult getOverallFlowAssuranceResult() {
    if (hydrateResult == FlowAssuranceResult.FAIL || waxResult == FlowAssuranceResult.FAIL
        || corrosionResult == FlowAssuranceResult.FAIL) {
      return FlowAssuranceResult.FAIL;
    }
    if (hydrateResult == FlowAssuranceResult.MARGINAL || waxResult == FlowAssuranceResult.MARGINAL
        || corrosionResult == FlowAssuranceResult.MARGINAL) {
      return FlowAssuranceResult.MARGINAL;
    }
    return FlowAssuranceResult.PASS;
  }

  /**
   * Gets CAPEX per km of tieback.
   *
   * @return CAPEX intensity in MUSD/km
   */
  public double getCapexPerKm() {
    if (distanceKm <= 0) {
      return 0.0;
    }
    return totalCapexMusd / distanceKm;
  }

  /**
   * Gets CAPEX per unit of reserves.
   *
   * @return CAPEX intensity in MUSD per reserve unit
   */
  public double getCapexPerReserveUnit() {
    if (recoverableReserves <= 0) {
      return 0.0;
    }
    return totalCapexMusd / recoverableReserves;
  }

  // ============================================================================
  // COMPARABLE IMPLEMENTATION
  // ============================================================================

  /**
   * Compares by NPV (descending - higher NPV is better).
   *
   * @param other other option to compare
   * @return comparison result
   */
  @Override
  public int compareTo(TiebackOption other) {
    // Higher NPV is better, so negate for descending order
    return Double.compare(other.npvMusd, this.npvMusd);
  }

  // ============================================================================
  // SUMMARY METHODS
  // ============================================================================

  /**
   * Generates a summary string.
   *
   * @return formatted summary
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Tieback Option: ").append(optionId).append(" ===\n");
    sb.append(String.format("Distance: %.1f km, Depth: %.0f m\n", distanceKm, maxWaterDepthM));
    sb.append(String.format("Production: %.2f %s, Field life: %.1f years\n", maxProductionRate,
        rateUnit, fieldLifeYears));
    sb.append(String.format("CAPEX: %.0f MUSD (%.1f MUSD/km)\n", totalCapexMusd, getCapexPerKm()));
    sb.append(String.format("NPV: %.0f MUSD, IRR: %.1f%%, Payback: %.1f years\n", npvMusd,
        irr * 100, paybackYears));
    sb.append(String.format("Flow Assurance: Hydrate=%s, Wax=%s, Corrosion=%s\n", hydrateResult,
        waxResult, corrosionResult));
    sb.append(String.format("Feasible: %s", feasible ? "YES" : "NO - " + infeasibilityReason));
    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("TiebackOption[%s, %.0fkm, NPV=%.0f MUSD, %s]", optionId, distanceKm,
        npvMusd, feasible ? "FEASIBLE" : "INFEASIBLE");
  }

  // ============================================================================
  // GETTERS AND SETTERS
  // ============================================================================

  /**
   * Gets the discovery name.
   *
   * @return discovery name
   */
  public String getDiscoveryName() {
    return discoveryName;
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
   * Gets the option ID.
   *
   * @return option ID
   */
  public String getOptionId() {
    return optionId;
  }

  /**
   * Gets the distance.
   *
   * @return distance in km
   */
  public double getDistanceKm() {
    return distanceKm;
  }

  /**
   * Sets the distance.
   *
   * @param distanceKm distance in km
   */
  public void setDistanceKm(double distanceKm) {
    this.distanceKm = distanceKm;
  }

  /**
   * Gets the pipeline diameter.
   *
   * @return pipeline diameter in inches
   */
  public double getPipelineDiameterInches() {
    return pipelineDiameterInches;
  }

  /**
   * Sets the pipeline diameter.
   *
   * @param pipelineDiameterInches pipeline diameter in inches
   */
  public void setPipelineDiameterInches(double pipelineDiameterInches) {
    this.pipelineDiameterInches = pipelineDiameterInches;
  }

  /**
   * Gets the maximum water depth.
   *
   * @return water depth in meters
   */
  public double getMaxWaterDepthM() {
    return maxWaterDepthM;
  }

  /**
   * Sets the maximum water depth.
   *
   * @param maxWaterDepthM water depth in meters
   */
  public void setMaxWaterDepthM(double maxWaterDepthM) {
    this.maxWaterDepthM = maxWaterDepthM;
  }

  /**
   * Gets the arrival pressure.
   *
   * @return arrival pressure in bara
   */
  public double getArrivalPressureBara() {
    return arrivalPressureBara;
  }

  /**
   * Sets the arrival pressure.
   *
   * @param arrivalPressureBara arrival pressure in bara
   */
  public void setArrivalPressureBara(double arrivalPressureBara) {
    this.arrivalPressureBara = arrivalPressureBara;
  }

  /**
   * Gets the arrival temperature.
   *
   * @return arrival temperature in Celsius
   */
  public double getArrivalTemperatureC() {
    return arrivalTemperatureC;
  }

  /**
   * Sets the arrival temperature.
   *
   * @param arrivalTemperatureC arrival temperature in Celsius
   */
  public void setArrivalTemperatureC(double arrivalTemperatureC) {
    this.arrivalTemperatureC = arrivalTemperatureC;
  }

  /**
   * Gets the well count.
   *
   * @return well count
   */
  public int getWellCount() {
    return wellCount;
  }

  /**
   * Sets the well count.
   *
   * @param wellCount well count
   */
  public void setWellCount(int wellCount) {
    this.wellCount = wellCount;
  }

  /**
   * Gets the maximum production rate.
   *
   * @return production rate
   */
  public double getMaxProductionRate() {
    return maxProductionRate;
  }

  /**
   * Sets the maximum production rate.
   *
   * @param maxProductionRate production rate
   */
  public void setMaxProductionRate(double maxProductionRate) {
    this.maxProductionRate = maxProductionRate;
  }

  /**
   * Gets the rate unit.
   *
   * @return rate unit string
   */
  public String getRateUnit() {
    return rateUnit;
  }

  /**
   * Sets the rate unit.
   *
   * @param rateUnit rate unit string
   */
  public void setRateUnit(String rateUnit) {
    this.rateUnit = rateUnit;
  }

  /**
   * Gets the recoverable reserves.
   *
   * @return recoverable reserves
   */
  public double getRecoverableReserves() {
    return recoverableReserves;
  }

  /**
   * Sets the recoverable reserves.
   *
   * @param recoverableReserves reserves amount
   */
  public void setRecoverableReserves(double recoverableReserves) {
    this.recoverableReserves = recoverableReserves;
  }

  /**
   * Gets the reserves unit.
   *
   * @return reserves unit string
   */
  public String getReservesUnit() {
    return reservesUnit;
  }

  /**
   * Sets the reserves unit.
   *
   * @param reservesUnit reserves unit string
   */
  public void setReservesUnit(String reservesUnit) {
    this.reservesUnit = reservesUnit;
  }

  /**
   * Gets the field life.
   *
   * @return field life in years
   */
  public double getFieldLifeYears() {
    return fieldLifeYears;
  }

  /**
   * Sets the field life.
   *
   * @param fieldLifeYears field life in years
   */
  public void setFieldLifeYears(double fieldLifeYears) {
    this.fieldLifeYears = fieldLifeYears;
  }

  /**
   * Gets the subsea CAPEX.
   *
   * @return subsea CAPEX in MUSD
   */
  public double getSubseaCapexMusd() {
    return subseaCapexMusd;
  }

  /**
   * Sets the subsea CAPEX.
   *
   * @param subseaCapexMusd subsea CAPEX in MUSD
   */
  public void setSubseaCapexMusd(double subseaCapexMusd) {
    this.subseaCapexMusd = subseaCapexMusd;
  }

  /**
   * Gets the pipeline CAPEX.
   *
   * @return pipeline CAPEX in MUSD
   */
  public double getPipelineCapexMusd() {
    return pipelineCapexMusd;
  }

  /**
   * Sets the pipeline CAPEX.
   *
   * @param pipelineCapexMusd pipeline CAPEX in MUSD
   */
  public void setPipelineCapexMusd(double pipelineCapexMusd) {
    this.pipelineCapexMusd = pipelineCapexMusd;
  }

  /**
   * Gets the umbilical CAPEX.
   *
   * @return umbilical CAPEX in MUSD
   */
  public double getUmbilicalCapexMusd() {
    return umbilicalCapexMusd;
  }

  /**
   * Sets the umbilical CAPEX.
   *
   * @param umbilicalCapexMusd umbilical CAPEX in MUSD
   */
  public void setUmbilicalCapexMusd(double umbilicalCapexMusd) {
    this.umbilicalCapexMusd = umbilicalCapexMusd;
  }

  /**
   * Gets the drilling CAPEX.
   *
   * @return drilling CAPEX in MUSD
   */
  public double getDrillingCapexMusd() {
    return drillingCapexMusd;
  }

  /**
   * Sets the drilling CAPEX.
   *
   * @param drillingCapexMusd drilling CAPEX in MUSD
   */
  public void setDrillingCapexMusd(double drillingCapexMusd) {
    this.drillingCapexMusd = drillingCapexMusd;
  }

  /**
   * Gets the host modification CAPEX.
   *
   * @return host modification CAPEX in MUSD
   */
  public double getHostModificationCapexMusd() {
    return hostModificationCapexMusd;
  }

  /**
   * Sets the host modification CAPEX.
   *
   * @param hostModificationCapexMusd host modification CAPEX in MUSD
   */
  public void setHostModificationCapexMusd(double hostModificationCapexMusd) {
    this.hostModificationCapexMusd = hostModificationCapexMusd;
  }

  /**
   * Gets the total CAPEX.
   *
   * @return total CAPEX in MUSD
   */
  public double getTotalCapexMusd() {
    return totalCapexMusd;
  }

  /**
   * Sets the total CAPEX.
   *
   * @param totalCapexMusd total CAPEX in MUSD
   */
  public void setTotalCapexMusd(double totalCapexMusd) {
    this.totalCapexMusd = totalCapexMusd;
  }

  /**
   * Gets the NPV.
   *
   * @return NPV in MUSD
   */
  public double getNpvMusd() {
    return npvMusd;
  }

  /**
   * Sets the NPV.
   *
   * @param npvMusd NPV in MUSD
   */
  public void setNpvMusd(double npvMusd) {
    this.npvMusd = npvMusd;
  }

  /**
   * Gets the IRR.
   *
   * @return IRR (0-1)
   */
  public double getIrr() {
    return irr;
  }

  /**
   * Sets the IRR.
   *
   * @param irr IRR (0-1)
   */
  public void setIrr(double irr) {
    this.irr = irr;
  }

  /**
   * Gets the payback period.
   *
   * @return payback in years
   */
  public double getPaybackYears() {
    return paybackYears;
  }

  /**
   * Sets the payback period.
   *
   * @param paybackYears payback in years
   */
  public void setPaybackYears(double paybackYears) {
    this.paybackYears = paybackYears;
  }

  /**
   * Gets the breakeven price.
   *
   * @return breakeven price
   */
  public double getBreakevenPrice() {
    return breakevenPrice;
  }

  /**
   * Sets the breakeven price.
   *
   * @param breakevenPrice breakeven price
   */
  public void setBreakevenPrice(double breakevenPrice) {
    this.breakevenPrice = breakevenPrice;
  }

  /**
   * Gets the hydrate result.
   *
   * @return hydrate screening result
   */
  public FlowAssuranceResult getHydrateResult() {
    return hydrateResult;
  }

  /**
   * Sets the hydrate result.
   *
   * @param hydrateResult hydrate screening result
   */
  public void setHydrateResult(FlowAssuranceResult hydrateResult) {
    this.hydrateResult = hydrateResult;
  }

  /**
   * Gets the wax result.
   *
   * @return wax screening result
   */
  public FlowAssuranceResult getWaxResult() {
    return waxResult;
  }

  /**
   * Sets the wax result.
   *
   * @param waxResult wax screening result
   */
  public void setWaxResult(FlowAssuranceResult waxResult) {
    this.waxResult = waxResult;
  }

  /**
   * Gets the corrosion result.
   *
   * @return corrosion screening result
   */
  public FlowAssuranceResult getCorrosionResult() {
    return corrosionResult;
  }

  /**
   * Sets the corrosion result.
   *
   * @param corrosionResult corrosion screening result
   */
  public void setCorrosionResult(FlowAssuranceResult corrosionResult) {
    this.corrosionResult = corrosionResult;
  }

  /**
   * Gets the hydrate margin.
   *
   * @return hydrate margin in Celsius
   */
  public double getHydrateMarginC() {
    return hydrateMarginC;
  }

  /**
   * Sets the hydrate margin.
   *
   * @param hydrateMarginC hydrate margin in Celsius
   */
  public void setHydrateMarginC(double hydrateMarginC) {
    this.hydrateMarginC = hydrateMarginC;
  }

  /**
   * Gets the WAT margin.
   *
   * @return WAT margin in Celsius
   */
  public double getWatMarginC() {
    return watMarginC;
  }

  /**
   * Sets the WAT margin.
   *
   * @param watMarginC WAT margin in Celsius
   */
  public void setWatMarginC(double watMarginC) {
    this.watMarginC = watMarginC;
  }

  /**
   * Gets the flow assurance notes.
   *
   * @return flow assurance notes
   */
  public String getFlowAssuranceNotes() {
    return flowAssuranceNotes;
  }

  /**
   * Sets the flow assurance notes.
   *
   * @param flowAssuranceNotes notes string
   */
  public void setFlowAssuranceNotes(String flowAssuranceNotes) {
    this.flowAssuranceNotes = flowAssuranceNotes;
  }

  /**
   * Checks if the option is feasible.
   *
   * @return true if feasible
   */
  public boolean isFeasible() {
    return feasible;
  }

  /**
   * Sets the feasibility flag.
   *
   * @param feasible true if feasible
   */
  public void setFeasible(boolean feasible) {
    this.feasible = feasible;
  }

  /**
   * Gets the infeasibility reason.
   *
   * @return reason string or null
   */
  public String getInfeasibilityReason() {
    return infeasibilityReason;
  }

  /**
   * Sets the infeasibility reason.
   *
   * @param infeasibilityReason reason string
   */
  public void setInfeasibilityReason(String infeasibilityReason) {
    this.infeasibilityReason = infeasibilityReason;
  }
}
