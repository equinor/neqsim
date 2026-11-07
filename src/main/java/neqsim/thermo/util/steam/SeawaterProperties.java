package neqsim.thermo.util.steam;

/**
 * Industrial seawater properties from IAPWS-IF97 liquid water plus the IAPWS-08 saline Gibbs energy.
 *
 * <p>
 * The saline coefficients are Table 2 of IAPWS R13-08 (2008), reproduced with attribution to the International
 * Association for the Properties of Water and Steam. Using IF97 for the water contribution follows the industrial
 * approach in IAPWS Advisory Note 5 (2016 revision) and Kretzschmar et al. (2015), doi:10.1080/19443994.2014.925838.
 * This is not the IAPWS-95-based TEOS-10 model. Salinity is Reference-Composition Salinity in g/kg of solution, not
 * practical salinity or salt per kg of water. The implemented cooling envelope is 273.15-353 K, 1-120 bara and 0-120
 * g/kg. Pressure derivatives outside the oceanographic range (T at most 313 K and S at most 42 g/kg) are
 * extrapolations; inspect {@link #isWithinPressureReferenceRange(double, double)} before using density or acoustic
 * results.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SeawaterProperties {
  /** Reduced salinity in kg/kg (IAPWS R13-08 Table 1). */
  private static final double SALINITY_SCALE = 0.03516504 * 40.0 / 35.0;
  /** Rows contain salinity, temperature and pressure exponents followed by the Gibbs coefficient [J/kg]. */
  private static final double[][] COEFFICIENTS = {{1, 0, 0, 5812.81456626732}, {2, 0, 0, 1416.27648484197},
      {3, 0, 0, -2432.14662381794}, {4, 0, 0, 2025.80115603697}, {5, 0, 0, -1091.66841042967},
      {6, 0, 0, 374.601237877840}, {7, 0, 0, -48.5891069025409}, {1, 1, 0, 851.226734946706},
      {2, 1, 0, 168.072408311545}, {3, 1, 0, -493.407510141682}, {4, 1, 0, 543.835333000098},
      {5, 1, 0, -196.028306689776}, {6, 1, 0, 36.7571622995805}, {2, 2, 0, 880.031352997204},
      {3, 2, 0, -43.0664675978042}, {4, 2, 0, -68.5572509204491}, {2, 3, 0, -225.267649263401},
      {3, 3, 0, -10.0227370861875}, {4, 3, 0, 49.3667694856254}, {2, 4, 0, 91.4260447751259},
      {3, 4, 0, 0.875600661808945}, {4, 4, 0, -17.1397577419788}, {2, 5, 0, -21.6603240875311},
      {4, 5, 0, 2.49697009569508}, {2, 6, 0, 2.13016970847183}, {2, 0, 1, -3310.49154044839},
      {3, 0, 1, 199.459603073901}, {4, 0, 1, -54.7919133532887}, {5, 0, 1, 36.0284195611086},
      {2, 1, 1, 729.116529735046}, {3, 1, 1, -175.292041186547}, {4, 1, 1, -22.6683558512829},
      {2, 2, 1, -860.764303783977}, {3, 2, 1, 383.058066002476}, {2, 3, 1, 694.244814133268},
      {3, 3, 1, -460.319931801257}, {2, 4, 1, -297.728741987187}, {3, 4, 1, 234.565187611355},
      {2, 0, 2, 384.794152978599}, {3, 0, 2, -52.2940909281335}, {4, 0, 2, -4.08193978912261},
      {2, 1, 2, -343.956902961561}, {3, 1, 2, 83.1923927801819}, {2, 2, 2, 337.409530269367},
      {3, 2, 2, -54.1917262517112}, {2, 3, 2, -204.889641964903}, {2, 4, 2, 74.7261411387560},
      {2, 0, 3, -96.5324320107458}, {3, 0, 3, 68.0444942726459}, {4, 0, 3, -30.1755111971161},
      {2, 1, 3, 124.687671116248}, {3, 1, 3, -29.4830643494290}, {2, 2, 3, -178.314556207638},
      {3, 2, 3, 25.6398487389914}, {2, 3, 3, 113.561697840594}, {2, 4, 3, -36.4872919001588},
      {2, 0, 4, 15.8408172766824}, {3, 0, 4, -3.41251932441282}, {2, 1, 4, -31.6569643860730},
      {2, 2, 4, 44.2040358308000}, {2, 3, 4, -11.1282734326413}, {2, 0, 5, -2.62480156590992},
      {2, 1, 5, 7.04658803315449}, {2, 2, 5, -7.92001547211682}};

  /** Prevents instantiation. */
  private SeawaterProperties() {
  }

  /**
   * Validates the implemented liquid cooling envelope.
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   * @param salinity reference-composition salinity [g/kg solution]
   * @throws IllegalArgumentException if an input is non-finite or outside the cooling envelope
   */
  public static void validate(double temperature, double pressure, double salinity) {
    if (!Double.isFinite(temperature) || temperature < 273.15 || temperature > 353.0 || !Double.isFinite(pressure)
        || pressure < 1.0 || pressure > 120.0 || !Double.isFinite(salinity) || salinity < 0.0 || salinity > 120.0) {
      throw new IllegalArgumentException("Cooling water requires 273.15 <= T <= 353 K, 1 <= P <= 120 bara"
          + " and 0 <= salinity <= 120 g/kg solution");
    }
  }

  /**
   * Reports whether the saline pressure dependence is inside the IAPWS-08 oceanographic reference range.
   *
   * @param temperature temperature [K], already within the cooling envelope
   * @param salinity reference-composition salinity [g/kg solution]
   * @return true for zero salinity or T at most 313 K and salinity at most 42 g/kg
   */
  public static boolean isWithinPressureReferenceRange(double temperature, double salinity) {
    return Double.isFinite(temperature) && Double.isFinite(salinity) && temperature >= 273.15 && temperature <= 353.0
        && salinity >= 0.0 && (salinity == 0.0 || (temperature <= 313.0 && salinity <= 42.0));
  }

  /**
   * Evaluates a temperature/pressure derivative of the saline specific Gibbs energy (IAPWS R13-08 Eq. 4).
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   * @param salinity reference-composition salinity [g/kg solution]
   * @param temperatureOrder temperature derivative order, zero through two
   * @param pressureOrder pressure derivative order, zero through two
   * @return derivative of saline Gibbs energy in J/kg, with derivatives per K and per Pa
   * @throws IllegalArgumentException if inputs or derivative orders are outside the supported range
   */
  public static double salineGibbsDerivative(double temperature, double pressure, double salinity, int temperatureOrder,
      int pressureOrder) {
    validate(temperature, pressure, salinity);
    if (temperatureOrder < 0 || pressureOrder < 0 || temperatureOrder + pressureOrder > 2) {
      throw new IllegalArgumentException("Supported Gibbs derivative orders have a sum of at most two");
    }
    if (salinity == 0.0) {
      return 0.0;
    }
    double x = Math.sqrt(salinity / 1000.0 / SALINITY_SCALE);
    double tau = (temperature - 273.15) / 40.0;
    double pi = (pressure * 1e5 - 101325.0) / 1e8;
    double sum = 0.0;
    for (double[] row : COEFFICIENTS) {
      int i = (int) row[0];
      int j = (int) row[1];
      int k = (int) row[2];
      if (j < temperatureOrder || k < pressureOrder) {
        continue;
      }
      double term = row[3] * (i == 1 ? x * x * Math.log(x) : Math.pow(x, i));
      for (int order = 0; order < temperatureOrder; order++) {
        term *= (j - order) / 40.0;
      }
      for (int order = 0; order < pressureOrder; order++) {
        term *= (k - order) / 1e8;
      }
      sum += term * Math.pow(tau, j - temperatureOrder) * Math.pow(pi, k - pressureOrder);
    }
    return sum;
  }

  /**
   * Evaluates a derivative of the complete industrial seawater Gibbs energy.
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   * @param salinity reference-composition salinity [g/kg solution]
   * @param temperatureOrder temperature derivative order
   * @param pressureOrder pressure derivative order
   * @return derivative in J/kg with temperature derivatives per K and pressure derivatives per Pa
   * @throws IllegalArgumentException if the state or derivative is unsupported
   */
  public static double gibbsDerivative(double temperature, double pressure, double salinity, int temperatureOrder,
      int pressureOrder) {
    return salineGibbsDerivative(temperature, pressure, salinity, temperatureOrder, pressureOrder)
        + Iapws_if97.liquidGibbsDerivative(pressure / 10.0, temperature, temperatureOrder, pressureOrder);
  }

  /**
   * Calculates enthalpy of the bulk cooling solution on the industrial reference basis.
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   * @param salinity reference-composition salinity [g/kg solution]
   * @return specific enthalpy [J/kg solution]
   * @throws IllegalArgumentException if the state is outside the cooling envelope
   */
  public static double specificEnthalpy(double temperature, double pressure, double salinity) {
    return gibbsDerivative(temperature, pressure, salinity, 0, 0)
        - temperature * gibbsDerivative(temperature, pressure, salinity, 1, 0);
  }

  /**
   * Calculates specific heat consistently as the temperature derivative of enthalpy.
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   * @param salinity reference-composition salinity [g/kg solution]
   * @return isobaric heat capacity [J/(kg solution K)]
   * @throws IllegalArgumentException if the state is outside the cooling envelope
   */
  public static double specificHeatCapacity(double temperature, double pressure, double salinity) {
    return -temperature * gibbsDerivative(temperature, pressure, salinity, 2, 0);
  }
}
