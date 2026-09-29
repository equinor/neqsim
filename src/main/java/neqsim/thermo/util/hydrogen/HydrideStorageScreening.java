package neqsim.thermo.util.hydrogen;

/**
 * Screening calculations connecting solid hydride structure data to hydrogen gas thermodynamics.
 *
 * <p>
 * The capacity calculations use formula-unit stoichiometry and crystallographic cell volume. The equilibrium
 * calculations describe the reaction releasing hydrogen from a solid with unit solid activities, constant desorption
 * enthalpy and entropy per mole of H2, and a 1 bara standard fugacity. Supply the actual hydrogen fugacity from a gas
 * property model, for example NeqSim's gas-phase fugacity coefficient times hydrogen mole fraction and absolute
 * pressure. This class does not calculate solid chemical potentials, competing decomposition products, kinetics, or
 * reversible working capacity.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class HydrideStorageScreening {
  private static final double AVOGADRO = 6.02214076e23;
  private static final double HYDROGEN_ATOM_MOLAR_MASS_G_PER_MOL = 1.00794;
  private static final double GAS_CONSTANT_J_PER_MOL_K = 8.314462618;
  private static final double STANDARD_FUGACITY_BAR = 1.0;

  /** Private constructor for utility class. */
  private HydrideStorageScreening() {
  }

  /**
   * Calculates the theoretical hydrogen mass fraction in a fully hydrogenated solid.
   *
   * @param hostMolarMassGPerMol molar mass of the hydrogen-free host per formula unit in g/mol
   * @param hydrogenAtomsPerFormula number of H atoms in each formula unit
   * @return hydrogen mass divided by fully hydrogenated solid mass, in percent
   */
  public static double gravimetricCapacityPercent(double hostMolarMassGPerMol, int hydrogenAtomsPerFormula) {
    requirePositiveFinite(hostMolarMassGPerMol, "hostMolarMassGPerMol");
    requirePositive(hydrogenAtomsPerFormula, "hydrogenAtomsPerFormula");
    double hydrogenMass = hydrogenAtomsPerFormula * HYDROGEN_ATOM_MOLAR_MASS_G_PER_MOL;
    return 100.0 * hydrogenMass / (hostMolarMassGPerMol + hydrogenMass);
  }

  /**
   * Calculates ideal crystallographic hydrogen mass density, excluding porosity and vessel mass.
   *
   * @param hydrogenAtomsPerFormula number of H atoms per formula unit
   * @param formulaUnitsPerCell number of formula units in a crystallographic unit cell
   * @param cellVolumeAngstrom3 unit cell volume in cubic angstroms
   * @return hydrogen mass per unit cell volume in g/L
   */
  public static double volumetricHydrogenDensityGPerL(int hydrogenAtomsPerFormula, int formulaUnitsPerCell,
      double cellVolumeAngstrom3) {
    requirePositive(hydrogenAtomsPerFormula, "hydrogenAtomsPerFormula");
    requirePositive(formulaUnitsPerCell, "formulaUnitsPerCell");
    requirePositiveFinite(cellVolumeAngstrom3, "cellVolumeAngstrom3");
    double hydrogenMassGPerCell = hydrogenAtomsPerFormula * (double) formulaUnitsPerCell
        * HYDROGEN_ATOM_MOLAR_MASS_G_PER_MOL / AVOGADRO;
    return hydrogenMassGPerCell / (cellVolumeAngstrom3 * 1.0e-27);
  }

  /**
   * Calculates the Gibbs energy for hydrogen desorption at the supplied gas fugacity.
   *
   * <p>
   * Delta G = Delta H - T Delta S + R T ln(f_H2 / 1 bara). A negative result favours desorption relative to the assumed
   * solid products. Delta H and Delta S must describe the same reaction and be expressed per mole of released H2, not
   * per atom or formula unit.
   * </p>
   *
   * @param temperatureK temperature in K
   * @param hydrogenFugacityBar hydrogen partial fugacity in bara
   * @param desorptionEnthalpyJPerMolH2 reaction enthalpy in J/mol H2
   * @param desorptionEntropyJPerMolH2K reaction entropy in J/(mol H2 K)
   * @return desorption Gibbs energy in J/mol H2
   */
  public static double desorptionGibbsEnergyJPerMolH2(double temperatureK, double hydrogenFugacityBar,
      double desorptionEnthalpyJPerMolH2, double desorptionEntropyJPerMolH2K) {
    requirePositiveFinite(temperatureK, "temperatureK");
    requirePositiveFinite(hydrogenFugacityBar, "hydrogenFugacityBar");
    requirePositiveFinite(desorptionEnthalpyJPerMolH2, "desorptionEnthalpyJPerMolH2");
    requirePositiveFinite(desorptionEntropyJPerMolH2K, "desorptionEntropyJPerMolH2K");
    return desorptionEnthalpyJPerMolH2 - temperatureK * desorptionEntropyJPerMolH2K
        + GAS_CONSTANT_J_PER_MOL_K * temperatureK * Math.log(hydrogenFugacityBar / STANDARD_FUGACITY_BAR);
  }

  /**
   * Calculates equilibrium hydrogen fugacity at a given temperature.
   *
   * @param temperatureK temperature in K
   * @param desorptionEnthalpyJPerMolH2 reaction enthalpy in J/mol H2
   * @param desorptionEntropyJPerMolH2K reaction entropy in J/(mol H2 K)
   * @return equilibrium hydrogen fugacity in bara
   */
  public static double equilibriumFugacityBar(double temperatureK, double desorptionEnthalpyJPerMolH2,
      double desorptionEntropyJPerMolH2K) {
    requirePositiveFinite(temperatureK, "temperatureK");
    requirePositiveFinite(desorptionEnthalpyJPerMolH2, "desorptionEnthalpyJPerMolH2");
    requirePositiveFinite(desorptionEntropyJPerMolH2K, "desorptionEntropyJPerMolH2K");
    double logFugacity = (desorptionEntropyJPerMolH2K - desorptionEnthalpyJPerMolH2 / temperatureK)
        / GAS_CONSTANT_J_PER_MOL_K;
    double fugacity = STANDARD_FUGACITY_BAR * Math.exp(logFugacity);
    if (!Double.isFinite(fugacity) || fugacity <= 0.0) {
      throw new IllegalArgumentException("equilibrium fugacity is outside the finite positive range");
    }
    return fugacity;
  }

  /**
   * Calculates the equilibrium temperature at a supplied hydrogen fugacity.
   *
   * @param hydrogenFugacityBar hydrogen partial fugacity in bara
   * @param desorptionEnthalpyJPerMolH2 reaction enthalpy in J/mol H2
   * @param desorptionEntropyJPerMolH2K reaction entropy in J/(mol H2 K)
   * @return equilibrium temperature in K
   */
  public static double equilibriumTemperatureK(double hydrogenFugacityBar, double desorptionEnthalpyJPerMolH2,
      double desorptionEntropyJPerMolH2K) {
    requirePositiveFinite(hydrogenFugacityBar, "hydrogenFugacityBar");
    requirePositiveFinite(desorptionEnthalpyJPerMolH2, "desorptionEnthalpyJPerMolH2");
    requirePositiveFinite(desorptionEntropyJPerMolH2K, "desorptionEntropyJPerMolH2K");
    double denominator = desorptionEntropyJPerMolH2K
        - GAS_CONSTANT_J_PER_MOL_K * Math.log(hydrogenFugacityBar / STANDARD_FUGACITY_BAR);
    if (denominator <= 0.0) {
      throw new IllegalArgumentException("no positive equilibrium temperature at this fugacity");
    }
    return desorptionEnthalpyJPerMolH2 / denominator;
  }

  /**
   * Validates a positive integer input.
   *
   * @param value input value
   * @param name argument name
   */
  private static void requirePositive(int value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }

  /**
   * Validates a finite positive real input.
   *
   * @param value input value
   * @param name argument name
   */
  private static void requirePositiveFinite(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }
}
