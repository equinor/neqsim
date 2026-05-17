package neqsim.process.equipment.lng;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.thermo.system.SystemInterface;

/**
 * Represents a single horizontal layer in a stratified LNG tank.
 *
 * <p>
 * Each layer has its own composition, temperature, density, and volume. Layers interact through
 * interfacial heat and mass transfer and can merge when their density difference drops below a
 * threshold.
 * </p>
 *
 * <p>
 * The layer model supports the two key physical mechanisms that drive stratification and rollover:
 * </p>
 * <ul>
 * <li><b>Weathering from top:</b> preferential evaporation of nitrogen and methane from the top
 * layer makes it denser (heavier)</li>
 * <li><b>Heat ingress from bottom/sides:</b> warming of the bottom layer makes it lighter,
 * potentially unstable when topped by a denser layer</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LNGTankLayer implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1005L;

  /** Layer identifier (0 = bottom, increasing upward). */
  private int layerIndex;

  /** Total moles in this layer (mol). */
  private double totalMoles;

  /** Layer volume (m3). */
  private double volume;

  /** Layer temperature (K). */
  private double temperature;

  /** Layer pressure (bara). */
  private double pressure;

  /** Layer density (kg/m3). */
  private double density;

  /** Layer molar mass (kg/mol). */
  private double molarMass;

  /** Mole fractions keyed by component name. */
  private Map<String, Double> composition;

  /** Reference to the thermodynamic system representing this layer's liquid. */
  private transient SystemInterface thermoSystem;

  /**
   * Constructor for LNGTankLayer.
   *
   * @param layerIndex layer index (0 = bottom)
   */
  public LNGTankLayer(int layerIndex) {
    this.layerIndex = layerIndex;
    this.composition = new LinkedHashMap<String, Double>();
  }

  /**
   * Constructor with full initialisation.
   *
   * @param layerIndex layer index (0 = bottom)
   * @param totalMoles total moles in layer (mol)
   * @param temperature layer temperature (K)
   * @param pressure layer pressure (bara)
   */
  public LNGTankLayer(int layerIndex, double totalMoles, double temperature, double pressure) {
    this(layerIndex);
    this.totalMoles = totalMoles;
    this.temperature = temperature;
    this.pressure = pressure;
  }

  /**
   * Initialize the layer from a NeqSim thermodynamic system.
   *
   * <p>
   * Extracts composition, density, molar mass, and volume from the thermo system's liquid phase
   * after a flash calculation has been performed.
   * </p>
   *
   * @param system thermodynamic system with liquid phase (post-flash)
   */
  public void initFromThermoSystem(SystemInterface system) {
    this.thermoSystem = system;
    this.temperature = system.getTemperature();
    this.pressure = system.getPressure();

    // Extract liquid phase properties
    if (system.hasPhaseType("oil")) {
      neqsim.thermo.phase.PhaseInterface liqPhase = system.getPhase("oil");
      this.density = liqPhase.getDensity("kg/m3");
      this.molarMass = liqPhase.getMolarMass("kg/mol");
      this.totalMoles = liqPhase.getNumberOfMolesInPhase();

      // Extract composition
      composition.clear();
      for (int i = 0; i < liqPhase.getNumberOfComponents(); i++) {
        String name = liqPhase.getComponent(i).getComponentName();
        double xFrac = liqPhase.getComponent(i).getx();
        composition.put(name, xFrac);
      }

      // Calculate volume from mass and density
      if (density > 0) {
        double mass = totalMoles * molarMass;
        this.volume = mass / density;
      }
    }
  }

  /**
   * Get the mass of this layer.
   *
   * @return mass (kg)
   */
  public double getMass() {
    return totalMoles * molarMass;
  }

  /**
   * Check if this layer is denser than another.
   *
   * @param other the other layer to compare against
   * @return true if this layer is denser
   */
  public boolean isDenserThan(LNGTankLayer other) {
    return this.density > other.density;
  }

  /**
   * Get the density difference with another layer.
   *
   * @param other the other layer
   * @return absolute density difference (kg/m3)
   */
  public double getDensityDifference(LNGTankLayer other) {
    return Math.abs(this.density - other.density);
  }

  /**
   * Remove moles from this layer (boil-off from top layer).
   *
   * <p>
   * The composition of the removed vapor is determined by the vapor-liquid equilibrium at the
   * layer's temperature and pressure.
   * </p>
   *
   * @param molesToRemove moles of vapor to remove (mol)
   * @param vaporComposition composition of the vapor being removed (mole fractions by component)
   */
  public void removeVapor(double molesToRemove, Map<String, Double> vaporComposition) {
    if (molesToRemove <= 0 || molesToRemove >= totalMoles) {
      return;
    }

    // Update liquid composition after preferential evaporation
    double remainingMoles = totalMoles - molesToRemove;
    Map<String, Double> newComp = new LinkedHashMap<String, Double>();
    double sumX = 0.0;

    for (Map.Entry<String, Double> entry : composition.entrySet()) {
      String comp = entry.getKey();
      double xOld = entry.getValue();
      double yVap = vaporComposition.containsKey(comp) ? vaporComposition.get(comp) : 0.0;
      double newX = (xOld * totalMoles - yVap * molesToRemove) / remainingMoles;
      if (newX < 0) {
        newX = 0;
      }
      newComp.put(comp, newX);
      sumX += newX;
    }

    // Normalize
    if (sumX > 0) {
      for (Map.Entry<String, Double> entry : newComp.entrySet()) {
        entry.setValue(entry.getValue() / sumX);
      }
    }

    this.composition = newComp;
    this.totalMoles = remainingMoles;
  }

  /**
   * Add heat to this layer (e.g., from wall heat ingress).
   *
   * @param heatJoules heat added (J)
   * @param cpJPerMolK molar heat capacity of the liquid (J/(mol*K))
   */
  public void addHeat(double heatJoules, double cpJPerMolK) {
    if (totalMoles > 0 && cpJPerMolK > 0) {
      double deltaT = heatJoules / (totalMoles * cpJPerMolK);
      this.temperature += deltaT;
    }
  }

  /**
   * Get layer index.
   *
   * @return layer index (0 = bottom)
   */
  public int getLayerIndex() {
    return layerIndex;
  }

  /**
   * Set layer index.
   *
   * @param layerIndex layer index
   */
  public void setLayerIndex(int layerIndex) {
    this.layerIndex = layerIndex;
  }

  /**
   * Get total moles.
   *
   * @return total moles (mol)
   */
  public double getTotalMoles() {
    return totalMoles;
  }

  /**
   * Set total moles.
   *
   * @param totalMoles total moles (mol)
   */
  public void setTotalMoles(double totalMoles) {
    this.totalMoles = totalMoles;
  }

  /**
   * Get layer volume.
   *
   * @return volume (m3)
   */
  public double getVolume() {
    return volume;
  }

  /**
   * Set layer volume.
   *
   * @param volume volume (m3)
   */
  public void setVolume(double volume) {
    this.volume = volume;
  }

  /**
   * Get layer temperature.
   *
   * @return temperature (K)
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * Set layer temperature.
   *
   * @param temperature temperature (K)
   */
  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  /**
   * Get layer pressure.
   *
   * @return pressure (bara)
   */
  public double getPressure() {
    return pressure;
  }

  /**
   * Set layer pressure.
   *
   * @param pressure pressure (bara)
   */
  public void setPressure(double pressure) {
    this.pressure = pressure;
  }

  /**
   * Get layer density.
   *
   * @return density (kg/m3)
   */
  public double getDensity() {
    return density;
  }

  /**
   * Set layer density.
   *
   * @param density density (kg/m3)
   */
  public void setDensity(double density) {
    this.density = density;
  }

  /**
   * Get layer molar mass.
   *
   * @return molar mass (kg/mol)
   */
  public double getMolarMass() {
    return molarMass;
  }

  /**
   * Set layer molar mass.
   *
   * @param molarMass molar mass (kg/mol)
   */
  public void setMolarMass(double molarMass) {
    this.molarMass = molarMass;
  }

  /**
   * Get layer composition.
   *
   * @return map of component name to mole fraction
   */
  public Map<String, Double> getComposition() {
    return composition;
  }

  /**
   * Set layer composition.
   *
   * @param composition map of component name to mole fraction
   */
  public void setComposition(Map<String, Double> composition) {
    this.composition = composition;
  }

  /**
   * Get the thermo system for this layer.
   *
   * @return thermodynamic system or null if not set
   */
  public SystemInterface getThermoSystem() {
    return thermoSystem;
  }

  /**
   * Set the thermo system for this layer.
   *
   * @param thermoSystem thermodynamic system
   */
  public void setThermoSystem(SystemInterface thermoSystem) {
    this.thermoSystem = thermoSystem;
  }
}
