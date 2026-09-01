package neqsim.process.equipment.reactor.sulfurrecovery;

import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Claus converter with cyclic below-dew-point sulfur adsorption and regeneration state.
 *
 * <p>
 * The reaction calculation is inherited from {@link ClausCatalyticConverter}. In adsorption mode, elemental sulfur is
 * removed from the gas up to the available bed capacity. Regeneration mode releases a user-configured fraction of
 * stored sulfur as a separate product stream. The retained inventory makes this unit suitable for sequential
 * dynamic/cycle studies.
 * </p>
 */
public class SubDewPointSulfurReactor extends ClausCatalyticConverter {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Cyclic bed operating mode. */
  public enum BedMode {
    /** Low-temperature Claus reaction and sulfur adsorption. */
    ADSORPTION,
    /** Hot regeneration and sulfur removal from the catalyst bed. */
    REGENERATION
  }

  private BedMode bedMode = BedMode.ADSORPTION;
  private double sulfurCapacityKg = 1000.0;
  private double adsorptionEfficiency = 0.995;
  private double regenerationFractionPerRun = 1.0;
  private double storedSulfurS8Moles;
  private StreamInterface sulfurProductStream;

  /** Create an unconnected sub-dew-point reactor. */
  public SubDewPointSulfurReactor(String name) {
    super(name);
  }

  /** Create a sub-dew-point reactor connected to a process stream. */
  public SubDewPointSulfurReactor(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    super.run(id);
    SystemInterface system = SulfurProcessUtil.prepareSystem(getOutletStream().getThermoSystem());
    double transferredMoles;
    if (bedMode == BedMode.ADSORPTION) {
      double capacityMoles = sulfurCapacityKg / SulfurProcessUtil.S8_MOLAR_MASS_KG_PER_MOL;
      double availableCapacity = Math.max(0.0, capacityMoles - storedSulfurS8Moles);
      transferredMoles = Math.min(availableCapacity, SulfurProcessUtil.moles(system, "S8") * adsorptionEfficiency);
      SulfurProcessUtil.addMoles(system, "S8", -transferredMoles);
      storedSulfurS8Moles += transferredMoles;
    } else {
      transferredMoles = storedSulfurS8Moles * SulfurProcessUtil.clamp(regenerationFractionPerRun, 0.0, 1.0);
      storedSulfurS8Moles -= transferredMoles;
    }
    SulfurProcessUtil.flash(system, getName());
    SulfurProcessUtil.updateOutlet(getOutletStream(), system, id);
    sulfurProductStream = SulfurProcessUtil.createSingleComponentStream(getName() + " sulfur product", system, "S8",
        transferredMoles, system.getTemperature(), system.getPressure(), id);
    setCalculationIdentifier(id);
  }

  /** Set bed operating mode. */
  public void setBedMode(BedMode bedMode) {
    if (bedMode == null) {
      throw new IllegalArgumentException("bedMode cannot be null");
    }
    this.bedMode = bedMode;
  }

  /** Return bed operating mode. */
  public BedMode getBedMode() {
    return bedMode;
  }

  /** Set total sulfur inventory capacity. */
  public void setSulfurCapacity(double value, String unit) {
    if ("tonne".equalsIgnoreCase(unit) || "t".equalsIgnoreCase(unit)) {
      sulfurCapacityKg = Math.max(value, 0.0) * 1000.0;
    } else if ("g".equalsIgnoreCase(unit)) {
      sulfurCapacityKg = Math.max(value, 0.0) / 1000.0;
    } else {
      sulfurCapacityKg = Math.max(value, 0.0);
    }
  }

  /** Set adsorption efficiency. */
  public void setAdsorptionEfficiency(double efficiency) {
    adsorptionEfficiency = SulfurProcessUtil.clamp(efficiency, 0.0, 1.0);
  }

  /** Set the stored-inventory fraction released by one regeneration run. */
  public void setRegenerationFractionPerRun(double fraction) {
    regenerationFractionPerRun = SulfurProcessUtil.clamp(fraction, 0.0, 1.0);
  }

  /** Return current stored sulfur inventory. */
  public double getStoredSulfur(String unit) {
    double massKg = storedSulfurS8Moles * SulfurProcessUtil.S8_MOLAR_MASS_KG_PER_MOL;
    return "tonne".equalsIgnoreCase(unit) ? massKg / 1000.0 : massKg;
  }

  /** Return sulfur transferred during the latest run. */
  public StreamInterface getSulfurProductStream() {
    return sulfurProductStream;
  }
}
