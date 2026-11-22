package neqsim.process.equipment.subsea;

import java.io.Serializable;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Liquid carried in an umbilical hydraulic or chemical injection line.
 *
 * <p>
 * Holds density, temperature-dependent dynamic viscosity (Andrade form
 * {@code mu(T) = mu_ref * exp(B * (1/T - 1/T_ref))}) and isothermal bulk modulus. The presets are generic screening
 * values for typical products and must be replaced by the product datasheet in a design. A fluid can also be taken from
 * a NeqSim thermodynamic system with {@link #fromNeqSim(String, SystemInterface, double, double)}.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class UmbilicalFluid implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference temperature for the viscosity in K. */
  private static final double REFERENCE_TEMPERATURE_K = 293.15;

  /** Fluid name. */
  private final String name;
  /** Density in kg/m3 (assumed independent of temperature over the umbilical range). */
  private final double densityKgM3;
  /** Dynamic viscosity at the reference temperature (20 C) in Pa s. */
  private final double viscosityRefPas;
  /** Andrade coefficient B in K. */
  private final double viscosityTempCoefficientK;
  /** Isothermal bulk modulus in Pa. */
  private final double bulkModulusPa;

  /**
   * Create a fluid.
   *
   * @param name fluid name
   * @param densityKgM3 density in kg/m3
   * @param viscosityAt20CmPas dynamic viscosity at 20 C in mPa s (cP)
   * @param viscosityTempCoefficientK Andrade coefficient B in K (0 for temperature independent)
   * @param bulkModulusGPa isothermal bulk modulus in GPa
   */
  public UmbilicalFluid(String name, double densityKgM3, double viscosityAt20CmPas, double viscosityTempCoefficientK,
      double bulkModulusGPa) {
    if (densityKgM3 <= 0.0 || viscosityAt20CmPas <= 0.0 || bulkModulusGPa <= 0.0) {
      throw new IllegalArgumentException("Density, viscosity and bulk modulus must be positive for fluid " + name);
    }
    this.name = name;
    this.densityKgM3 = densityKgM3;
    this.viscosityRefPas = viscosityAt20CmPas * 1.0e-3;
    this.viscosityTempCoefficientK = viscosityTempCoefficientK;
    this.bulkModulusPa = bulkModulusGPa * 1.0e9;
  }

  /**
   * Generic water-glycol subsea control fluid.
   *
   * @return fluid
   */
  public static UmbilicalFluid waterGlycolHydraulic() {
    return new UmbilicalFluid("Water-glycol hydraulic fluid", 1065.0, 8.0, 2400.0, 3.0);
  }

  /**
   * Pure mono-ethylene glycol.
   *
   * @return fluid
   */
  public static UmbilicalFluid meg() {
    return new UmbilicalFluid("MEG", 1113.0, 20.9, 3800.0, 3.0);
  }

  /**
   * Pure methanol.
   *
   * @return fluid
   */
  public static UmbilicalFluid methanol() {
    return new UmbilicalFluid("Methanol", 792.0, 0.59, 1240.0, 0.9);
  }

  /**
   * Fresh water.
   *
   * @return fluid
   */
  public static UmbilicalFluid water() {
    return new UmbilicalFluid("Water", 1000.0, 1.0, 2000.0, 2.2);
  }

  /**
   * Generic water-based or glycol-based chemical (scale, corrosion, hydrate inhibitor).
   *
   * @return fluid
   */
  public static UmbilicalFluid genericChemical() {
    return new UmbilicalFluid("Generic chemical", 1030.0, 3.0, 2200.0, 2.3);
  }

  /**
   * Select a preset from a line name (MEG, methanol, hydraulic, otherwise generic chemical).
   *
   * @param lineName line name or function
   * @param hydraulic true if the line is a hydraulic control line
   * @return fluid preset
   */
  public static UmbilicalFluid forLineName(String lineName, boolean hydraulic) {
    String n = lineName == null ? "" : lineName.toLowerCase();
    if (n.contains("meg") || n.contains("glycol")) {
      return hydraulic ? waterGlycolHydraulic() : meg();
    }
    if (n.contains("meoh") || n.contains("methanol")) {
      return methanol();
    }
    return hydraulic ? waterGlycolHydraulic() : genericChemical();
  }

  /**
   * Take density, viscosity and bulk modulus of a liquid from a NeqSim thermodynamic system.
   *
   * <p>
   * The system is cloned, flashed at the given conditions (TP flash) and its properties are initialised. The bulk
   * modulus follows from a second flash at a 20 bar higher pressure. The viscosity is taken at the given temperature
   * and treated as temperature independent. A mixing rule must already be set on the system.
   * </p>
   *
   * @param name fluid name
   * @param system thermodynamic system with a mixing rule
   * @param temperatureK temperature in K
   * @param pressureBara pressure in bara
   * @return fluid with NeqSim properties
   * @throws IllegalArgumentException if the system does not give a single liquid-like phase
   */
  public static UmbilicalFluid fromNeqSim(String name, SystemInterface system, double temperatureK,
      double pressureBara) {
    double[] props = flashProperties(system, temperatureK, pressureBara);
    double[] props2 = flashProperties(system, temperatureK, pressureBara + 20.0);
    double compressibilityDensity = props2[0] - props[0];
    if (compressibilityDensity <= 0.0) {
      throw new IllegalArgumentException(
          "Density does not increase with pressure for " + name + "; not a liquid-like fluid");
    }
    double bulkModulusPa = props[0] * 20.0e5 / compressibilityDensity;
    return new UmbilicalFluid(name, props[0], props[1] * 1.0e3, 0.0, bulkModulusPa / 1.0e9);
  }

  /**
   * Flash a clone of the system and read density and viscosity.
   *
   * @param system thermodynamic system
   * @param temperatureK temperature in K
   * @param pressureBara pressure in bara
   * @return array {density kg/m3, viscosity Pa s}
   */
  private static double[] flashProperties(SystemInterface system, double temperatureK, double pressureBara) {
    SystemInterface copy = system.clone();
    copy.setTemperature(temperatureK);
    copy.setPressure(pressureBara);
    new ThermodynamicOperations(copy).TPflash();
    copy.initProperties();
    if (copy.getNumberOfPhases() > 1 || copy.getPhase(0).getType() == PhaseType.GAS) {
      throw new IllegalArgumentException(
          "Fluid is not a single liquid phase at " + temperatureK + " K and " + pressureBara + " bara");
    }
    return new double[] {copy.getDensity("kg/m3"), copy.getViscosity("kg/msec")};
  }

  /**
   * Get the fluid name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Get density.
   *
   * @return density in kg/m3
   */
  public double getDensityKgM3() {
    return densityKgM3;
  }

  /**
   * Get the reference (20 C) dynamic viscosity.
   *
   * @return viscosity in Pa s
   */
  public double getViscosityRefPas() {
    return viscosityRefPas;
  }

  /**
   * Get the dynamic viscosity at a temperature.
   *
   * @param temperatureK temperature in K
   * @return dynamic viscosity in Pa s
   */
  public double getViscosityPas(double temperatureK) {
    return viscosityRefPas * Math.exp(viscosityTempCoefficientK * (1.0 / temperatureK - 1.0 / REFERENCE_TEMPERATURE_K));
  }

  /**
   * Get the isothermal bulk modulus.
   *
   * @return bulk modulus in Pa
   */
  public double getBulkModulusPa() {
    return bulkModulusPa;
  }
}
