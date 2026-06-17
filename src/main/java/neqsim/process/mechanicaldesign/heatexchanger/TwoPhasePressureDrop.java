package neqsim.process.mechanicaldesign.heatexchanger;

/**
 * Two-phase pressure drop correlations for heat exchanger design.
 *
 * <p>
 * Implements the Friedel (1979) correlation, the most widely-used general two-phase pressure drop
 * method for horizontal and vertical tubes. Also includes the Muller-Steinhagen and Heck (1986)
 * correlation as a simpler alternative.
 * </p>
 *
 * <p>
 * The Friedel correlation uses a two-phase friction multiplier phi_lo^2 applied to the all-liquid
 * frictional pressure drop:
 * </p>
 *
 * <pre>
 * (dP/dz)_tp = phi_lo^2 * (dP/dz)_lo
 *
 * phi_lo^2 = E + 3.24 * F * H / (Fr^0.045 * We^0.035)
 * </pre>
 *
 * <p>
 * The correlation is validated for wide ranges of flow conditions (all flow patterns except
 * stratified flow) and is recommended by HTRI as a primary method.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Friedel, L. (1979). "Improved friction pressure drop correlations for horizontal and vertical
 * two-phase pipe flow." European Two-Phase Flow Group Meeting, Ispra, Italy, Paper E2.</li>
 * <li>Muller-Steinhagen, H. and Heck, K. (1986). "A simple friction pressure drop correlation for
 * two-phase flow in pipes." Chemical Engineering and Processing, 20, 297-308.</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see ThermalDesignCalculator
 */
public final class TwoPhasePressureDrop {

  /**
   * Private constructor to prevent instantiation.
   */
  private TwoPhasePressureDrop() {}

  /**
   * Calculates the two-phase frictional pressure drop gradient using the Friedel (1979)
   * correlation.
   *
   * <p>
   * This is the recommended method for most conditions. Valid for all flow patterns except
   * stratified flow. Accuracy is typically within 30% for the recommended range.
   * </p>
   *
   * @param massFlux total mass flux G (kg/(m2*s))
   * @param vaporQuality local vapor quality x (0 to 1)
   * @param tubeID tube inner diameter (m)
   * @param liquidDensity liquid density (kg/m3)
   * @param vaporDensity vapor density (kg/m3)
   * @param liquidViscosity liquid dynamic viscosity (Pa*s)
   * @param vaporViscosity vapor dynamic viscosity (Pa*s)
   * @param surfaceTension surface tension (N/m)
   * @return frictional pressure drop gradient (Pa/m)
   */
  public static double calcFriedelGradient(double massFlux, double vaporQuality, double tubeID,
      double liquidDensity, double vaporDensity, double liquidViscosity, double vaporViscosity,
      double surfaceTension) {

    if (massFlux <= 0 || tubeID <= 0 || liquidDensity <= 0 || vaporDensity <= 0
        || liquidViscosity <= 0 || vaporViscosity <= 0) {
      return 0.0;
    }

    double x = Math.max(0.0, Math.min(1.0, vaporQuality));

    // All-liquid friction factor and pressure drop gradient
    double Re_lo = massFlux * tubeID / liquidViscosity;
    double f_lo = calcFanningFriction(Re_lo);
    double dPdz_lo = 4.0 * f_lo * massFlux * massFlux / (2.0 * liquidDensity * tubeID);

    if (x < 1e-10) {
      return dPdz_lo;
    }

    // All-vapor friction factor
    double Re_vo = massFlux * tubeID / vaporViscosity;
    double f_vo = calcFanningFriction(Re_vo);

    // Friedel parameter E
    double E = Math.pow(1.0 - x, 2) + x * x * (liquidDensity * f_vo) / (vaporDensity * f_lo);

    // Friedel parameter F
    double F = Math.pow(x, 0.78) * Math.pow(1.0 - x, 0.224);

    // Friedel parameter H
    double H = Math.pow(liquidDensity / vaporDensity, 0.91)
        * Math.pow(vaporViscosity / liquidViscosity, 0.19)
        * Math.pow(1.0 - vaporViscosity / liquidViscosity, 0.7);

    // Homogeneous density
    double rho_h = 1.0 / (x / vaporDensity + (1.0 - x) / liquidDensity);

    // Froude number
    double g = 9.81;
    double Fr = massFlux * massFlux / (rho_h * rho_h * g * tubeID);

    // Weber number
    double We =
        (surfaceTension > 0) ? massFlux * massFlux * tubeID / (rho_h * surfaceTension) : 1e6; // Large
                                                                                              // number
                                                                                              // if
                                                                                              // surface
                                                                                              // tension
                                                                                              // unknown

    // Two-phase multiplier
    double FrWeFactor = Math.pow(Math.max(Fr, 1e-10), 0.045) * Math.pow(Math.max(We, 1e-10), 0.035);
    double phi_lo_sq = E + 3.24 * F * H / FrWeFactor;

    return phi_lo_sq * dPdz_lo;
  }

  /**
   * Calculates the total two-phase frictional pressure drop over a tube length.
   *
   * @param massFlux total mass flux (kg/(m2*s))
   * @param vaporQuality vapor quality (assumed constant along length) (0 to 1)
   * @param tubeID tube inner diameter (m)
   * @param tubeLength tube length (m)
   * @param liquidDensity liquid density (kg/m3)
   * @param vaporDensity vapor density (kg/m3)
   * @param liquidViscosity liquid viscosity (Pa*s)
   * @param vaporViscosity vapor viscosity (Pa*s)
   * @param surfaceTension surface tension (N/m)
   * @return total frictional pressure drop (Pa)
   */
  public static double calcFriedelPressureDrop(double massFlux, double vaporQuality, double tubeID,
      double tubeLength, double liquidDensity, double vaporDensity, double liquidViscosity,
      double vaporViscosity, double surfaceTension) {

    return calcFriedelGradient(massFlux, vaporQuality, tubeID, liquidDensity, vaporDensity,
        liquidViscosity, vaporViscosity, surfaceTension) * tubeLength;
  }

  /**
   * Calculates the average two-phase pressure drop over a quality change using numerical
   * integration.
   *
   * <p>
   * Integrates the Friedel gradient over the quality range using Simpson's rule.
   * </p>
   *
   * @param massFlux total mass flux (kg/(m2*s))
   * @param qualityIn inlet vapor quality (0 to 1)
   * @param qualityOut outlet vapor quality (0 to 1)
   * @param tubeID tube inner diameter (m)
   * @param tubeLength tube length (m)
   * @param liquidDensity liquid density (kg/m3)
   * @param vaporDensity vapor density (kg/m3)
   * @param liquidViscosity liquid viscosity (Pa*s)
   * @param vaporViscosity vapor viscosity (Pa*s)
   * @param surfaceTension surface tension (N/m)
   * @param intervals number of integration intervals (minimum 4, must be even)
   * @return average frictional pressure drop over the tube (Pa)
   */
  public static double calcFriedelAveragePressureDrop(double massFlux, double qualityIn,
      double qualityOut, double tubeID, double tubeLength, double liquidDensity,
      double vaporDensity, double liquidViscosity, double vaporViscosity, double surfaceTension,
      int intervals) {

    if (tubeLength <= 0) {
      return 0.0;
    }

    double xIn = Math.max(0.0, Math.min(1.0, qualityIn));
    double xOut = Math.max(0.0, Math.min(1.0, qualityOut));

    if (Math.abs(xIn - xOut) < 1e-10) {
      return calcFriedelPressureDrop(massFlux, xIn, tubeID, tubeLength, liquidDensity, vaporDensity,
          liquidViscosity, vaporViscosity, surfaceTension);
    }

    int n = Math.max(4, intervals);
    if (n % 2 != 0) {
      n++;
    }

    double dx = (xOut - xIn) / n;

    // Simpson's rule on the gradient, then multiply by length
    double sum = calcFriedelGradient(massFlux, xIn, tubeID, liquidDensity, vaporDensity,
        liquidViscosity, vaporViscosity, surfaceTension)
        + calcFriedelGradient(massFlux, xOut, tubeID, liquidDensity, vaporDensity, liquidViscosity,
            vaporViscosity, surfaceTension);

    for (int i = 1; i < n; i++) {
      double x = xIn + i * dx;
      double grad = calcFriedelGradient(massFlux, x, tubeID, liquidDensity, vaporDensity,
          liquidViscosity, vaporViscosity, surfaceTension);
      sum += (i % 2 == 0) ? 2.0 * grad : 4.0 * grad;
    }

    double avgGradient = Math.abs(sum * dx / 3.0 / (xOut - xIn));
    return avgGradient * tubeLength;
  }

  /**
   * Calculates the two-phase frictional pressure drop gradient using the Muller-Steinhagen and Heck
   * (1986) correlation.
   *
   * <p>
   * A simpler alternative to Friedel that does not require surface tension:
   * </p>
   *
   * <pre>
   * (dP/dz)_tp = G_factor * (1-x)^(1/3) + B * x^3
   *
   * where G_factor = A + 2*(B-A)*x
   * A = (dP/dz)_lo (all-liquid)
   * B = (dP/dz)_vo (all-vapor)
   * </pre>
   *
   * <p>
   * Accuracy is typically within 30-50%, but the method is very robust and easy to implement.
   * </p>
   *
   * @param massFlux total mass flux G (kg/(m2*s))
   * @param vaporQuality local vapor quality (0 to 1)
   * @param tubeID tube inner diameter (m)
   * @param liquidDensity liquid density (kg/m3)
   * @param vaporDensity vapor density (kg/m3)
   * @param liquidViscosity liquid viscosity (Pa*s)
   * @param vaporViscosity vapor viscosity (Pa*s)
   * @return frictional pressure drop gradient (Pa/m)
   */
  public static double calcMullerSteinhagenHeckGradient(double massFlux, double vaporQuality,
      double tubeID, double liquidDensity, double vaporDensity, double liquidViscosity,
      double vaporViscosity) {

    if (massFlux <= 0 || tubeID <= 0 || liquidDensity <= 0 || vaporDensity <= 0
        || liquidViscosity <= 0 || vaporViscosity <= 0) {
      return 0.0;
    }

    double x = Math.max(0.0, Math.min(1.0, vaporQuality));

    // All-liquid pressure gradient
    double Re_lo = massFlux * tubeID / liquidViscosity;
    double f_lo = calcFanningFriction(Re_lo);
    double A = 4.0 * f_lo * massFlux * massFlux / (2.0 * liquidDensity * tubeID);

    // All-vapor pressure gradient
    double Re_vo = massFlux * tubeID / vaporViscosity;
    double f_vo = calcFanningFriction(Re_vo);
    double B = 4.0 * f_vo * massFlux * massFlux / (2.0 * vaporDensity * tubeID);

    // MSH correlation
    double G_factor = A + 2.0 * (B - A) * x;
    return G_factor * Math.pow(1.0 - x, 1.0 / 3.0) + B * x * x * x;
  }

  /**
   * Calculates the gravitational pressure drop component for vertical two-phase flow.
   *
   * <p>
   * Uses homogeneous void fraction:
   * </p>
   *
   * <pre>
   * alpha = 1 / (1 + (1-x)/x * rho_v/rho_l)
   * rho_tp = rho_l*(1-alpha) + rho_v*alpha
   * (dP/dz)_grav = rho_tp * g
   * </pre>
   *
   * @param vaporQuality vapor quality (0 to 1)
   * @param liquidDensity liquid density (kg/m3)
   * @param vaporDensity vapor density (kg/m3)
   * @return gravitational pressure drop gradient (Pa/m), positive for upflow
   */
  public static double calcGravitationalGradient(double vaporQuality, double liquidDensity,
      double vaporDensity) {
    if (liquidDensity <= 0 || vaporDensity <= 0) {
      return 0.0;
    }

    double x = Math.max(0.0, Math.min(1.0, vaporQuality));

    // Homogeneous void fraction
    double alpha;
    if (x < 1e-10) {
      alpha = 0.0;
    } else if (x > 1.0 - 1e-10) {
      alpha = 1.0;
    } else {
      alpha = 1.0 / (1.0 + (1.0 - x) / x * vaporDensity / liquidDensity);
    }

    double rho_tp = liquidDensity * (1.0 - alpha) + vaporDensity * alpha;
    double g = 9.81;
    return rho_tp * g;
  }

  /**
   * Calculates the acceleration pressure drop component due to quality change.
   *
   * <pre>
   * dP_accel = G^2 * [x_out^2/(alpha_out*rho_v) + (1-x_out)^2/((1-alpha_out)*rho_l)
   *          - x_in^2/(alpha_in*rho_v) - (1-x_in)^2/((1-alpha_in)*rho_l)]
   * </pre>
   *
   * @param massFlux total mass flux (kg/(m2*s))
   * @param qualityIn inlet vapor quality (0 to 1)
   * @param qualityOut outlet vapor quality (0 to 1)
   * @param liquidDensity liquid density (kg/m3)
   * @param vaporDensity vapor density (kg/m3)
   * @return acceleration pressure drop (Pa)
   */
  public static double calcAccelerationPressureDrop(double massFlux, double qualityIn,
      double qualityOut, double liquidDensity, double vaporDensity) {

    if (massFlux <= 0 || liquidDensity <= 0 || vaporDensity <= 0) {
      return 0.0;
    }

    double momentumFlux_out = calcMomentumFlux(qualityOut, liquidDensity, vaporDensity);
    double momentumFlux_in = calcMomentumFlux(qualityIn, liquidDensity, vaporDensity);

    return massFlux * massFlux * (momentumFlux_out - momentumFlux_in);
  }

  /**
   * Calculates the momentum flux term for a given quality.
   *
   * @param quality vapor quality (0 to 1)
   * @param liquidDensity liquid density (kg/m3)
   * @param vaporDensity vapor density (kg/m3)
   * @return momentum flux term (m3/kg)
   */
  private static double calcMomentumFlux(double quality, double liquidDensity,
      double vaporDensity) {
    double x = Math.max(0.0, Math.min(1.0, quality));

    if (x < 1e-10) {
      return 1.0 / liquidDensity;
    }
    if (x > 1.0 - 1e-10) {
      return 1.0 / vaporDensity;
    }

    // Homogeneous void fraction
    double alpha = 1.0 / (1.0 + (1.0 - x) / x * vaporDensity / liquidDensity);

    double liquidTerm = Math.pow(1.0 - x, 2) / ((1.0 - alpha) * liquidDensity);
    double vaporTerm = x * x / (alpha * vaporDensity);

    return liquidTerm + vaporTerm;
  }

  /**
   * Calculates the Fanning friction factor for smooth tubes.
   *
   * <p>
   * Uses Blasius for turbulent flow (Re &gt; 2300) and 16/Re for laminar.
   * </p>
   *
   * @param Re Reynolds number
   * @return Fanning friction factor
   */
  static double calcFanningFriction(double Re) {
    if (Re <= 0) {
      return 0.0;
    }
    if (Re < 2300) {
      return 16.0 / Re;
    }
    // Blasius correlation
    return 0.079 / Math.pow(Re, 0.25);
  }
}
