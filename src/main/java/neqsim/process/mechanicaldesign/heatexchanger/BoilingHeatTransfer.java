package neqsim.process.mechanicaldesign.heatexchanger;

/**
 * Boiling heat transfer correlations for nucleate and forced-convective boiling.
 *
 * <p>
 * Implements two widely-used correlations for flow boiling inside tubes:
 * </p>
 * <ul>
 * <li><b>Chen (1966):</b> Superposition model combining forced-convective and nucleate boiling with
 * a suppression factor S and enhancement factor F.</li>
 * <li><b>Gungor-Winterton (1986, 1987):</b> Enhanced Chen-type correlation with improved
 * enhancement and suppression factor fitting against a larger database (3,600+ data points).</li>
 * </ul>
 *
 * <p>
 * The Chen correlation computes:
 * </p>
 *
 * <pre>
 * h_tp = h_mac * F + h_mic * S
 * </pre>
 *
 * <p>
 * where h_mac is the forced-convective (macroscopic) component from Dittus-Boelter at liquid-only
 * conditions, and h_mic is the nucleate boiling (microscopic) component from Forster-Zuber. F
 * enhances the convective part due to two-phase flow acceleration, and S suppresses nucleate
 * boiling at high mixture velocities.
 * </p>
 *
 * <p>
 * The Gungor-Winterton (1987) simplified correlation computes:
 * </p>
 *
 * <pre>
 * h_tp = h_l * E_gw
 * E_gw = 1 + 3000 * Bo^0.86 + 1.12 * (x/(1-x))^0.75 * (rho_l/rho_v)^0.41
 * </pre>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Chen, J.C. (1966). "Correlation for boiling heat transfer to saturated fluids in convective
 * flow." I&amp;EC Process Design and Development, 5(3), 322-329.</li>
 * <li>Gungor, K.E. and Winterton, R.H.S. (1986). "A general correlation for flow boiling in tubes
 * and annuli." Int. J. Heat Mass Transfer, 29(3), 351-358.</li>
 * <li>Gungor, K.E. and Winterton, R.H.S. (1987). "Simplified general correlation for saturated flow
 * boiling and comparisons of correlations with data." Chem. Eng. Res. Des., 65, 148-156.</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see ThermalDesignCalculator
 * @see ShahCondensation
 */
public final class BoilingHeatTransfer {

  /**
   * Private constructor to prevent instantiation.
   */
  private BoilingHeatTransfer() {}

  /**
   * Calculates the local two-phase boiling HTC using the Chen (1966) correlation.
   *
   * <p>
   * h_tp = h_mac * F + h_mic * S, where:
   * </p>
   * <ul>
   * <li>h_mac = 0.023 * (k_l/D) * Re_l^0.8 * Pr_l^0.4 (Dittus-Boelter for liquid-only)</li>
   * <li>h_mic = 0.00122 * (k_l^0.79 * Cp_l^0.45 * rho_l^0.49) / (sigma^0.5 * mu_l^0.29 * h_fg^0.24
   * * rho_v^0.24) * dT_sat^0.24 * dP_sat^0.75 (Forster-Zuber)</li>
   * <li>F = enhancement factor from Martinelli parameter X_tt</li>
   * <li>S = suppression factor from Re_tp</li>
   * </ul>
   *
   * @param massFlux total mass flux G (kg/(m2*s))
   * @param vaporQuality local vapor quality x (0 to 1)
   * @param tubeID tube inner diameter (m)
   * @param liquidDensity liquid density (kg/m3)
   * @param vaporDensity vapor density (kg/m3)
   * @param liquidViscosity liquid viscosity (Pa*s)
   * @param vaporViscosity vapor viscosity (Pa*s)
   * @param liquidCp liquid heat capacity (J/(kg*K))
   * @param liquidConductivity liquid thermal conductivity (W/(m*K))
   * @param surfaceTension surface tension (N/m)
   * @param heatOfVaporization latent heat of vaporization (J/kg)
   * @param wallSuperheat wall superheat dT_sat = T_wall - T_sat (K)
   * @param satPressureDiff pressure difference corresponding to dT_sat (Pa)
   * @return local two-phase boiling HTC (W/(m2*K))
   */
  public static double calcChenHTC(double massFlux, double vaporQuality, double tubeID,
      double liquidDensity, double vaporDensity, double liquidViscosity, double vaporViscosity,
      double liquidCp, double liquidConductivity, double surfaceTension, double heatOfVaporization,
      double wallSuperheat, double satPressureDiff) {

    if (liquidViscosity <= 0 || liquidConductivity <= 0 || tubeID <= 0 || massFlux <= 0) {
      return 0.0;
    }

    double x = Math.max(0.001, Math.min(0.999, vaporQuality));

    // ---- Macroscopic (forced convective) component ----
    // Liquid-only Reynolds number
    double G_l = massFlux * (1.0 - x);
    double Re_l = G_l * tubeID / liquidViscosity;
    double Pr_l = liquidCp * liquidViscosity / liquidConductivity;

    // Dittus-Boelter for liquid fraction
    double h_l = 0.023 * Math.pow(Math.max(Re_l, 1.0), 0.8) * Math.pow(Pr_l, 0.4)
        * liquidConductivity / tubeID;

    // Martinelli parameter X_tt
    double X_tt =
        calcMartinelliParameter(x, liquidDensity, vaporDensity, liquidViscosity, vaporViscosity);

    // Chen enhancement factor F
    double F = calcChenEnhancementFactor(X_tt);

    // ---- Microscopic (nucleate boiling) component - Forster-Zuber ----
    double h_mic = 0.0;
    if (wallSuperheat > 0 && satPressureDiff > 0 && surfaceTension > 0 && heatOfVaporization > 0) {
      double numerator = Math.pow(liquidConductivity, 0.79) * Math.pow(liquidCp, 0.45)
          * Math.pow(liquidDensity, 0.49);
      double denominator = Math.pow(surfaceTension, 0.5) * Math.pow(liquidViscosity, 0.29)
          * Math.pow(heatOfVaporization, 0.24) * Math.pow(vaporDensity, 0.24);

      if (denominator > 0) {
        h_mic = 0.00122 * (numerator / denominator) * Math.pow(wallSuperheat, 0.24)
            * Math.pow(satPressureDiff, 0.75);
      }
    }

    // Chen suppression factor S
    double Re_tp = Re_l * Math.pow(F, 1.25);
    double S = calcChenSuppressionFactor(Re_tp);

    return h_l * F + h_mic * S;
  }

  /**
   * Calculates the local two-phase boiling HTC using the Gungor-Winterton (1987) simplified
   * correlation.
   *
   * <p>
   * This is a simpler alternative to Chen that does not require wall superheat or surface tension:
   * </p>
   *
   * <pre>
   * h_tp = h_l * [1 + 3000 * Bo^0.86 + 1.12 * (x/(1-x))^0.75 * (rho_l/rho_v)^0.41]
   * </pre>
   *
   * <p>
   * where Bo is the boiling number q/(G * h_fg).
   * </p>
   *
   * @param massFlux total mass flux G (kg/(m2*s))
   * @param vaporQuality local vapor quality x (0 to 1)
   * @param tubeID tube inner diameter (m)
   * @param liquidDensity liquid density (kg/m3)
   * @param vaporDensity vapor density (kg/m3)
   * @param liquidViscosity liquid viscosity (Pa*s)
   * @param liquidCp liquid heat capacity (J/(kg*K))
   * @param liquidConductivity liquid thermal conductivity (W/(m*K))
   * @param heatFlux heat flux at tube wall (W/m2)
   * @param heatOfVaporization latent heat of vaporization (J/kg)
   * @return local two-phase boiling HTC (W/(m2*K))
   */
  public static double calcGungorWintertonHTC(double massFlux, double vaporQuality, double tubeID,
      double liquidDensity, double vaporDensity, double liquidViscosity, double liquidCp,
      double liquidConductivity, double heatFlux, double heatOfVaporization) {

    if (liquidViscosity <= 0 || liquidConductivity <= 0 || tubeID <= 0 || massFlux <= 0) {
      return 0.0;
    }

    double x = Math.max(0.001, Math.min(0.999, vaporQuality));

    // Liquid-only HTC (Dittus-Boelter)
    double Re_l = massFlux * (1.0 - x) * tubeID / liquidViscosity;
    double Pr_l = liquidCp * liquidViscosity / liquidConductivity;
    double h_l = 0.023 * Math.pow(Math.max(Re_l, 1.0), 0.8) * Math.pow(Pr_l, 0.4)
        * liquidConductivity / tubeID;

    // Boiling number
    double Bo =
        (heatOfVaporization > 0 && massFlux > 0) ? heatFlux / (massFlux * heatOfVaporization) : 0.0;

    // Convective enhancement
    double convectiveEnhancement = 0.0;
    if (x < 0.999 && vaporDensity > 0) {
      convectiveEnhancement =
          1.12 * Math.pow(x / (1.0 - x), 0.75) * Math.pow(liquidDensity / vaporDensity, 0.41);
    }

    // Nucleate boiling enhancement
    double nucleateEnhancement = 3000.0 * Math.pow(Math.max(Bo, 0.0), 0.86);

    double E = 1.0 + nucleateEnhancement + convectiveEnhancement;

    return h_l * E;
  }

  /**
   * Calculates the Gungor-Winterton HTC with corrections for horizontal stratified flow and
   * subcooled boiling.
   *
   * <p>
   * For horizontal tubes where Froude number Fr_l &lt; 0.05, applies a stratified flow correction.
   * For subcooled boiling (x = 0), uses the nucleate boiling only term.
   * </p>
   *
   * @param massFlux total mass flux G (kg/(m2*s))
   * @param vaporQuality local vapor quality x (0 to 1)
   * @param tubeID tube inner diameter (m)
   * @param liquidDensity liquid density (kg/m3)
   * @param vaporDensity vapor density (kg/m3)
   * @param liquidViscosity liquid viscosity (Pa*s)
   * @param liquidCp liquid heat capacity (J/(kg*K))
   * @param liquidConductivity liquid thermal conductivity (W/(m*K))
   * @param heatFlux heat flux (W/m2)
   * @param heatOfVaporization latent heat (J/kg)
   * @param isHorizontal true for horizontal tube orientation
   * @return corrected boiling HTC (W/(m2*K))
   */
  public static double calcGungorWintertonCorrectedHTC(double massFlux, double vaporQuality,
      double tubeID, double liquidDensity, double vaporDensity, double liquidViscosity,
      double liquidCp, double liquidConductivity, double heatFlux, double heatOfVaporization,
      boolean isHorizontal) {

    double h = calcGungorWintertonHTC(massFlux, vaporQuality, tubeID, liquidDensity, vaporDensity,
        liquidViscosity, liquidCp, liquidConductivity, heatFlux, heatOfVaporization);

    if (isHorizontal && liquidDensity > 0) {
      // Froude number check for stratified flow
      double G_l = massFlux * (1.0 - Math.max(0.001, Math.min(0.999, vaporQuality)));
      double v_l = G_l / liquidDensity;
      double g = 9.81;
      double Fr_l = v_l * v_l / (g * tubeID);

      if (Fr_l < 0.05) {
        // Stratified flow correction factor
        double E2 = Math.pow(Fr_l, 0.1 - 2.0 * Fr_l);
        h *= E2;
      }
    }

    return h;
  }

  /**
   * Calculates the Lockhart-Martinelli parameter X_tt for turbulent-turbulent flow.
   *
   * <pre>
   * X_tt = ((1 - x) / x) ^ 0.9 * (rho_v / rho_l) ^ 0.5 * (mu_l / mu_v) ^ 0.1
   * </pre>
   *
   * @param x vapor quality (0 to 1)
   * @param liquidDensity liquid density (kg/m3)
   * @param vaporDensity vapor density (kg/m3)
   * @param liquidViscosity liquid viscosity (Pa*s)
   * @param vaporViscosity vapor viscosity (Pa*s)
   * @return Martinelli parameter X_tt
   */
  public static double calcMartinelliParameter(double x, double liquidDensity, double vaporDensity,
      double liquidViscosity, double vaporViscosity) {
    if (x <= 0 || x >= 1 || vaporDensity <= 0 || liquidDensity <= 0 || vaporViscosity <= 0) {
      return 1.0;
    }

    return Math.pow((1.0 - x) / x, 0.9) * Math.pow(vaporDensity / liquidDensity, 0.5)
        * Math.pow(liquidViscosity / vaporViscosity, 0.1);
  }

  /**
   * Calculates the Chen enhancement factor F.
   *
   * <p>
   * F accounts for the increase in turbulence due to vapor presence:
   * </p>
   *
   * <pre>
   * F = 1.0                         when 1/X_tt &lt;= 0.1
   * F = 2.35 * (0.213 + 1/X_tt)^0.736  otherwise
   * </pre>
   *
   * @param X_tt Martinelli parameter
   * @return enhancement factor F (always &gt;= 1.0)
   */
  public static double calcChenEnhancementFactor(double X_tt) {
    if (X_tt <= 0) {
      return 1.0;
    }

    double invXtt = 1.0 / X_tt;

    if (invXtt <= 0.1) {
      return 1.0;
    }

    return 2.35 * Math.pow(0.213 + invXtt, 0.736);
  }

  /**
   * Calculates the Chen suppression factor S.
   *
   * <p>
   * S accounts for the suppression of nucleate boiling at high Reynolds numbers:
   * </p>
   *
   * <pre>
   * S = 1 / (1 + 2.53e-6 * Re_tp ^ 1.17)
   * </pre>
   *
   * @param Re_tp two-phase Reynolds number = Re_l * F^1.25
   * @return suppression factor S (0 to 1)
   */
  public static double calcChenSuppressionFactor(double Re_tp) {
    if (Re_tp <= 0) {
      return 1.0;
    }

    return 1.0 / (1.0 + 2.53e-6 * Math.pow(Re_tp, 1.17));
  }

  /**
   * Calculates the average boiling HTC over a quality range by numerical integration.
   *
   * <p>
   * Uses the Gungor-Winterton correlation integrated over the quality range with Simpson's rule.
   * </p>
   *
   * @param massFlux total mass flux (kg/(m2*s))
   * @param tubeID tube inner diameter (m)
   * @param liquidDensity liquid density (kg/m3)
   * @param vaporDensity vapor density (kg/m3)
   * @param liquidViscosity liquid viscosity (Pa*s)
   * @param liquidCp liquid heat capacity (J/(kg*K))
   * @param liquidConductivity liquid thermal conductivity (W/(m*K))
   * @param heatFlux heat flux (W/m2)
   * @param heatOfVaporization latent heat (J/kg)
   * @param qualityIn quality at zone inlet (0 to 1)
   * @param qualityOut quality at zone outlet (0 to 1)
   * @param intervals number of integration intervals (minimum 4, must be even)
   * @return average boiling HTC over the quality range (W/(m2*K))
   */
  public static double calcAverageHTC(double massFlux, double tubeID, double liquidDensity,
      double vaporDensity, double liquidViscosity, double liquidCp, double liquidConductivity,
      double heatFlux, double heatOfVaporization, double qualityIn, double qualityOut,
      int intervals) {

    if (massFlux <= 0 || liquidViscosity <= 0 || tubeID <= 0) {
      return 0.0;
    }

    double xIn = Math.max(0.001, Math.min(0.999, qualityIn));
    double xOut = Math.max(0.001, Math.min(0.999, qualityOut));

    if (Math.abs(xIn - xOut) < 1e-10) {
      return calcGungorWintertonHTC(massFlux, xIn, tubeID, liquidDensity, vaporDensity,
          liquidViscosity, liquidCp, liquidConductivity, heatFlux, heatOfVaporization);
    }

    int n = Math.max(4, intervals);
    if (n % 2 != 0) {
      n++;
    }

    double dx = (xOut - xIn) / n;
    double sum = calcGungorWintertonHTC(massFlux, xIn, tubeID, liquidDensity, vaporDensity,
        liquidViscosity, liquidCp, liquidConductivity, heatFlux, heatOfVaporization)
        + calcGungorWintertonHTC(massFlux, xOut, tubeID, liquidDensity, vaporDensity,
            liquidViscosity, liquidCp, liquidConductivity, heatFlux, heatOfVaporization);

    for (int i = 1; i < n; i++) {
      double x = xIn + i * dx;
      double h = calcGungorWintertonHTC(massFlux, x, tubeID, liquidDensity, vaporDensity,
          liquidViscosity, liquidCp, liquidConductivity, heatFlux, heatOfVaporization);
      sum += (i % 2 == 0) ? 2.0 * h : 4.0 * h;
    }

    return Math.abs(sum * dx / 3.0 / (xOut - xIn));
  }
}
