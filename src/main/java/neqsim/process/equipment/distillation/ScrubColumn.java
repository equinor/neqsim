package neqsim.process.equipment.distillation;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * LNG scrub column model for heavy hydrocarbon removal prior to liquefaction.
 *
 * <p>
 * The scrub column removes C5+ (and optionally C3+/C4+) heavy hydrocarbons from the feed gas before
 * it enters the main cryogenic heat exchanger. This prevents freeze-out of heavy hydrocarbons
 * (particularly benzene, cyclohexane) in the MCHE at cryogenic temperatures.
 * </p>
 *
 * <p>
 * This class extends {@link DistillationColumn} and adds:
 * </p>
 * <ul>
 * <li>Heavy key component specification (e.g., "n-pentane", "i-pentane")</li>
 * <li>Freeze-out temperature checking at column bottoms</li>
 * <li>Maximum allowable heavy component fraction in overhead gas</li>
 * <li>NGL recovery tracking for the bottoms product</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ScrubColumn extends DistillationColumn {
  private static final long serialVersionUID = 1004;
  private static final Logger logger = LogManager.getLogger(ScrubColumn.class);

  /** Heavy key component name (e.g., "n-pentane"). */
  private String heavyKeyComponent = "n-pentane";

  /** Maximum allowable mole fraction of heavy key in overhead gas. */
  private double maxHeavyKeyInOverhead = 0.001;

  /** Minimum column bottoms temperature to avoid freeze-out (K). */
  private double minimumBottomsTemperature = 273.15 - 50.0;

  /** Whether freeze-out risk was detected in the last run. */
  private boolean freezeOutRisk = false;

  /** Freeze-out temperature of the critical component (K). */
  private double freezeOutTemperature = 0.0;

  /** NGL recovery fraction (moles heavy in bottoms / moles heavy in feed). */
  private double nglRecovery = 0.0;

  /** Heavy key mole fraction in overhead after run. */
  private double heavyKeyInOverheadMolFrac = 0.0;

  /**
   * Constructor for ScrubColumn.
   *
   * @param name name of the scrub column
   * @param numberOfTrays number of theoretical trays (excluding condenser/reboiler)
   * @param hasReboiler set true to include a reboiler
   * @param hasCondenser set true to include a condenser
   */
  public ScrubColumn(String name, int numberOfTrays, boolean hasReboiler, boolean hasCondenser) {
    super(name, numberOfTrays, hasReboiler, hasCondenser);
  }

  /**
   * Set the heavy key component for separation specification.
   *
   * @param componentName name of the heavy key component (e.g., "n-pentane")
   */
  public void setHeavyKeyComponent(String componentName) {
    this.heavyKeyComponent = componentName;
  }

  /**
   * Get the heavy key component name.
   *
   * @return heavy key component name
   */
  public String getHeavyKeyComponent() {
    return heavyKeyComponent;
  }

  /**
   * Set the maximum allowable mole fraction of the heavy key in the overhead gas.
   *
   * @param maxFraction maximum mole fraction (e.g., 0.001 for 0.1%)
   */
  public void setMaxHeavyKeyInOverhead(double maxFraction) {
    this.maxHeavyKeyInOverhead = maxFraction;
  }

  /**
   * Get the maximum allowable heavy key mole fraction in overhead.
   *
   * @return maximum mole fraction
   */
  public double getMaxHeavyKeyInOverhead() {
    return maxHeavyKeyInOverhead;
  }

  /**
   * Set the minimum bottoms temperature to avoid freeze-out.
   *
   * @param temperature minimum temperature
   * @param unit temperature unit ("K" or "C")
   */
  public void setMinimumBottomsTemperature(double temperature, String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      this.minimumBottomsTemperature = temperature + 273.15;
    } else {
      this.minimumBottomsTemperature = temperature;
    }
  }

  /**
   * Get the minimum bottoms temperature.
   *
   * @return minimum bottoms temperature (K)
   */
  public double getMinimumBottomsTemperature() {
    return minimumBottomsTemperature;
  }

  /**
   * Check if freeze-out risk was detected in the last run.
   *
   * @return true if freeze-out risk was detected
   */
  public boolean hasFreezeOutRisk() {
    return freezeOutRisk;
  }

  /**
   * Get the freeze-out temperature of the critical component.
   *
   * @return freeze-out temperature (K)
   */
  public double getFreezeOutTemperature() {
    return freezeOutTemperature;
  }

  /**
   * Get the NGL recovery fraction (heavy components recovered in bottoms).
   *
   * @return NGL recovery fraction (0.0 to 1.0)
   */
  public double getNGLRecovery() {
    return nglRecovery;
  }

  /**
   * Get the heavy key mole fraction in the overhead gas.
   *
   * @return heavy key mole fraction
   */
  public double getHeavyKeyInOverheadMolFrac() {
    return heavyKeyInOverheadMolFrac;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Run the parent distillation column solver
    super.run(id);

    // Analyse overhead composition for heavy key
    StreamInterface gasOut = getGasOutStream();
    if (gasOut != null && gasOut.getThermoSystem() != null) {
      checkHeavyKeyInOverhead(gasOut);
    }

    // Check bottoms for freeze-out risk
    StreamInterface liqOut = getLiquidOutStream();
    if (liqOut != null && liqOut.getThermoSystem() != null) {
      checkFreezeOutRisk(liqOut);
    }

    // Calculate NGL recovery
    calculateNGLRecovery();

    if (freezeOutRisk) {
      logger.warn(String.format(
          "Scrub Column '%s': Freeze-out risk detected! Bottoms T=%.1f K, freeze-out T=%.1f K",
          getName(), liqOut != null ? liqOut.getTemperature() : 0.0, freezeOutTemperature));
    }

    if (heavyKeyInOverheadMolFrac > maxHeavyKeyInOverhead) {
      logger
          .warn(String.format("Scrub Column '%s': Heavy key %s in overhead = %.4f exceeds max %.4f",
              getName(), heavyKeyComponent, heavyKeyInOverheadMolFrac, maxHeavyKeyInOverhead));
    }

    setCalculationIdentifier(id);
  }

  /**
   * Check the overhead gas for heavy key component content.
   *
   * @param gasOut the gas outlet stream
   */
  private void checkHeavyKeyInOverhead(StreamInterface gasOut) {
    SystemInterface gasSystem = gasOut.getThermoSystem();
    heavyKeyInOverheadMolFrac = 0.0;
    try {
      if (gasSystem.getPhase(0).hasComponent(heavyKeyComponent)) {
        heavyKeyInOverheadMolFrac = gasSystem.getPhase(0).getComponent(heavyKeyComponent).getz();
      }
    } catch (Exception ex) {
      logger.debug("Heavy key component '" + heavyKeyComponent + "' not found in overhead", ex);
    }
  }

  /**
   * Check the bottoms liquid for freeze-out risk.
   *
   * @param liqOut the liquid outlet stream
   */
  private void checkFreezeOutRisk(StreamInterface liqOut) {
    freezeOutRisk = false;
    double bottomsTemp = liqOut.getTemperature();

    // Check if bottoms temperature is below minimum
    if (bottomsTemp < minimumBottomsTemperature) {
      freezeOutRisk = true;
    }

    // Estimate freeze-out temperature using solid flash
    try {
      SystemInterface checkSystem = liqOut.getThermoSystem().clone();
      ThermodynamicOperations ops = new ThermodynamicOperations(checkSystem);
      ops.TPSolidflash();
      if (checkSystem.hasPhaseType("solid")) {
        freezeOutRisk = true;
        freezeOutTemperature = bottomsTemp;
        logger.info("Solid phase detected at bottoms temperature: " + bottomsTemp + " K");
      }
    } catch (Exception ex) {
      // Solid flash may not be available for all systems
      logger.debug("Solid flash check failed", ex);
    }
  }

  /**
   * Calculate the NGL recovery of heavy components in the bottoms product.
   */
  private void calculateNGLRecovery() {
    nglRecovery = 0.0;
    StreamInterface gasOut = getGasOutStream();
    StreamInterface liqOut = getLiquidOutStream();

    if (gasOut == null || liqOut == null) {
      return;
    }

    SystemInterface gasSystem = gasOut.getThermoSystem();
    SystemInterface liqSystem = liqOut.getThermoSystem();

    if (gasSystem == null || liqSystem == null) {
      return;
    }

    try {
      if (gasSystem.getPhase(0).hasComponent(heavyKeyComponent)
          && liqSystem.getPhase(0).hasComponent(heavyKeyComponent)) {
        double molesInGas = gasSystem.getPhase(0).getComponent(heavyKeyComponent).getz()
            * gasSystem.getTotalNumberOfMoles();
        double molesInLiq = liqSystem.getPhase(0).getComponent(heavyKeyComponent).getz()
            * liqSystem.getTotalNumberOfMoles();
        double totalMoles = molesInGas + molesInLiq;
        if (totalMoles > 0) {
          nglRecovery = molesInLiq / totalMoles;
        }
      }
    } catch (Exception ex) {
      logger.debug("NGL recovery calculation failed", ex);
    }
  }
}
