package neqsim.thermo.phase;

import java.io.Serializable;

/**
 * Parameter-neutral standard Pitzer apparent-molar-volume model for one binary electrolyte.
 *
 * <p>
 * This class evaluates the pressure derivative of the standard binary Pitzer excess-Gibbs-energy equation. It
 * deliberately contains no salt-specific coefficients. Callers must supply parameters evaluated at the same temperature
 * and pressure as the requested state. The class is therefore a thermodynamic kernel, not a qualified density
 * correlation or a replacement for NeqSim's default aqueous-density model.
 * </p>
 *
 * <p>
 * All quantities use SI units. Apparent molar volumes are in m<sup>3</sup>/mol, molality is in mol/kg solvent, pressure
 * is in Pa, and electrolyte molar mass is in kg/mol.
 * </p>
 */
public final class PitzerBinaryVolumetricModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Universal gas constant in J/(mol K). */
  private static final double GAS_CONSTANT = 8.31446261815324;

  /** Standard Pitzer b parameter in (kg/mol)<sup>1/2</sup>. */
  public static final double STANDARD_B = 1.2;

  /** Standard Pitzer alpha parameter in (kg/mol)<sup>1/2</sup>. */
  public static final double STANDARD_ALPHA = 2.0;

  private final int cationStoichiometry;
  private final int anionStoichiometry;
  private final int cationCharge;
  private final int anionCharge;

  /**
   * Construct a standard binary-electrolyte volumetric model.
   *
   * @param cationStoichiometry number of cations in one neutral formula unit
   * @param anionStoichiometry number of anions in one neutral formula unit
   * @param cationCharge positive integer cation charge
   * @param anionCharge negative integer anion charge
   */
  public PitzerBinaryVolumetricModel(int cationStoichiometry, int anionStoichiometry, int cationCharge,
      int anionCharge) {
    if (cationStoichiometry <= 0 || anionStoichiometry <= 0) {
      throw new IllegalArgumentException("Pitzer stoichiometric coefficients must be positive");
    }
    if (cationCharge <= 0 || anionCharge >= 0) {
      throw new IllegalArgumentException("Pitzer binary model requires positive cation and negative anion charges");
    }
    long formulaCharge = (long) cationStoichiometry * cationCharge + (long) anionStoichiometry * anionCharge;
    if (formulaCharge != 0L) {
      throw new IllegalArgumentException("Pitzer binary formula unit must be electrically neutral");
    }
    this.cationStoichiometry = cationStoichiometry;
    this.anionStoichiometry = anionStoichiometry;
    this.cationCharge = cationCharge;
    this.anionCharge = anionCharge;
  }

  /**
   * Calculate apparent molar volume from the infinite-dilution standard state.
   *
   * @param molality electrolyte formula-unit molality in mol/kg solvent
   * @param limitingApparentMolarVolume apparent molar volume at infinite dilution in m<sup>3</sup>/mol
   * @param parameters caller-supplied parameters evaluated at the requested temperature and pressure
   * @return apparent molar volume in m<sup>3</sup>/mol
   */
  public double calculateApparentMolarVolume(double molality, double limitingApparentMolarVolume,
      StateParameters parameters) {
    requireMolality(molality, true);
    requireFinite(limitingApparentMolarVolume, "Limiting apparent molar volume");
    requireParameters(parameters);
    return checkedVolume(limitingApparentMolarVolume + calculateExcessVolume(molality, parameters));
  }

  /**
   * Calculate apparent molar volume relative to a measured non-zero reference molality.
   *
   * <p>
   * This difference form avoids estimating an infinite-dilution volume from small differences of dilute-solution
   * densities. The reference value and all state parameters must describe the same temperature and pressure.
   * </p>
   *
   * @param molality requested formula-unit molality in mol/kg solvent
   * @param referenceMolality non-zero reference molality in mol/kg solvent
   * @param referenceApparentMolarVolume apparent molar volume at the reference molality in m<sup>3</sup>/mol
   * @param parameters caller-supplied parameters evaluated at the common temperature and pressure
   * @return apparent molar volume in m<sup>3</sup>/mol
   */
  public double calculateApparentMolarVolumeFromReference(double molality, double referenceMolality,
      double referenceApparentMolarVolume, StateParameters parameters) {
    requireMolality(molality, true);
    requireMolality(referenceMolality, false);
    requireFinite(referenceApparentMolarVolume, "Reference apparent molar volume");
    requireParameters(parameters);
    return checkedVolume(referenceApparentMolarVolume + calculateExcessVolume(molality, parameters)
        - calculateExcessVolume(referenceMolality, parameters));
  }

  /**
   * Convert apparent molar volume to solution density on a one-kilogram solvent basis.
   *
   * @param molality electrolyte molality in mol/kg solvent
   * @param electrolyteMolarMass neutral formula-unit molar mass in kg/mol
   * @param pureSolventDensity pure-solvent density at the same temperature and pressure in kg/m<sup>3</sup>
   * @param apparentMolarVolume apparent molar volume in m<sup>3</sup>/mol
   * @return solution density in kg/m<sup>3</sup>
   */
  public static double calculateDensity(double molality, double electrolyteMolarMass, double pureSolventDensity,
      double apparentMolarVolume) {
    requireMolality(molality, true);
    requirePositive(electrolyteMolarMass, "Electrolyte molar mass");
    requirePositive(pureSolventDensity, "Pure-solvent density");
    requireFinite(apparentMolarVolume, "Apparent molar volume");

    double solutionMass = 1.0 + molality * electrolyteMolarMass;
    double solutionVolume = 1.0 / pureSolventDensity + molality * apparentMolarVolume;
    if (!Double.isFinite(solutionVolume) || solutionVolume <= 0.0) {
      throw new IllegalArgumentException("Apparent molar volume produces a non-physical solution volume");
    }
    double density = solutionMass / solutionVolume;
    if (!Double.isFinite(density) || density <= 0.0) {
      throw new IllegalArgumentException("Apparent molar volume produces a non-physical solution density");
    }
    return density;
  }

  /**
   * Convert solution density to apparent molar volume on a one-kilogram solvent basis.
   *
   * @param molality non-zero electrolyte molality in mol/kg solvent
   * @param electrolyteMolarMass neutral formula-unit molar mass in kg/mol
   * @param pureSolventDensity pure-solvent density at the same temperature and pressure in kg/m<sup>3</sup>
   * @param solutionDensity measured or modelled solution density in kg/m<sup>3</sup>
   * @return apparent molar volume in m<sup>3</sup>/mol
   */
  public static double calculateApparentMolarVolumeFromDensity(double molality, double electrolyteMolarMass,
      double pureSolventDensity, double solutionDensity) {
    requireMolality(molality, false);
    requirePositive(electrolyteMolarMass, "Electrolyte molar mass");
    requirePositive(pureSolventDensity, "Pure-solvent density");
    requirePositive(solutionDensity, "Solution density");

    double solutionVolume = (1.0 + molality * electrolyteMolarMass) / solutionDensity;
    return checkedVolume((solutionVolume - 1.0 / pureSolventDensity) / molality);
  }

  /**
   * Calculate stoichiometric ionic strength for the binary electrolyte.
   *
   * @param molality formula-unit molality in mol/kg solvent
   * @return ionic strength in mol/kg solvent
   */
  public double calculateIonicStrength(double molality) {
    requireMolality(molality, true);
    double ionCount = (double) cationStoichiometry + anionStoichiometry;
    double chargeProduct = Math.abs((double) cationCharge * anionCharge);
    return 0.5 * ionCount * chargeProduct * molality;
  }

  /**
   * Evaluate the standard Pitzer attenuation function.
   *
   * @param x non-negative dimensionless argument
   * @return {@code 2 * (1 - (1 + x) * exp(-x)) / x^2}, with its analytical limit at zero
   */
  static double attenuation(double x) {
    if (!Double.isFinite(x) || x < 0.0) {
      throw new IllegalArgumentException("Pitzer attenuation argument must be finite and non-negative");
    }
    if (x < 1.0e-4) {
      return 1.0 + x * (-2.0 / 3.0 + x * (1.0 / 4.0 + x * (-1.0 / 15.0 + x / 72.0)));
    }
    return 2.0 * (1.0 - (1.0 + x) * Math.exp(-x)) / (x * x);
  }

  private double calculateExcessVolume(double molality, StateParameters parameters) {
    double ionicStrength = calculateIonicStrength(molality);
    double squareRootIonicStrength = Math.sqrt(ionicStrength);
    double ionCount = (double) cationStoichiometry + anionStoichiometry;
    double chargeProduct = Math.abs((double) cationCharge * anionCharge);

    double debyeHuckelVolume = ionCount * chargeProduct * parameters.getDebyeHuckelVolumeSlope() / (2.0 * STANDARD_B)
        * Math.log1p(STANDARD_B * squareRootIonicStrength);

    double bVolume = parameters.getBeta0PressureDerivative()
        + parameters.getBeta1PressureDerivative() * attenuation(STANDARD_ALPHA * squareRootIonicStrength);
    double stoichiometricProduct = cationStoichiometry * anionStoichiometry;
    double interactionVolume = stoichiometricProduct * GAS_CONSTANT * parameters.getTemperatureK()
        * (2.0 * molality * bVolume
            + molality * molality * Math.sqrt(stoichiometricProduct) * parameters.getCphiPressureDerivative());

    double excessVolume = debyeHuckelVolume + interactionVolume;
    if (!Double.isFinite(excessVolume)) {
      throw new IllegalArgumentException("Pitzer volumetric parameters produce a non-finite apparent molar volume");
    }
    return excessVolume;
  }

  private static void requireParameters(StateParameters parameters) {
    if (parameters == null) {
      throw new IllegalArgumentException("Pitzer volumetric state parameters must not be null");
    }
  }

  private static void requireMolality(double molality, boolean allowZero) {
    if (!Double.isFinite(molality) || molality < 0.0 || (!allowZero && molality == 0.0)) {
      throw new IllegalArgumentException(
          allowZero ? "Molality must be finite and non-negative" : "Molality must be finite and positive");
    }
  }

  private static void requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  private static double checkedVolume(double volume) {
    if (!Double.isFinite(volume)) {
      throw new IllegalArgumentException("Pitzer volumetric calculation produced a non-finite volume");
    }
    return volume;
  }

  /** Parameters evaluated at one common temperature and pressure. */
  public static final class StateParameters implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double temperatureK;
    private final double pressurePa;
    private final double debyeHuckelVolumeSlope;
    private final double beta0PressureDerivative;
    private final double beta1PressureDerivative;
    private final double cphiPressureDerivative;

    /**
     * Construct already-evaluated SI volumetric parameters for one state.
     *
     * @param temperatureK temperature in K
     * @param pressurePa absolute pressure in Pa
     * @param debyeHuckelVolumeSlope Debye-Huckel volume slope in m<sup>3</sup> kg<sup>1/2</sup> mol<sup>-3/2</sup>
     * @param beta0PressureDerivative pressure derivative of beta zero in Pa<sup>-1</sup> kg/mol
     * @param beta1PressureDerivative pressure derivative of beta one in Pa<sup>-1</sup> kg/mol
     * @param cphiPressureDerivative pressure derivative of C phi in Pa<sup>-1</sup> kg<sup>2</sup>/mol<sup>2</sup>
     */
    public StateParameters(double temperatureK, double pressurePa, double debyeHuckelVolumeSlope,
        double beta0PressureDerivative, double beta1PressureDerivative, double cphiPressureDerivative) {
      requirePositive(temperatureK, "Pitzer volumetric temperature");
      requirePositive(pressurePa, "Pitzer volumetric pressure");
      requireFinite(debyeHuckelVolumeSlope, "Debye-Huckel volume slope");
      requireFinite(beta0PressureDerivative, "Beta-zero pressure derivative");
      requireFinite(beta1PressureDerivative, "Beta-one pressure derivative");
      requireFinite(cphiPressureDerivative, "C-phi pressure derivative");
      this.temperatureK = temperatureK;
      this.pressurePa = pressurePa;
      this.debyeHuckelVolumeSlope = debyeHuckelVolumeSlope;
      this.beta0PressureDerivative = beta0PressureDerivative;
      this.beta1PressureDerivative = beta1PressureDerivative;
      this.cphiPressureDerivative = cphiPressureDerivative;
    }

    /** @return temperature in K */
    public double getTemperatureK() {
      return temperatureK;
    }

    /** @return absolute pressure in Pa */
    public double getPressurePa() {
      return pressurePa;
    }

    /** @return Debye-Huckel volume slope in SI units */
    public double getDebyeHuckelVolumeSlope() {
      return debyeHuckelVolumeSlope;
    }

    /** @return pressure derivative of beta zero in SI units */
    public double getBeta0PressureDerivative() {
      return beta0PressureDerivative;
    }

    /** @return pressure derivative of beta one in SI units */
    public double getBeta1PressureDerivative() {
      return beta1PressureDerivative;
    }

    /** @return pressure derivative of C phi in SI units */
    public double getCphiPressureDerivative() {
      return cphiPressureDerivative;
    }
  }
}
