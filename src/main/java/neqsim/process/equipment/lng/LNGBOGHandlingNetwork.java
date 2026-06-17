package neqsim.process.equipment.lng;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Models the BOG handling network on an LNG carrier or terminal.
 *
 * <p>
 * The BOG handling network determines the fate of boil-off gas generated in the cargo tanks. On a
 * modern LNG carrier, BOG can be:
 * </p>
 * <ul>
 * <li><b>Fuel for propulsion:</b> Used as fuel in DFDE (dual fuel diesel electric) engines or steam
 * boilers</li>
 * <li><b>Reliquefied:</b> Compressed and cooled to return to the cargo tanks as liquid</li>
 * <li><b>Burned in GCU:</b> Gas Combustion Unit for excess BOG disposal</li>
 * <li><b>Vented:</b> Emergency only — environmental regulations prohibit routine venting</li>
 * </ul>
 *
 * <p>
 * The network acts as a sink for BOG and determines the net cargo loss rate. The reliquefaction
 * return reduces the effective boil-off, while fuel consumption increases it. The split between
 * handling modes affects both the economic outcome and the compositional ageing rate.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LNGBOGHandlingNetwork implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1010L;

  /** Logger object. */
  private static final Logger logger = LogManager.getLogger(LNGBOGHandlingNetwork.class);

  /**
   * BOG handling mode.
   */
  public enum HandlingMode {
    /** BOG used as fuel for propulsion (steam turbine, DFDE). */
    FUEL_ONLY,
    /** BOG reliquefied and returned to cargo tanks. */
    RELIQUEFACTION,
    /** Combination of fuel use and reliquefaction. */
    FUEL_PLUS_RELIQUEFACTION,
    /** Excess BOG burned in Gas Combustion Unit. */
    GCU,
    /** MEGI (M-type, Electronically Controlled, Gas Injection) engine fuel. */
    MEGI
  }

  /** Current handling mode. */
  private HandlingMode handlingMode = HandlingMode.FUEL_ONLY;

  /** Fuel gas consumption rate (kg/hr). Vessel propulsion demand. */
  private double fuelGasConsumptionRate = 5000.0;

  /** Reliquefaction plant capacity (kg/hr). Typical range 3000-8000 kg/hr. */
  private double reliquefactionCapacity = 5000.0;

  /** Reliquefaction efficiency (fraction of BOG successfully reliquefied). */
  private double reliquefactionEfficiency = 0.90;

  /** Reliquefaction power consumption (kW per kg/hr of reliquefied gas). */
  private double reliquefactionSpecificPower = 0.8;

  /** GCU capacity (kg/hr). */
  private double gcuCapacity = 10000.0;

  /** Whether the loaded voyage is in progress (true) or ballast voyage (false). */
  private boolean loadedVoyage = true;

  /** Vessel speed (knots). Affects fuel consumption. */
  private double vesselSpeed = 19.5;

  /** Base fuel gas consumption at design speed (kg/hr). */
  private double baseFuelConsumption = 5000.0;

  /** Speed exponent for fuel consumption (Q ~ V^n, typically n=2.5-3.0). */
  private double speedExponent = 2.8;

  /** Design speed (knots). */
  private double designSpeed = 19.5;

  /**
   * Default constructor.
   */
  public LNGBOGHandlingNetwork() {}

  /**
   * Constructor with handling mode.
   *
   * @param mode BOG handling mode
   */
  public LNGBOGHandlingNetwork(HandlingMode mode) {
    this.handlingMode = mode;
  }

  /**
   * Calculate the disposition of BOG across all handling modes.
   *
   * <p>
   * Priority order: fuel consumption first, then reliquefaction (if available), then GCU for
   * excess.
   * </p>
   *
   * @param bogGeneratedKgHr total BOG generated (kg/hr)
   * @return BOG disposition breakdown
   */
  public BOGDisposition calculateDisposition(double bogGeneratedKgHr) {
    BOGDisposition disp = new BOGDisposition();
    disp.bogGenerated = bogGeneratedKgHr;

    double remainingBOG = bogGeneratedKgHr;

    // Current fuel demand (adjusted for speed)
    double fuelDemand = calculateFuelDemand();

    // 1. Fuel consumption — always takes priority
    if (handlingMode != HandlingMode.RELIQUEFACTION) {
      double fuelUsed = Math.min(remainingBOG, fuelDemand);
      disp.bogToFuel = fuelUsed;
      remainingBOG -= fuelUsed;
    }

    // 2. Reliquefaction
    if (handlingMode == HandlingMode.RELIQUEFACTION
        || handlingMode == HandlingMode.FUEL_PLUS_RELIQUEFACTION) {
      double reliqCapacityAvail = reliquefactionCapacity;
      double reliquefied = Math.min(remainingBOG, reliqCapacityAvail) * reliquefactionEfficiency;
      disp.bogReliquefied = reliquefied;
      disp.reliquefactionPowerKW = reliquefied * reliquefactionSpecificPower;
      remainingBOG -= reliquefied;
    }

    // 3. GCU for any remaining excess
    if (remainingBOG > 0) {
      double gcuBurned = Math.min(remainingBOG, gcuCapacity);
      disp.bogToGCU = gcuBurned;
      remainingBOG -= gcuBurned;
    }

    // 4. Any remaining = forced boil-off / vented (should not happen)
    if (remainingBOG > 0) {
      disp.bogVented = remainingBOG;
      logger
          .warn(String.format("%.1f kg/hr BOG exceeds handling capacity — venting!", remainingBOG));
    }

    // Net cargo loss = generated - reliquefied return
    disp.netCargoLoss = bogGeneratedKgHr - disp.bogReliquefied;

    return disp;
  }

  /**
   * Calculate current fuel gas demand based on vessel speed.
   *
   * <p>
   * Fuel consumption scales approximately with the cube of speed (due to cubic relationship between
   * hull resistance and speed): Q_fuel = Q_base * (V / V_design)^n
   * </p>
   *
   * @return fuel demand (kg/hr)
   */
  public double calculateFuelDemand() {
    if (!loadedVoyage) {
      // Ballast voyage — lower fuel demand (no cargo, lighter ship)
      return baseFuelConsumption * 0.7 * Math.pow(vesselSpeed / designSpeed, speedExponent);
    }
    return baseFuelConsumption * Math.pow(vesselSpeed / designSpeed, speedExponent);
  }

  /**
   * BOG disposition breakdown across handling modes.
   */
  public static class BOGDisposition implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1011L;

    /** Total BOG generated (kg/hr). */
    public double bogGenerated;

    /** BOG consumed as fuel (kg/hr). */
    public double bogToFuel;

    /** BOG reliquefied and returned to cargo (kg/hr). */
    public double bogReliquefied;

    /** BOG burned in GCU (kg/hr). */
    public double bogToGCU;

    /** BOG vented (emergency only) (kg/hr). */
    public double bogVented;

    /** Net cargo loss (kg/hr) = generated - reliquefied. */
    public double netCargoLoss;

    /** Reliquefaction power consumption (kW). */
    public double reliquefactionPowerKW;

    /**
     * Get the effective net BOG rate considering reliquefaction return.
     *
     * @return net BOG rate (kg/hr)
     */
    public double getNetBOGRate() {
      return netCargoLoss;
    }

    /**
     * Check if any BOG is being vented.
     *
     * @return true if BOG is being vented
     */
    public boolean isVenting() {
      return bogVented > 0;
    }

    /**
     * Get the reliquefaction return fraction.
     *
     * @return fraction of BOG reliquefied (0-1)
     */
    public double getReliquefactionFraction() {
      return (bogGenerated > 0) ? bogReliquefied / bogGenerated : 0;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return String.format(
          "BOG=%.0f kg/hr [Fuel=%.0f, Reliq=%.0f, GCU=%.0f, Vent=%.0f], NetLoss=%.0f kg/hr",
          bogGenerated, bogToFuel, bogReliquefied, bogToGCU, bogVented, netCargoLoss);
    }
  }

  /**
   * Get handling mode.
   *
   * @return current handling mode
   */
  public HandlingMode getHandlingMode() {
    return handlingMode;
  }

  /**
   * Set handling mode.
   *
   * @param mode handling mode
   */
  public void setHandlingMode(HandlingMode mode) {
    this.handlingMode = mode;
  }

  /**
   * Get fuel gas consumption rate.
   *
   * @return fuel consumption (kg/hr)
   */
  public double getFuelGasConsumptionRate() {
    return fuelGasConsumptionRate;
  }

  /**
   * Set fuel gas consumption rate.
   *
   * @param rate fuel consumption (kg/hr)
   */
  public void setFuelGasConsumptionRate(double rate) {
    this.fuelGasConsumptionRate = rate;
  }

  /**
   * Get reliquefaction capacity.
   *
   * @return capacity (kg/hr)
   */
  public double getReliquefactionCapacity() {
    return reliquefactionCapacity;
  }

  /**
   * Set reliquefaction capacity.
   *
   * @param capacity capacity (kg/hr)
   */
  public void setReliquefactionCapacity(double capacity) {
    this.reliquefactionCapacity = capacity;
  }

  /**
   * Get reliquefaction efficiency.
   *
   * @return efficiency (0-1)
   */
  public double getReliquefactionEfficiency() {
    return reliquefactionEfficiency;
  }

  /**
   * Set reliquefaction efficiency.
   *
   * @param efficiency efficiency (0-1)
   */
  public void setReliquefactionEfficiency(double efficiency) {
    this.reliquefactionEfficiency = efficiency;
  }

  /**
   * Get GCU capacity.
   *
   * @return capacity (kg/hr)
   */
  public double getGcuCapacity() {
    return gcuCapacity;
  }

  /**
   * Set GCU capacity.
   *
   * @param capacity capacity (kg/hr)
   */
  public void setGcuCapacity(double capacity) {
    this.gcuCapacity = capacity;
  }

  /**
   * Check if loaded voyage.
   *
   * @return true if loaded voyage
   */
  public boolean isLoadedVoyage() {
    return loadedVoyage;
  }

  /**
   * Set whether loaded voyage.
   *
   * @param loaded true if loaded voyage
   */
  public void setLoadedVoyage(boolean loaded) {
    this.loadedVoyage = loaded;
  }

  /**
   * Get vessel speed.
   *
   * @return speed (knots)
   */
  public double getVesselSpeed() {
    return vesselSpeed;
  }

  /**
   * Set vessel speed.
   *
   * @param speed speed (knots)
   */
  public void setVesselSpeed(double speed) {
    this.vesselSpeed = speed;
  }

  /**
   * Get base fuel consumption.
   *
   * @return base consumption (kg/hr)
   */
  public double getBaseFuelConsumption() {
    return baseFuelConsumption;
  }

  /**
   * Set base fuel consumption at design speed.
   *
   * @param consumption base consumption (kg/hr)
   */
  public void setBaseFuelConsumption(double consumption) {
    this.baseFuelConsumption = consumption;
  }

  /**
   * Get design speed.
   *
   * @return design speed (knots)
   */
  public double getDesignSpeed() {
    return designSpeed;
  }

  /**
   * Set design speed.
   *
   * @param speed design speed (knots)
   */
  public void setDesignSpeed(double speed) {
    this.designSpeed = speed;
  }

  /**
   * Get reliquefaction specific power.
   *
   * @return specific power (kW per kg/hr)
   */
  public double getReliquefactionSpecificPower() {
    return reliquefactionSpecificPower;
  }

  /**
   * Set reliquefaction specific power.
   *
   * @param power specific power (kW per kg/hr)
   */
  public void setReliquefactionSpecificPower(double power) {
    this.reliquefactionSpecificPower = power;
  }
}
