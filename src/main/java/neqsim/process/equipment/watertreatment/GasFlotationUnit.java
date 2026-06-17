package neqsim.process.equipment.watertreatment;

import java.util.UUID;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Gas flotation unit for produced water treatment.
 *
 * <p>
 * Models Induced Gas Flotation (IGF) or Dissolved Gas Flotation (DGF) for removal of dispersed oil
 * droplets from produced water. Fine gas bubbles are injected into the water; oil droplets attach
 * to the bubbles and rise to the surface where they are skimmed off.
 * </p>
 *
 * <h2>Design Parameters</h2>
 * <ul>
 * <li><b>Flotation gas supply:</b> Minimum 4 bar above water pressure, minimum 10 Avol% gas
 * flow</li>
 * <li><b>Reject flow:</b> Minimum 2% of inlet water flow per unit/stage</li>
 * <li><b>Reject valves:</b> Actuated, sized for min to max flow per production profile</li>
 * <li><b>Gas mixing dP:</b> At least 0.5 bar across mixing valve for proper dispersion</li>
 * </ul>
 *
 * <h2>Performance</h2>
 * <ul>
 * <li>Typical oil removal: 80-95%</li>
 * <li>Effective for droplets larger than 5 microns</li>
 * <li>Usually 3-4 stages in series</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class GasFlotationUnit extends Separator {
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // DESIGN PARAMETERS
  // ============================================================================

  /** Number of flotation stages. */
  private int numberOfStages = 4;

  /** Overall oil removal efficiency (0.0-1.0). */
  private double oilRemovalEfficiency = 0.90;

  /** Per-stage efficiency (0.0-1.0). Calculated from overall and number of stages. */
  private double perStageEfficiency = 0.0;

  /** Minimum flotation gas overpressure above water pressure (bar). */
  private double minGasOverpressureBar = 4.0;

  /** Minimum flotation gas volume fraction (Avol%). */
  private double minGasVolumeFractionPct = 10.0;

  /** Minimum reject flow per stage as fraction of inlet water flow. */
  private double minRejectFractionPerStage = 0.02;

  /** Minimum gas mixing valve dP (bar). */
  private double minGasMixingDPBar = 0.5;

  /** Flotation gas type: "fuel_gas" or "nitrogen". */
  private String flotationGasType = "fuel_gas";

  /** Water flow rate in m3/h. */
  private double waterFlowRateM3h = 100.0;

  /** Inlet oil concentration in mg/L. */
  private double inletOilMgL = 200.0;

  /** Calculated outlet oil concentration in mg/L. */
  private double outletOilMgL = 0.0;

  /** Gas flow rate in Am3/h. */
  private double flotationGasFlowRateM3h = 0.0;

  /** Reject flow rate per stage in m3/h. */
  private double rejectFlowPerStageM3h = 0.0;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a gas flotation unit.
   *
   * @param name equipment name
   */
  public GasFlotationUnit(String name) {
    super(name);
    setOrientation("horizontal");
  }

  /**
   * Creates a gas flotation unit with inlet stream.
   *
   * @param name equipment name
   * @param inletStream water stream from upstream treatment
   */
  public GasFlotationUnit(String name, StreamInterface inletStream) {
    super(name, inletStream);
    setOrientation("horizontal");
  }

  // ============================================================================
  // CONFIGURATION
  // ============================================================================

  /**
   * Sets the number of flotation stages.
   *
   * @param stages number of stages (typically 3-4)
   */
  public void setNumberOfStages(int stages) {
    this.numberOfStages = Math.max(1, stages);
  }

  /**
   * Gets the number of flotation stages.
   *
   * @return number of stages
   */
  public int getNumberOfStages() {
    return numberOfStages;
  }

  /**
   * Sets the overall oil removal efficiency.
   *
   * @param efficiency efficiency (0.0-1.0)
   */
  public void setOilRemovalEfficiency(double efficiency) {
    this.oilRemovalEfficiency = Math.min(1.0, Math.max(0.0, efficiency));
  }

  /**
   * Gets the overall oil removal efficiency.
   *
   * @return efficiency (0.0-1.0)
   */
  public double getOilRemovalEfficiency() {
    return oilRemovalEfficiency;
  }

  /**
   * Sets the flotation gas type.
   *
   * @param gasType "fuel_gas" or "nitrogen"
   */
  public void setFlotationGasType(String gasType) {
    this.flotationGasType = gasType;
  }

  /**
   * Gets the flotation gas type.
   *
   * @return gas type string
   */
  public String getFlotationGasType() {
    return flotationGasType;
  }

  /**
   * Sets the inlet oil concentration.
   *
   * @param oilMgL oil concentration in mg/L
   */
  public void setInletOilConcentration(double oilMgL) {
    this.inletOilMgL = oilMgL;
  }

  /**
   * Gets the inlet oil concentration.
   *
   * @return oil concentration in mg/L
   */
  public double getInletOilConcentration() {
    return inletOilMgL;
  }

  /**
   * Sets the water flow rate.
   *
   * @param flowRateM3h flow rate in m3/h
   */
  public void setWaterFlowRate(double flowRateM3h) {
    this.waterFlowRateM3h = flowRateM3h;
  }

  /**
   * Gets the water flow rate.
   *
   * @return flow rate in m3/h
   */
  public double getWaterFlowRate() {
    return waterFlowRateM3h;
  }

  // ============================================================================
  // CALCULATIONS
  // ============================================================================

  /**
   * Calculates the per-stage efficiency from the overall efficiency and number of stages.
   *
   * <p>
   * Assumes each stage removes the same fraction of the remaining oil: (1 - eta_overall) = (1 -
   * eta_stage)^n
   * </p>
   *
   * @return per-stage efficiency (0.0-1.0)
   */
  public double calcPerStageEfficiency() {
    perStageEfficiency = 1.0 - Math.pow(1.0 - oilRemovalEfficiency, 1.0 / numberOfStages);
    return perStageEfficiency;
  }

  /**
   * Gets the calculated per-stage efficiency.
   *
   * @return per-stage efficiency (0.0-1.0)
   */
  public double getPerStageEfficiency() {
    return perStageEfficiency;
  }

  /**
   * Calculates the minimum required flotation gas flow rate.
   *
   * <p>
   * The gas flow must be at least 10 Avol% of the water flow rate: Q_gas_min = 0.10 * Q_water
   * </p>
   *
   * @return minimum gas flow rate in Am3/h
   */
  public double calcMinimumGasFlowRate() {
    flotationGasFlowRateM3h = waterFlowRateM3h * minGasVolumeFractionPct / 100.0;
    return flotationGasFlowRateM3h;
  }

  /**
   * Gets the calculated flotation gas flow rate.
   *
   * @return gas flow rate in Am3/h
   */
  public double getFlotationGasFlowRate() {
    return flotationGasFlowRateM3h;
  }

  /**
   * Calculates the minimum reject flow rate per stage.
   *
   * <p>
   * Design rate for reject flow shall be minimum 2% per unit/stage of the inlet water flow rate.
   * </p>
   *
   * @return reject flow per stage in m3/h
   */
  public double calcRejectFlowPerStage() {
    rejectFlowPerStageM3h = waterFlowRateM3h * minRejectFractionPerStage;
    return rejectFlowPerStageM3h;
  }

  /**
   * Gets the reject flow per stage.
   *
   * @return reject flow rate in m3/h
   */
  public double getRejectFlowPerStage() {
    return rejectFlowPerStageM3h;
  }

  /**
   * Gets the total reject flow rate for all stages.
   *
   * @return total reject flow in m3/h
   */
  public double getTotalRejectFlow() {
    return rejectFlowPerStageM3h * numberOfStages;
  }

  // ============================================================================
  // RUN
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (!getInletStreams().isEmpty()) {
      super.run(id);
    }

    // Calculate performance
    calcPerStageEfficiency();
    calcMinimumGasFlowRate();
    calcRejectFlowPerStage();

    // Calculate outlet concentration
    outletOilMgL = inletOilMgL * (1.0 - oilRemovalEfficiency);

    setCalculationIdentifier(id);
  }

  // ============================================================================
  // RESULTS
  // ============================================================================

  /**
   * Gets the outlet oil concentration.
   *
   * @return oil concentration in mg/L
   */
  public double getOutletOilMgL() {
    return outletOilMgL;
  }

  /**
   * Gets a design validation summary.
   *
   * @return summary string
   */
  public String getDesignValidationSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Gas Flotation Unit Design Validation\n");
    sb.append("=====================================\n");
    sb.append(String.format("Number of stages: %d\n", numberOfStages));
    sb.append(String.format("Flotation gas type: %s\n", flotationGasType));
    sb.append(String.format("Overall efficiency: %.1f%%\n", oilRemovalEfficiency * 100.0));
    sb.append(String.format("Per-stage efficiency: %.1f%%\n", perStageEfficiency * 100.0));
    sb.append(String.format("Water flow: %.1f m3/h\n", waterFlowRateM3h));
    sb.append(String.format("Min gas flow: %.1f Am3/h (%.0f Avol%%)\n", flotationGasFlowRateM3h,
        minGasVolumeFractionPct));
    sb.append(String.format("Min gas overpressure: %.1f bar above water\n", minGasOverpressureBar));
    sb.append(String.format("Min gas mixing valve dP: %.1f bar\n", minGasMixingDPBar));
    sb.append(String.format("Reject flow per stage: %.1f m3/h (%.1f%% of inlet)\n",
        rejectFlowPerStageM3h, minRejectFractionPerStage * 100.0));
    sb.append(String.format("Total reject flow: %.1f m3/h\n", getTotalRejectFlow()));
    sb.append(String.format("Inlet OIW: %.0f mg/L\n", inletOilMgL));
    sb.append(String.format("Outlet OIW: %.0f mg/L\n", outletOilMgL));

    if ("nitrogen".equals(flotationGasType)) {
      sb.append("WARNING: If nitrogen is used, evaluate possible corrosion and ");
      sb.append("precipitation problems\n");
    }
    return sb.toString();
  }
}
