package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Thermal conductivity of pure water and steam using polynomial correlations fitted to NIST WebBook
 * reference data.
 *
 * <p>
 * Uses separate correlations for the dilute gas (steam) and liquid regions, with a density-based
 * crossover for the transition/supercritical region.
 * </p>
 *
 * <p>
 * Dilute gas (steam): quadratic polynomial in T fitted to NIST data 373-1200 K, accuracy better
 * than 2%.
 * </p>
 *
 * <p>
 * Liquid water: quadratic polynomial in T fitted to NIST data 273-450 K, accuracy better than 2%.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>NIST WebBook - Thermophysical Properties of Fluid Systems</li>
 * <li>Huber, M.L., Perkins, R.A., et al. (2012). New International Formulation for the Thermal
 * Conductivity of H2O. J. Phys. Chem. Ref. Data 41(3), 033102.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class WaterConductivityMethod extends Conductivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Density threshold for gas-like behavior [kg/m3]. Below this density, the steam polynomial is
   * used.
   */
  private static final double RHO_GAS_LIMIT = 25.0;

  /**
   * Density threshold for liquid-like behavior [kg/m3]. Above this density, the liquid polynomial
   * is used.
   */
  private static final double RHO_LIQ_LIMIT = 100.0;

  /**
   * Steam (dilute gas) thermal conductivity coefficients. lambda_steam = A0 + A1*T + A2*T^2
   * [W/(m*K)]. Fitted to NIST data for steam at 1 bar, 373-1200 K. Accuracy: better than 2%.
   */
  private static final double STEAM_A0 = 0.00153;
  private static final double STEAM_A1 = 5.20e-5;
  private static final double STEAM_A2 = 2.80e-8;

  /**
   * Liquid water thermal conductivity coefficients. lambda_liq = B0 + B1*T + B2*T^2 [W/(m*K)].
   * Fitted to NIST data for liquid water at 1 atm, 273-450 K. Accuracy: better than 2%.
   */
  private static final double LIQ_B0 = -0.2758;
  private static final double LIQ_B1 = 4.612e-3;
  private static final double LIQ_B2 = -5.545e-6;

  /**
   * Constructor for WaterConductivityMethod.
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public WaterConductivityMethod(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public WaterConductivityMethod clone() {
    WaterConductivityMethod properties = null;
    try {
      properties = (WaterConductivityMethod) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public double calcConductivity() {
    double temp = phase.getPhase().getTemperature();
    double rho = phase.getPhase().getDensity(); // kg/m3

    if (rho < RHO_GAS_LIMIT) {
      // Steam / dilute gas region
      conductivity = calcSteamConductivity(temp);
    } else if (rho > RHO_LIQ_LIMIT) {
      // Liquid region
      conductivity = calcLiquidConductivity(temp);
    } else {
      // Transition region: linear interpolation by density
      double lambdaGas = calcSteamConductivity(temp);
      double lambdaLiq = calcLiquidConductivity(temp);
      double frac = (rho - RHO_GAS_LIMIT) / (RHO_LIQ_LIMIT - RHO_GAS_LIMIT);
      conductivity = lambdaGas + frac * (lambdaLiq - lambdaGas);
    }

    if (conductivity < 1e-10) {
      conductivity = 1e-10;
    }
    return conductivity;
  }

  /**
   * Calculates thermal conductivity of steam (dilute water vapor).
   *
   * <p>
   * Polynomial in temperature fitted to NIST data. lambda = A0 + A1*T + A2*T^2 [W/(m*K)].
   * </p>
   *
   * @param temp temperature in K
   * @return thermal conductivity in W/(m*K)
   */
  private double calcSteamConductivity(double temp) {
    return STEAM_A0 + STEAM_A1 * temp + STEAM_A2 * temp * temp;
  }

  /**
   * Calculates thermal conductivity of liquid water.
   *
   * <p>
   * Polynomial in temperature fitted to NIST data. lambda = B0 + B1*T + B2*T^2 [W/(m*K)].
   * </p>
   *
   * @param temp temperature in K
   * @return thermal conductivity in W/(m*K)
   */
  private double calcLiquidConductivity(double temp) {
    return LIQ_B0 + LIQ_B1 * temp + LIQ_B2 * temp * temp;
  }
}
