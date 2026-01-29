package neqsim.process.mechanicaldesign.valve.choke;

import neqsim.thermo.system.SystemInterface;

/**
 * Sachdeva et al. (1986) mechanistic model for two-phase choke flow.
 *
 * <p>
 * This is the industry-standard mechanistic model for calculating two-phase flow through production
 * chokes. It handles both critical (choked) and subcritical flow regimes.
 * </p>
 *
 * <p>
 * The model is based on the following assumptions:
 * </p>
 * <ul>
 * <li>Homogeneous flow (no slip between phases)</li>
 * <li>Polytropic gas expansion</li>
 * <li>Incompressible liquid phase</li>
 * <li>Negligible friction losses in the choke</li>
 * <li>Thermal equilibrium between phases</li>
 * </ul>
 *
 * <p>
 * <b>Mass Flow Rate Equation:</b>
 * </p>
 * 
 * <pre>
 * m_dot = Cd * A2 * sqrt(2 * P1 * rho1 / denominator)
 * 
 * where denominator accounts for:
 * - Kinetic energy change (velocity change through restriction)
 * - Polytropic gas expansion work
 * - Liquid work against pressure gradient
 * </pre>
 *
 * <p>
 * <b>Critical Pressure Ratio Correlation:</b>
 * </p>
 * 
 * <pre>
 * y_critical = 0.5847 - 0.0227 * ln(x_g)
 * </pre>
 *
 * <p>
 * where x_g is the gas mass fraction (quality).
 * </p>
 *
 * <p>
 * <b>Reference:</b> Sachdeva, R., Schmidt, Z., Brill, J.P., and Blais, R.M. (1986). "Two-Phase Flow
 * Through Chokes." SPE 15657, presented at the 61st Annual Technical Conference, New Orleans, LA,
 * October 5-8.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class SachdevaChokeFlow extends MultiphaseChokeFlow {
  private static final long serialVersionUID = 1L;

  /** Minimum gas quality for correlation validity. */
  private static final double MIN_GAS_QUALITY = 0.001;

  /** Maximum gas quality for correlation validity. */
  private static final double MAX_GAS_QUALITY = 0.999;

  /**
   * Default constructor for SachdevaChokeFlow.
   */
  public SachdevaChokeFlow() {
    super();
    this.dischargeCoefficient = 0.84;
  }

  /**
   * Constructor with choke diameter.
   *
   * @param chokeDiameter choke throat diameter in meters
   */
  public SachdevaChokeFlow(double chokeDiameter) {
    super(chokeDiameter);
    this.dischargeCoefficient = 0.84;
  }

  /** {@inheritDoc} */
  @Override
  public double calculateMassFlowRate(SystemInterface fluid, double upstreamPressure,
      double downstreamPressure) {
    // Get fluid properties
    double gasQuality = calculateGasQuality(fluid);
    double gamma = fluid.getGamma2();

    // Determine effective pressure ratio
    double criticalRatio = calculateCriticalPressureRatio(gasQuality, gamma);
    double actualRatio = downstreamPressure / upstreamPressure;
    double effectiveRatio = Math.max(actualRatio, criticalRatio);

    // Get phase densities
    double rhoGas1 = getGasDensity(fluid, upstreamPressure);
    double rhoLiquid = getLiquidDensity(fluid);
    double rhoMix1 = calculateMixtureDensity(gasQuality, rhoGas1, rhoLiquid);

    // Handle edge cases
    if (gasQuality <= MIN_GAS_QUALITY) {
      // Pure liquid - use liquid flow equation
      return calculateLiquidOnlyFlow(fluid, upstreamPressure, downstreamPressure, rhoLiquid);
    }
    if (gasQuality >= MAX_GAS_QUALITY) {
      // Pure gas - use gas flow equation
      return calculateGasOnlyFlow(fluid, upstreamPressure, downstreamPressure, rhoGas1, gamma);
    }

    // Two-phase Sachdeva model
    double y = effectiveRatio;
    double xg = gasQuality;
    double xL = 1.0 - xg;
    double n = polytropicExponent;

    // Calculate downstream gas density (polytropic expansion)
    double rhoGas2 = rhoGas1 * Math.pow(y, 1.0 / n);

    // Area ratio term (A2/A1)^2
    double areaRatioSq = Math.pow(chokeDiameter / upstreamDiameter, 4);

    // Kinetic energy term
    double kineticTerm =
        (1.0 / (y * y) - areaRatioSq) * (xg / (rhoGas2 * rhoGas2) + xL / (rhoLiquid * rhoLiquid));

    // Work terms
    double liquidWorkTerm = 2.0 * xL / rhoLiquid * (1.0 - y);
    double gasWorkTerm = 2.0 * xg / ((n - 1.0) * rhoGas1) * (1.0 - Math.pow(y, (n - 1.0) / n));

    // Total denominator
    double denominator = kineticTerm + liquidWorkTerm + gasWorkTerm;

    if (denominator <= 0) {
      // Invalid condition - return 0 flow
      return 0.0;
    }

    // Mass flow rate
    double A2 = getChokeArea();
    double massFlowRate =
        dischargeCoefficient * A2 * Math.sqrt(2.0 * upstreamPressure * rhoMix1 / denominator);

    return massFlowRate;
  }

  /** {@inheritDoc} */
  @Override
  public double calculateDownstreamPressure(SystemInterface fluid, double upstreamPressure,
      double massFlowRate) {
    // Use bisection method to find downstream pressure
    double gasQuality = calculateGasQuality(fluid);
    double gamma = fluid.getGamma2();
    double criticalRatio = calculateCriticalPressureRatio(gasQuality, gamma);

    // Calculate mass flow at critical condition (maximum possible)
    double P2_critical = upstreamPressure * criticalRatio;
    double maxMassFlow = calculateMassFlowRate(fluid, upstreamPressure, P2_critical);

    // Check if requested flow exceeds critical flow
    if (massFlowRate >= maxMassFlow * 0.999) {
      // Flow is at or above critical - return critical pressure
      return P2_critical;
    }

    // Search for downstream pressure in subcritical range
    double P2_low = P2_critical; // Critical pressure (max flow occurs here)
    double P2_high = upstreamPressure * 0.999; // Just below upstream

    double tolerance = 1e-6;
    int maxIterations = 50;

    for (int i = 0; i < maxIterations; i++) {
      double P2_mid = (P2_low + P2_high) / 2.0;
      double calcMassFlow = calculateMassFlowRate(fluid, upstreamPressure, P2_mid);

      if (Math.abs(calcMassFlow - massFlowRate) / massFlowRate < tolerance) {
        return P2_mid;
      }

      if (calcMassFlow > massFlowRate) {
        // Calculated flow too high - need higher P2 (less pressure drop)
        P2_low = P2_mid;
      } else {
        // Calculated flow too low - need lower P2 (more pressure drop)
        P2_high = P2_mid;
      }
    }

    // Return best estimate
    return (P2_low + P2_high) / 2.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calculateCriticalPressureRatio(double gasQuality, double specificHeatRatio) {
    // Sachdeva correlation for critical pressure ratio
    // y_c = 0.5847 - 0.0227 * ln(x_g)
    // Valid for gas quality > 0.02

    if (gasQuality <= MIN_GAS_QUALITY) {
      // Near-liquid: use liquid flashing criteria (typically high)
      return 0.90;
    }
    if (gasQuality >= MAX_GAS_QUALITY) {
      // Near-gas: use isentropic gas critical ratio
      return Math.pow(2.0 / (specificHeatRatio + 1.0),
          specificHeatRatio / (specificHeatRatio - 1.0));
    }

    // Clamp gas quality for correlation validity
    double xg = Math.max(0.02, Math.min(0.98, gasQuality));

    // Sachdeva correlation
    double yCritical = 0.5847 - 0.0227 * Math.log(xg);

    // Ensure physically reasonable bounds
    return Math.max(0.3, Math.min(0.9, yCritical));
  }

  /**
   * Calculates the mixture density using mass fractions.
   *
   * @param gasQuality gas mass fraction
   * @param rhoGas gas density in kg/m3
   * @param rhoLiquid liquid density in kg/m3
   * @return mixture density in kg/m3
   */
  private double calculateMixtureDensity(double gasQuality, double rhoGas, double rhoLiquid) {
    if (gasQuality >= 1.0) {
      return rhoGas;
    }
    if (gasQuality <= 0.0) {
      return rhoLiquid;
    }
    // Homogeneous mixture density
    return 1.0 / (gasQuality / rhoGas + (1.0 - gasQuality) / rhoLiquid);
  }

  /**
   * Gets the gas density from the fluid.
   *
   * @param fluid thermodynamic system
   * @param pressure pressure in Pa
   * @return gas density in kg/m3
   */
  private double getGasDensity(SystemInterface fluid, double pressure) {
    if (fluid.hasPhaseType("gas")) {
      int gasPhaseIndex = fluid.getPhaseIndex("gas");
      return fluid.getPhase(gasPhaseIndex).getDensity("kg/m3");
    }
    // Estimate from ideal gas law
    double MW = fluid.getMolarMass("kg/mol");
    double T = fluid.getTemperature("K");
    double Z = fluid.getZ();
    return pressure * MW / (Z * 8.314 * T);
  }

  /**
   * Gets the liquid density from the fluid.
   *
   * @param fluid thermodynamic system
   * @return liquid density in kg/m3
   */
  private double getLiquidDensity(SystemInterface fluid) {
    if (fluid.hasPhaseType("oil")) {
      int oilPhaseIndex = fluid.getPhaseIndex("oil");
      return fluid.getPhase(oilPhaseIndex).getDensity("kg/m3");
    }
    if (fluid.hasPhaseType("aqueous")) {
      int waterPhaseIndex = fluid.getPhaseIndex("aqueous");
      return fluid.getPhase(waterPhaseIndex).getDensity("kg/m3");
    }
    // Default to water density
    return 1000.0;
  }

  /**
   * Calculates liquid-only flow through the choke.
   *
   * @param fluid thermodynamic system
   * @param P1 upstream pressure in Pa
   * @param P2 downstream pressure in Pa
   * @param rhoLiquid liquid density in kg/m3
   * @return mass flow rate in kg/s
   */
  private double calculateLiquidOnlyFlow(SystemInterface fluid, double P1, double P2,
      double rhoLiquid) {
    double deltaP = P1 - P2;
    if (deltaP <= 0) {
      return 0.0;
    }
    double A2 = getChokeArea();
    return dischargeCoefficient * A2 * Math.sqrt(2.0 * rhoLiquid * deltaP);
  }

  /**
   * Calculates gas-only flow through the choke.
   *
   * @param fluid thermodynamic system
   * @param P1 upstream pressure in Pa
   * @param P2 downstream pressure in Pa
   * @param rhoGas1 upstream gas density in kg/m3
   * @param gamma specific heat ratio
   * @return mass flow rate in kg/s
   */
  private double calculateGasOnlyFlow(SystemInterface fluid, double P1, double P2, double rhoGas1,
      double gamma) {
    double A2 = getChokeArea();

    // Critical pressure ratio for gas
    double criticalRatio = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
    double pressureRatio = P2 / P1;

    if (pressureRatio <= criticalRatio) {
      // Critical (choked) flow
      double criticalFactor = Math.pow(2.0 / (gamma + 1.0), (gamma + 1.0) / (2.0 * (gamma - 1.0)));
      return dischargeCoefficient * A2 * Math.sqrt(gamma * P1 * rhoGas1) * criticalFactor;
    } else {
      // Subcritical flow
      double term1 = 2.0 * gamma / (gamma - 1.0);
      double term2 =
          Math.pow(pressureRatio, 2.0 / gamma) - Math.pow(pressureRatio, (gamma + 1.0) / gamma);
      return dischargeCoefficient * A2 * Math.sqrt(term1 * P1 * rhoGas1 * term2);
    }
  }

  /**
   * Calculates the discharge coefficient based on Reynolds number and void fraction.
   *
   * <p>
   * This method provides a variable discharge coefficient that accounts for flow conditions.
   * </p>
   *
   * @param reynoldsNumber Reynolds number at choke throat
   * @param gasVoidFraction volumetric gas fraction at throat
   * @return adjusted discharge coefficient
   */
  public double calculateVariableDischargeCoefficient(double reynoldsNumber,
      double gasVoidFraction) {
    double Cd_base = 0.84;

    // Reynolds number correction (turbulent flow assumed for Re > 10000)
    double Re_correction = 1.0;
    if (reynoldsNumber > 1000 && reynoldsNumber < 100000) {
      Re_correction = 1.0 - 0.05 / Math.sqrt(reynoldsNumber / 10000.0);
    }

    // Two-phase correction (lower Cd for liquid-rich flows)
    double tp_correction = 1.0 - 0.1 * (1.0 - gasVoidFraction);

    return Cd_base * Re_correction * tp_correction;
  }

  /** {@inheritDoc} */
  @Override
  public String getModelName() {
    return "Sachdeva et al. (1986)";
  }
}
