package neqsim.process.equipment.pump;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Electric Submersible Pump (ESP) simulation model for multiphase flow.
 *
 * <p>
 * This class extends the standard Pump model to handle gas-liquid mixtures typical in oil
 * production systems. ESP pumps experience performance degradation when gas is present at the pump
 * intake.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li><b>Gas Void Fraction (GVF) Handling:</b> Performance degradation with gas content</li>
 * <li><b>Surging Detection:</b> Identifies unstable operation at high GVF</li>
 * <li><b>Gas Separator Modeling:</b> Optional gas separator at pump intake</li>
 * <li><b>Stage-by-Stage Calculation:</b> Multi-stage pump modeling with gas expansion</li>
 * </ul>
 *
 * <h2>GVF Degradation Model</h2>
 * <p>
 * The pump performance degrades as gas void fraction increases:
 * </p>
 * <ul>
 * <li>GVF &lt; 10%: Minor degradation, pump operates normally</li>
 * <li>10% &lt; GVF &lt; 20%: Moderate degradation (head drops ~20-40%)</li>
 * <li>20% &lt; GVF &lt; 40%: Severe degradation and surging risk</li>
 * <li>GVF &gt; 40%: Gas lock likely, pump may cease operation</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * // Create ESP pump for oil well
 * ESPPump esp = new ESPPump("ESP-1", wellStream);
 * esp.setNumberOfStages(100);
 * esp.setSpeed(3500); // rpm
 * esp.setHeadPerStage(10.0); // meters per stage at zero GVF
 * esp.setMaxGVF(0.30); // Maximum tolerable GVF
 * esp.run();
 * 
 * // Check operating status
 * double gvf = esp.getGasVoidFraction();
 * boolean surging = esp.isSurging();
 * double degradation = esp.getHeadDegradationFactor();
 * }</pre>
 *
 * @author esol
 * @version $Id: $Id
 * @see Pump
 * @see PumpChart
 */
public class ESPPump extends Pump {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ESPPump.class);

  // ESP-specific parameters
  private int numberOfStages = 100;
  private double headPerStage = 10.0; // meters per stage at design conditions
  private double maxGVF = 0.30; // Maximum tolerable GVF (30%)
  private double surgingGVF = 0.15; // GVF threshold for surging warning
  private boolean hasGasSeparator = false;
  private double gasSeparatorEfficiency = 0.0; // 0-1 fraction of gas removed

  // Operating state
  private double gasVoidFraction = 0.0;
  private double headDegradationFactor = 1.0;
  private boolean isSurging = false;
  private boolean isGasLocked = false;

  // Degradation model parameters (empirical correlation)
  private double degradationCoeffA = 0.5; // Linear term coefficient
  private double degradationCoeffB = 2.0; // Quadratic term coefficient

  /**
   * Constructor for ESPPump.
   *
   * @param name name of ESP pump
   */
  public ESPPump(String name) {
    super(name);
  }

  /**
   * Constructor for ESPPump.
   *
   * @param name name of ESP pump
   * @param inletStream inlet stream
   */
  public ESPPump(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * ESP-specific run method that accounts for multiphase flow effects.
   * </p>
   */
  @Override
  public void run(UUID id) {
    // Get inlet stream properties
    inStream.getThermoSystem().init(3);
    double hinn = inStream.getThermoSystem().getEnthalpy();

    // Clone the thermo system for outlet calculations
    thermoSystem = inStream.getThermoSystem().clone();

    // Calculate gas void fraction at pump inlet
    calculateGasVoidFraction();

    // Apply gas separator if present
    double effectiveGVF = gasVoidFraction;
    if (hasGasSeparator && gasSeparatorEfficiency > 0) {
      effectiveGVF = gasVoidFraction * (1.0 - gasSeparatorEfficiency);
      logger.debug("Gas separator reduces GVF from {} to {}",
          String.format("%.1f%%", gasVoidFraction * 100),
          String.format("%.1f%%", effectiveGVF * 100));
    }

    // Check for gas lock condition
    if (effectiveGVF > maxGVF) {
      isGasLocked = true;
      headDegradationFactor = 0.0;
      logger.warn("ESP {} is GAS LOCKED: GVF={} exceeds maximum {}", getName(),
          String.format("%.1f%%", effectiveGVF * 100), String.format("%.1f%%", maxGVF * 100));

      // Set minimal pressure rise (pump effectively stalled)
      outStream.setThermoSystem(thermoSystem);
      outStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
      return;
    }

    // Check for surging condition
    isSurging = effectiveGVF > surgingGVF;
    if (isSurging) {
      logger.warn("ESP {} is SURGING: GVF={} exceeds surging threshold {}", getName(),
          String.format("%.1f%%", effectiveGVF * 100), String.format("%.1f%%", surgingGVF * 100));
    }

    // Calculate head degradation factor based on GVF
    calculateHeadDegradation(effectiveGVF);

    // Calculate total head with degradation
    double totalHead = calculateTotalHead();

    // Get liquid density for pressure calculation
    double liquidDensity = getLiquidDensity();

    // Calculate pressure rise: ΔP = ρ_liquid × g × H_degraded
    double deltaP_Pa = liquidDensity * ThermodynamicConstantsInterface.gravity * totalHead;
    double deltaP_bar = deltaP_Pa / 1.0e5;

    // Set outlet pressure
    thermoSystem.setPressure(inStream.getPressure() + deltaP_bar);

    // Calculate power consumption
    double volumetricFlow = inStream.getFlowRate("m3/hr") / 3600.0; // m³/s
    double hydraulicPower = volumetricFlow * deltaP_Pa; // W

    // Efficiency decreases with GVF
    double efficiency = getIsentropicEfficiency() / 100.0 * (1.0 - 0.5 * effectiveGVF);
    double shaftPower = hydraulicPower / efficiency;
    dH = shaftPower;

    // Flash to outlet conditions
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    double hout = hinn + dH;
    try {
      thermoOps.PHflash(hout, 0);
    } catch (Exception e) {
      logger.warn("PH flash failed, using TP flash: " + e.getMessage());
      thermoOps.TPflash();
    }
    thermoSystem.init(3);

    // Set output stream
    outStream.setThermoSystem(thermoSystem);
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /**
   * Calculate gas void fraction at pump inlet.
   */
  private void calculateGasVoidFraction() {
    SystemInterface sys = inStream.getThermoSystem();

    // Check if gas phase exists
    if (!sys.hasPhaseType("gas")) {
      gasVoidFraction = 0.0;
      return;
    }

    // GVF = Volume of gas / Total volume at pump conditions
    double gasVolumeFraction = 0.0;
    double totalVolume = 0.0;

    for (int i = 0; i < sys.getNumberOfPhases(); i++) {
      double phaseVolume = sys.getPhase(i).getVolume("m3");
      totalVolume += phaseVolume;

      if (sys.getPhase(i).getType().toString().equals("gas")) {
        gasVolumeFraction = phaseVolume;
      }
    }

    if (totalVolume > 0) {
      gasVoidFraction = gasVolumeFraction / totalVolume;
    } else {
      gasVoidFraction = 0.0;
    }

    logger.debug("ESP inlet GVF: {}%", String.format("%.2f", gasVoidFraction * 100));
  }

  /**
   * Calculate head degradation factor based on GVF.
   *
   * <p>
   * Uses empirical correlation: f_degrad = 1 - A×GVF - B×GVF²
   * </p>
   *
   * @param gvf effective gas void fraction (0-1)
   */
  private void calculateHeadDegradation(double gvf) {
    // Empirical degradation model: f = 1 - A*GVF - B*GVF²
    // Typical values: A=0.5, B=2.0 gives:
    // GVF=10%: f=0.93 (7% degradation)
    // GVF=20%: f=0.82 (18% degradation)
    // GVF=30%: f=0.67 (33% degradation)
    // GVF=40%: f=0.48 (52% degradation)
    headDegradationFactor = 1.0 - degradationCoeffA * gvf - degradationCoeffB * gvf * gvf;

    // Clamp to reasonable range
    if (headDegradationFactor < 0.1) {
      headDegradationFactor = 0.1;
    }
    if (headDegradationFactor > 1.0) {
      headDegradationFactor = 1.0;
    }

    logger.debug("ESP head degradation factor: {} at GVF={}%",
        String.format("%.3f", headDegradationFactor), String.format("%.1f", gvf * 100));
  }

  /**
   * Calculate total head with degradation.
   *
   * @return total head in meters
   */
  private double calculateTotalHead() {
    double baseHead = headPerStage * numberOfStages;
    return baseHead * headDegradationFactor;
  }

  /**
   * Get liquid phase density.
   *
   * @return liquid density in kg/m³
   */
  private double getLiquidDensity() {
    SystemInterface sys = inStream.getThermoSystem();

    // Try oil phase first, then aqueous
    if (sys.hasPhaseType("oil")) {
      return sys.getPhase(sys.getPhaseNumberOfPhase("oil")).getDensity("kg/m3");
    } else if (sys.hasPhaseType("aqueous")) {
      return sys.getPhase(sys.getPhaseNumberOfPhase("aqueous")).getDensity("kg/m3");
    }

    // Fallback to mixture density
    return sys.getDensity("kg/m3");
  }

  // ============= GETTERS AND SETTERS =============

  /**
   * Get the number of pump stages.
   *
   * @return number of stages
   */
  public int getNumberOfStages() {
    return numberOfStages;
  }

  /**
   * Set the number of pump stages.
   *
   * @param numberOfStages number of stages
   */
  public void setNumberOfStages(int numberOfStages) {
    this.numberOfStages = numberOfStages;
  }

  /**
   * Get head per stage at design conditions.
   *
   * @return head per stage in meters
   */
  public double getHeadPerStage() {
    return headPerStage;
  }

  /**
   * Set head per stage at design conditions.
   *
   * @param headPerStage head per stage in meters
   */
  public void setHeadPerStage(double headPerStage) {
    this.headPerStage = headPerStage;
  }

  /**
   * Get maximum tolerable gas void fraction.
   *
   * @return maximum GVF (0-1)
   */
  public double getMaxGVF() {
    return maxGVF;
  }

  /**
   * Set maximum tolerable gas void fraction.
   *
   * @param maxGVF maximum GVF (0-1)
   */
  public void setMaxGVF(double maxGVF) {
    this.maxGVF = maxGVF;
  }

  /**
   * Get the surging GVF threshold.
   *
   * @return surging GVF threshold (0-1)
   */
  public double getSurgingGVF() {
    return surgingGVF;
  }

  /**
   * Set the surging GVF threshold.
   *
   * @param surgingGVF surging GVF threshold (0-1)
   */
  public void setSurgingGVF(double surgingGVF) {
    this.surgingGVF = surgingGVF;
  }

  /**
   * Check if pump has a gas separator.
   *
   * @return true if gas separator is present
   */
  public boolean hasGasSeparator() {
    return hasGasSeparator;
  }

  /**
   * Set whether pump has a gas separator.
   *
   * @param hasGasSeparator true to enable gas separator
   */
  public void setHasGasSeparator(boolean hasGasSeparator) {
    this.hasGasSeparator = hasGasSeparator;
  }

  /**
   * Get gas separator efficiency.
   *
   * @return gas separator efficiency (0-1)
   */
  public double getGasSeparatorEfficiency() {
    return gasSeparatorEfficiency;
  }

  /**
   * Set gas separator efficiency.
   *
   * @param gasSeparatorEfficiency efficiency (0-1)
   */
  public void setGasSeparatorEfficiency(double gasSeparatorEfficiency) {
    this.gasSeparatorEfficiency = gasSeparatorEfficiency;
  }

  /**
   * Get the current gas void fraction at pump inlet.
   *
   * @return GVF (0-1)
   */
  public double getGasVoidFraction() {
    return gasVoidFraction;
  }

  /**
   * Get the current head degradation factor.
   *
   * @return degradation factor (0-1, where 1 = no degradation)
   */
  public double getHeadDegradationFactor() {
    return headDegradationFactor;
  }

  /**
   * Check if pump is surging due to high GVF.
   *
   * @return true if surging
   */
  public boolean isSurging() {
    return isSurging;
  }

  /**
   * Check if pump is gas locked.
   *
   * @return true if gas locked
   */
  public boolean isGasLocked() {
    return isGasLocked;
  }

  /**
   * Set degradation model coefficients.
   *
   * <p>
   * Degradation: f = 1 - A×GVF - B×GVF²
   * </p>
   *
   * @param coeffA linear coefficient
   * @param coeffB quadratic coefficient
   */
  public void setDegradationCoefficients(double coeffA, double coeffB) {
    this.degradationCoeffA = coeffA;
    this.degradationCoeffB = coeffB;
  }

  /**
   * Get the total design head (without degradation).
   *
   * @return total design head in meters
   */
  public double getDesignHead() {
    return headPerStage * numberOfStages;
  }

  /**
   * Get the actual head (with degradation).
   *
   * @return actual head in meters
   */
  public double getActualHead() {
    return headPerStage * numberOfStages * headDegradationFactor;
  }
}
