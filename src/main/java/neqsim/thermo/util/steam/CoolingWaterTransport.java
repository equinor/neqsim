package neqsim.thermo.util.steam;

/**
 * Reference transport properties for liquid cooling water, with seawater salinity corrections.
 *
 * <p>
 * Water uses IAPWS R12-08 Eqs. 11-12 and R15-11 Eqs. 16-17. Critical enhancement is omitted, appropriate for the
 * 273.15-353 K cooling envelope, far from the critical region. Seawater uses the viscosity multiplier of Sharqawy et
 * al. (2010), Eqs. 22-23, and the saline conductivity increment of IAPWS G10-15 (2015), Eqs. 1-5. The water reference
 * is evaluated at the actual pressure. The viscosity multiplier was measured near atmospheric pressure; its use at
 * higher pressure is an engineering extension. These are implementations of the published equations, not a
 * redistribution of the MIT software.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class CoolingWaterTransport {
  /** IAPWS R12-08 Table 2: temperature exponent, density exponent, coefficient. */
  private static final double[][] VISCOSITY = {{0, 0, 0.520094}, {1, 0, 0.0850895}, {2, 0, -1.08374}, {3, 0, -0.289555},
      {0, 1, 0.222531}, {1, 1, 0.999115}, {2, 1, 1.88797}, {3, 1, 1.26613}, {5, 1, 0.120573}, {0, 2, -0.281378},
      {1, 2, -0.906851}, {2, 2, -0.772479}, {3, 2, -0.489837}, {4, 2, -0.257040}, {0, 3, 0.161913}, {1, 3, 0.257399},
      {0, 4, -0.0325372}, {3, 4, 0.0698452}, {4, 5, 0.00872102}, {3, 6, -0.00435673}, {5, 6, -0.000593264}};
  /** IAPWS R15-11 Table 2: row is temperature exponent, column is density exponent. */
  private static final double[][] CONDUCTIVITY = {
      {1.60397357, -0.646013523, 0.111443906, 0.102997357, -0.0504123634, 0.00609859258},
      {2.33771842, -2.78843778, 1.53616167, -0.463045512, 0.0832827019, -0.00719201245},
      {2.19650529, -4.54580785, 3.55777244, -1.40944978, 0.275418278, -0.0205938816},
      {-1.21051378, 1.60812989, -0.621178141, 0.0716373224, 0.0, 0.0},
      {-2.7203370, 4.57586331, -3.18369245, 1.1168348, -0.19268305, 0.012913842}};

  /** Prevents instantiation. */
  private CoolingWaterTransport() {
  }

  /**
   * Validates liquid cooling transport inputs.
   *
   * @param temperature temperature [K]
   * @param density density of the pure-water reference at T and P [kg/m3]
   * @throws IllegalArgumentException if inputs are non-finite or outside the cooling envelope
   */
  private static void validate(double temperature, double density) {
    if (!Double.isFinite(temperature) || temperature < 273.15 || temperature > 353.0 || !Double.isFinite(density)
        || density < 900.0 || density > 1200.0) {
      throw new IllegalArgumentException("Liquid cooling transport requires 273.15 <= T <= 353 K"
          + " and 900 <= reference-water density <= 1200 kg/m3");
    }
  }

  /**
   * Evaluates IAPWS-08 background viscosity for liquid water.
   *
   * @param temperature temperature [K]
   * @param density pure-water density [kg/m3]
   * @return dynamic viscosity [Pa s]
   * @throws IllegalArgumentException if inputs are outside the cooling envelope
   */
  public static double waterViscosity(double temperature, double density) {
    validate(temperature, density);
    return backgroundViscosity(temperature, density);
  }

  /**
   * Evaluates IAPWS R12-08 Eqs. 11-12 (no critical enhancement) without range validation.
   *
   * @param temperature temperature [K]
   * @param density pure-water density [kg/m3]
   * @return dynamic viscosity [Pa s]
   */
  static double backgroundViscosity(double temperature, double density) {
    double t = temperature / 647.096;
    double r = density / 322.0;
    double dilute = 100.0 * Math.sqrt(t) / (1.67752 + 2.20462 / t + 0.6366564 / (t * t) - 0.241605 / (t * t * t));
    double sum = 0.0;
    for (double[] row : VISCOSITY) {
      sum += row[2] * Math.pow(1.0 / t - 1.0, row[0]) * Math.pow(r - 1.0, row[1]);
    }
    return dilute * Math.exp(r * sum) * 1e-6;
  }

  /**
   * Evaluates IAPWS-11 background conductivity for liquid water.
   *
   * @param temperature temperature [K]
   * @param density pure-water density [kg/m3]
   * @return thermal conductivity [W/(m K)]
   * @throws IllegalArgumentException if inputs are outside the cooling envelope
   */
  public static double waterConductivity(double temperature, double density) {
    validate(temperature, density);
    return backgroundConductivity(temperature, density);
  }

  /**
   * Evaluates IAPWS R15-11 Eqs. 16-17 (no critical enhancement) without range validation.
   *
   * @param temperature temperature [K]
   * @param density pure-water density [kg/m3]
   * @return thermal conductivity [W/(m K)]
   */
  static double backgroundConductivity(double temperature, double density) {
    double t = temperature / 647.096;
    double r = density / 322.0;
    double dilute = Math.sqrt(t) / (0.002443221 + 0.01323095 / t + 0.006770357 / (t * t) - 0.003454586 / (t * t * t)
        + 0.0004096266 / (t * t * t * t));
    double sum = 0.0;
    for (int i = 0; i < CONDUCTIVITY.length; i++) {
      for (int j = 0; j < CONDUCTIVITY[i].length; j++) {
        sum += CONDUCTIVITY[i][j] * Math.pow(1.0 / t - 1.0, i) * Math.pow(r - 1.0, j);
      }
    }
    return dilute * Math.exp(r * sum) * 1e-3;
  }

  /**
   * Calculates fresh/seawater viscosity using the IAPWS pure-water reference at the actual pressure.
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   * @param salinity reference-composition salinity [g/kg solution]
   * @return dynamic viscosity [Pa s]
   * @throws IllegalArgumentException if inputs are outside the cooling envelope
   */
  public static double viscosity(double temperature, double pressure, double salinity) {
    SeawaterProperties.validate(temperature, pressure, salinity);
    double density = 1.0 / Iapws_if97.v_pt(pressure / 10.0, temperature);
    double t = temperature - 273.15;
    double s = salinity / 1000.0;
    double a = 1.540913604 + 0.019981117208 * t - 0.000095203865864 * t * t;
    double b = 7.9739318223 - 0.075614568881 * t + 0.00047237011074 * t * t;
    return waterViscosity(temperature, density) * (1.0 + a * s + b * s * s);
  }

  /**
   * Calculates fresh/seawater thermal conductivity using an IAPWS pure-water reference.
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   * @param salinity reference-composition salinity [g/kg solution]
   * @return thermal conductivity [W/(m K)]
   * @throws IllegalArgumentException if inputs are outside the cooling envelope
   */
  public static double conductivity(double temperature, double pressure, double salinity) {
    SeawaterProperties.validate(temperature, pressure, salinity);
    double density = 1.0 / Iapws_if97.v_pt(pressure / 10.0, temperature);
    return waterConductivity(temperature, density)
        + seawaterConductivityIncrement(temperature, pressure / 10.0, salinity);
  }

  /**
   * Calculates the saline conductivity increment from IAPWS G10-15 Eqs. 1-5 and Table 1.
   *
   * @param temperature temperature [K]
   * @param pressureMPa absolute pressure [MPa]
   * @param salinity reference-composition salinity [g/kg solution]
   * @return saline increment [W/(m K)], to add to a consistent pure-water reference
   * @throws IllegalArgumentException if inputs are outside the guideline range
   */
  public static double seawaterConductivityIncrement(double temperature, double pressureMPa, double salinity) {
    if (!Double.isFinite(temperature) || temperature < 273.15 || temperature > 523.15 || !Double.isFinite(pressureMPa)
        || pressureMPa <= 0.0 || pressureMPa > 140.0 || !Double.isFinite(salinity) || salinity < 0.0
        || salinity > 170.0) {
      throw new IllegalArgumentException(
          "Seawater conductivity increment requires 273.15-523.15 K," + " 0 < P <= 140 MPa and 0-170 g/kg solution");
    }
    double t = temperature - 273.15;
    double a = (-7.180891e-5 + 1.831971e-7 * pressureMPa) * Math.exp((1.048077e-3 - 4.494722e-6 * pressureMPa) * t);
    double b = (0.1463375 + 9.208586e-4 * pressureMPa) * Math.exp((-3.086908e-3 + 1.798489e-5 * pressureMPa) * t);
    return a * Math.pow(salinity, 1.0 + b);
  }
}
