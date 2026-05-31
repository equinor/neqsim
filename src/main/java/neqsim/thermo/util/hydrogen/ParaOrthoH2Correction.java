package neqsim.thermo.util.hydrogen;

import java.io.Serializable;

/**
 * Utility calculations for para/ortho hydrogen spin-isomer corrections.
 *
 * <p>
 * The model uses the rigid-rotor rotational partition function for molecular hydrogen with the
 * correct nuclear spin statistical weights. It is intended for cryogenic hydrogen screening,
 * liquefaction pre-design and agent workflows where normal hydrogen properties need a first-order
 * correction for equilibrium para-hydrogen enrichment below about 100 K.
 * </p>
 *
 * <p>
 * The class does not replace detailed NIST/Leachman property calls. Use it to estimate the
 * equilibrium para fraction, heat released by conversion from normal hydrogen, additional
 * equilibrium rotational heat capacity, a bounded thermal-conductivity correction factor and a
 * screening conversion time for common catalysts.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ParaOrthoH2Correction implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double GAS_CONSTANT = 8.314462618;
  private static final double MOLAR_MASS_H2_KG_PER_MOL = 2.01588e-3;
  private static final double ROTATIONAL_TEMPERATURE_K = 85.3;
  private static final double NORMAL_PARA_FRACTION = 0.25;
  private static final double REFERENCE_CONVERSION_TEMPERATURE_K = 77.0;
  private static final int MAX_ROTATIONAL_QUANTUM_NUMBER = 80;

  /** Catalyst families used for para/ortho conversion time screening. */
  public enum ConversionCatalyst {
    /** No catalyst present; conversion is effectively frozen for engineering screening. */
    NONE(Double.POSITIVE_INFINITY, 0.0),
    /** Activated carbon or charcoal conversion bed. */
    ACTIVATED_CHARCOAL(1800.0, 180.0),
    /** Hydrous ferric oxide conversion catalyst. */
    HYDROUS_FERRIC_OXIDE(600.0, 140.0),
    /** Generic paramagnetic oxide such as chromium, manganese or rare-earth oxide. */
    PARAMAGNETIC_OXIDE(300.0, 120.0);

    private final double referenceTimeSeconds;
    private final double activationTemperatureK;

    /**
     * Creates a catalyst entry for conversion-time screening.
     *
     * @param referenceTimeSeconds time constant at 77 K in seconds
     * @param activationTemperatureK empirical temperature sensitivity in K
     */
    ConversionCatalyst(double referenceTimeSeconds, double activationTemperatureK) {
      this.referenceTimeSeconds = referenceTimeSeconds;
      this.activationTemperatureK = activationTemperatureK;
    }
  }

  /**
   * Private constructor for utility class.
   */
  private ParaOrthoH2Correction() {}

  /**
   * Returns the normal hydrogen para fraction at room-temperature equilibrium.
   *
   * @return para fraction of normal hydrogen, equal to 0.25
   */
  public static double getNormalParaFraction() {
    return NORMAL_PARA_FRACTION;
  }

  /**
   * Calculates the equilibrium para-hydrogen fraction at the specified temperature.
   *
   * @param temperatureK hydrogen temperature in K, must be greater than 0
   * @return equilibrium para fraction in the range 0 to 1
   */
  public static double getEquilibriumParaFraction(double temperatureK) {
    validateTemperature(temperatureK);
    RotationalPartition para = calculatePartition(temperatureK, true);
    RotationalPartition ortho = calculatePartition(temperatureK, false);
    double paraWeighted = para.partitionFunction;
    double orthoWeighted = 3.0 * ortho.partitionFunction;
    return paraWeighted / (paraWeighted + orthoWeighted);
  }

  /**
   * Calculates the equilibrium ortho-hydrogen fraction at the specified temperature.
   *
   * @param temperatureK hydrogen temperature in K, must be greater than 0
   * @return equilibrium ortho fraction in the range 0 to 1
   */
  public static double getEquilibriumOrthoFraction(double temperatureK) {
    return 1.0 - getEquilibriumParaFraction(temperatureK);
  }

  /**
   * Calculates the heat released when hydrogen converts from normal composition to equilibrium.
   *
   * @param temperatureK hydrogen temperature in K, must be greater than 0
   * @return exothermic conversion heat in J/kg, positive when heat is released
   */
  public static double getNormalToEquilibriumHeatJPerKg(double temperatureK) {
    return getConversionHeatJPerKg(NORMAL_PARA_FRACTION, getEquilibriumParaFraction(temperatureK),
        temperatureK);
  }

  /**
   * Calculates the heat released between two spin-isomer compositions at fixed temperature.
   *
   * @param initialParaFraction initial para fraction in the range 0 to 1
   * @param finalParaFraction final para fraction in the range 0 to 1
   * @param temperatureK hydrogen temperature in K, must be greater than 0
   * @return heat released in J/kg, positive when the final state has lower rotational energy
   */
  public static double getConversionHeatJPerKg(double initialParaFraction, double finalParaFraction,
      double temperatureK) {
    validateFraction(initialParaFraction, "initialParaFraction");
    validateFraction(finalParaFraction, "finalParaFraction");
    validateTemperature(temperatureK);
    double initialEnergy = rotationalEnergyJPerMol(temperatureK, initialParaFraction);
    double finalEnergy = rotationalEnergyJPerMol(temperatureK, finalParaFraction);
    return (initialEnergy - finalEnergy) / MOLAR_MASS_H2_KG_PER_MOL;
  }

  /**
   * Calculates equilibrium rotational heat capacity for spin-equilibrated hydrogen.
   *
   * @param temperatureK hydrogen temperature in K, must be greater than 0
   * @return rotational heat capacity in J/(kg K)
   */
  public static double getEquilibriumRotationalCpJPerKgK(double temperatureK) {
    validateTemperature(temperatureK);
    return numericalCp(temperatureK, true);
  }

  /**
   * Calculates rotational heat capacity for frozen normal hydrogen spin composition.
   *
   * @param temperatureK hydrogen temperature in K, must be greater than 0
   * @return rotational heat capacity in J/(kg K)
   */
  public static double getFrozenNormalRotationalCpJPerKgK(double temperatureK) {
    validateTemperature(temperatureK);
    return numericalCp(temperatureK, false);
  }

  /**
   * Calculates the extra rotational heat capacity from para/ortho equilibration.
   *
   * @param temperatureK hydrogen temperature in K, must be greater than 0
   * @return equilibrium minus frozen-normal rotational heat capacity in J/(kg K)
   */
  public static double getCpCorrectionJPerKgK(double temperatureK) {
    return getEquilibriumRotationalCpJPerKgK(temperatureK)
        - getFrozenNormalRotationalCpJPerKgK(temperatureK);
  }

  /**
   * Estimates the thermal-conductivity correction factor for equilibrium hydrogen.
   *
   * @param temperatureK hydrogen temperature in K, must be greater than 0
   * @return factor to multiply normal-hydrogen thermal conductivity by
   */
  public static double getThermalConductivityCorrectionFactor(double temperatureK) {
    return getThermalConductivityCorrectionFactor(temperatureK,
        getEquilibriumParaFraction(temperatureK));
  }

  /**
   * Estimates a bounded thermal-conductivity correction factor from para content.
   *
   * <p>
   * The correlation is deliberately conservative: it approaches 1.0 above cryogenic temperatures
   * and limits the correction to a narrow band for screening use.
   * </p>
   *
   * @param temperatureK hydrogen temperature in K, must be greater than 0
   * @param paraFraction para-hydrogen fraction in the range 0 to 1
   * @return factor to multiply normal-hydrogen thermal conductivity by
   */
  public static double getThermalConductivityCorrectionFactor(double temperatureK,
      double paraFraction) {
    validateTemperature(temperatureK);
    validateFraction(paraFraction, "paraFraction");
    double cryogenicWeight = 1.0 / (1.0 + Math.exp((temperatureK - 120.0) / 20.0));
    double enrichment = (paraFraction - NORMAL_PARA_FRACTION) / (1.0 - NORMAL_PARA_FRACTION);
    double factor = 1.0 - 0.08 * cryogenicWeight * enrichment;
    return Math.max(0.90, Math.min(1.04, factor));
  }

  /**
   * Estimates para/ortho conversion time for a catalyst family.
   *
   * @param temperatureK hydrogen temperature in K, must be greater than 0
   * @param catalyst conversion catalyst family, not null
   * @return first-order conversion time constant in seconds
   */
  public static double estimateEquilibrationTimeSeconds(double temperatureK,
      ConversionCatalyst catalyst) {
    validateTemperature(temperatureK);
    if (catalyst == null) {
      throw new IllegalArgumentException("catalyst cannot be null");
    }
    if (catalyst == ConversionCatalyst.NONE) {
      return Double.POSITIVE_INFINITY;
    }
    double exponent = catalyst.activationTemperatureK
        * (1.0 / temperatureK - 1.0 / REFERENCE_CONVERSION_TEMPERATURE_K);
    double time = catalyst.referenceTimeSeconds * Math.exp(exponent);
    return Math.max(1.0, Math.min(1.0e12, time));
  }

  /**
   * Calculates a rotational partition function for either para or ortho states.
   *
   * @param temperatureK hydrogen temperature in K
   * @param para true for even-J para states, false for odd-J ortho states
   * @return partition function and average rotational energy data
   */
  private static RotationalPartition calculatePartition(double temperatureK, boolean para) {
    double q = 0.0;
    double energyWeighted = 0.0;
    int start = para ? 0 : 1;
    for (int j = start; j <= MAX_ROTATIONAL_QUANTUM_NUMBER; j += 2) {
      double jTerm = j * (j + 1.0);
      double degeneracy = 2.0 * j + 1.0;
      double boltzmann = Math.exp(-ROTATIONAL_TEMPERATURE_K * jTerm / temperatureK);
      double weight = degeneracy * boltzmann;
      q += weight;
      energyWeighted += weight * GAS_CONSTANT * ROTATIONAL_TEMPERATURE_K * jTerm;
    }
    return new RotationalPartition(q, energyWeighted / q);
  }

  /**
   * Calculates mixture rotational energy at a fixed para fraction.
   *
   * @param temperatureK hydrogen temperature in K
   * @param paraFraction para fraction in the range 0 to 1
   * @return rotational energy in J/mol
   */
  private static double rotationalEnergyJPerMol(double temperatureK, double paraFraction) {
    RotationalPartition para = calculatePartition(temperatureK, true);
    RotationalPartition ortho = calculatePartition(temperatureK, false);
    return paraFraction * para.averageEnergyJPerMol
        + (1.0 - paraFraction) * ortho.averageEnergyJPerMol;
  }

  /**
   * Calculates heat capacity by finite-difference differentiation of rotational energy.
   *
   * @param temperatureK hydrogen temperature in K
   * @param equilibrium true for spin-equilibrium composition, false for frozen normal composition
   * @return heat capacity in J/(kg K)
   */
  private static double numericalCp(double temperatureK, boolean equilibrium) {
    double delta = Math.max(0.01, temperatureK * 1.0e-4);
    if (temperatureK - delta <= 0.0) {
      double upper = rotationalEnergyForCp(temperatureK + delta, equilibrium);
      double center = rotationalEnergyForCp(temperatureK, equilibrium);
      return (upper - center) / delta / MOLAR_MASS_H2_KG_PER_MOL;
    }
    double upper = rotationalEnergyForCp(temperatureK + delta, equilibrium);
    double lower = rotationalEnergyForCp(temperatureK - delta, equilibrium);
    return (upper - lower) / (2.0 * delta) / MOLAR_MASS_H2_KG_PER_MOL;
  }

  /**
   * Calculates rotational energy for the finite-difference heat capacity routine.
   *
   * @param temperatureK hydrogen temperature in K
   * @param equilibrium true for spin-equilibrium composition, false for frozen normal composition
   * @return rotational energy in J/mol
   */
  private static double rotationalEnergyForCp(double temperatureK, boolean equilibrium) {
    double paraFraction =
        equilibrium ? getEquilibriumParaFraction(temperatureK) : NORMAL_PARA_FRACTION;
    return rotationalEnergyJPerMol(temperatureK, paraFraction);
  }

  /**
   * Validates temperature input.
   *
   * @param temperatureK temperature in K
   */
  private static void validateTemperature(double temperatureK) {
    if (!Double.isFinite(temperatureK) || temperatureK <= 0.0) {
      throw new IllegalArgumentException("temperatureK must be finite and greater than 0");
    }
  }

  /**
   * Validates a composition fraction.
   *
   * @param fraction fraction value
   * @param name parameter name for diagnostics
   */
  private static void validateFraction(double fraction, String name) {
    if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException(name + " must be finite and in the range [0, 1]");
    }
  }

  /** Rotational partition data for one spin-isomer family. */
  private static final class RotationalPartition {
    private final double partitionFunction;
    private final double averageEnergyJPerMol;

    /**
     * Creates rotational partition data.
     *
     * @param partitionFunction rotational partition function
     * @param averageEnergyJPerMol average rotational energy in J/mol
     */
    private RotationalPartition(double partitionFunction, double averageEnergyJPerMol) {
      this.partitionFunction = partitionFunction;
      this.averageEnergyJPerMol = averageEnergyJPerMol;
    }
  }
}
