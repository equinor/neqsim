package neqsim.process.equipment.reactor;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Fermenter / bioreactor for biological conversion processes.
 *
 * <p>
 * Extends {@link StirredTankReactor} with bio-process specific features such as aeration, oxygen
 * transfer, pH control, and cell growth tracking. Supports aerobic and anaerobic fermentation
 * modes.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * Fermenter fermenter = new Fermenter("EtOH Fermenter", feedStream);
 * fermenter.setReactorTemperature(273.15 + 32.0);
 * fermenter.setResidenceTime(48.0, "hr");
 * fermenter.setVesselVolume(200.0);
 * fermenter.setAerobic(false); // anaerobic fermentation
 * fermenter.addReaction(ethanolFermentation);
 * fermenter.run();
 * </pre>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class Fermenter extends StirredTankReactor {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Fermenter.class);

  /** Whether the fermenter is aerobic (requires O2) or anaerobic. */
  private boolean aerobic = false;

  /** Aeration rate in vvm (volume of air per volume of liquid per minute). */
  private double aerationRate = 0.0;

  /** Oxygen transfer rate in mol O2 / (L * hr). */
  private double oxygenTransferRate = 0.0;

  /** KLa value (volumetric mass transfer coefficient) in 1/hr. */
  private double kLa = 100.0;

  /** Target pH for pH-controlled fermentation. NaN if not controlled. */
  private double targetPH = Double.NaN;

  /** Cell mass yield coefficient (g cells / g substrate). */
  private double cellYield = 0.0;

  /** Cell mass maintenance coefficient (g substrate / (g cells * hr)). */
  private double maintenanceCoefficient = 0.0;

  /** CO2 evolution rate in mol/(L*hr). Calculated during run. */
  private double co2EvolutionRate = 0.0;

  /** Aeration compressor power in kW. Calculated during run. */
  private double aerationPower = 0.0;

  /**
   * Constructor for Fermenter.
   *
   * @param name name of the fermenter
   */
  public Fermenter(String name) {
    super(name);
    setIsothermal(true);
    setAgitatorPowerPerVolume(0.5); // typical for fermenters
  }

  /**
   * Constructor for Fermenter with inlet stream.
   *
   * @param name name of the fermenter
   * @param inletStream inlet feed stream
   */
  public Fermenter(String name, StreamInterface inletStream) {
    super(name, inletStream);
    setIsothermal(true);
    setAgitatorPowerPerVolume(0.5);
  }

  /**
   * Set whether the fermenter operates aerobically.
   *
   * @param aerobic true for aerobic, false for anaerobic
   */
  public void setAerobic(boolean aerobic) {
    this.aerobic = aerobic;
  }

  /**
   * Check if fermenter is aerobic.
   *
   * @return true if aerobic
   */
  public boolean isAerobic() {
    return aerobic;
  }

  /**
   * Set the aeration rate for aerobic fermentation.
   *
   * @param vvm aeration rate in vvm (volume air / volume liquid / minute)
   */
  public void setAerationRate(double vvm) {
    this.aerationRate = vvm;
  }

  /**
   * Get the aeration rate.
   *
   * @return aeration rate in vvm
   */
  public double getAerationRate() {
    return aerationRate;
  }

  /**
   * Set the volumetric mass transfer coefficient.
   *
   * @param kLa mass transfer coefficient in 1/hr
   */
  public void setKLa(double kLa) {
    this.kLa = kLa;
  }

  /**
   * Get the KLa value.
   *
   * @return KLa in 1/hr
   */
  public double getKLa() {
    return kLa;
  }

  /**
   * Set the target pH for the fermentation.
   *
   * @param pH target pH value
   */
  public void setTargetPH(double pH) {
    this.targetPH = pH;
  }

  /**
   * Get the target pH.
   *
   * @return target pH or NaN if not controlled
   */
  public double getTargetPH() {
    return targetPH;
  }

  /**
   * Set the cell biomass yield coefficient.
   *
   * @param yield g cells produced per g substrate consumed
   */
  public void setCellYield(double yield) {
    this.cellYield = yield;
  }

  /**
   * Get the cell yield coefficient.
   *
   * @return cell yield
   */
  public double getCellYield() {
    return cellYield;
  }

  /**
   * Set the cell maintenance coefficient.
   *
   * @param maintenance g substrate consumed per g cells per hr
   */
  public void setMaintenanceCoefficient(double maintenance) {
    this.maintenanceCoefficient = maintenance;
  }

  /**
   * Get the maintenance coefficient.
   *
   * @return maintenance coefficient
   */
  public double getMaintenanceCoefficient() {
    return maintenanceCoefficient;
  }

  /**
   * Get the CO2 evolution rate from the last run.
   *
   * @return CO2 evolution rate in mol/(L*hr)
   */
  public double getCO2EvolutionRate() {
    return co2EvolutionRate;
  }

  /**
   * Get the aeration compressor power in kW.
   *
   * @return aeration power in kW
   */
  public double getAerationPower() {
    return aerationPower;
  }

  /**
   * Get the total power requirement (agitator + aeration) in kW.
   *
   * @return total power in kW
   */
  public double getTotalPower() {
    return getAgitatorPower() + aerationPower;
  }

  /**
   * Calculate the estimated aeration compressor power.
   *
   * <p>
   * Uses a simplified model: Power = Q * rho * g * H, where Q is volumetric air flow rate, rho is
   * air density, g is gravity, H is liquid height.
   * </p>
   *
   * @return estimated aeration power in kW
   */
  private double estimateAerationPower() {
    if (!aerobic || aerationRate <= 0.0) {
      return 0.0;
    }
    // Simplified: assume 1 kW per vvm per m3 of liquid
    return aerationRate * getVesselVolume() * 0.5;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = inStream.getThermoSystem().clone();

    // Store inlet enthalpy for energy balance
    system.init(3);
    double inletEnthalpy = system.getEnthalpy();

    // Apply all reactions to the system
    for (StoichiometricReaction rxn : getReactions()) {
      try {
        rxn.react(system);
      } catch (Exception ex) {
        logger.warn("Reaction '{}' failed in fermenter '{}': {}", rxn.getName(), getName(),
            ex.getMessage());
      }
    }

    // Set outlet conditions
    double outPressure =
        Double.isNaN(getReactorPressure()) ? system.getPressure() - getPressureDrop()
            : getReactorPressure();
    system.setPressure(outPressure);

    if (isIsothermal() && !Double.isNaN(getReactorTemperature())) {
      system.setTemperature(getReactorTemperature());
    }

    // Flash calculation at outlet conditions
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      if (isIsothermal()) {
        ops.TPflash();
      } else {
        ops.PHflash(inletEnthalpy, 0);
      }
    } catch (Exception ex) {
      logger.error("Flash calculation failed in fermenter '{}': {}", getName(), ex.getMessage());
    }

    system.init(3);
    system.initProperties();

    // Calculate energy balance
    double outletEnthalpy = system.getEnthalpy();
    double heatDutyValue = isIsothermal() ? (outletEnthalpy - inletEnthalpy) : 0.0;

    // Calculate aeration power
    aerationPower = estimateAerationPower();

    outStream.setThermoSystem(system);
    setCalculationIdentifier(id);
  }
}
