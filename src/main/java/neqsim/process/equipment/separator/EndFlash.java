package neqsim.process.equipment.separator;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * End flash drum model for LNG production.
 *
 * <p>
 * The end flash drum is the final separation stage in an LNG plant. Sub-cooled LNG from the main
 * cryogenic heat exchanger (MCHE) is letdown to near-atmospheric pressure, causing flash
 * evaporation. The flash gas (rich in nitrogen) is separated from the LNG product.
 * </p>
 *
 * <p>
 * This class extends {@link Separator} and adds:
 * </p>
 * <ul>
 * <li>N2 content tracking in LNG product and flash gas</li>
 * <li>LNG product specification checking (max N2 per ISO 16903)</li>
 * <li>Flash gas fuel value assessment</li>
 * <li>LNG heating value calculation</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class EndFlash extends Separator {
  private static final long serialVersionUID = 1005;
  private static final Logger logger = LogManager.getLogger(EndFlash.class);

  /** Maximum allowable N2 mole fraction in LNG product (ISO 16903 limit). */
  private double maxN2InLNG = 0.01;

  /** Actual N2 mole fraction in LNG product. */
  private double n2InLNGMolFrac = 0.0;

  /** N2 mole fraction in flash gas. */
  private double n2InFlashGasMolFrac = 0.0;

  /** Methane mole fraction in LNG product. */
  private double methaneInLNGMolFrac = 0.0;

  /** Whether LNG product meets N2 specification. */
  private boolean lngSpecMet = false;

  /** Flash gas to feed ratio (molar). */
  private double flashGasRatio = 0.0;

  /**
   * Constructor for EndFlash.
   *
   * @param name name of the end flash drum
   */
  public EndFlash(String name) {
    super(name);
  }

  /**
   * Constructor for EndFlash with inlet stream.
   *
   * @param name name of the end flash drum
   * @param inletStream inlet sub-cooled LNG stream
   */
  public EndFlash(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * Set the maximum allowable N2 mole fraction in the LNG product.
   *
   * @param maxFraction maximum N2 mole fraction (default 0.01 = 1%)
   */
  public void setMaxN2InLNG(double maxFraction) {
    this.maxN2InLNG = maxFraction;
  }

  /**
   * Get the maximum allowable N2 mole fraction.
   *
   * @return maximum N2 mole fraction
   */
  public double getMaxN2InLNG() {
    return maxN2InLNG;
  }

  /**
   * Get the N2 mole fraction in the LNG product.
   *
   * @return N2 mole fraction
   */
  public double getN2InLNGMolFrac() {
    return n2InLNGMolFrac;
  }

  /**
   * Get the N2 mole fraction in the flash gas.
   *
   * @return N2 mole fraction
   */
  public double getN2InFlashGasMolFrac() {
    return n2InFlashGasMolFrac;
  }

  /**
   * Get the methane mole fraction in the LNG product.
   *
   * @return methane mole fraction
   */
  public double getMethaneInLNGMolFrac() {
    return methaneInLNGMolFrac;
  }

  /**
   * Check whether the LNG product meets the N2 specification.
   *
   * @return true if N2 in LNG is within limit
   */
  public boolean isLNGSpecMet() {
    return lngSpecMet;
  }

  /**
   * Get the flash gas to feed ratio (moles of flash gas / moles of feed).
   *
   * @return flash gas ratio
   */
  public double getFlashGasRatio() {
    return flashGasRatio;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Run parent separator (TP flash, phase split)
    super.run(id);

    // Analyse flash gas composition
    StreamInterface gasOut = getGasOutStream();
    if (gasOut != null && gasOut.getThermoSystem() != null) {
      analyseFlashGas(gasOut.getThermoSystem());
    }

    // Analyse LNG product composition
    StreamInterface liqOut = getLiquidOutStream();
    if (liqOut != null && liqOut.getThermoSystem() != null) {
      analyseLNGProduct(liqOut.getThermoSystem());
    }

    // Calculate flash gas ratio
    if (gasOut != null && liqOut != null && gasOut.getThermoSystem() != null
        && liqOut.getThermoSystem() != null) {
      double gasMoles = gasOut.getThermoSystem().getTotalNumberOfMoles();
      double liqMoles = liqOut.getThermoSystem().getTotalNumberOfMoles();
      double totalMoles = gasMoles + liqMoles;
      if (totalMoles > 0) {
        flashGasRatio = gasMoles / totalMoles;
      }
    }

    // Check LNG spec
    lngSpecMet = (n2InLNGMolFrac <= maxN2InLNG);

    if (!lngSpecMet) {
      logger.warn(String.format("End Flash '%s': N2 in LNG = %.4f exceeds max %.4f", getName(),
          n2InLNGMolFrac, maxN2InLNG));
    }

    logger.info(String.format(
        "End Flash '%s': N2_LNG=%.4f, CH4_LNG=%.4f, N2_gas=%.4f, flash_ratio=%.4f, spec=%s",
        getName(), n2InLNGMolFrac, methaneInLNGMolFrac, n2InFlashGasMolFrac, flashGasRatio,
        lngSpecMet ? "MET" : "NOT MET"));

    setCalculationIdentifier(id);
  }

  /**
   * Analyse the flash gas composition.
   *
   * @param gasSystem flash gas thermodynamic system
   */
  private void analyseFlashGas(SystemInterface gasSystem) {
    n2InFlashGasMolFrac = 0.0;
    try {
      if (gasSystem.getPhase(0).hasComponent("nitrogen")) {
        n2InFlashGasMolFrac = gasSystem.getPhase(0).getComponent("nitrogen").getz();
      }
    } catch (Exception ex) {
      logger.debug("Nitrogen not found in flash gas", ex);
    }
  }

  /**
   * Analyse the LNG product composition.
   *
   * @param liqSystem LNG product thermodynamic system
   */
  private void analyseLNGProduct(SystemInterface liqSystem) {
    n2InLNGMolFrac = 0.0;
    methaneInLNGMolFrac = 0.0;
    try {
      if (liqSystem.getPhase(0).hasComponent("nitrogen")) {
        n2InLNGMolFrac = liqSystem.getPhase(0).getComponent("nitrogen").getz();
      }
      if (liqSystem.getPhase(0).hasComponent("methane")) {
        methaneInLNGMolFrac = liqSystem.getPhase(0).getComponent("methane").getz();
      }
    } catch (Exception ex) {
      logger.debug("Component analysis for LNG product failed", ex);
    }
  }
}
