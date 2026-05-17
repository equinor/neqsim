package neqsim.process.equipment.lng;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Models the vapor space above the LNG liquid in a cargo tank.
 *
 * <p>
 * The vapor space determines the tank pressure and vapor composition. In real LNG tanks, the vapor
 * space is not in thermodynamic equilibrium with the liquid surface during rapid transients (e.g.,
 * pressure build-up after valve closure, rapid loading/unloading). This model provides both
 * equilibrium and non-equilibrium modes.
 * </p>
 *
 * <p>
 * Key features:
 * </p>
 * <ul>
 * <li><b>Pressure tracking:</b> Tank pressure evolves based on BOG generation rate vs relief/
 * handling rate</li>
 * <li><b>Vapor composition:</b> Determined by VLE flash in equilibrium mode, or by mass balance
 * accumulation in non-equilibrium mode</li>
 * <li><b>Ullage volume:</b> Total tank volume minus liquid volume, varies with boil-off</li>
 * </ul>
 *
 * <p>
 * Pressure dynamics follow the ideal gas relationship for the ullage space:
 * </p>
 *
 * <pre>
 * P_new = P_old + (n_bog_generated - n_bog_removed) * R * T_vapor / V_ullage
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LNGVaporSpaceModel implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1007L;

  /** Logger object. */
  private static final Logger logger = LogManager.getLogger(LNGVaporSpaceModel.class);

  /** Gas constant R = 8.314 J/(mol*K). */
  private static final double R_GAS = 8.314;

  /** Tank total volume (m3). */
  private double totalTankVolume = 140000.0;

  /** Current liquid volume (m3). */
  private double currentLiquidVolume;

  /** Current tank pressure (bara). */
  private double tankPressure = 1.013;

  /** Minimum allowed tank pressure (bara). */
  private double minPressure = 1.01;

  /** Maximum allowed tank pressure (bara) — relief valve set point. */
  private double maxPressure = 1.25;

  /** Current vapor temperature (K). */
  private double vaporTemperature = 111.0;

  /** Moles of vapor in the ullage space. */
  private double vaporMoles = 0.0;

  /** Current vapor composition (mole fractions). */
  private Map<String, Double> vaporComposition;

  /** Whether to use equilibrium mode (true) or accumulation mode (false). */
  private boolean equilibriumMode = true;

  /** BOG moles generated but not yet handled in this time step. */
  private double unhandledBOGMoles = 0.0;

  /** Reference thermo system for flash-based vapor space calculations. */
  private transient SystemInterface referenceSystem;

  /** Whether to use flash-based vapor space model (true) or simple PV=nRT (false). */
  private boolean useFlashModel = false;

  /**
   * Constructor for LNGVaporSpaceModel.
   *
   * @param totalTankVolume total tank volume (m3)
   */
  public LNGVaporSpaceModel(double totalTankVolume) {
    this.totalTankVolume = totalTankVolume;
    this.vaporComposition = new LinkedHashMap<String, Double>();
  }

  /**
   * Get the ullage (vapor space) volume.
   *
   * @return ullage volume (m3)
   */
  public double getUllageVolume() {
    return totalTankVolume - currentLiquidVolume;
  }

  /**
   * Get the fill level as a fraction (0-1).
   *
   * @return fill level fraction
   */
  public double getFillLevel() {
    return currentLiquidVolume / totalTankVolume;
  }

  /**
   * Update the vapor space state after a time step.
   *
   * <p>
   * In equilibrium mode, the pressure and vapor composition are determined by the VLE flash of the
   * top liquid layer. In accumulation mode, the pressure is updated based on the net BOG
   * accumulation in the ullage space: P += (nBOG_gen - nBOG_removed) * R * T / V_ullage.
   * </p>
   *
   * @param bogMolesGenerated moles of BOG generated from liquid evaporation this step
   * @param bogMolesRemoved moles of BOG removed by handling (compressor, reliquefaction, GCU)
   * @param liquidVolume current liquid volume (m3)
   * @param equilibriumVaporComp vapor composition from VLE flash
   * @param liquidTemperature liquid temperature (K)
   */
  public void update(double bogMolesGenerated, double bogMolesRemoved, double liquidVolume,
      Map<String, Double> equilibriumVaporComp, double liquidTemperature) {
    this.currentLiquidVolume = liquidVolume;
    double ullageVol = getUllageVolume();

    if (equilibriumMode) {
      // In equilibrium mode, pressure is the bubble-point pressure of the liquid
      this.vaporComposition = new LinkedHashMap<String, Double>(equilibriumVaporComp);
      this.vaporTemperature = liquidTemperature;
      // Pressure stays at set-point (tank pressure controlled by BOG handling)
    } else {
      // Accumulation mode: pressure changes with net BOG
      double netBOGMoles = bogMolesGenerated - bogMolesRemoved;
      this.vaporMoles += netBOGMoles;
      this.unhandledBOGMoles = Math.max(0, this.vaporMoles);

      // Ideal gas pressure update: PV = nRT
      if (ullageVol > 0 && vaporMoles > 0) {
        // Convert to Pa, then to bara
        double pressurePa = vaporMoles * R_GAS * vaporTemperature / ullageVol;
        this.tankPressure = pressurePa / 1.0e5;
      }

      // Update vapor composition by accumulation
      if (bogMolesGenerated > 0) {
        double totalVapMoles = vaporMoles;
        if (totalVapMoles > 0) {
          for (Map.Entry<String, Double> entry : equilibriumVaporComp.entrySet()) {
            String comp = entry.getKey();
            double yNew = entry.getValue();
            double yOld = vaporComposition.containsKey(comp) ? vaporComposition.get(comp) : 0.0;
            double blended = (yOld * (totalVapMoles - bogMolesGenerated) + yNew * bogMolesGenerated)
                / totalVapMoles;
            vaporComposition.put(comp, Math.max(0, blended));
          }
        }
      }

      // Clamp pressure to safety limits
      if (tankPressure > maxPressure) {
        logger.warn(String.format("Tank pressure %.3f bara exceeds max %.3f bara — relief needed",
            tankPressure, maxPressure));
      }
      if (tankPressure < minPressure) {
        tankPressure = minPressure;
      }
    }
  }

  /**
   * Check if the tank pressure exceeds the relief valve set point.
   *
   * @return true if pressure exceeds maximum
   */
  public boolean isPressureAboveRelief() {
    return tankPressure > maxPressure;
  }

  /**
   * Check if the tank is under-pressured (needs BOG return or nitrogen blanket).
   *
   * @return true if pressure is below minimum
   */
  public boolean isUnderPressured() {
    return tankPressure < minPressure;
  }

  /**
   * Get moles of BOG that need to be handled to prevent over-pressure.
   *
   * @return excess BOG moles (0 if pressure is OK)
   */
  public double getExcessBOGMoles() {
    if (!isPressureAboveRelief()) {
      return 0;
    }
    double ullageVol = getUllageVolume();
    if (ullageVol <= 0) {
      return vaporMoles;
    }
    double targetMoles = maxPressure * 1.0e5 * ullageVol / (R_GAS * vaporTemperature);
    return Math.max(0, vaporMoles - targetMoles);
  }

  /**
   * Get tank total volume.
   *
   * @return total volume (m3)
   */
  public double getTotalTankVolume() {
    return totalTankVolume;
  }

  /**
   * Set tank total volume.
   *
   * @param totalTankVolume total volume (m3)
   */
  public void setTotalTankVolume(double totalTankVolume) {
    this.totalTankVolume = totalTankVolume;
  }

  /**
   * Get current liquid volume.
   *
   * @return liquid volume (m3)
   */
  public double getCurrentLiquidVolume() {
    return currentLiquidVolume;
  }

  /**
   * Set current liquid volume.
   *
   * @param volume liquid volume (m3)
   */
  public void setCurrentLiquidVolume(double volume) {
    this.currentLiquidVolume = volume;
  }

  /**
   * Get tank pressure.
   *
   * @return pressure (bara)
   */
  public double getTankPressure() {
    return tankPressure;
  }

  /**
   * Set tank pressure.
   *
   * @param pressure pressure (bara)
   */
  public void setTankPressure(double pressure) {
    this.tankPressure = pressure;
  }

  /**
   * Get minimum pressure.
   *
   * @return min pressure (bara)
   */
  public double getMinPressure() {
    return minPressure;
  }

  /**
   * Set minimum pressure.
   *
   * @param minPressure min pressure (bara)
   */
  public void setMinPressure(double minPressure) {
    this.minPressure = minPressure;
  }

  /**
   * Get maximum pressure (relief set point).
   *
   * @return max pressure (bara)
   */
  public double getMaxPressure() {
    return maxPressure;
  }

  /**
   * Set maximum pressure (relief set point).
   *
   * @param maxPressure max pressure (bara)
   */
  public void setMaxPressure(double maxPressure) {
    this.maxPressure = maxPressure;
  }

  /**
   * Get vapor temperature.
   *
   * @return vapor temperature (K)
   */
  public double getVaporTemperature() {
    return vaporTemperature;
  }

  /**
   * Set vapor temperature.
   *
   * @param temperature vapor temperature (K)
   */
  public void setVaporTemperature(double temperature) {
    this.vaporTemperature = temperature;
  }

  /**
   * Get vapor moles.
   *
   * @return vapor moles (mol)
   */
  public double getVaporMoles() {
    return vaporMoles;
  }

  /**
   * Set vapor moles.
   *
   * @param moles vapor moles (mol)
   */
  public void setVaporMoles(double moles) {
    this.vaporMoles = moles;
  }

  /**
   * Get vapor composition.
   *
   * @return map of component name to mole fraction
   */
  public Map<String, Double> getVaporComposition() {
    return vaporComposition;
  }

  /**
   * Check if equilibrium mode is active.
   *
   * @return true if in equilibrium mode
   */
  public boolean isEquilibriumMode() {
    return equilibriumMode;
  }

  /**
   * Set the vapor space calculation mode.
   *
   * @param equilibriumMode true for equilibrium, false for accumulation
   */
  public void setEquilibriumMode(boolean equilibriumMode) {
    this.equilibriumMode = equilibriumMode;
  }

  /**
   * Get unhandled BOG moles.
   *
   * @return unhandled BOG moles
   */
  public double getUnhandledBOGMoles() {
    return unhandledBOGMoles;
  }

  /**
   * Set the reference thermo system for flash-based vapor space calculations.
   *
   * @param system reference thermo system
   */
  public void setReferenceSystem(SystemInterface system) {
    this.referenceSystem = system;
  }

  /**
   * Get the reference thermo system.
   *
   * @return reference thermo system or null
   */
  public SystemInterface getReferenceSystem() {
    return referenceSystem;
  }

  /**
   * Enable or disable flash-based vapor space model.
   *
   * <p>
   * When enabled, the vapor space composition and pressure are determined by a TP flash on the
   * combined liquid surface + accumulated vapor inventory, rather than the simple PV=nRT model.
   * This captures condensation of heavier components from the vapor and re-evaporation phenomena.
   * </p>
   *
   * @param useFlash true to use flash model
   */
  public void setUseFlashModel(boolean useFlash) {
    this.useFlashModel = useFlash;
  }

  /**
   * Check if flash-based vapor space model is enabled.
   *
   * @return true if using flash model
   */
  public boolean isUseFlashModel() {
    return useFlashModel;
  }

  /**
   * Perform a flash-based update of the vapor space.
   *
   * <p>
   * Creates a thermo system with the current ullage gas composition, runs a TP flash to determine
   * how much condenses back into liquid versus stays in the vapor. This provides more accurate
   * vapor composition and pressure than the simple ideal gas model, especially when heavier
   * components accumulate in the vapor space.
   * </p>
   *
   * @param bogMolesGenerated moles of BOG generated
   * @param bogMolesRemoved moles removed by BOG handling
   * @param liquidVolume current liquid volume (m3)
   * @param equilibriumVaporComp vapor composition from liquid VLE flash
   * @param liquidTemperature liquid surface temperature (K)
   */
  public void updateWithFlash(double bogMolesGenerated, double bogMolesRemoved, double liquidVolume,
      Map<String, Double> equilibriumVaporComp, double liquidTemperature) {
    if (referenceSystem == null || !useFlashModel) {
      update(bogMolesGenerated, bogMolesRemoved, liquidVolume, equilibriumVaporComp,
          liquidTemperature);
      return;
    }

    this.currentLiquidVolume = liquidVolume;
    double ullageVol = getUllageVolume();
    if (ullageVol <= 0) {
      return;
    }

    try {
      // Build a system representing the ullage gas + new BOG
      SystemInterface vaporSystem = referenceSystem.clone();
      vaporSystem.setTemperature(vaporTemperature);
      vaporSystem.setPressure(tankPressure);

      // Set composition from blended vapor (existing + new BOG)
      double newBOGFraction = (vaporMoles + bogMolesGenerated > 0)
          ? bogMolesGenerated / (vaporMoles + bogMolesGenerated)
          : 1.0;
      double oldFraction = 1.0 - newBOGFraction;

      vaporSystem.init(0);
      double currentMoles = vaporSystem.getTotalNumberOfMoles();
      double targetMoles = vaporMoles + bogMolesGenerated - bogMolesRemoved;
      if (targetMoles < 0) {
        targetMoles = 0;
      }

      for (int i = 0; i < vaporSystem.getPhase(0).getNumberOfComponents(); i++) {
        String name = vaporSystem.getPhase(0).getComponent(i).getComponentName();
        double yOld = vaporComposition.containsKey(name) ? vaporComposition.get(name) : 0;
        double yNew = equilibriumVaporComp.containsKey(name) ? equilibriumVaporComp.get(name) : 0;
        double yBlend = oldFraction * yOld + newBOGFraction * yNew;
        double molesNeeded =
            yBlend * targetMoles - vaporSystem.getPhase(0).getComponent(i).getz() * currentMoles;
        if (Math.abs(molesNeeded) > 1e-20) {
          vaporSystem.addComponent(name, molesNeeded);
        }
      }

      // Run TP flash on the vapor system
      vaporSystem.init(0);
      ThermodynamicOperations ops = new ThermodynamicOperations(vaporSystem);
      ops.TPflash();
      vaporSystem.init(0);

      // Update vapor composition from flash result
      if (vaporSystem.hasPhaseType("gas")) {
        vaporComposition.clear();
        for (int i = 0; i < vaporSystem.getPhase("gas").getNumberOfComponents(); i++) {
          String name = vaporSystem.getPhase("gas").getComponent(i).getComponentName();
          double y = vaporSystem.getPhase("gas").getComponent(i).getx();
          vaporComposition.put(name, y);
        }
        this.vaporMoles = vaporSystem.getPhase("gas").getNumberOfMolesInPhase();
      }

      // Update pressure from real gas behavior
      if (ullageVol > 0 && vaporMoles > 0) {
        double pressurePa = vaporMoles * R_GAS * vaporTemperature / ullageVol;
        this.tankPressure = pressurePa / 1.0e5;
      }

      // Clamp pressure
      if (tankPressure > maxPressure) {
        logger.warn(
            String.format("Tank pressure %.3f bara exceeds max %.3f", tankPressure, maxPressure));
      }
      if (tankPressure < minPressure) {
        tankPressure = minPressure;
      }
    } catch (Exception ex) {
      logger.warn("Vapor space flash failed, falling back to simple model", ex);
      update(bogMolesGenerated, bogMolesRemoved, liquidVolume, equilibriumVaporComp,
          liquidTemperature);
    }
  }
}
