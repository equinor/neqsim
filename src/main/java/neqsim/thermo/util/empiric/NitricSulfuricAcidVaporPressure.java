package neqsim.thermo.util.empiric;

import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * NitricSulfuricAcidVaporPressure class.
 * </p>
 *
 * <p>
 * Implements the Van Laar activity-coefficient model of Taleb, Ponche and
 * Mirabel for the
 * equilibrium partial vapour pressures of water, nitric acid and sulfuric acid
 * over their binary and
 * ternary liquid mixtures. The model is valid at low (stratospheric)
 * temperatures, roughly 190-298
 * K, and reproduces the saturation vapour pressures of the H2O-HNO3-H2SO4
 * system used in
 * polar-stratospheric-cloud and aerosol modelling.
 * </p>
 *
 * <p>
 * Reference: D. Taleb, J. L. Ponche and P. Mirabel, "Vapor pressures in the
 * ternary system
 * water-nitric acid-sulfuric acid at low temperature", Journal of Geophysical
 * Research, 101(D20),
 * 25967-25977, 1996.
 * </p>
 *
 * <p>
 * Component indexing follows the paper: component 1 = water (H2O), component 2
 * = nitric acid (HNO3),
 * component 3 = sulfuric acid (H2SO4). The three constituent binary systems are
 * I = H2O-HNO3, II =
 * H2O-H2SO4 and III = HNO3-H2SO4. All temperatures are in kelvin and all
 * logarithms in the model are
 * base-10. Pure-component and partial pressures returned by this class are
 * expressed in pascal (Pa).
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class NitricSulfuricAcidVaporPressure {
  /** Conversion factor from torr (mmHg) to pascal. */
  private static final double TORR_TO_PA = 133.322368421;

  /** Conversion factor from millibar to pascal. */
  private static final double MBAR_TO_PA = 100.0;

  /** Conversion factor from standard atmosphere to pascal. */
  private static final double ATM_TO_PA = 101325.0;

  /** Molar mass of water (H2O) in g/mol. */
  public static final double MOLAR_MASS_WATER = 18.015;

  /** Molar mass of nitric acid (HNO3) in g/mol. */
  public static final double MOLAR_MASS_NITRIC = 63.012;

  /** Molar mass of sulfuric acid (H2SO4) in g/mol. */
  public static final double MOLAR_MASS_SULFURIC = 98.079;

  /** System I (H2O-HNO3) Van Laar exponent B for water (B_I,1). */
  private static final double B_I_1 = 0.5695;

  /**
   * System I (H2O-HNO3) Van Laar exponent B for nitric acid (B_I,2 = 1/B_I,1).
   */
  private static final double B_I_2 = 1.0 / 0.5695;

  /** System II (H2O-H2SO4) Van Laar exponent B for water (B_II,1). */
  private static final double B_II_1 = 0.527;

  /**
   * System II (H2O-H2SO4) Van Laar exponent B for sulfuric acid (B_II,3 =
   * 1/B_II,1).
   */
  private static final double B_II_3 = 1.0 / 0.527;

  /** System III (HNO3-H2SO4) Van Laar exponent B for nitric acid (B_III,2). */
  private static final double B_III_2 = 0.4;

  /**
   * System III (HNO3-H2SO4) Van Laar exponent B for sulfuric acid (B_III,3 =
   * 1/B_III,2).
   */
  private static final double B_III_3 = 1.0 / 0.4;

  /**
   * System III (HNO3-H2SO4) Van Laar parameter A for nitric acid (A_III,2),
   * fitted at 273 K.
   */
  private static final double A_III_2 = -250.52;

  /**
   * System III (HNO3-H2SO4) Van Laar parameter A for sulfuric acid (A_III,3),
   * fitted at 273 K.
   */
  private static final double A_III_3 = -100.21;

  /**
   * Private constructor preventing instantiation of this static utility class.
   */
  private NitricSulfuricAcidVaporPressure() {
  }

  /**
   * <p>
   * System I (H2O-HNO3) temperature-dependent Van Laar parameter for water
   * (A_I,1).
   * </p>
   *
   * @param temperature temperature in kelvin (valid roughly 190-298 K)
   * @return the parameter A_I,1
   */
  private static double aI1(double temperature) {
    return -391.43 - 7.44e4 / temperature;
  }

  /**
   * <p>
   * System I (H2O-HNO3) temperature-dependent Van Laar parameter for nitric acid
   * (A_I,2).
   * </p>
   *
   * @param temperature temperature in kelvin (valid roughly 190-298 K)
   * @return the parameter A_I,2
   */
  private static double aI2(double temperature) {
    return -627.739 - 1.406e5 / temperature;
  }

  /**
   * <p>
   * System II (H2O-H2SO4) temperature-dependent Van Laar parameter for water
   * (A_II,1).
   * </p>
   *
   * @param temperature temperature in kelvin (valid roughly 190-298 K)
   * @return the parameter A_II,1
   */
  private static double aII1(double temperature) {
    return 2.989e3 - 2.147e6 / temperature + 2.33e8 / (temperature * temperature);
  }

  /**
   * <p>
   * System II (H2O-H2SO4) temperature-dependent Van Laar parameter for sulfuric
   * acid (A_II,3).
   * </p>
   *
   * @param temperature temperature in kelvin (valid roughly 190-298 K)
   * @return the parameter A_II,3
   */
  private static double aII3(double temperature) {
    return 5.672e3 - 4.074e6 / temperature + 4.421e8 / (temperature * temperature);
  }

  /**
   * <p>
   * Pure-component saturation vapour pressure of liquid water.
   * </p>
   *
   * <p>
   * Computed from log10(P0/mbar) = 8.42926609 - 1827.17843/T - 71208.271/T^2.
   * </p>
   *
   * @param temperature temperature in kelvin (valid roughly 190-298 K)
   * @return pure water vapour pressure in pascal (Pa)
   */
  public static double pureVaporPressureWater(double temperature) {
    double log10Pmbar = 8.42926609 - 1827.17843 / temperature
        - 71208.271 / (temperature * temperature);
    return Math.pow(10.0, log10Pmbar) * MBAR_TO_PA;
  }

  /**
   * Antoine coefficient A for the pure HNO3 vapour pressure correlation.
   *
   * <p>
   * log10(P0/torr) = HNO3_ANTOINE_A - HNO3_ANTOINE_B / (T - HNO3_ANTOINE_C)
   * </p>
   *
   * <p>
   * Values A = 7.57628, B = 1470.385, C = 43 are fitted simultaneously to two
   * physical constraints:
   * (1) the experimental normal boiling point of pure HNO3 at 1 atm (83 degC =
   * 356.15 K, P = 760 torr); and
   * (2) a 6.9 % increase in P0 at 273.15 K relative to the original Pennington
   * (1951) / Taleb et al. (1996) values (A = 7.61628, B = 1486.238) which
   * systematically under-predicted the Vandoni (1944) ternary salting-out
   * peak pressures at 273 K by approximately 7 %.
   * The updated C = 43 K is unchanged from the original Pennington value.
   * </p>
   */
  private static final double HNO3_ANTOINE_A = 7.57628;

  /**
   * Antoine coefficient B for the pure HNO3 vapour pressure correlation.
   *
   * @see #HNO3_ANTOINE_A
   */
  private static final double HNO3_ANTOINE_B = 1470.385;

  /**
   * Antoine coefficient C (temperature offset, K) for the pure HNO3 vapour
   * pressure correlation.
   *
   * @see #HNO3_ANTOINE_A
   */
  private static final double HNO3_ANTOINE_C = 43.0;

  /**
   * <p>
   * Pure-component saturation vapour pressure of liquid nitric acid.
   * </p>
   *
   * <p>
   * Computed from log10(P0/torr) = HNO3_ANTOINE_A - HNO3_ANTOINE_B / (T -
   * HNO3_ANTOINE_C).
   * The Antoine coefficients are fitted to the experimental normal boiling point
   * (83 degC at 1 atm) and to the Vandoni (1944) ternary P_HNO3 peak data at
   * 273 K as compiled by Taleb, Ponche and Mirabel (1996).
   * Valid range: approximately 190-400 K.
   * </p>
   *
   * @param temperature temperature in kelvin (valid roughly 190-400 K)
   * @return pure nitric acid vapour pressure in pascal (Pa)
   */
  public static double pureVaporPressureNitricAcid(double temperature) {
    double log10Ptorr = HNO3_ANTOINE_A - HNO3_ANTOINE_B / (temperature - HNO3_ANTOINE_C);
    return Math.pow(10.0, log10Ptorr) * TORR_TO_PA;
  }

  /**
   * <p>
   * Pure-component saturation vapour pressure of liquid sulfuric acid.
   * </p>
   *
   * <p>
   * Computed from ln(P0/atm) = -10156/T + 16.259.
   * </p>
   *
   * @param temperature temperature in kelvin (valid roughly 190-298 K)
   * @return pure sulfuric acid vapour pressure in pascal (Pa)
   */
  public static double pureVaporPressureSulfuricAcid(double temperature) {
    double lnPatm = -10156.0 / temperature + 16.259;
    return Math.exp(lnPatm) * ATM_TO_PA;
  }

  /**
   * <p>
   * Quantity T*log10(gamma) for water in the ternary mixture, i.e. the right-hand
   * side of equation
   * (10a) of Taleb et al. (1996).
   * </p>
   *
   * @param x1          mole fraction of water (H2O)
   * @param x2          mole fraction of nitric acid (HNO3)
   * @param x3          mole fraction of sulfuric acid (H2SO4)
   * @param temperature temperature in kelvin
   * @return the product of temperature and the base-10 logarithm of the water
   *         activity coefficient
   */
  private static double tLog10GammaWater(double x1, double x2, double x3, double temperature) {
    double aI1 = aI1(temperature);
    double aII1 = aII1(temperature);
    double num1 = aI1 * (x2 * x2 + B_III_2 * x2 * x3) - 0.5 * A_III_2 * B_I_1 * x2 * x3;
    double den1 = B_I_1 * x1 + x2 + B_III_2 * x3;
    double num2 = aII1 * (x3 * x3 + B_III_2 * x2 * x3) - 0.5 * A_III_3 * B_II_1 * x2 * x3;
    double den2 = B_II_1 * x1 + B_III_2 * x2 + x3;
    return num1 / (den1 * den1) + num2 / (den2 * den2);
  }

  /**
   * <p>
   * Quantity T*log10(gamma) for nitric acid in the ternary mixture, i.e. the
   * right-hand side of
   * equation (10b) of Taleb et al. (1996).
   * </p>
   *
   * @param x1          mole fraction of water (H2O)
   * @param x2          mole fraction of nitric acid (HNO3)
   * @param x3          mole fraction of sulfuric acid (H2SO4)
   * @param temperature temperature in kelvin
   * @return the product of temperature and the base-10 logarithm of the nitric
   *         acid activity
   *         coefficient
   */
  private static double tLog10GammaNitric(double x1, double x2, double x3, double temperature) {
    double aI2 = aI2(temperature);
    double aII3 = aII3(temperature);
    double aII1 = aII1(temperature);
    double num1 = aI2 * (x1 * x1 + B_II_3 * x1 * x3) - 0.5 * aII3 * B_I_2 * x1 * x3;
    double den1 = x1 + x2 * B_I_2 + B_II_3 * x3;
    double num2 = A_III_3 * (x3 * x3 + B_II_1 * x1 * x3) - 0.5 * aII1 * B_III_2 * x1 * x3;
    double den2 = B_II_1 * x1 + B_III_2 * x2 + x3;
    return num1 / (den1 * den1) + num2 / (den2 * den2);
  }

  /**
   * <p>
   * Quantity T*log10(gamma) for sulfuric acid in the ternary mixture, i.e. the
   * right-hand side of
   * equation (10c) of Taleb et al. (1996).
   * </p>
   *
   * @param x1          mole fraction of water (H2O)
   * @param x2          mole fraction of nitric acid (HNO3)
   * @param x3          mole fraction of sulfuric acid (H2SO4)
   * @param temperature temperature in kelvin
   * @return the product of temperature and the base-10 logarithm of the sulfuric
   *         acid activity
   *         coefficient
   */
  private static double tLog10GammaSulfuric(double x1, double x2, double x3, double temperature) {
    double aII3 = aII3(temperature);
    double aI2 = aI2(temperature);
    double aI1 = aI1(temperature);
    double num1 = aII3 * (x1 * x1 + B_I_2 * x1 * x2) - 0.5 * aI2 * B_II_3 * x1 * x2;
    double den1 = x1 + x2 * B_I_2 + B_II_3 * x3;
    double num2 = A_III_2 * (x2 * x2 + B_I_1 * x1 * x2) - 0.5 * aI1 * B_III_3 * x1 * x2;
    double den2 = B_I_1 * x1 + x2 + B_III_3 * x3;
    return num1 / (den1 * den1) + num2 / (den2 * den2);
  }

  /**
   * <p>
   * Activity coefficient of water in the ternary (or binary, by setting one acid
   * mole fraction to
   * zero) mixture, from equation (10a).
   * </p>
   *
   * @param x1          mole fraction of water (H2O)
   * @param x2          mole fraction of nitric acid (HNO3)
   * @param x3          mole fraction of sulfuric acid (H2SO4)
   * @param temperature temperature in kelvin
   * @return the water activity coefficient (dimensionless)
   */
  public static double activityCoefficientWater(double x1, double x2, double x3,
      double temperature) {
    return Math.pow(10.0, tLog10GammaWater(x1, x2, x3, temperature) / temperature);
  }

  /**
   * <p>
   * Activity coefficient of nitric acid in the ternary (or binary) mixture, from
   * equation (10b).
   * </p>
   *
   * @param x1          mole fraction of water (H2O)
   * @param x2          mole fraction of nitric acid (HNO3)
   * @param x3          mole fraction of sulfuric acid (H2SO4)
   * @param temperature temperature in kelvin
   * @return the nitric acid activity coefficient (dimensionless)
   */
  public static double activityCoefficientNitricAcid(double x1, double x2, double x3,
      double temperature) {
    return Math.pow(10.0, tLog10GammaNitric(x1, x2, x3, temperature) / temperature);
  }

  /**
   * <p>
   * Activity coefficient of sulfuric acid in the ternary (or binary) mixture,
   * from equation (10c).
   * </p>
   *
   * @param x1          mole fraction of water (H2O)
   * @param x2          mole fraction of nitric acid (HNO3)
   * @param x3          mole fraction of sulfuric acid (H2SO4)
   * @param temperature temperature in kelvin
   * @return the sulfuric acid activity coefficient (dimensionless)
   */
  public static double activityCoefficientSulfuricAcid(double x1, double x2, double x3,
      double temperature) {
    return Math.pow(10.0, tLog10GammaSulfuric(x1, x2, x3, temperature) / temperature);
  }

  /**
   * <p>
   * Equilibrium partial vapour pressure of water over the mixture, P1 = gamma1 *
   * x1 * P0,1.
   * </p>
   *
   * @param x1          mole fraction of water (H2O)
   * @param x2          mole fraction of nitric acid (HNO3)
   * @param x3          mole fraction of sulfuric acid (H2SO4)
   * @param temperature temperature in kelvin
   * @return the water partial pressure in pascal (Pa)
   */
  public static double partialPressureWater(double x1, double x2, double x3, double temperature) {
    return activityCoefficientWater(x1, x2, x3, temperature) * x1
        * pureVaporPressureWater(temperature);
  }

  /**
   * <p>
   * Equilibrium partial vapour pressure of nitric acid over the mixture, P2 =
   * gamma2 * x2 * P0,2.
   * </p>
   *
   * @param x1          mole fraction of water (H2O)
   * @param x2          mole fraction of nitric acid (HNO3)
   * @param x3          mole fraction of sulfuric acid (H2SO4)
   * @param temperature temperature in kelvin
   * @return the nitric acid partial pressure in pascal (Pa)
   */
  public static double partialPressureNitricAcid(double x1, double x2, double x3,
      double temperature) {
    return activityCoefficientNitricAcid(x1, x2, x3, temperature) * x2
        * pureVaporPressureNitricAcid(temperature);
  }

  /**
   * <p>
   * Equilibrium partial vapour pressure of sulfuric acid over the mixture, P3 =
   * gamma3 * x3 * P0,3.
   * </p>
   *
   * @param x1          mole fraction of water (H2O)
   * @param x2          mole fraction of nitric acid (HNO3)
   * @param x3          mole fraction of sulfuric acid (H2SO4)
   * @param temperature temperature in kelvin
   * @return the sulfuric acid partial pressure in pascal (Pa)
   */
  public static double partialPressureSulfuricAcid(double x1, double x2, double x3,
      double temperature) {
    return activityCoefficientSulfuricAcid(x1, x2, x3, temperature) * x3
        * pureVaporPressureSulfuricAcid(temperature);
  }

  /**
   * <p>
   * Convert component mass fractions (or weight percentages, which are normalised
   * internally) to
   * mole fractions for the H2O-HNO3-H2SO4 system.
   * </p>
   *
   * @param massWater    mass fraction or weight percent of water (H2O)
   * @param massNitric   mass fraction or weight percent of nitric acid (HNO3)
   * @param massSulfuric mass fraction or weight percent of sulfuric acid (H2SO4)
   * @return a three-element array of mole fractions ordered as {x_H2O, x_HNO3,
   *         x_H2SO4}
   */
  public static double[] moleFractionsFromMassFractions(double massWater, double massNitric,
      double massSulfuric) {
    double n1 = massWater / MOLAR_MASS_WATER;
    double n2 = massNitric / MOLAR_MASS_NITRIC;
    double n3 = massSulfuric / MOLAR_MASS_SULFURIC;
    double total = n1 + n2 + n3;
    return new double[] { n1 / total, n2 / total, n3 / total };
  }

  /**
   * <p>
   * Demonstration entry point printing a few reference values from the model.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects (unused)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    double t = 273.15;
    // Binary H2O-H2SO4 at 50 wt% sulfuric acid, 273.15 K
    double[] x = moleFractionsFromMassFractions(50.0, 0.0, 50.0);
    System.out.println("Water partial pressure over 50 wt% H2SO4 at 273.15 K = "
        + partialPressureWater(x[0], x[1], x[2], t) / TORR_TO_PA + " torr");
    // Ternary example at 273.15 K
    double[] xt = moleFractionsFromMassFractions(60.0, 20.0, 20.0);
    System.out.println("Ternary P_H2O = "
        + partialPressureWater(xt[0], xt[1], xt[2], t) / TORR_TO_PA + " torr, P_HNO3 = "
        + partialPressureNitricAcid(xt[0], xt[1], xt[2], t) / TORR_TO_PA + " torr");
  }
}
