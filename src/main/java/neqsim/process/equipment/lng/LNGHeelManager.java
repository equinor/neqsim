package neqsim.process.equipment.lng;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Manages LNG heel retention, cooldown, and mixing operations.
 *
 * <p>
 * The "heel" is the small quantity of LNG retained in cargo tanks at the end of unloading. It
 * serves critical functions:
 * </p>
 * <ul>
 * <li><b>Tank cooldown:</b> Maintains cryogenic temperature during ballast voyage, preventing
 * thermal cycling damage to the containment system</li>
 * <li><b>Mixing with new cargo:</b> When fresh cargo is loaded on top of heel, the resulting
 * mixture may have different composition and density, potentially creating stratification</li>
 * <li><b>Spray cooling:</b> Heel can be sprayed to maintain tank temperature during long ballast
 * voyages</li>
 * </ul>
 *
 * <p>
 * Heel quantity is a trade-off: too little means the tank warms up and requires expensive cooldown;
 * too much means cargo revenue loss. Typical heel is 2-5% of tank volume.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LNGHeelManager implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1012L;

  /** Logger object. */
  private static final Logger logger = LogManager.getLogger(LNGHeelManager.class);

  /** Heel volume as fraction of total tank volume (0-1). */
  private double heelFraction = 0.03;

  /** Heel composition (mole fractions keyed by component name). */
  private Map<String, Double> heelComposition;

  /** Heel temperature (K). */
  private double heelTemperature = 111.0;

  /** Heel density (kg/m3). */
  private double heelDensity = 450.0;

  /** Total tank volume (m3). */
  private double tankVolume = 140000.0;

  /** Spray cooling rate (kg/hr). Rate of heel recirculation as spray. */
  private double sprayCoolingRate = 2000.0;

  /** Whether spray cooling is active. */
  private boolean sprayCoolingActive = false;

  /** Maximum allowable tank temperature for warm tank loading (K). */
  private double maxWarmTankTemperature = 273.15 - 130.0;

  /** Current tank wall temperature (K). Only relevant during ballast. */
  private double tankWallTemperature;

  /**
   * Default constructor.
   */
  public LNGHeelManager() {
    this.heelComposition = new LinkedHashMap<String, Double>();
    this.tankWallTemperature = 111.0;
  }

  /**
   * Constructor with heel fraction.
   *
   * @param heelFraction heel as fraction of tank volume (0-1)
   * @param tankVolume total tank volume (m3)
   */
  public LNGHeelManager(double heelFraction, double tankVolume) {
    this();
    this.heelFraction = heelFraction;
    this.tankVolume = tankVolume;
  }

  /**
   * Set the heel composition from an aged LNG fluid (typically the residual at end of unloading).
   *
   * @param composition mole fractions keyed by component name
   * @param temperature heel temperature (K)
   * @param density heel density (kg/m3)
   */
  public void setHeelState(Map<String, Double> composition, double temperature, double density) {
    this.heelComposition = new LinkedHashMap<String, Double>(composition);
    this.heelTemperature = temperature;
    this.heelDensity = density;
  }

  /**
   * Calculate the resulting mixture when new cargo is loaded on top of heel.
   *
   * <p>
   * Returns the mixed composition assuming instantaneous ideal mixing. In reality, incomplete
   * mixing creates stratification which must be handled by the layered tank model.
   * </p>
   *
   * @param newCargoComposition new cargo mole fractions
   * @param newCargoMoles moles of new cargo
   * @param newCargoTemperature new cargo temperature (K)
   * @return mixed composition (mole fractions)
   */
  public Map<String, Double> calculateMixedComposition(Map<String, Double> newCargoComposition,
      double newCargoMoles, double newCargoTemperature) {
    double heelVolume = getHeelVolume();
    double heelMoles = 0;
    if (heelDensity > 0) {
      // Estimate heel moles from volume and approximate molar mass (16.5 g/mol for LNG)
      double approxMolarMass = 0.0165;
      heelMoles = heelVolume * heelDensity / approxMolarMass;
    }

    double totalMoles = heelMoles + newCargoMoles;
    Map<String, Double> mixed = new LinkedHashMap<String, Double>();

    if (totalMoles <= 0) {
      return newCargoComposition;
    }

    // Mole-weighted mixing
    for (Map.Entry<String, Double> entry : newCargoComposition.entrySet()) {
      String comp = entry.getKey();
      double xNew = entry.getValue() * newCargoMoles / totalMoles;
      double xHeel =
          heelComposition.containsKey(comp) ? heelComposition.get(comp) * heelMoles / totalMoles
              : 0.0;
      mixed.put(comp, xNew + xHeel);
    }

    // Add heel-only components
    for (Map.Entry<String, Double> entry : heelComposition.entrySet()) {
      if (!mixed.containsKey(entry.getKey())) {
        mixed.put(entry.getKey(), entry.getValue() * heelMoles / totalMoles);
      }
    }

    return mixed;
  }

  /**
   * Create a two-layer initial condition for the layered tank model.
   *
   * <p>
   * The heel becomes the bottom layer and the new cargo the top layer. This is the starting point
   * for a stratification/rollover analysis.
   * </p>
   *
   * @param tankModel the layered tank model to configure
   * @param newCargoSystem thermo system representing the new cargo
   * @param newCargoVolume volume of new cargo (m3)
   */
  public void createStratifiedInitialCondition(LNGTankLayeredModel tankModel,
      SystemInterface newCargoSystem, double newCargoVolume) {
    double heelVolume = getHeelVolume();

    // Bottom layer = heel
    LNGTankLayer heelLayer = new LNGTankLayer(0);
    double heelMolarMass = 0.0165; // approximate
    double heelMoles = heelVolume * heelDensity / heelMolarMass;
    heelLayer.setTotalMoles(heelMoles);
    heelLayer.setTemperature(heelTemperature);
    heelLayer.setPressure(tankModel.getTankPressure());
    heelLayer.setDensity(heelDensity);
    heelLayer.setMolarMass(heelMolarMass);
    heelLayer.setVolume(heelVolume);
    heelLayer.setComposition(new LinkedHashMap<String, Double>(heelComposition));

    // Top layer = new cargo
    LNGTankLayer cargoLayer = new LNGTankLayer(1);
    cargoLayer.initFromThermoSystem(newCargoSystem);
    cargoLayer.setVolume(newCargoVolume);

    tankModel.getLayers().clear();
    tankModel.getLayers().add(heelLayer);
    tankModel.addLayerOnTop(cargoLayer);

    logger.info(String.format(
        "Created stratified initial condition: heel=%.0f m3 (rho=%.1f), cargo=%.0f m3", heelVolume,
        heelDensity, newCargoVolume));
  }

  /**
   * Simulate spray cooling during ballast voyage.
   *
   * <p>
   * Spray cooling recirculates heel LNG through nozzles at the top of the tank. The spray contacts
   * the warm tank walls and re-evaporates, absorbing heat and maintaining low wall temperature. The
   * resulting BOG is handled by the BOG network.
   * </p>
   *
   * @param timeStepHours time step (hours)
   * @param ambientTemperature ambient temperature (K)
   * @param wallHeatTransferCoeff wall heat transfer coefficient (W/m2/K)
   * @param wallArea tank wall area (m2)
   * @return BOG generated from spray cooling (kg) during this time step
   */
  public double simulateSprayCooling(double timeStepHours, double ambientTemperature,
      double wallHeatTransferCoeff, double wallArea) {
    if (!sprayCoolingActive) {
      return 0;
    }

    // Heat ingress to tank walls during ballast
    double wallHeatIngress =
        wallHeatTransferCoeff * wallArea * (ambientTemperature - tankWallTemperature);
    if (wallHeatIngress < 0) {
      wallHeatIngress = 0;
    }

    // Spray absorbs heat through evaporation
    double latentHeat = 510000.0; // J/kg approximate
    double bogFromSpray = wallHeatIngress * timeStepHours * 3600.0 / latentHeat;

    // Cap by spray rate
    double maxSprayBOG = sprayCoolingRate * timeStepHours;
    bogFromSpray = Math.min(bogFromSpray, maxSprayBOG);

    return bogFromSpray;
  }

  /**
   * Get heel volume.
   *
   * @return heel volume (m3)
   */
  public double getHeelVolume() {
    return tankVolume * heelFraction;
  }

  /**
   * Get heel fraction.
   *
   * @return heel fraction (0-1)
   */
  public double getHeelFraction() {
    return heelFraction;
  }

  /**
   * Set heel fraction.
   *
   * @param fraction heel fraction (0-1)
   */
  public void setHeelFraction(double fraction) {
    this.heelFraction = Math.max(0, Math.min(1, fraction));
  }

  /**
   * Get heel composition.
   *
   * @return map of component name to mole fraction
   */
  public Map<String, Double> getHeelComposition() {
    return heelComposition;
  }

  /**
   * Get heel temperature.
   *
   * @return temperature (K)
   */
  public double getHeelTemperature() {
    return heelTemperature;
  }

  /**
   * Set heel temperature.
   *
   * @param temperature temperature (K)
   */
  public void setHeelTemperature(double temperature) {
    this.heelTemperature = temperature;
  }

  /**
   * Get heel density.
   *
   * @return density (kg/m3)
   */
  public double getHeelDensity() {
    return heelDensity;
  }

  /**
   * Set heel density.
   *
   * @param density density (kg/m3)
   */
  public void setHeelDensity(double density) {
    this.heelDensity = density;
  }

  /**
   * Get tank volume.
   *
   * @return tank volume (m3)
   */
  public double getTankVolume() {
    return tankVolume;
  }

  /**
   * Set tank volume.
   *
   * @param volume tank volume (m3)
   */
  public void setTankVolume(double volume) {
    this.tankVolume = volume;
  }

  /**
   * Get spray cooling rate.
   *
   * @return spray rate (kg/hr)
   */
  public double getSprayCoolingRate() {
    return sprayCoolingRate;
  }

  /**
   * Set spray cooling rate.
   *
   * @param rate spray rate (kg/hr)
   */
  public void setSprayCoolingRate(double rate) {
    this.sprayCoolingRate = rate;
  }

  /**
   * Check if spray cooling is active.
   *
   * @return true if spray cooling active
   */
  public boolean isSprayCoolingActive() {
    return sprayCoolingActive;
  }

  /**
   * Set spray cooling active state.
   *
   * @param active true to activate spray cooling
   */
  public void setSprayCoolingActive(boolean active) {
    this.sprayCoolingActive = active;
  }

  /**
   * Get max warm tank temperature.
   *
   * @return max temperature (K)
   */
  public double getMaxWarmTankTemperature() {
    return maxWarmTankTemperature;
  }

  /**
   * Set max warm tank temperature.
   *
   * @param temperature max temperature (K)
   */
  public void setMaxWarmTankTemperature(double temperature) {
    this.maxWarmTankTemperature = temperature;
  }

  /**
   * Get tank wall temperature.
   *
   * @return wall temperature (K)
   */
  public double getTankWallTemperature() {
    return tankWallTemperature;
  }

  /**
   * Set tank wall temperature.
   *
   * @param temperature wall temperature (K)
   */
  public void setTankWallTemperature(double temperature) {
    this.tankWallTemperature = temperature;
  }
}
