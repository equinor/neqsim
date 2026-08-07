package neqsim.thermo.mixingrule;

import java.util.Locale;

/**
 * Burgoyne-Nielsen (2026) drop-in binary-interaction parameters for the Soreide-Whitson model.
 *
 * <p>
 * The correlations use the original Soreide-Whitson water alpha function. Reduced temperatures are calculated with the
 * fixed critical temperatures used for the published regressions, rather than the active NeqSim component data. See M.
 * Burgoyne and M. H. Nielsen, Fluid Phase Equilibria 114824 (2026), doi:10.1016/j.fluid.2026.114824.
 * </p>
 */
final class SoreideWhitson2026ParameterSet {
  private SoreideWhitson2026ParameterSet() {
  }

  /** Supported water-gas pairs and their salinity and non-aqueous parameters. */
  private enum Gas {
    CO2(304.20, 0.0409, -0.0807, 0.0526, 0.0079, -0.0085, 0.1896),
    H2S(373.20, 0.0341, -0.0655, 0.0376, 0.0, 0.0, 0.1610), METHANE(190.60, 0.1304, -0.1295, 0.0394, 0.0, 0.0, 0.4850),
    NITROGEN(126.10, 0.2173, -0.1468, 0.0302, 0.0, 0.0, 0.4778),
    HYDROGEN(33.145, 0.3658, -0.0625, 0.0030, 0.0, 0.0, 0.4680),
    ETHANE(305.40, 0.0813, -0.1287, 0.0646, 0.0, 0.0, 0.4920),
    PROPANE(369.80, 0.0606, -0.1165, 0.0772, 0.0, 0.0, 0.5525),
    N_BUTANE(425.20, 0.0488, -0.1072, 0.0836, 0.0, 0.0, 0.5091);

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
      return -1.5893 + 9.8873e-3 * temperature - 2.2188e-5 * temperature * temperature
          + 1.8499e-8 * temperature * temperature * temperature;
    case H2S:
      return -74.6914 / temperature + 1348.9615 * Math.exp(-4504.96 / temperature) + 0.22598;
    case METHANE:
      return rational(reducedTemperature, -2.1756, 1.0388, 0.6436);
    case NITROGEN:
      return -1.6689 + 3.441589e-3 * temperature;
    case HYDROGEN:
      return rational(reducedTemperature, -14.9412, 2.2832, 0.3893);
    case ETHANE:
      return rational(reducedTemperature, -1.2669, 0.1526, 1.4335);
    case PROPANE:
      return rational(reducedTemperature, -1.1460, 0.5760, 1.3107);
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
      return 9.8873e-3 - 2.0 * 2.2188e-5 * temperature + 3.0 * 1.8499e-8 * temperature * temperature;
    case H2S:
      return 74.6914 / (temperature * temperature)
          + 1348.9615 * Math.exp(-4504.96 / temperature) * 4504.96 / (temperature * temperature);
    case METHANE:
      return rationalFirstDerivative(reducedTemperature, -2.1756, 1.0388, 0.6436) / gas.criticalTemperature;
    case NITROGEN:
      return 3.441589e-3;
    case HYDROGEN:
      return rationalFirstDerivative(reducedTemperature, -14.9412, 2.2832, 0.3893) / gas.criticalTemperature;
    case ETHANE:
      return rationalFirstDerivative(reducedTemperature, -1.2669, 0.1526, 1.4335) / gas.criticalTemperature;
    case PROPANE:
      return rationalFirstDerivative(reducedTemperature, -1.1460, 0.5760, 1.3107) / gas.criticalTemperature;
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
      return -2.0 * 2.2188e-5 + 6.0 * 1.8499e-8 * temperature;
    case H2S:
      double exponential = Math.exp(-4504.96 / temperature);
      return -2.0 * 74.6914 / (temperature * temperature * temperature) + 1348.9615 * exponential
          * (4504.96 * 4504.96 / Math.pow(temperature, 4.0) - 2.0 * 4504.96 / Math.pow(temperature, 3.0));
    case METHANE:
      return rationalSecondDerivative(reducedTemperature, -2.1756, 1.0388, 0.6436)
          / (gas.criticalTemperature * gas.criticalTemperature);
    case NITROGEN:
      return 0.0;
    case HYDROGEN:
      return rationalSecondDerivative(reducedTemperature, -14.9412, 2.2832, 0.3893)
          / (gas.criticalTemperature * gas.criticalTemperature);
    case ETHANE:
      return rationalSecondDerivative(reducedTemperature, -1.2669, 0.1526, 1.4335)
          / (gas.criticalTemperature * gas.criticalTemperature);
    case PROPANE:
      return rationalSecondDerivative(reducedTemperature, -1.1460, 0.5760, 1.3107)
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
