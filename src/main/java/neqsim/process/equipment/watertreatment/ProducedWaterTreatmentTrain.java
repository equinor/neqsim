package neqsim.process.equipment.watertreatment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Produced water treatment train for offshore oil and gas facilities.
 *
 * <p>
 * Models a typical produced water treatment process including:
 * </p>
 * <ul>
 * <li><b>Hydrocyclone</b> - Primary oil removal (bulk separation)</li>
 * <li><b>Flotation unit</b> - Secondary treatment for fine droplets</li>
 * <li><b>Skim tank</b> - Polishing for final discharge spec</li>
 * </ul>
 *
 * <h2>Norwegian Continental Shelf Discharge Limits</h2>
 * <ul>
 * <li>Oil-in-water: 30 mg/L monthly weighted average</li>
 * <li>Dispersed oil: Monitored daily</li>
 * <li>Zero harmful discharge target</li>
 * </ul>
 *
 * <h2>Typical Equipment Performance</h2>
 * <table border="1">
 * <caption>Equipment Removal Efficiency</caption>
 * <tr>
 * <th>Equipment</th>
 * <th>Droplet Size</th>
 * <th>Efficiency</th>
 * </tr>
 * <tr>
 * <td>Hydrocyclone</td>
 * <td>&gt;20 μm</td>
 * <td>90-98%</td>
 * </tr>
 * <tr>
 * <td>IGF/DGF</td>
 * <td>&gt;5 μm</td>
 * <td>80-95%</td>
 * </tr>
 * <tr>
 * <td>Skim Tank</td>
 * <td>&gt;50 μm</td>
 * <td>60-80%</td>
 * </tr>
 * </table>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProducedWaterTreatmentTrain extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;

  /** NCS monthly average OIW limit (mg/L). */
  public static final double NCS_OIW_LIMIT_MGL = 30.0;

  /** OSPAR limit (mg/L). */
  public static final double OSPAR_OIW_LIMIT_MGL = 30.0;

  // Treatment stages
  private final List<WaterTreatmentStage> stages = new ArrayList<>();

  // Stream references
  private StreamInterface inletStream;
  private StreamInterface treatedWaterStream;
  private StreamInterface recoveredOilStream;

  // Operating parameters
  private double inletOilConcentrationMgL = 1000.0; // Typical 500-2000 mg/L from separator
  private double outletOilConcentrationMgL = 0.0;
  private double waterFlowRateM3h = 100.0;
  private double recoveredOilM3h = 0.0;
  private double overallEfficiency = 0.0;

  // Calculated values
  private boolean isCompliant = false;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a produced water treatment train.
   *
   * @param name equipment name
   */
  public ProducedWaterTreatmentTrain(String name) {
    super(name);
    // Add default treatment stages
    stages.add(new WaterTreatmentStage("Hydrocyclone", StageType.HYDROCYCLONE, 0.95));
    stages.add(new WaterTreatmentStage("IGF", StageType.FLOTATION, 0.90));
    stages.add(new WaterTreatmentStage("Skim Tank", StageType.SKIM_TANK, 0.70));
  }

  /**
   * Creates a produced water treatment train with inlet stream.
   *
   * @param name equipment name
   * @param inletStream water stream from production separator
   */
  public ProducedWaterTreatmentTrain(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  // ============================================================================
  // CONFIGURATION
  // ============================================================================

  /**
   * Sets the inlet water stream.
   *
   * @param stream produced water stream
   */
  public void setInletStream(StreamInterface stream) {
    this.inletStream = stream;
  }

  /**
   * Gets the inlet stream.
   *
   * @return inlet stream
   */
  public StreamInterface getInletStream() {
    return inletStream;
  }

  /**
   * Sets the inlet oil-in-water concentration.
   *
   * @param oiwMgL oil concentration in mg/L (ppm by mass for water)
   */
  public void setInletOilConcentration(double oiwMgL) {
    this.inletOilConcentrationMgL = oiwMgL;
  }

  /**
   * Sets the water flow rate.
   *
   * @param flowRateM3h water flow rate in m³/h
   */
  public void setWaterFlowRate(double flowRateM3h) {
    this.waterFlowRateM3h = flowRateM3h;
  }

  /**
   * Adds a treatment stage.
   *
   * @param stage treatment stage to add
   */
  public void addStage(WaterTreatmentStage stage) {
    stages.add(stage);
  }

  /**
   * Clears default stages and allows custom configuration.
   */
  public void clearStages() {
    stages.clear();
  }

  /**
   * Sets stage efficiency by name.
   *
   * @param stageName stage name
   * @param efficiency efficiency (0.0-1.0)
   */
  public void setStageEfficiency(String stageName, double efficiency) {
    for (WaterTreatmentStage stage : stages) {
      if (stage.getName().equalsIgnoreCase(stageName)) {
        stage.setEfficiency(efficiency);
        return;
      }
    }
  }

  // ============================================================================
  // RUN CALCULATION
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Get inlet conditions from stream if available
    if (inletStream != null && inletStream.getFluid() != null) {
      SystemInterface fluid = inletStream.getFluid();
      if (fluid.hasPhaseType("aqueous")) {
        waterFlowRateM3h = fluid.getPhase("aqueous").getVolume("m3") * 3600.0;
      }
      // Estimate oil content if we have oil phase
      if (fluid.hasPhaseType("oil") && fluid.hasPhaseType("aqueous")) {
        double oilMass = fluid.getPhase("oil").getMass(); // kg
        double waterVolume = fluid.getPhase("aqueous").getVolume("m3");
        if (waterVolume > 0) {
          inletOilConcentrationMgL = (oilMass / waterVolume) * 1e6; // mg/L
        }
      }
    }

    // Calculate treatment through each stage
    double currentOIW = inletOilConcentrationMgL;
    double totalRecoveredOil = 0.0;

    for (WaterTreatmentStage stage : stages) {
      double removedOIW = currentOIW * stage.getEfficiency();
      stage.setInletOIW(currentOIW);
      stage.setOutletOIW(currentOIW - removedOIW);
      currentOIW = stage.getOutletOIW();

      // Calculate recovered oil volume
      double removedOilKgH = (removedOIW / 1e6) * waterFlowRateM3h * 1000.0; // kg/h
      double oilDensity = 850.0; // kg/m³ typical
      double recoveredM3h = removedOilKgH / oilDensity;
      stage.setRecoveredOilM3h(recoveredM3h);
      totalRecoveredOil += recoveredM3h;
    }

    outletOilConcentrationMgL = currentOIW;
    recoveredOilM3h = totalRecoveredOil;
    overallEfficiency = 1.0 - (outletOilConcentrationMgL / inletOilConcentrationMgL);
    isCompliant = outletOilConcentrationMgL <= NCS_OIW_LIMIT_MGL;

    // Create outlet streams if inlet provided
    if (inletStream != null) {
      treatedWaterStream = new Stream(getName() + "_treatedWater", inletStream.getFluid().clone());
      recoveredOilStream = new Stream(getName() + "_recoveredOil", inletStream.getFluid().clone());
    }

    setCalculationIdentifier(id);
  }

  // ============================================================================
  // RESULTS
  // ============================================================================

  /**
   * Gets outlet oil-in-water concentration.
   *
   * @return OIW in mg/L
   */
  public double getOilInWaterMgL() {
    return outletOilConcentrationMgL;
  }

  /**
   * Gets outlet oil-in-water concentration in ppm.
   *
   * @return OIW in ppm (same as mg/L for water)
   */
  public double getOilInWaterPpm() {
    return outletOilConcentrationMgL;
  }

  /**
   * Checks if discharge meets NCS limits.
   *
   * @return true if OIW &lt; 30 mg/L
   */
  public boolean isDischargeCompliant() {
    return isCompliant;
  }

  /**
   * Checks compliance against a specific limit.
   *
   * @param limitMgL OIW limit in mg/L
   * @return true if compliant
   */
  public boolean isCompliantWith(double limitMgL) {
    return outletOilConcentrationMgL <= limitMgL;
  }

  /**
   * Gets overall treatment efficiency.
   *
   * @return efficiency (0.0-1.0)
   */
  public double getOverallEfficiency() {
    return overallEfficiency;
  }

  /**
   * Gets recovered oil flow rate.
   *
   * @return recovered oil in m³/h
   */
  public double getRecoveredOilM3h() {
    return recoveredOilM3h;
  }

  /**
   * Gets annual oil discharge.
   *
   * @param operatingHoursPerYear annual operating hours
   * @return annual oil discharge in tonnes
   */
  public double getAnnualOilDischargeTonnes(double operatingHoursPerYear) {
    double oilMassKgH = (outletOilConcentrationMgL / 1e6) * waterFlowRateM3h * 1000.0;
    return oilMassKgH * operatingHoursPerYear / 1000.0; // tonnes
  }

  /**
   * Gets treated water stream.
   *
   * @return treated water outlet
   */
  public StreamInterface getTreatedWaterStream() {
    return treatedWaterStream;
  }

  /**
   * Gets recovered oil stream.
   *
   * @return recovered oil outlet
   */
  public StreamInterface getRecoveredOilStream() {
    return recoveredOilStream;
  }

  /**
   * Gets list of treatment stages.
   *
   * @return treatment stages
   */
  public List<WaterTreatmentStage> getStages() {
    return new ArrayList<>(stages);
  }

  /**
   * Generates treatment summary report.
   *
   * @return markdown formatted report
   */
  public String generateReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("# Produced Water Treatment Report\n\n");
    sb.append(String.format("**Inlet OIW:** %.1f mg/L\n", inletOilConcentrationMgL));
    sb.append(String.format("**Outlet OIW:** %.1f mg/L\n", outletOilConcentrationMgL));
    sb.append(String.format("**Overall Efficiency:** %.1f%%\n", overallEfficiency * 100));
    sb.append(String.format("**NCS Compliant:** %s\n\n", isCompliant ? "✓ YES" : "✗ NO"));

    sb.append("## Stage Performance\n\n");
    sb.append("| Stage | Type | Inlet (mg/L) | Outlet (mg/L) | Efficiency |\n");
    sb.append("|-------|------|--------------|---------------|------------|\n");
    for (WaterTreatmentStage stage : stages) {
      sb.append(String.format("| %s | %s | %.1f | %.1f | %.1f%% |\n", stage.getName(),
          stage.getType(), stage.getInletOIW(), stage.getOutletOIW(), stage.getEfficiency() * 100));
    }

    return sb.toString();
  }

  // ============================================================================
  // INNER CLASSES
  // ============================================================================

  /**
   * Treatment stage type enumeration.
   */
  public enum StageType {
    /** Hydrocyclone - centrifugal separation. */
    HYDROCYCLONE,
    /** Induced/Dissolved Gas Flotation. */
    FLOTATION,
    /** Gravity skim tank. */
    SKIM_TANK,
    /** Coalescer pack. */
    COALESCER,
    /** Media filter. */
    FILTER,
    /** Membrane separation. */
    MEMBRANE
  }

  /**
   * Water treatment stage model.
   */
  public static class WaterTreatmentStage implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String name;
    private final StageType type;
    private double efficiency;
    private double inletOIW;
    private double outletOIW;
    private double recoveredOilM3h;

    /**
     * Creates a treatment stage.
     *
     * @param name stage name
     * @param type equipment type
     * @param efficiency removal efficiency (0.0-1.0)
     */
    public WaterTreatmentStage(String name, StageType type, double efficiency) {
      this.name = name;
      this.type = type;
      this.efficiency = Math.min(1.0, Math.max(0.0, efficiency));
    }

    public String getName() {
      return name;
    }

    public StageType getType() {
      return type;
    }

    public double getEfficiency() {
      return efficiency;
    }

    public void setEfficiency(double efficiency) {
      this.efficiency = Math.min(1.0, Math.max(0.0, efficiency));
    }

    public double getInletOIW() {
      return inletOIW;
    }

    public void setInletOIW(double inletOIW) {
      this.inletOIW = inletOIW;
    }

    public double getOutletOIW() {
      return outletOIW;
    }

    public void setOutletOIW(double outletOIW) {
      this.outletOIW = outletOIW;
    }

    public double getRecoveredOilM3h() {
      return recoveredOilM3h;
    }

    public void setRecoveredOilM3h(double recoveredOilM3h) {
      this.recoveredOilM3h = recoveredOilM3h;
    }
  }
}
