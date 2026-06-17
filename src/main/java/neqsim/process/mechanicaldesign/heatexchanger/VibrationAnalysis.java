package neqsim.process.mechanicaldesign.heatexchanger;

/**
 * Flow-induced vibration analysis for shell and tube heat exchangers.
 *
 * <p>
 * Evaluates four vibration mechanisms per TEMA RCB-4.6 and industry practice:
 * </p>
 * <ul>
 * <li><b>Vortex shedding</b> - Von Karman vortices at specific Strouhal frequencies</li>
 * <li><b>Fluid-elastic instability</b> - Connors criterion for critical velocity</li>
 * <li><b>Acoustic resonance</b> - Standing waves in shell cavity</li>
 * <li><b>Turbulent buffeting</b> - Random excitation from turbulence</li>
 * </ul>
 *
 * <p>
 * If any critical velocity ratio exceeds 1.0, the exchanger should be redesigned (wider pitch,
 * shorter span, different baffle type).
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class VibrationAnalysis {

  /** Strouhal number for tube banks (typical range 0.2-0.5). */
  private static final double STROUHAL_DEFAULT = 0.22;

  /** Connors instability constant for various pitch patterns. */
  private static final double CONNORS_TRIANGULAR = 3.3;
  private static final double CONNORS_SQUARE = 3.4;

  /** Safety margin for fluid-elastic instability (TEMA recommendation). */
  private static final double FLUIDELASTIC_SAFETY_FACTOR = 0.5;

  /**
   * Private constructor to prevent instantiation.
   */
  private VibrationAnalysis() {}

  /**
   * Calculates the natural frequency of a straight tube span using beam vibration theory.
   *
   * <p>
   * f_n = C_n / (2 * pi) * sqrt(E * I / (rho_eff * A * L^4))
   * </p>
   * <ul>
   * <li>Simply supported (pinned-pinned): C_n = pi^2 = 9.87</li>
   * <li>Fixed-fixed: C_n = 22.37</li>
   * <li>Clamped-simply supported: C_n = 15.42</li>
   * </ul>
   *
   * @param tubeOD tube outer diameter (m)
   * @param tubeID tube inner diameter (m)
   * @param unsupportedSpan distance between supports (m)
   * @param tubeMaterialE Young's modulus of tube material (Pa)
   * @param tubeDensity tube material density (kg/m3)
   * @param fluidDensityTube density of fluid inside tube (kg/m3)
   * @param fluidDensityShell density of fluid on shell side (kg/m3)
   * @param endCondition boundary condition: "pinned", "fixed", or "clamped-pinned"
   * @return natural frequency (Hz)
   */
  public static double calcNaturalFrequency(double tubeOD, double tubeID, double unsupportedSpan,
      double tubeMaterialE, double tubeDensity, double fluidDensityTube, double fluidDensityShell,
      String endCondition) {
    if (tubeOD <= 0 || tubeID >= tubeOD || unsupportedSpan <= 0 || tubeMaterialE <= 0) {
      return 0.0;
    }

    // Second moment of area (tube cross-section)
    double I = Math.PI / 64.0 * (Math.pow(tubeOD, 4) - Math.pow(tubeID, 4));

    // Cross-sectional area
    double aTube = Math.PI / 4.0 * (tubeOD * tubeOD - tubeID * tubeID);
    double aCore = Math.PI / 4.0 * tubeID * tubeID;

    // Effective mass per unit length (tube metal + internal fluid + hydrodynamic mass)
    double mMetal = tubeDensity * aTube;
    double mInternal = fluidDensityTube * aCore;
    double mHydro = fluidDensityShell * Math.PI / 4.0 * tubeOD * tubeOD;
    // Hydrodynamic mass coefficient ~ 1.0 for tube banks
    double mEff = mMetal + mInternal + 1.0 * mHydro;

    if (mEff <= 0) {
      return 0.0;
    }

    // Eigenvalue constant
    double Cn;
    if ("fixed".equalsIgnoreCase(endCondition)) {
      Cn = 22.37;
    } else if ("clamped-pinned".equalsIgnoreCase(endCondition)) {
      Cn = 15.42;
    } else {
      // Default: pinned-pinned (simply supported)
      Cn = 9.87;
    }

    double fn =
        Cn / (2.0 * Math.PI) * Math.sqrt(tubeMaterialE * I / (mEff * Math.pow(unsupportedSpan, 4)));
    return fn;
  }

  /**
   * Calculates the vortex shedding frequency for crossflow over a tube bank.
   *
   * <p>
   * f_vs = St * V_crossflow / d_o
   * </p>
   *
   * @param crossflowVelocity shell-side crossflow velocity (m/s)
   * @param tubeOD tube outer diameter (m)
   * @param tubePitch tube pitch (m)
   * @return vortex shedding frequency (Hz)
   */
  public static double calcVortexSheddingFrequency(double crossflowVelocity, double tubeOD,
      double tubePitch) {
    if (tubeOD <= 0 || crossflowVelocity <= 0) {
      return 0.0;
    }

    // Effective Strouhal number adjusted for pitch ratio
    double pitchRatio = tubePitch / tubeOD;
    double St = STROUHAL_DEFAULT;
    if (pitchRatio > 1.0 && pitchRatio < 3.0) {
      // Fitzhugh's correlation for tube banks
      St = 0.22 * (1.0 + 0.2 / (pitchRatio - 0.8));
    }

    return St * crossflowVelocity / tubeOD;
  }

  /**
   * Calculates the critical velocity for fluid-elastic instability using Connors' criterion.
   *
   * <p>
   * V_crit = K * f_n * d_o * sqrt(2 * pi * zeta * m_eff / (rho * d_o^2))
   * </p>
   *
   * <p>
   * where K is Connors' constant (typically 3.3 for triangular, 3.4 for square pitch).
   * </p>
   *
   * @param naturalFrequency tube natural frequency (Hz)
   * @param tubeOD tube outer diameter (m)
   * @param dampingRatio logarithmic decrement of damping (typical 0.01-0.05)
   * @param effectiveMassPerLength effective tube mass per unit length (kg/m)
   * @param shellFluidDensity shell-side fluid density (kg/m3)
   * @param triangularPitch true for triangular layout, false for square
   * @return critical velocity for instability (m/s)
   */
  public static double calcCriticalVelocityConnors(double naturalFrequency, double tubeOD,
      double dampingRatio, double effectiveMassPerLength, double shellFluidDensity,
      boolean triangularPitch) {
    if (naturalFrequency <= 0 || tubeOD <= 0 || shellFluidDensity <= 0) {
      return Double.MAX_VALUE;
    }

    double K = triangularPitch ? CONNORS_TRIANGULAR : CONNORS_SQUARE;
    double massRatio = effectiveMassPerLength / (shellFluidDensity * tubeOD * tubeOD);

    return K * naturalFrequency * tubeOD * Math.sqrt(2.0 * Math.PI * dampingRatio * massRatio);
  }

  /**
   * Calculates the acoustic resonance frequency of the shell cavity.
   *
   * <p>
   * f_ac = n * c / (2 * W_eff) for standing waves across the shell
   * </p>
   *
   * @param shellID shell inside diameter (m)
   * @param sonicVelocity speed of sound in shell-side fluid (m/s)
   * @param mode acoustic mode number (1, 2, 3, ...)
   * @return acoustic resonance frequency (Hz)
   */
  public static double calcAcousticFrequency(double shellID, double sonicVelocity, int mode) {
    if (shellID <= 0 || sonicVelocity <= 0 || mode < 1) {
      return 0.0;
    }

    // Effective width considering tube presence (Blevins correction)
    // Typically c_eff = c * sqrt(1 - sigma) where sigma is area fraction
    // Simplified: use shell ID directly
    return mode * sonicVelocity / (2.0 * shellID);
  }

  /**
   * Estimates the speed of sound in the shell-side fluid considering the presence of tubes
   * (effective acoustic velocity).
   *
   * <p>
   * c_eff = c_fluid / sqrt(1 + sigma * rho_fluid / rho_tube)
   * </p>
   *
   * <p>
   * where sigma is the tube volume fraction in the shell.
   * </p>
   *
   * @param fluidSonicVelocity speed of sound in the shell-side fluid (m/s)
   * @param tubeVolumeFraction fraction of shell volume occupied by tubes
   * @return effective acoustic velocity in the bundle (m/s)
   */
  public static double calcEffectiveAcousticVelocity(double fluidSonicVelocity,
      double tubeVolumeFraction) {
    if (fluidSonicVelocity <= 0) {
      return 0.0;
    }

    // Parker correction for tube banks
    double correction = Math.sqrt(1.0 / (1.0 - tubeVolumeFraction));
    if (correction > 3.0) {
      correction = 3.0;
    }

    return fluidSonicVelocity / correction;
  }

  /**
   * Performs a complete vibration screening for a shell and tube exchanger.
   *
   * <p>
   * Returns a result object with pass/fail status and individual mechanism assessments.
   * </p>
   *
   * @param tubeOD tube outer diameter (m)
   * @param tubeID tube inner diameter (m)
   * @param unsupportedSpan longest unsupported tube span (m)
   * @param tubePitch tube pitch (m)
   * @param tubeMaterialE tube material Young's modulus (Pa)
   * @param tubeDensity tube material density (kg/m3)
   * @param crossflowVelocity shell-side crossflow velocity (m/s)
   * @param shellFluidDensity shell-side fluid density (kg/m3)
   * @param tubeFluidDensity tube-side fluid density (kg/m3)
   * @param shellID shell inside diameter (m)
   * @param sonicVelocity speed of sound in shell fluid (m/s)
   * @param dampingRatio tube damping ratio (typical 0.03 in liquid, 0.01 in gas)
   * @param triangularPitch true for triangular layout
   * @return vibration screening result
   */
  public static VibrationResult performScreening(double tubeOD, double tubeID,
      double unsupportedSpan, double tubePitch, double tubeMaterialE, double tubeDensity,
      double crossflowVelocity, double shellFluidDensity, double tubeFluidDensity, double shellID,
      double sonicVelocity, double dampingRatio, boolean triangularPitch) {

    VibrationResult result = new VibrationResult();

    // 1. Natural frequency
    double fn = calcNaturalFrequency(tubeOD, tubeID, unsupportedSpan, tubeMaterialE, tubeDensity,
        tubeFluidDensity, shellFluidDensity, "pinned");
    result.naturalFrequencyHz = fn;

    // 2. Vortex shedding
    double fvs = calcVortexSheddingFrequency(crossflowVelocity, tubeOD, tubePitch);
    result.vortexSheddingFrequencyHz = fvs;
    result.vortexSheddingRatio = (fn > 0) ? fvs / fn : 0.0;
    result.vortexSheddingCritical =
        (result.vortexSheddingRatio > 0.8 && result.vortexSheddingRatio < 1.2);

    // 3. Fluid-elastic instability
    double aTube = Math.PI / 4.0 * (tubeOD * tubeOD - tubeID * tubeID);
    double aCore = Math.PI / 4.0 * tubeID * tubeID;
    double mEff = tubeDensity * aTube + tubeFluidDensity * aCore
        + shellFluidDensity * Math.PI / 4.0 * tubeOD * tubeOD;
    double vCrit = calcCriticalVelocityConnors(fn, tubeOD, dampingRatio, mEff, shellFluidDensity,
        triangularPitch);
    result.criticalVelocityMs = vCrit;
    result.velocityRatio = (vCrit > 0 && vCrit < Double.MAX_VALUE)
        ? crossflowVelocity / (vCrit * FLUIDELASTIC_SAFETY_FACTOR)
        : 0.0;
    result.fluidElasticCritical = result.velocityRatio > 1.0;

    // 4. Acoustic resonance
    double fac1 = calcAcousticFrequency(shellID, sonicVelocity, 1);
    result.acousticFrequencyHz = fac1;
    result.acousticRatio = (fac1 > 0) ? fvs / fac1 : 0.0;
    result.acousticCritical = (result.acousticRatio > 0.8 && result.acousticRatio < 1.2);

    // Overall assessment
    result.passed =
        !result.vortexSheddingCritical && !result.fluidElasticCritical && !result.acousticCritical;

    return result;
  }

  /**
   * Results of vibration screening analysis.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class VibrationResult {
    /** Tube natural frequency (Hz). */
    public double naturalFrequencyHz;

    /** Vortex shedding frequency (Hz). */
    public double vortexSheddingFrequencyHz;

    /** Ratio of vortex shedding to natural frequency. */
    public double vortexSheddingRatio;

    /** True if vortex shedding is near resonance (0.8-1.2). */
    public boolean vortexSheddingCritical;

    /** Critical crossflow velocity for fluid-elastic instability (m/s). */
    public double criticalVelocityMs;

    /** Ratio of actual crossflow velocity to reduced critical velocity. */
    public double velocityRatio;

    /** True if fluid-elastic instability threshold exceeded. */
    public boolean fluidElasticCritical;

    /** First-mode acoustic resonance frequency (Hz). */
    public double acousticFrequencyHz;

    /** Ratio of vortex shedding to acoustic resonance frequency. */
    public double acousticRatio;

    /** True if acoustic resonance near vortex shedding. */
    public boolean acousticCritical;

    /** Overall pass/fail: true if no critical vibration mechanism found. */
    public boolean passed;

    /**
     * Default constructor for VibrationResult.
     */
    public VibrationResult() {}

    /**
     * Returns a summary message indicating the vibration screening status.
     *
     * @return summary status string
     */
    public String getSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append("Vibration Screening: ").append(passed ? "PASS" : "FAIL").append("\n");
      sb.append(String.format("  Natural frequency: %.1f Hz%n", naturalFrequencyHz));
      sb.append(
          String.format("  Vortex shedding: %.1f Hz (ratio=%.2f) %s%n", vortexSheddingFrequencyHz,
              vortexSheddingRatio, vortexSheddingCritical ? "CRITICAL" : "OK"));
      sb.append(String.format("  Fluid-elastic: V_crit=%.2f m/s (ratio=%.2f) %s%n",
          criticalVelocityMs, velocityRatio, fluidElasticCritical ? "CRITICAL" : "OK"));
      sb.append(String.format("  Acoustic: %.1f Hz (ratio=%.2f) %s%n", acousticFrequencyHz,
          acousticRatio, acousticCritical ? "CRITICAL" : "OK"));
      return sb.toString();
    }
  }
}
