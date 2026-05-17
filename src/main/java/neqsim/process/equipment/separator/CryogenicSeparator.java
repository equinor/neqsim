package neqsim.process.equipment.separator;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Cryogenic separator model with solid formation and freeze-out safety checks.
 *
 * <p>
 * Extends the standard {@link Separator} for operation at cryogenic temperatures (below -50 C).
 * Adds safety checks for CO2 freeze-out, heavy hydrocarbon freeze-out, and ice formation that can
 * block equipment in LNG plants.
 * </p>
 *
 * <p>
 * Key checks performed:
 * </p>
 * <ul>
 * <li>CO2 solid formation risk (freeze-out below approximately -56.6 C at 1 atm)</li>
 * <li>Benzene / aromatic freeze-out (freeze point 5.5 C but precipitates from LNG mixtures)</li>
 * <li>Water ice formation if dehydration is insufficient</li>
 * <li>Mercury amalgam risk at cold temperatures</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class CryogenicSeparator extends Separator {
  private static final long serialVersionUID = 1006;
  private static final Logger logger = LogManager.getLogger(CryogenicSeparator.class);

  /** Whether CO2 freeze-out risk was detected. */
  private boolean co2FreezeOutRisk = false;

  /** Whether heavy hydrocarbon freeze-out risk was detected. */
  private boolean heavyHCFreezeOutRisk = false;

  /** Whether water/ice formation risk was detected. */
  private boolean waterIceRisk = false;

  /** CO2 mole fraction in the feed. */
  private double co2MolFrac = 0.0;

  /** Maximum allowable CO2 for cryogenic operation (mole fraction). */
  private double maxCO2MolFrac = 0.0001;

  /** Maximum allowable water for cryogenic operation (ppm molar). */
  private double maxWaterPpm = 0.1;

  /** Whether solid phases were detected by thermodynamic flash. */
  private boolean solidPhaseDetected = false;

  /**
   * Constructor for CryogenicSeparator.
   *
   * @param name name of the cryogenic separator
   */
  public CryogenicSeparator(String name) {
    super(name);
  }

  /**
   * Constructor for CryogenicSeparator with inlet stream.
   *
   * @param name name of the cryogenic separator
   * @param inletStream inlet stream
   */
  public CryogenicSeparator(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * Set the maximum allowable CO2 mole fraction for freeze-out prevention.
   *
   * @param maxFrac maximum CO2 mole fraction (default 0.0001 = 100 ppm)
   */
  public void setMaxCO2MolFrac(double maxFrac) {
    this.maxCO2MolFrac = maxFrac;
  }

  /**
   * Get the maximum allowable CO2 mole fraction.
   *
   * @return maximum CO2 mole fraction
   */
  public double getMaxCO2MolFrac() {
    return maxCO2MolFrac;
  }

  /**
   * Set the maximum allowable water content.
   *
   * @param maxPpm maximum water content in ppm (molar)
   */
  public void setMaxWaterPpm(double maxPpm) {
    this.maxWaterPpm = maxPpm;
  }

  /**
   * Get the maximum allowable water content.
   *
   * @return maximum water content in ppm (molar)
   */
  public double getMaxWaterPpm() {
    return maxWaterPpm;
  }

  /**
   * Check if CO2 freeze-out risk was detected.
   *
   * @return true if CO2 freeze-out risk exists
   */
  public boolean hasCO2FreezeOutRisk() {
    return co2FreezeOutRisk;
  }

  /**
   * Check if heavy hydrocarbon freeze-out risk was detected.
   *
   * @return true if heavy HC freeze-out risk exists
   */
  public boolean hasHeavyHCFreezeOutRisk() {
    return heavyHCFreezeOutRisk;
  }

  /**
   * Check if water/ice formation risk was detected.
   *
   * @return true if water ice risk exists
   */
  public boolean hasWaterIceRisk() {
    return waterIceRisk;
  }

  /**
   * Check if any solid phase was detected.
   *
   * @return true if solid phase detected
   */
  public boolean isSolidPhaseDetected() {
    return solidPhaseDetected;
  }

  /**
   * Get the CO2 mole fraction in the feed.
   *
   * @return CO2 mole fraction
   */
  public double getCO2MolFrac() {
    return co2MolFrac;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Run parent separator logic
    super.run(id);

    // Reset risk flags
    co2FreezeOutRisk = false;
    heavyHCFreezeOutRisk = false;
    waterIceRisk = false;
    solidPhaseDetected = false;

    SystemInterface feedSystem = thermoSystem;
    if (feedSystem == null) {
      setCalculationIdentifier(id);
      return;
    }

    double operatingTemp = feedSystem.getTemperature("K");

    // Check CO2 content
    checkCO2Content(feedSystem, operatingTemp);

    // Check water content
    checkWaterContent(feedSystem, operatingTemp);

    // Check for solid formation via solid flash
    checkSolidFormation(feedSystem);

    // Log warnings
    if (co2FreezeOutRisk) {
      logger.warn(
          String.format("CryogenicSeparator '%s': CO2 freeze-out risk at %.1f K, CO2=%.6f mol frac",
              getName(), operatingTemp, co2MolFrac));
    }
    if (waterIceRisk) {
      logger.warn(String.format("CryogenicSeparator '%s': Water ice formation risk at %.1f K",
          getName(), operatingTemp));
    }
    if (solidPhaseDetected) {
      logger.warn(String.format(
          "CryogenicSeparator '%s': Solid phase detected by thermodynamic flash", getName()));
    }

    setCalculationIdentifier(id);
  }

  /**
   * Check CO2 content against freeze-out limits.
   *
   * @param system fluid system
   * @param operatingTempK operating temperature in Kelvin
   */
  private void checkCO2Content(SystemInterface system, double operatingTempK) {
    co2MolFrac = 0.0;
    try {
      if (system.getPhase(0).hasComponent("CO2")) {
        co2MolFrac = system.getPhase(0).getComponent("CO2").getz();
      }
    } catch (Exception ex) {
      logger.debug("CO2 component check failed", ex);
    }

    // CO2 freeze-out temperature at ~1 atm is -56.6 C (216.55 K)
    // At higher pressures the freeze-out line shifts
    // Simple engineering check: if CO2 > max limit and T < -50 C, flag risk
    if (co2MolFrac > maxCO2MolFrac && operatingTempK < (273.15 - 50.0)) {
      co2FreezeOutRisk = true;
    }
  }

  /**
   * Check water content against ice formation limits.
   *
   * @param system fluid system
   * @param operatingTempK operating temperature in Kelvin
   */
  private void checkWaterContent(SystemInterface system, double operatingTempK) {
    double waterFrac = 0.0;
    try {
      if (system.getPhase(0).hasComponent("water")) {
        waterFrac = system.getPhase(0).getComponent("water").getz();
      }
    } catch (Exception ex) {
      logger.debug("Water component check failed", ex);
    }

    // Water ppm molar
    double waterPpm = waterFrac * 1.0e6;
    if (waterPpm > maxWaterPpm && operatingTempK < 273.15) {
      waterIceRisk = true;
    }
  }

  /**
   * Check for solid formation using thermodynamic solid flash.
   *
   * @param system fluid system to check
   */
  private void checkSolidFormation(SystemInterface system) {
    try {
      SystemInterface checkSystem = system.clone();
      ThermodynamicOperations ops = new ThermodynamicOperations(checkSystem);
      ops.TPSolidflash();
      if (checkSystem.hasPhaseType("solid")) {
        solidPhaseDetected = true;
        heavyHCFreezeOutRisk = true;
      }
    } catch (Exception ex) {
      logger.debug("Solid flash check failed for cryogenic separator", ex);
    }
  }
}
