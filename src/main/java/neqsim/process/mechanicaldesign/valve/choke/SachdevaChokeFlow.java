package neqsim.process.mechanicaldesign.valve.choke;

import neqsim.thermo.system.SystemInterface;

/**
 * Sachdeva et al. (1986) mechanistic model for two-phase choke flow.
 *
 * <p>
 * This is the industry-standard mechanistic model for calculating two-phase flow through production chokes. It handles
 * both critical (choked) and subcritical flow regimes.
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
 * The mass flux is the throat mixture density times the velocity from the integrated pressure work. The critical
 * pressure ratio maximizes that flux. The two-argument critical-pressure correlation remains available as an estimate
 * when phase densities are unavailable.
 * </p>
 * <p>
 * <b>Reference:</b> Sachdeva, R., Schmidt, Z., Brill, J.P., and Blais, R.M. (1986). "Two-Phase Flow Through Chokes."
 * SPE 15657, presented at the 61st Annual Technical Conference, New Orleans, LA, October 5-8.
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
  public double calculateMassFlowRate(SystemInterface fluid, double upstreamPressure, double downstreamPressure) {
    if (upstreamPressure <= 0.0 || downstreamPressure >= upstreamPressure) {
      return 0.0;
    }
    double gasQuality = calculateGasQuality(fluid);
    if (gasQuality <= MIN_GAS_QUALITY) {
      return calculateLiquidOnlyFlow(upstreamPressure, downstreamPressure, getLiquidDensity(fluid));
    }
    Expansion expansion = expansion(fluid, upstreamPressure, gasQuality);
    double ratio = downstreamPressure / upstreamPressure;
    double effectiveRatio = ratio > 0.0 && !expansion.isBelowCriticalRatio(ratio) ? ratio : expansion.criticalRatio();
    return massFlowAtRatio(upstreamPressure, effectiveRatio, expansion);
  }

  /** {@inheritDoc} */
  @Override
  public double calculateDownstreamPressure(SystemInterface fluid, double upstreamPressure, double massFlowRate) {
    if (massFlowRate <= 0.0) {
      return upstreamPressure;
    }
    double gasQuality = calculateGasQuality(fluid);
    if (gasQuality <= MIN_GAS_QUALITY) {
      double capacity = dischargeCoefficient * getChokeArea();
      if (capacity <= 0.0) {
        return 0.0;
      }
      double massFlux = massFlowRate / capacity;
      return Math.max(0.0, upstreamPressure - massFlux * massFlux / (2.0 * getLiquidDensity(fluid)));
    }

    Expansion expansion = expansion(fluid, upstreamPressure, gasQuality);
    double lower = expansion.criticalRatio();
    if (massFlowRate >= massFlowAtRatio(upstreamPressure, lower, expansion)) {
      return upstreamPressure * lower;
    }
    double upper = 1.0;
    for (int i = 0; i < 52; i++) {
      double middle = 0.5 * (lower + upper);
      if (massFlowAtRatio(upstreamPressure, middle, expansion) > massFlowRate) {
        lower = middle;
      } else {
        upper = middle;
      }
    }
    return upstreamPressure * (0.5 * (lower + upper));
  }

  /** {@inheritDoc} */
  @Override
  protected double calculateFluidCriticalPressureRatio(SystemInterface fluid, double upstreamPressure) {
    double gasQuality = calculateGasQuality(fluid);
    // The incompressible branch has no sonic plateau.
    return gasQuality <= MIN_GAS_QUALITY ? 0.0 : expansion(fluid, upstreamPressure, gasQuality).criticalRatio();
  }

  /**
   * Creates the pressure-work law for the upstream phase state.
   *
   * @param fluid upstream fluid
   * @param upstreamPressure upstream pressure in Pa
   * @param gasQuality upstream gas mass fraction
   * @return expansion law
   */
  private Expansion expansion(SystemInterface fluid, double upstreamPressure, double gasQuality) {
    double gasDensity = getGasDensity(fluid, upstreamPressure);
    if (gasQuality >= MAX_GAS_QUALITY) {
      return new Expansion(1.0 / gasDensity, 0.0, fluid.getGamma2());
    }
    return new Expansion(gasQuality / gasDensity, (1.0 - gasQuality) / getLiquidDensity(fluid), polytropicExponent);
  }

  /**
   * Calculates mass rate from pressure work and specific volume at the throat.
   *
   * @param upstreamPressure upstream pressure in Pa
   * @param ratio throat-to-upstream pressure ratio
   * @param expansion phase-volume and pressure-work law
   * @return mass rate in kg/s
   */
  private double massFlowAtRatio(double upstreamPressure, double ratio, Expansion expansion) {
    double gasVolume = expansion.gasVolume(ratio);
    return dischargeCoefficient * getChokeArea() * Math.sqrt(2.0 * upstreamPressure * expansion.work(ratio))
        / (gasVolume + expansion.liquidVolume);
  }

  /**
   * Homogeneous specific volumes and pressure work for a frozen gas mass fraction.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class Expansion {
    private final double inletGasVolume;
    private final double liquidVolume;
    private final double exponent;

    /**
     * Sets the mass-weighted phase specific volumes and gas expansion exponent.
     *
     * @param inletGasVolume upstream gas contribution in m3/kg of mixture
     * @param liquidVolume constant liquid contribution in m3/kg of mixture
     * @param exponent polytropic exponent
     */
    private Expansion(double inletGasVolume, double liquidVolume, double exponent) {
      this.inletGasVolume = inletGasVolume;
      this.liquidVolume = liquidVolume;
      this.exponent = exponent;
    }

    /**
     * Gas contribution to the mixture specific volume at the throat.
     *
     * @param ratio throat-to-upstream pressure ratio
     * @return gas contribution in m3/kg of mixture
     */
    private double gasVolume(double ratio) {
      return inletGasVolume * Math.pow(ratio, -1.0 / exponent);
    }

    /**
     * Pressure work divided by upstream pressure, including the exponent-one limit.
     *
     * @param ratio throat-to-upstream pressure ratio
     * @return integral of mixture specific volume from throat to upstream pressure ratio
     */
    private double work(double ratio) {
      double power = 1.0 - 1.0 / exponent;
      double logRatio = Math.log(ratio);
      double gasWork = power == 0.0 ? -logRatio : -Math.expm1(power * logRatio) / power;
      return liquidVolume * (1.0 - ratio) + inletGasVolume * gasWork;
    }

    /**
     * Tests the sign of the derivative of the squared flux, proportional to work divided by volume squared.
     *
     * @param ratio throat-to-upstream pressure ratio
     * @return whether increasing the ratio increases the flux
     */
    private boolean isBelowCriticalRatio(double ratio) {
      double gas = gasVolume(ratio);
      double volume = gas + liquidVolume;
      return 2.0 * work(ratio) * (gas / volume) > exponent * ratio * volume;
    }

    /**
     * Finds the pressure ratio at the maximum homogeneous throat mass flux.
     *
     * @return critical pressure ratio
     */
    private double criticalRatio() {
      if (liquidVolume == 0.0) {
        double power = 1.0 - 1.0 / exponent;
        return power == 0.0 ? Math.exp(-0.5) : Math.exp(-Math.log1p(0.5 * (exponent - 1.0)) / power);
      }

      double lower = 0.0;
      double upper = 1.0;
      for (int i = 0; i < 48; i++) {
        double ratio = 0.5 * (lower + upper);
        if (isBelowCriticalRatio(ratio)) {
          lower = ratio;
        } else {
          upper = ratio;
        }
      }
      return 0.5 * (lower + upper);
    }
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
      return Math.pow(2.0 / (specificHeatRatio + 1.0), specificHeatRatio / (specificHeatRatio - 1.0));
    }

    // Clamp gas quality for correlation validity
    double xg = Math.max(0.02, Math.min(0.98, gasQuality));

    // Sachdeva correlation
    double yCritical = 0.5847 - 0.0227 * Math.log(xg);

    // Ensure physically reasonable bounds
    return Math.max(0.3, Math.min(0.9, yCritical));
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
   * @param upstreamPressure upstream pressure in Pa
   * @param downstreamPressure downstream pressure in Pa
   * @param rhoLiquid liquid density in kg/m3
   * @return mass flow rate in kg/s
   */
  private double calculateLiquidOnlyFlow(double upstreamPressure, double downstreamPressure, double rhoLiquid) {
    return dischargeCoefficient * getChokeArea() * Math.sqrt(2.0 * rhoLiquid * (upstreamPressure - downstreamPressure));
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
  public double calculateVariableDischargeCoefficient(double reynoldsNumber, double gasVoidFraction) {
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
