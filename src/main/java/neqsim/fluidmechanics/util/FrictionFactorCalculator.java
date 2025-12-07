package neqsim.fluidmechanics.util;

/**
 * Utility class for calculating friction factors in pipe flow.
 *
 * <p>
 * This class provides static methods for calculating Darcy and Fanning friction factors for
 * laminar, transitional, and turbulent flow regimes. The correlations implemented include:
 * </p>
 * <ul>
 * <li>Laminar flow (Re &lt; 2300): f = 64/Re (Hagen-Poiseuille)</li>
 * <li>Transition zone (2300 &lt; Re &lt; 4000): Linear interpolation</li>
 * <li>Turbulent flow (Re &gt; 4000): Haaland equation</li>
 * </ul>
 *
 * @author Even Solbraa
 */
public final class FrictionFactorCalculator {

  /** Reynolds number at which flow becomes transitional. */
  public static final double RE_LAMINAR_LIMIT = 2300.0;

  /** Reynolds number at which flow becomes fully turbulent. */
  public static final double RE_TURBULENT_LIMIT = 4000.0;

  /** Minimum Reynolds number to avoid division by zero. */
  private static final double MIN_REYNOLDS = 1e-10;

  private FrictionFactorCalculator() {
    // Utility class - prevent instantiation
  }

  /**
   * Calculates the Darcy friction factor for pipe flow.
   *
   * <p>
   * The Darcy friction factor is used in the Darcy-Weisbach equation: ΔP = f * (L/D) * (ρV²/2)
   * </p>
   *
   * <p>
   * Note: The Darcy friction factor is 4 times the Fanning friction factor.
   * </p>
   *
   * @param reynoldsNumber the Reynolds number (dimensionless)
   * @param relativeRoughness the relative roughness ε/D (dimensionless)
   * @return the Darcy friction factor (dimensionless)
   */
  public static double calcDarcyFrictionFactor(double reynoldsNumber, double relativeRoughness) {
    if (Math.abs(reynoldsNumber) < MIN_REYNOLDS) {
      return 0.0;
    }

    double absRe = Math.abs(reynoldsNumber);

    if (absRe < RE_LAMINAR_LIMIT) {
      // Laminar flow - Hagen-Poiseuille
      return 64.0 / absRe;
    } else if (absRe < RE_TURBULENT_LIMIT) {
      // Transition zone - linear interpolation
      double fLaminar = 64.0 / RE_LAMINAR_LIMIT;
      double fTurbulent = calcHaalandFrictionFactor(RE_TURBULENT_LIMIT, relativeRoughness);
      return fLaminar + (fTurbulent - fLaminar) * (absRe - RE_LAMINAR_LIMIT)
          / (RE_TURBULENT_LIMIT - RE_LAMINAR_LIMIT);
    } else {
      // Turbulent flow - Haaland equation
      return calcHaalandFrictionFactor(absRe, relativeRoughness);
    }
  }

  /**
   * Calculates the Fanning friction factor for pipe flow.
   *
   * <p>
   * The Fanning friction factor is used in some formulations of pressure drop: τ_w = f * (ρV²/2)
   * </p>
   *
   * <p>
   * Note: The Fanning friction factor is 1/4 of the Darcy friction factor.
   * </p>
   *
   * @param reynoldsNumber the Reynolds number (dimensionless)
   * @param relativeRoughness the relative roughness ε/D (dimensionless)
   * @return the Fanning friction factor (dimensionless)
   */
  public static double calcFanningFrictionFactor(double reynoldsNumber, double relativeRoughness) {
    return calcDarcyFrictionFactor(reynoldsNumber, relativeRoughness) / 4.0;
  }

  /**
   * Calculates the friction factor using the Haaland equation.
   *
   * <p>
   * The Haaland equation is an explicit approximation to the Colebrook-White equation and is valid
   * for turbulent flow in rough pipes:
   * </p>
   *
   * <p>
   * 1/√f = -1.8 log₁₀[(ε/3.7D)^1.11 + 6.9/Re]
   * </p>
   *
   * @param reynoldsNumber the Reynolds number (dimensionless), must be positive
   * @param relativeRoughness the relative roughness ε/D (dimensionless)
   * @return the Darcy friction factor (dimensionless)
   */
  public static double calcHaalandFrictionFactor(double reynoldsNumber, double relativeRoughness) {
    double term = Math.pow(relativeRoughness / 3.7, 1.11) + 6.9 / reynoldsNumber;
    return Math.pow(1.0 / (-1.8 * Math.log10(term)), 2.0);
  }

  /**
   * Determines the flow regime based on Reynolds number.
   *
   * @param reynoldsNumber the Reynolds number (dimensionless)
   * @return the flow regime as a string: "no-flow", "laminar", "transition", or "turbulent"
   */
  public static String getFlowRegime(double reynoldsNumber) {
    double absRe = Math.abs(reynoldsNumber);
    if (absRe < MIN_REYNOLDS) {
      return "no-flow";
    } else if (absRe < RE_LAMINAR_LIMIT) {
      return "laminar";
    } else if (absRe < RE_TURBULENT_LIMIT) {
      return "transition";
    } else {
      return "turbulent";
    }
  }

  /**
   * Calculates the pressure drop per unit length using Darcy-Weisbach equation.
   *
   * @param frictionFactor the Darcy friction factor (dimensionless)
   * @param diameter the pipe inner diameter (m)
   * @param density the fluid density (kg/m³)
   * @param velocity the fluid velocity (m/s)
   * @return pressure drop per unit length (Pa/m)
   */
  public static double calcPressureDropPerLength(double frictionFactor, double diameter,
      double density, double velocity) {
    return frictionFactor * density * velocity * velocity / (2.0 * diameter);
  }
}
