package neqsim.process.equipment.separator.entrainment;

import java.io.Serializable;

/**
 * Calculates terminal settling velocity for droplets/bubbles using the Schiller-Naumann drag
 * correlation, which covers the full range from Stokes (creeping) flow through the intermediate
 * regime to the Newton (turbulent) regime.
 *
 * <p>
 * The Schiller-Naumann correlation provides a smooth transition across flow regimes (Schiller and
 * Naumann, 1935):
 * </p>
 *
 * $$ C_D = \frac{24}{Re}\left(1 + 0.15 Re^{0.687}\right) \quad \text{for } Re &lt; 1000 $$
 *
 * $$ C_D = 0.44 \quad \text{for } Re \geq 1000 $$
 *
 * <p>
 * The settling velocity is found by iterating the force balance:
 * </p>
 *
 * $$ v_t = \sqrt{\frac{4 g d_p |\Delta\rho|}{3 C_D \rho_c}} $$
 *
 * <p>
 * For the Stokes regime ($Re \ll 1$), this reduces to:
 * </p>
 *
 * $$ v_t = \frac{d_p^2 |\Delta\rho| g}{18 \mu_c} $$
 *
 * <p>
 * References: Schiller, L. and Naumann, Z. (1935), "A drag coefficient correlation", <i>Zeitschrift
 * des Vereines Deutscher Ingenieure</i>, 77, 318-320. Clift, R., Grace, J.R., and Weber, M.E.
 * (1978), <i>Bubbles, Drops and Particles</i>, Academic Press, New York.
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class DropletSettlingCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Gravitational acceleration [m/s2]. */
  private static final double G = 9.81;

  /** Maximum iterations for velocity convergence. */
  private static final int MAX_ITERATIONS = 50;

  /** Convergence tolerance for velocity iteration. */
  private static final double TOLERANCE = 1e-6;

  /**
   * Calculates the terminal settling velocity of a single droplet or bubble using the
   * Schiller-Naumann drag correlation.
   *
   * <p>
   * Positive velocity means downward (settling), applicable when dispersed phase is heavier.
   * Negative velocity means upward (buoyant rise), applicable for bubbles in liquid.
   * </p>
   *
   * @param dropletDiameter diameter of the droplet or bubble [m]
   * @param continuousDensity density of the continuous phase [kg/m3]
   * @param dispersedDensity density of the dispersed phase [kg/m3]
   * @param continuousViscosity dynamic viscosity of the continuous phase [Pa.s]
   * @return terminal settling velocity [m/s], positive = downward, negative = upward
   */
  public static double calcTerminalVelocity(double dropletDiameter, double continuousDensity,
      double dispersedDensity, double continuousViscosity) {
    if (dropletDiameter <= 0 || continuousDensity <= 0 || continuousViscosity <= 0) {
      return 0.0;
    }

    double deltaRho = Math.abs(dispersedDensity - continuousDensity);
    if (deltaRho < 1e-6) {
      return 0.0;
    }

    // Start with Stokes velocity as initial guess
    double vStokes =
        dropletDiameter * dropletDiameter * deltaRho * G / (18.0 * continuousViscosity);
    double velocity = vStokes;

    // Iterate to converge on velocity (needed for intermediate/Newton regime)
    for (int i = 0; i < MAX_ITERATIONS; i++) {
      double re = calcReynolds(dropletDiameter, velocity, continuousDensity, continuousViscosity);
      double cd = calcDragCoefficient(re);
      double vNew =
          Math.sqrt(4.0 * G * dropletDiameter * deltaRho / (3.0 * cd * continuousDensity));

      if (Math.abs(vNew - velocity) / (velocity + 1e-30) < TOLERANCE) {
        velocity = vNew;
        break;
      }
      velocity = 0.5 * (velocity + vNew); // Under-relaxation for stability
    }

    // Sign convention: positive = settling (dispersed heavier), negative = rising (dispersed
    // lighter)
    return (dispersedDensity > continuousDensity) ? velocity : -velocity;
  }

  /**
   * Calculates the drag coefficient using the Schiller-Naumann correlation.
   *
   * @param re particle Reynolds number
   * @return drag coefficient C_D
   */
  public static double calcDragCoefficient(double re) {
    if (re <= 0.0) {
      return 1e10; // Very large to give zero velocity
    }
    if (re < 1000.0) {
      return (24.0 / re) * (1.0 + 0.15 * Math.pow(re, 0.687));
    } else {
      return 0.44; // Newton regime
    }
  }

  /**
   * Calculates the particle Reynolds number.
   *
   * @param diameter droplet diameter [m]
   * @param velocity settling velocity [m/s]
   * @param continuousDensity density of continuous phase [kg/m3]
   * @param continuousViscosity dynamic viscosity of continuous phase [Pa.s]
   * @return Reynolds number
   */
  public static double calcReynolds(double diameter, double velocity, double continuousDensity,
      double continuousViscosity) {
    if (continuousViscosity <= 0.0) {
      return 0.0;
    }
    return continuousDensity * Math.abs(velocity) * diameter / continuousViscosity;
  }

  /**
   * Calculates the turbulence-corrected gravity cut diameter for a separator section.
   *
   * <p>
   * In a real separator, turbulence generated at the inlet redistributes small droplets,
   * increasing the effective cut diameter beyond the quiescent gravity value. This correction
   * applies the Csanady (1963) passive-particle turbulent diffusion model, simplified to an
   * engineering formula (Koenders et al., 2015):
   * </p>
   *
   * <p>
   * Turbulent diffusivity: $D_t = u_{rms} \cdot L_t$, where $L_t$ is the integral length
   * scale (vessel radius / 5).
   * </p>
   *
   * <p>
   * Effective cut diameter correction:
   * $d_{cut,turb} = d_{cut,grav} \cdot \left(1 + \frac{D_t}{v_t \cdot H}\right)^{0.25}$
   * </p>
   *
   * <p>
   * Turbulence intensity increases with gas load: at 50% of design K-factor the correction
   * is negligible; above 80% it becomes significant and can shift the effective cut
   * diameter by 20-60%.
   * </p>
   *
   * <p>
   * References: Csanady, G.T. (1963). Turbulent diffusion of heavy particles in the
   * atmosphere. <i>J. Atmos. Sci.</i>, 20, 201-208. Koenders, M.A., Slot, J.J.,
   * Hoeijmakers, H.W.M. (2015). Numerical simulation of gas-liquid separator performance.
   * <i>SPE Production and Operations</i>, 30(3), 215-225.
   * </p>
   *
   * @param gravityCutDiameter quiescent gravity cut diameter [m]
   * @param gasVelocity superficial gas velocity in the vessel [m/s]
   * @param settlingHeight effective droplet settling height [m]
   * @param kFactor operating Souders-Brown K-factor [m/s]
   * @param designKFactor maximum design K-factor [m/s]; use 0 if unknown
   * @param gasDensity gas phase density [kg/m3]
   * @param liquidDensity liquid dispersed phase density [kg/m3]
   * @param gasViscosity gas dynamic viscosity [Pa.s]
   * @return turbulence-corrected cut diameter [m], always &gt;= gravityCutDiameter
   */
  public static double calcTurbulenceCorrectedCutDiameter(double gravityCutDiameter,
      double gasVelocity, double settlingHeight, double kFactor, double designKFactor,
      double gasDensity, double liquidDensity, double gasViscosity) {

    if (gravityCutDiameter <= 0 || gasVelocity <= 0 || settlingHeight <= 0) {
      return gravityCutDiameter;
    }

    // Turbulence intensity in separator (Koenders et al. 2015):
    // increases quadratically with gas load fraction
    double turbulenceIntensity = 0.05; // 5% floor (even with low load, some turbulence)
    if (designKFactor > 0) {
      double loadFraction = Math.min(1.0, kFactor / designKFactor);
      turbulenceIntensity = 0.05 + 0.15 * loadFraction * loadFraction;
    }
    double uRms = turbulenceIntensity * gasVelocity;

    // Integral eddy length scale (~ vessel radius / 5, based on RANS analogy)
    double lt = settlingHeight / 5.0;

    // Turbulent diffusivity D_t = uRms * L_t (Csanady passive-particle limit)
    double turbDiff = uRms * lt;

    // Terminal settling velocity of the cut-size droplet
    double vt =
        Math.abs(calcTerminalVelocity(gravityCutDiameter, gasDensity, liquidDensity, gasViscosity));
    if (vt <= 0) {
      return gravityCutDiameter;
    }

    // Turbulence-corrected cut diameter:
    // correction = (1 + D_t / (v_t * H))^0.25 -- capped at 3x (physical limit)
    double correctionArg = 1.0 + turbDiff / (vt * settlingHeight);
    correctionArg = Math.min(correctionArg, 81.0); // cap: 81^0.25 = 3.0
    return gravityCutDiameter * Math.pow(correctionArg, 0.25);
  }

  /**
   * Holds the result of an API 12J compliance check for a separator design.
   *
   * <p>
   * API 12J (2014), <i>Specification for Oil and Gas Separators</i>, specifies minimum
   * performance criteria for gravity separators in upstream oil and gas service.
   * </p>
   */
  public static class ApiComplianceResult implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1001L;

    /** True when the gas section meets API 12J criteria. */
    public final boolean gasLiquidSectionCompliant;

    /** True when the liquid section meets API 12J minimum retention time criteria. */
    public final boolean liquidSectionCompliant;

    /** Human-readable gas section compliance status string. */
    public final String gasLiquidComment;

    /** Human-readable liquid section compliance status string. */
    public final String liquidComment;

    /** Gravity cut diameter [μm] at operating conditions. */
    public final double gravityCutDiameter_um;

    /** K-factor utilization fraction (operating / maximum API K-factor). */
    public final double kFactorUtilization;

    /**
     * Creates a new compliance result.
     *
     * @param gasOk gas section compliant flag
     * @param liquidOk liquid section compliant flag
     * @param gasComment gas section comment string
     * @param liquidCommentArg liquid section comment string
     * @param cutDiam_um gravity cut diameter in microns
     * @param kUtil K-factor utilization fraction
     */
    public ApiComplianceResult(boolean gasOk, boolean liquidOk, String gasComment,
        String liquidCommentArg, double cutDiam_um, double kUtil) {
      this.gasLiquidSectionCompliant = gasOk;
      this.liquidSectionCompliant = liquidOk;
      this.gasLiquidComment = gasComment;
      this.liquidComment = liquidCommentArg;
      this.gravityCutDiameter_um = cutDiam_um;
      this.kFactorUtilization = kUtil;
    }

    /**
     * Returns true when both gas and liquid sections are compliant.
     *
     * @return true if fully compliant
     */
    public boolean isFullyCompliant() {
      return gasLiquidSectionCompliant && liquidSectionCompliant;
    }
  }

  /**
   * Checks API 12J (2014) compliance for a separator gravity section.
   *
   * <p>
   * API 12J criteria applied:
   * </p>
   * <ul>
   * <li><b>Gas section — K-factor</b>: operating K must not exceed 0.107 m/s (vertical,
   * no mist eliminator) or 0.120 m/s (horizontal, no mist eliminator). With a mist
   * eliminator the limit depends on the device type — 0.12 to 0.30 m/s.</li>
   * <li><b>Gas section — droplet removal</b>: gravity section alone must remove droplets
   * &ge; 100 &mu;m from gas (API 12J Table 2 for clean service).</li>
   * <li><b>Liquid section — residence time</b>: minimum 3 minutes (180 s) for two-phase
   * separators in clean service; 5 minutes (300 s) for three-phase.</li>
   * </ul>
   *
   * <p>
   * Reference: API Specification 12J, 8th Edition (2014). <i>Specification for Oil and Gas
   * Separators</i>. American Petroleum Institute, Washington, DC.
   * </p>
   *
   * @param gravityCutDiameter_m gravity cut diameter at operating conditions [m]
   * @param kFactor operating Souders-Brown K-factor [m/s]
   * @param mistEliminatorPresent true when a mist eliminator is installed
   * @param liquidResidenceTime_s liquid residence time in the vessel [s]
   * @param orientation vessel orientation: {@code "vertical"} or {@code "horizontal"}
   * @param isThreePhase true for three-phase (gas/oil/water) separators
   * @return compliance result object encapsulating pass/fail status and comments
   */
  public static ApiComplianceResult checkApi12JCompliance(double gravityCutDiameter_m,
      double kFactor, boolean mistEliminatorPresent, double liquidResidenceTime_s,
      String orientation, boolean isThreePhase) {

    // -- Gas section K-factor limits (API 12J Table 2) --
    double maxKNoME =
        "vertical".equalsIgnoreCase(orientation) ? 0.107 : 0.120; // m/s without mist eliminator
    double maxKWithME = 0.25; // m/s with wire-mesh mist eliminator (typical)
    double maxK = mistEliminatorPresent ? maxKWithME : maxKNoME;
    double kUtil = (maxK > 0) ? kFactor / maxK : 0.0;

    // API 12J gravity section: must remove >= 100 um droplets
    double apiCutMax_m = 100e-6;
    boolean gasOk = (gravityCutDiameter_m <= apiCutMax_m) && (kFactor <= maxK);
    String gasComment;
    if (gasOk) {
      gasComment =
          String.format("COMPLIANT: cut=%.0f um (max 100 um), K=%.3f m/s (max %.3f m/s)",
              gravityCutDiameter_m * 1e6, kFactor, maxK);
    } else {
      gasComment =
          String.format("NON-COMPLIANT: cut=%.0f um (max 100 um), K=%.3f m/s (max %.3f m/s)",
              gravityCutDiameter_m * 1e6, kFactor, maxK);
    }

    // -- Liquid section residence time limit --
    double minResidenceTime = isThreePhase ? 300.0 : 180.0; // 5 min (3-phase) / 3 min (2-phase)
    boolean liquidOk = (liquidResidenceTime_s >= minResidenceTime);
    String liquidComment;
    if (liquidOk) {
      liquidComment = String.format("COMPLIANT: liquid residence time = %.0f s (min %.0f s)",
          liquidResidenceTime_s, minResidenceTime);
    } else {
      liquidComment = String.format("NON-COMPLIANT: liquid residence time = %.0f s (min %.0f s)",
          liquidResidenceTime_s, minResidenceTime);
    }

    return new ApiComplianceResult(gasOk, liquidOk, gasComment, liquidComment,
        gravityCutDiameter_m * 1e6, kUtil);
  }

  /**
   * Calculates the critical (cut) diameter for gravity separation in a given geometry.
   *
   * <p>
   * This is the smallest droplet that can settle across the available height within the available
   * residence time. In the Stokes regime:
   * </p>
   *
   * $$ d_{cut} = \sqrt{\frac{18 \mu_c H}{|\Delta\rho| g t_{res}}} $$
   *
   * <p>
   * For higher Reynolds numbers, the critical diameter is found iteratively.
   * </p>
   *
   * @param availableHeight height available for settling [m]
   * @param residenceTime gas or liquid residence time in the section [s]
   * @param continuousDensity density of continuous phase [kg/m3]
   * @param dispersedDensity density of dispersed phase [kg/m3]
   * @param continuousViscosity dynamic viscosity of continuous phase [Pa.s]
   * @return critical (cut) diameter [m]
   */
  public static double calcCriticalDiameter(double availableHeight, double residenceTime,
      double continuousDensity, double dispersedDensity, double continuousViscosity) {
    if (residenceTime <= 0 || availableHeight <= 0) {
      return Double.MAX_VALUE;
    }
    double requiredVelocity = availableHeight / residenceTime;
    double deltaRho = Math.abs(dispersedDensity - continuousDensity);
    if (deltaRho < 1e-6) {
      return Double.MAX_VALUE;
    }

    // Stokes estimate as initial guess
    double dCut = Math.sqrt(18.0 * continuousViscosity * requiredVelocity / (deltaRho * G));

    // Iterate for non-Stokes regime
    for (int i = 0; i < MAX_ITERATIONS; i++) {
      double vt = Math.abs(
          calcTerminalVelocity(dCut, continuousDensity, dispersedDensity, continuousViscosity));
      if (vt <= 0) {
        break;
      }
      double ratio = requiredVelocity / vt;
      // Adjust diameter: in Stokes regime, v ~ d^2, so d ~ sqrt(ratio) * d
      double dNew = dCut * Math.sqrt(ratio);
      if (Math.abs(dNew - dCut) / (dCut + 1e-30) < TOLERANCE) {
        dCut = dNew;
        break;
      }
      dCut = 0.5 * (dCut + dNew);
    }
    return dCut;
  }
}
