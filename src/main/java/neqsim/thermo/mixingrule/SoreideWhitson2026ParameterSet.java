package neqsim.thermo.mixingrule;

import java.util.Locale;

/**
 * Burgoyne-Nielsen (2026) drop-in binary-interaction parameters for the Soreide-Whitson model.
 *
 * <p>
 * The aqueous coefficients use the authors' November 2026 coupled-equilibrium refit (procedure 1). The correlations use
 * the original Soreide-Whitson water alpha function and the regression's fixed critical temperatures for reduced
 * temperature. The published non-aqueous BIPs are from Fluid Phase Equilibria 114824 (2026),
 * doi:10.1016/j.fluid.2026.114824. See <a href=
 * "https://github.com/mwburgoyne/SW_Framework_Refresh/blob/procedure1-2026-11/procedure1_refit/results/p1_params.json">
 * the tagged coefficient source</a>.
 * </p>
 */
final class SoreideWhitson2026ParameterSet {
  private static final double CO2_LINEAR = 0.009322719516912797;
  private static final double CO2_QUADRATIC = -1.977702057788579e-5;
  private static final double CO2_CUBIC = 1.527732067553674e-8;
  private static final double H2S_INVERSE_TEMPERATURE = -82.78098013402487;
  private static final double H2S_EXPONENTIAL_SCALE = 1739490.5143550164;
  private static final double H2S_EXPONENTIAL_TEMPERATURE = 8585.063329478664;
  private static final double N2_SLOPE = 0.003295272503921554;

  private SoreideWhitson2026ParameterSet() {
  }

  /** Supported water-gas pairs and their salinity and non-aqueous parameters. */
  private enum Gas {
    CO2(304.20, 0.0408481264767325, -0.07898609940571702, 0.050275716341630625, 0.006766107309550113,
        -0.007355686498441823, 0.1896),
    H2S(373.20, 0.04743797774228006, -0.09161393978798885, 0.049823955842864476, 0.0, 0.0, 0.1610),
    METHANE(190.60, 0.1343908999845734, -0.13425828898747677, 0.040842761446578946, 0.0, 0.0, 0.4850),
    NITROGEN(126.10, 0.2194835079768253, -0.14859950898753865, 0.030616725180758284, 0.0, 0.0, 0.4778),
    HYDROGEN(33.145, 0.37671225100706046, -0.06473044235781623, 0.0031554140401859033, 0.0, 0.0, 0.4680),
    ETHANE(305.40, 0.07776955167556454, -0.12336029130715784, 0.06258661601243726, 0.0, 0.0, 0.4920),
    PROPANE(369.80, 0.05805669163435654, -0.11105470117150809, 0.0692266175055278, 0.0, 0.0, 0.5525),
    N_BUTANE(425.20, 0.04564160593297639, -0.10008931089682649, 0.07292670218334689, 0.0, 0.0, 0.5091);

    private final double criticalTemperature;
    private final double salinityA0;
    private final double salinityA1;
    private final double salinityA2;
    private final double salinityB0;
    private final double salinityB1;
    private final double nonAqueousKij;

    Gas(double criticalTemperature, double salinityA0, double salinityA1, double salinityA2, double salinityB0,
        double salinityB1, double nonAqueousKij) {
      this.criticalTemperature = criticalTemperature;
      this.salinityA0 = salinityA0;
      this.salinityA1 = salinityA1;
      this.salinityA2 = salinityA2;
      this.salinityB0 = salinityB0;
      this.salinityB1 = salinityB1;
      this.nonAqueousKij = nonAqueousKij;
    }
  }

  static boolean supportsWaterGasPair(String firstComponent, String secondComponent) {
    return gasFromPair(firstComponent, secondComponent) != null;
  }

  static double aqueousKij(String firstComponent, String secondComponent, double temperature,
      double salinityConcentration) {
    Gas gas = requireGasFromPair(firstComponent, secondComponent);
    return freshwaterKij(gas, temperature) + salinityCorrection(gas, temperature, salinityConcentration);
  }

  static double aqueousKijdT(String firstComponent, String secondComponent, double temperature,
      double salinityConcentration) {
    Gas gas = requireGasFromPair(firstComponent, secondComponent);
    double reducedTemperature = temperature / gas.criticalTemperature;
    double salinityDerivative = (gas.salinityA1 + 2.0 * gas.salinityA2 * reducedTemperature) * salinityConcentration
        / gas.criticalTemperature
        + gas.salinityB1 * salinityConcentration * salinityConcentration / gas.criticalTemperature;
    return freshwaterKijdT(gas, temperature) + salinityDerivative;
  }

  static double aqueousKijdTdT(String firstComponent, String secondComponent, double temperature,
      double salinityConcentration) {
    Gas gas = requireGasFromPair(firstComponent, secondComponent);
    double salinitySecondDerivative = 2.0 * gas.salinityA2 * salinityConcentration
        / (gas.criticalTemperature * gas.criticalTemperature);
    return freshwaterKijdTdT(gas, temperature) + salinitySecondDerivative;
  }

  static double nonAqueousKij(String firstComponent, String secondComponent) {
    return requireGasFromPair(firstComponent, secondComponent).nonAqueousKij;
  }

  private static double freshwaterKij(Gas gas, double temperature) {
    double reducedTemperature = temperature / gas.criticalTemperature;
    switch (gas) {
    case CO2:
      return -1.5502105835458762 + CO2_LINEAR * temperature + CO2_QUADRATIC * temperature * temperature
          + CO2_CUBIC * temperature * temperature * temperature;
    case H2S:
      return H2S_INVERSE_TEMPERATURE / temperature
          + H2S_EXPONENTIAL_SCALE * Math.exp(-H2S_EXPONENTIAL_TEMPERATURE / temperature) + 0.2518306228082515;
    case METHANE:
      return rational(reducedTemperature, -2.1976782374118784, 0.7150153669761119, 0.8945029639230546);
    case NITROGEN:
      return -1.6210963967981318 + N2_SLOPE * temperature;
    case HYDROGEN:
      return rational(reducedTemperature, -16.019825767516267, 0.7312362834651882, 0.6813619680172914);
    case ETHANE:
      return rational(reducedTemperature, -1.271347888293827, -0.02927136624699385, 1.6371834535180028);
    case PROPANE:
      return rational(reducedTemperature, -1.1567778736282177, 0.2089459674695665, 1.81066287107836);
    case N_BUTANE:
      double omega = 0.1931;
      double a0 = 1.1120 - 1.7369 * Math.pow(omega, -0.1);
      double a1 = 1.1001 + 0.8360 * omega;
      double a2 = -0.15742 - 1.0988 * omega;
      return a0 + a1 * reducedTemperature + a2 * reducedTemperature * reducedTemperature;
    default:
      throw new IllegalStateException("Unsupported Burgoyne-Nielsen gas: " + gas);
    }
  }

  private static double freshwaterKijdT(Gas gas, double temperature) {
    double reducedTemperature = temperature / gas.criticalTemperature;
    switch (gas) {
    case CO2:
      return CO2_LINEAR + 2.0 * CO2_QUADRATIC * temperature + 3.0 * CO2_CUBIC * temperature * temperature;
    case H2S:
      return -H2S_INVERSE_TEMPERATURE / (temperature * temperature)
          + H2S_EXPONENTIAL_SCALE * Math.exp(-H2S_EXPONENTIAL_TEMPERATURE / temperature) * H2S_EXPONENTIAL_TEMPERATURE
              / (temperature * temperature);
    case METHANE:
      return rationalFirstDerivative(reducedTemperature, -2.1976782374118784, 0.7150153669761119, 0.8945029639230546)
          / gas.criticalTemperature;
    case NITROGEN:
      return N2_SLOPE;
    case HYDROGEN:
      return rationalFirstDerivative(reducedTemperature, -16.019825767516267, 0.7312362834651882, 0.6813619680172914)
          / gas.criticalTemperature;
    case ETHANE:
      return rationalFirstDerivative(reducedTemperature, -1.271347888293827, -0.02927136624699385, 1.6371834535180028)
          / gas.criticalTemperature;
    case PROPANE:
      return rationalFirstDerivative(reducedTemperature, -1.1567778736282177, 0.2089459674695665, 1.81066287107836)
          / gas.criticalTemperature;
    case N_BUTANE:
      double omega = 0.1931;
      double a1 = 1.1001 + 0.8360 * omega;
      double a2 = -0.15742 - 1.0988 * omega;
      return (a1 + 2.0 * a2 * reducedTemperature) / gas.criticalTemperature;
    default:
      throw new IllegalStateException("Unsupported Burgoyne-Nielsen gas: " + gas);
    }
  }

  private static double freshwaterKijdTdT(Gas gas, double temperature) {
    double reducedTemperature = temperature / gas.criticalTemperature;
    switch (gas) {
    case CO2:
      return 2.0 * CO2_QUADRATIC + 6.0 * CO2_CUBIC * temperature;
    case H2S:
      double exponential = Math.exp(-H2S_EXPONENTIAL_TEMPERATURE / temperature);
      return 2.0 * H2S_INVERSE_TEMPERATURE / (temperature * temperature * temperature) + H2S_EXPONENTIAL_SCALE
          * exponential * (H2S_EXPONENTIAL_TEMPERATURE * H2S_EXPONENTIAL_TEMPERATURE / Math.pow(temperature, 4.0)
              - 2.0 * H2S_EXPONENTIAL_TEMPERATURE / Math.pow(temperature, 3.0));
    case METHANE:
      return rationalSecondDerivative(reducedTemperature, -2.1976782374118784, 0.7150153669761119, 0.8945029639230546)
          / (gas.criticalTemperature * gas.criticalTemperature);
    case NITROGEN:
      return 0.0;
    case HYDROGEN:
      return rationalSecondDerivative(reducedTemperature, -16.019825767516267, 0.7312362834651882, 0.6813619680172914)
          / (gas.criticalTemperature * gas.criticalTemperature);
    case ETHANE:
      return rationalSecondDerivative(reducedTemperature, -1.271347888293827, -0.02927136624699385, 1.6371834535180028)
          / (gas.criticalTemperature * gas.criticalTemperature);
    case PROPANE:
      return rationalSecondDerivative(reducedTemperature, -1.1567778736282177, 0.2089459674695665, 1.81066287107836)
          / (gas.criticalTemperature * gas.criticalTemperature);
    case N_BUTANE:
      double a2 = -0.15742 - 1.0988 * 0.1931;
      return 2.0 * a2 / (gas.criticalTemperature * gas.criticalTemperature);
    default:
      throw new IllegalStateException("Unsupported Burgoyne-Nielsen gas: " + gas);
    }
  }

  private static double salinityCorrection(Gas gas, double temperature, double salinityConcentration) {
    double reducedTemperature = temperature / gas.criticalTemperature;
    return (gas.salinityA0 + gas.salinityA1 * reducedTemperature
        + gas.salinityA2 * reducedTemperature * reducedTemperature) * salinityConcentration
        + (gas.salinityB0 + gas.salinityB1 * reducedTemperature) * salinityConcentration * salinityConcentration;
  }

  private static double rational(double reducedTemperature, double numeratorConstant, double denominatorConstant,
      double denominatorSlope) {
    return (numeratorConstant + reducedTemperature) / (denominatorConstant + denominatorSlope * reducedTemperature);
  }

  private static double rationalFirstDerivative(double reducedTemperature, double numeratorConstant,
      double denominatorConstant, double denominatorSlope) {
    double denominator = denominatorConstant + denominatorSlope * reducedTemperature;
    return (denominatorConstant - denominatorSlope * numeratorConstant) / (denominator * denominator);
  }

  private static double rationalSecondDerivative(double reducedTemperature, double numeratorConstant,
      double denominatorConstant, double denominatorSlope) {
    double denominator = denominatorConstant + denominatorSlope * reducedTemperature;
    return -2.0 * denominatorSlope * (denominatorConstant - denominatorSlope * numeratorConstant)
        / (denominator * denominator * denominator);
  }

  private static Gas requireGasFromPair(String firstComponent, String secondComponent) {
    Gas gas = gasFromPair(firstComponent, secondComponent);
    if (gas == null) {
      throw new IllegalArgumentException(
          "Unsupported Burgoyne-Nielsen water-gas pair: " + firstComponent + " / " + secondComponent);
    }
    return gas;
  }

  private static Gas gasFromPair(String firstComponent, String secondComponent) {
    if (isWater(firstComponent)) {
      return gasFromName(secondComponent);
    }
    if (isWater(secondComponent)) {
      return gasFromName(firstComponent);
    }
    return null;
  }

  private static boolean isWater(String componentName) {
    return componentName != null && (componentName.equalsIgnoreCase("water") || componentName.equalsIgnoreCase("H2O"));
  }

  private static Gas gasFromName(String componentName) {
    if (componentName == null) {
      return null;
    }
    String normalized = componentName.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    if ("co2".equals(normalized) || "carbondioxide".equals(normalized)) {
      return Gas.CO2;
    } else if ("h2s".equals(normalized) || "hydrogensulfide".equals(normalized)) {
      return Gas.H2S;
    } else if ("methane".equals(normalized) || "ch4".equals(normalized)) {
      return Gas.METHANE;
    } else if ("nitrogen".equals(normalized) || "n2".equals(normalized)) {
      return Gas.NITROGEN;
    } else if ("hydrogen".equals(normalized) || "h2".equals(normalized)) {
      return Gas.HYDROGEN;
    } else if ("ethane".equals(normalized) || "c2h6".equals(normalized)) {
      return Gas.ETHANE;
    } else if ("propane".equals(normalized) || "c3h8".equals(normalized)) {
      return Gas.PROPANE;
    } else if ("nbutane".equals(normalized) || "nc4".equals(normalized) || "nc4h10".equals(normalized)) {
      return Gas.N_BUTANE;
    }
    return null;
  }
}
