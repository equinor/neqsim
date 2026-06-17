package neqsim.process.mechanicaldesign.heatexchanger;

/**
 * Shah condensation heat transfer correlation for in-tube and shell-side condensation.
 *
 * <p>
 * Implements the Shah (1979, 2009) correlation for film condensation inside horizontal tubes. This
 * is one of the most widely used correlations for condensation of pure fluids and mixtures in
 * horizontal tubes, validated against a large experimental database spanning refrigerants,
 * hydrocarbons, water, and organic fluids.
 * </p>
 *
 * <p>
 * The Shah correlation computes the local condensation coefficient as a function of vapor quality,
 * reduced pressure, and liquid-only Reynolds number:
 * </p>
 *
 * <pre>
 * h_cond = h_lo * [(1 - x)^0.8 + 3.8 * x^0.76 * (1 - x)^0.04 / P_r^0.38]
 * </pre>
 *
 * <p>
 * where h_lo is the liquid-only heat transfer coefficient (all mass flowing as liquid) computed
 * from Dittus-Boelter or Gnielinski.
 * </p>
 *
 * <p>
 * Also includes the Shah (2017) enhancement for vertical tubes.
 * </p>
 *
 * <p>
 * Reference: Shah, M.M. (1979). "A general correlation for heat transfer during film condensation
 * inside pipes." Int. J. Heat Mass Transfer, 22, 547-556. Shah, M.M. (2009). "An Improved and
 * Extended General Correlation for Heat Transfer During Condensation in Plain Tubes." HVAC&amp;R
 * Research, 15(5), 889-913.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see ThermalDesignCalculator
 */
public final class ShahCondensation {

  /** Minimum valid reduced pressure for this correlation. */
  private static final double MIN_REDUCED_PRESSURE = 0.001;

  /** Maximum valid reduced pressure for this correlation. */
  private static final double MAX_REDUCED_PRESSURE = 0.44;

  /**
   * Private constructor to prevent instantiation.
   */
  private ShahCondensation() {}

  /**
   * Calculates the local condensation heat transfer coefficient using the Shah (1979) correlation.
   *
   * <p>
   * Valid for: horizontal tubes, Re_lo &gt; 350, 0.002 &lt; P_r &lt; 0.44, vapor quality 0 to 1.
   * </p>
   *
   * @param hLiquidOnly liquid-only heat transfer coefficient (W/(m2*K)), i.e., the coefficient if
   *        the total mass flow were all liquid, calculated using Dittus-Boelter or Gnielinski
   * @param vaporQuality local vapor quality (mass fraction of vapor), range 0 to 1
   * @param reducedPressure reduced pressure P/P_crit, dimensionless
   * @return local condensation heat transfer coefficient (W/(m2*K))
   */
  public static double calcLocalHTC(double hLiquidOnly, double vaporQuality,
      double reducedPressure) {
    if (hLiquidOnly <= 0 || reducedPressure <= 0) {
      return 0.0;
    }

    // Clamp quality to physical range
    double x = Math.max(0.0, Math.min(1.0, vaporQuality));
    double Pr = Math.max(MIN_REDUCED_PRESSURE, Math.min(MAX_REDUCED_PRESSURE, reducedPressure));

    // Shah correlation
    double term1 = Math.pow(1.0 - x, 0.8);
    double term2 = 3.8 * Math.pow(x, 0.76) * Math.pow(1.0 - x, 0.04) / Math.pow(Pr, 0.38);

    return hLiquidOnly * (term1 + term2);
  }

  /**
   * Calculates the liquid-only heat transfer coefficient using Dittus-Boelter correlation.
   *
   * <p>
   * This is the baseline HTC assuming all mass flows as liquid:
   * </p>
   *
   * <pre>
   * h_lo = 0.023 * (k_l / D) * Re_lo ^ 0.8 * Pr_l ^ 0.4
   * </pre>
   *
   * @param massFluxTotal total mass flux G = m_dot / A_cross (kg/(m2*s))
   * @param tubeID tube inner diameter (m)
   * @param liquidDensity liquid density (kg/m3)
   * @param liquidViscosity liquid dynamic viscosity (Pa*s)
   * @param liquidCp liquid heat capacity (J/(kg*K))
   * @param liquidConductivity liquid thermal conductivity (W/(m*K))
   * @return liquid-only heat transfer coefficient (W/(m2*K))
   */
  public static double calcLiquidOnlyHTC(double massFluxTotal, double tubeID, double liquidDensity,
      double liquidViscosity, double liquidCp, double liquidConductivity) {
    if (liquidViscosity <= 0 || liquidConductivity <= 0 || tubeID <= 0 || massFluxTotal <= 0) {
      return 0.0;
    }

    double ReLo = massFluxTotal * tubeID / liquidViscosity;
    double PrL = liquidCp * liquidViscosity / liquidConductivity;

    // Dittus-Boelter for liquid-only flow
    double NuLo = 0.023 * Math.pow(ReLo, 0.8) * Math.pow(PrL, 0.4);
    return NuLo * liquidConductivity / tubeID;
  }

  /**
   * Calculates the average condensation HTC over a quality range by numerical integration.
   *
   * <p>
   * Integrates the local Shah correlation over the quality range using Simpson's rule with the
   * specified number of intervals.
   * </p>
   *
   * @param hLiquidOnly liquid-only heat transfer coefficient (W/(m2*K))
   * @param reducedPressure reduced pressure P/P_crit
   * @param qualityIn vapor quality at inlet of zone (0 to 1)
   * @param qualityOut vapor quality at outlet of zone (0 to 1)
   * @param intervals number of integration intervals (must be even, minimum 4)
   * @return average condensation HTC over the quality range (W/(m2*K))
   */
  public static double calcAverageHTC(double hLiquidOnly, double reducedPressure, double qualityIn,
      double qualityOut, int intervals) {
    if (hLiquidOnly <= 0 || reducedPressure <= 0) {
      return 0.0;
    }

    // Ensure quality range is valid
    double xIn = Math.max(0.0, Math.min(1.0, qualityIn));
    double xOut = Math.max(0.0, Math.min(1.0, qualityOut));

    if (Math.abs(xIn - xOut) < 1e-10) {
      return calcLocalHTC(hLiquidOnly, xIn, reducedPressure);
    }

    // Use Simpson's rule
    int n = Math.max(4, intervals);
    if (n % 2 != 0) {
      n++;
    }

    double dx = (xOut - xIn) / n;
    double sum = calcLocalHTC(hLiquidOnly, xIn, reducedPressure)
        + calcLocalHTC(hLiquidOnly, xOut, reducedPressure);

    for (int i = 1; i < n; i++) {
      double x = xIn + i * dx;
      double h = calcLocalHTC(hLiquidOnly, x, reducedPressure);
      sum += (i % 2 == 0) ? 2.0 * h : 4.0 * h;
    }

    return sum * Math.abs(dx) / 3.0 / Math.abs(xOut - xIn) * Math.abs(xOut - xIn);
  }

  /**
   * Calculates the condensation HTC with gravity correction for vertical tubes (Shah 2017).
   *
   * <p>
   * For vertical downflow condensation, applies a correction based on dimensionless velocity:
   * </p>
   *
   * <pre>
   * J_g = x * G / [g * D * rho_v * (rho_l - rho_v)]^0.5
   * </pre>
   *
   * <p>
   * When J_g &lt; 0.98, gravity-dominated regime applies with Nusselt theory enhancement.
   * </p>
   *
   * @param hLiquidOnly liquid-only HTC (W/(m2*K))
   * @param vaporQuality local vapor quality (0 to 1)
   * @param reducedPressure reduced pressure P/P_crit
   * @param massFlux total mass flux (kg/(m2*s))
   * @param tubeID tube inner diameter (m)
   * @param vaporDensity vapor density (kg/m3)
   * @param liquidDensity liquid density (kg/m3)
   * @return condensation HTC for vertical tube (W/(m2*K))
   */
  public static double calcVerticalTubeHTC(double hLiquidOnly, double vaporQuality,
      double reducedPressure, double massFlux, double tubeID, double vaporDensity,
      double liquidDensity) {
    if (hLiquidOnly <= 0 || reducedPressure <= 0 || tubeID <= 0) {
      return 0.0;
    }

    double x = Math.max(0.0, Math.min(1.0, vaporQuality));

    // Dimensionless vapor velocity
    double g = 9.81;
    double denominator = Math.sqrt(g * tubeID * vaporDensity * (liquidDensity - vaporDensity));
    double Jg = (denominator > 0) ? x * massFlux / denominator : Double.MAX_VALUE;

    // Standard Shah correlation
    double hShah = calcLocalHTC(hLiquidOnly, x, reducedPressure);

    if (Jg >= 0.98) {
      // Shear-dominated: standard Shah applies
      return hShah;
    }

    // Gravity-dominated: enhanced by Nusselt-type condensation
    double Pr = Math.max(MIN_REDUCED_PRESSURE, reducedPressure);
    double hNu = 1.32 * Math.pow(Pr, -1.0 / 3.0) * hLiquidOnly;

    // Interpolate between gravity and shear regimes
    double hGrav = hNu * Math.pow(1.0 - x, 0.8);
    return Math.max(hShah, hGrav);
  }

  /**
   * Checks if operating conditions are within the validated range of the Shah correlation.
   *
   * @param reducedPressure reduced pressure P/P_crit
   * @param reLiquidOnly liquid-only Reynolds number
   * @param massFlux mass flux (kg/(m2*s))
   * @return true if within validated range, false otherwise
   */
  public static boolean isInValidRange(double reducedPressure, double reLiquidOnly,
      double massFlux) {
    if (reducedPressure < MIN_REDUCED_PRESSURE || reducedPressure > MAX_REDUCED_PRESSURE) {
      return false;
    }
    if (reLiquidOnly < 350) {
      return false;
    }
    if (massFlux < 4 || massFlux > 820) {
      return false;
    }
    return true;
  }
}
