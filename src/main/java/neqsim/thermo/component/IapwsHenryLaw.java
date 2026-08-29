package neqsim.thermo.component;

import java.util.Locale;

/**
 * IAPWS G7-04 Henry-law correlation for common gases at infinite dilution in liquid H2O.
 *
 * <p>The correlation returns the mole-fraction Henry constant
 * {@code kH = lim(x -> 0) f / x} on the water saturation boundary. It does not
 * include a Poynting correction or electrolyte salting-out interaction.</p>
 *
 * <p>Coefficients and species-specific fitted ranges are from IAPWS G7-04,
 * based on Fernandez-Prini et al., J. Phys. Chem. Ref. Data 32 (2003),
 * doi:10.1063/1.1564818. The IAPWS guideline permits reproduction with
 * attribution.</p>
 */
public final class IapwsHenryLaw {
  /** Water molar mass in kg/mol for converting mole-fraction to molality standard state. */
  public static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.01801528;

  private static final double WATER_CRITICAL_TEMPERATURE = 647.096;
  private static final double WATER_CRITICAL_PRESSURE_MPA = 22.064;
  private static final double CORRELATION_MINIMUM_TEMPERATURE = 273.15;
  private static final double[] VAPOR_PRESSURE_A = {
      -7.85951783, 1.84408259, -11.7866497, 22.6807411, -15.9618719, 1.80122502};
  private static final double[] VAPOR_PRESSURE_B = {1.0, 1.5, 3.0, 3.5, 4.0, 7.5};

  private static final GasData HE = new GasData("He", -3.52839, 7.12983, 4.47770, 273.21, 553.18);
  private static final GasData NE = new GasData("Ne", -3.18301, 5.31448, 5.43774, 273.20, 543.36);
  private static final GasData AR = new GasData("Ar", -8.40954, 4.29587, 10.52779, 273.19, 568.36);
  private static final GasData KR = new GasData("Kr", -8.97358, 3.61508, 11.29963, 273.19, 525.56);
  private static final GasData XE = new GasData("Xe", -14.21635, 4.00041, 15.60999, 273.22, 574.85);
  private static final GasData H2 = new GasData("H2", -4.73284, 6.08954, 6.06066, 273.15, 636.09);
  private static final GasData N2 = new GasData("N2", -9.67578, 4.72162, 11.70585, 278.12, 636.46);
  private static final GasData O2 = new GasData("O2", -9.44833, 4.43822, 11.42005, 274.15, 616.52);
  private static final GasData CO = new GasData("CO", -10.52862, 5.13259, 12.01421, 278.15, 588.67);
  private static final GasData CO2 = new GasData("CO2", -8.55445, 4.01195, 9.52345, 274.19, 642.66);
  private static final GasData H2S = new GasData("H2S", -4.51499, 5.23538, 4.42126, 273.15, 533.09);
  private static final GasData CH4 = new GasData("CH4", -10.44708, 4.66491, 12.12986, 275.46, 633.11);
  private static final GasData C2H6 = new GasData("C2H6", -19.67563, 4.51222, 20.62567, 275.44, 473.46);
  private static final GasData SF6 = new GasData("SF6", -16.56118, 2.15289, 20.35440, 283.14, 505.55);

  /** Correlation qualification status for a species and temperature. */
  public enum Status {
    /** Temperature is inside the species-specific fitted data range. */
    WITHIN_FITTED_RANGE,
    /** Formula is defined, but temperature is outside the species-specific fitted data range. */
    GUIDELINE_EXTRAPOLATION,
    /** Species is not included in the IAPWS H2O correlation. */
    UNSUPPORTED_SPECIES,
    /** Temperature is outside the liquid-water correlation domain. */
    OUTSIDE_CORRELATION_DOMAIN
  }

  /** Immutable qualification result suitable for Java and JPype diagnostics. */
  public static final class Assessment {
    private final String canonicalGasName;
    private final double minimumFittedTemperature;
    private final double maximumFittedTemperature;
    private final Status status;

    private Assessment(GasData gas, Status status) {
      this.canonicalGasName = gas == null ? "" : gas.name;
      this.minimumFittedTemperature = gas == null ? Double.NaN : gas.minimumTemperature;
      this.maximumFittedTemperature = gas == null ? Double.NaN : gas.maximumTemperature;
      this.status = status;
    }

    /**
     * @return canonical IAPWS gas symbol, or an empty string for an unsupported species
     */
    public String getCanonicalGasName() {
      return canonicalGasName;
    }

    /**
     * @return lower fitted-data temperature in K, or NaN for an unsupported species
     */
    public double getMinimumFittedTemperature() {
      return minimumFittedTemperature;
    }

    /**
     * @return upper fitted-data temperature in K, or NaN for an unsupported species
     */
    public double getMaximumFittedTemperature() {
      return maximumFittedTemperature;
    }

    /**
     * @return correlation qualification status
     */
    public Status getStatus() {
      return status;
    }

    /**
     * @return true when the species exists in the IAPWS H2O table
     */
    public boolean isSupportedSpecies() {
      return status != Status.UNSUPPORTED_SPECIES;
    }

    /**
     * @return true when the formula is numerically defined at the requested temperature
     */
    public boolean isUsable() {
      return status == Status.WITHIN_FITTED_RANGE || status == Status.GUIDELINE_EXTRAPOLATION;
    }
  }

  private static final class GasData {
    private final String name;
    private final double a;
    private final double b;
    private final double c;
    private final double minimumTemperature;
    private final double maximumTemperature;

    private GasData(String name, double a, double b, double c, double minimumTemperature,
        double maximumTemperature) {
      this.name = name;
      this.a = a;
      this.b = b;
      this.c = c;
      this.minimumTemperature = minimumTemperature;
      this.maximumTemperature = maximumTemperature;
    }
  }

  private IapwsHenryLaw() {}

  /**
   * Assess whether the IAPWS H2O correlation supports a component at a temperature.
   *
   * @param componentName NeqSim component name or IAPWS formula
   * @param temperature temperature in K
   * @return immutable assessment with fitted range and status
   */
  public static Assessment assess(String componentName, double temperature) {
    GasData gas = findGas(componentName);
    if (gas == null) {
      return new Assessment(null, Status.UNSUPPORTED_SPECIES);
    }
    if (!Double.isFinite(temperature) || temperature < CORRELATION_MINIMUM_TEMPERATURE
        || temperature >= WATER_CRITICAL_TEMPERATURE) {
      return new Assessment(gas, Status.OUTSIDE_CORRELATION_DOMAIN);
    }
    if (temperature < gas.minimumTemperature || temperature > gas.maximumTemperature) {
      return new Assessment(gas, Status.GUIDELINE_EXTRAPOLATION);
    }
    return new Assessment(gas, Status.WITHIN_FITTED_RANGE);
  }

  /**
   * Evaluate the IAPWS mole-fraction Henry constant.
   *
   * @param componentName NeqSim component name or IAPWS formula
   * @param temperature temperature in K
   * @return kH in bar
   * @throws IllegalArgumentException for an unsupported species or temperature outside the domain
   */
  public static double getHenryCoefficientBar(String componentName, double temperature) {
    GasData gas = requireUsable(componentName, temperature);
    double reducedTemperature = temperature / WATER_CRITICAL_TEMPERATURE;
    double tau = 1.0 - reducedTemperature;
    double logPressureMpa = Math.log(WATER_CRITICAL_PRESSURE_MPA)
        + vaporPressureSeries(tau) / reducedTemperature;
    double logHenryMpa = logPressureMpa + gas.a / reducedTemperature
        + gas.b * Math.pow(tau, 0.355) / reducedTemperature
        + gas.c * Math.pow(reducedTemperature, -0.41) * Math.exp(tau);
    return Math.exp(logHenryMpa) * 10.0;
  }

  /**
   * Evaluate the logarithmic temperature derivative of the IAPWS Henry constant.
   *
   * @param componentName NeqSim component name or IAPWS formula
   * @param temperature temperature in K
   * @return d(ln(kH))/dT in 1/K
   * @throws IllegalArgumentException for an unsupported species or temperature outside the domain
   */
  public static double getLnHenryCoefficientTemperatureDerivative(String componentName,
      double temperature) {
    GasData gas = requireUsable(componentName, temperature);
    double tr = temperature / WATER_CRITICAL_TEMPERATURE;
    double tau = 1.0 - tr;
    double series = vaporPressureSeries(tau);
    double seriesDerivative = 0.0;
    for (int i = 0; i < VAPOR_PRESSURE_A.length; i++) {
      seriesDerivative -= VAPOR_PRESSURE_A[i] * VAPOR_PRESSURE_B[i]
          * Math.pow(tau, VAPOR_PRESSURE_B[i] - 1.0);
    }
    double derivativeByTr = seriesDerivative / tr - series / (tr * tr)
        - gas.a / (tr * tr)
        + gas.b * (-0.355 * Math.pow(tau, -0.645) / tr
            - Math.pow(tau, 0.355) / (tr * tr));
    double finalTerm = gas.c * Math.pow(tr, -0.41) * Math.exp(tau);
    derivativeByTr += finalTerm * (-0.41 / tr - 1.0);
    return derivativeByTr / WATER_CRITICAL_TEMPERATURE;
  }

  private static double vaporPressureSeries(double tau) {
    double sum = 0.0;
    for (int i = 0; i < VAPOR_PRESSURE_A.length; i++) {
      sum += VAPOR_PRESSURE_A[i] * Math.pow(tau, VAPOR_PRESSURE_B[i]);
    }
    return sum;
  }

  private static GasData requireUsable(String componentName, double temperature) {
    Assessment assessment = assess(componentName, temperature);
    if (!assessment.isUsable()) {
      throw new IllegalArgumentException("IAPWS Henry correlation is not usable for component '"
          + componentName + "' at " + temperature + " K: " + assessment.getStatus());
    }
    return findGas(componentName);
  }

  private static GasData findGas(String componentName) {
    if (componentName == null) {
      return null;
    }
    String name = componentName.trim().toLowerCase(Locale.ROOT);
    switch (name) {
      case "he":
      case "helium":
        return HE;
      case "ne":
      case "neon":
        return NE;
      case "ar":
      case "argon":
        return AR;
      case "kr":
      case "krypton":
        return KR;
      case "xe":
      case "xenon":
        return XE;
      case "h2":
      case "hydrogen":
        return H2;
      case "n2":
      case "nitrogen":
        return N2;
      case "o2":
      case "oxygen":
        return O2;
      case "co":
      case "carbon monoxide":
        return CO;
      case "co2":
      case "carbon dioxide":
        return CO2;
      case "h2s":
      case "hydrogen sulfide":
      case "hydrogen sulphide":
        return H2S;
      case "ch4":
      case "methane":
        return CH4;
      case "c2h6":
      case "ethane":
        return C2H6;
      case "sf6":
      case "sulfur hexafluoride":
      case "sulphur hexafluoride":
        return SF6;
      default:
        return null;
    }
  }
}
