package neqsim.fluidmechanics.util;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * FlowRegimeDetector class.
 * </p>
 * 
 * <p>
 * Determines the two-phase flow regime in horizontal and inclined pipes based on fluid properties
 * and flow conditions. Implements flow pattern maps based on Taitel-Dukler (1976) theory and
 * extensions by Barnea et al.
 * </p>
 * 
 * <p>
 * Supported flow regimes:
 * <ul>
 * <li>Stratified smooth - low gas and liquid rates, gravity-dominated</li>
 * <li>Stratified wavy - stratified with interfacial waves</li>
 * <li>Slug - intermittent large liquid slugs and Taylor bubbles</li>
 * <li>Annular - high gas rate, liquid film on wall</li>
 * <li>Dispersed bubble - high liquid rate, dispersed gas bubbles</li>
 * <li>Mist/Droplet - very high gas rate, dispersed liquid droplets</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class FlowRegimeDetector {

  /** Gravitational acceleration. */
  private static final double GRAVITY = 9.81;

  /**
   * Flow regime enumeration.
   */
  public enum FlowRegime {
    /** Stratified smooth flow. */
    STRATIFIED_SMOOTH("stratified"),
    /** Stratified wavy flow. */
    STRATIFIED_WAVY("stratified"),
    /** Slug flow. */
    SLUG("slug"),
    /** Annular flow. */
    ANNULAR("annular"),
    /** Dispersed bubble flow. */
    BUBBLE("bubble"),
    /** Mist/Droplet flow. */
    DROPLET("droplet");

    private final String nodeName;

    FlowRegime(String nodeName) {
      this.nodeName = nodeName;
    }

    /**
     * Gets the flow node type name corresponding to this regime.
     *
     * @return flow node type name
     */
    public String getNodeName() {
      return nodeName;
    }
  }

  /**
   * Private constructor to prevent instantiation.
   */
  private FlowRegimeDetector() {}

  /**
   * <p>
   * Detects the flow regime for horizontal two-phase flow using Taitel-Dukler criteria.
   * </p>
   *
   * @param system the thermodynamic system with gas (phase 0) and liquid (phase 1)
   * @param gasVelocity superficial gas velocity in m/s
   * @param liquidVelocity superficial liquid velocity in m/s
   * @param pipeDiameter pipe inner diameter in m
   * @param inclination pipe inclination from horizontal in degrees (positive = uphill)
   * @return the detected flow regime
   */
  public static FlowRegime detectFlowRegime(SystemInterface system, double gasVelocity,
      double liquidVelocity, double pipeDiameter, double inclination) {

    // Get fluid properties
    double rhoG = system.getPhase(0).getDensity("kg/m3");
    double rhoL = system.getPhase(1).getDensity("kg/m3");
    double muG = system.getPhase(0).getViscosity("kg/msec");
    double muL = system.getPhase(1).getViscosity("kg/msec");
    double sigma = system.getInterphaseProperties().getSurfaceTension(0, 1);

    // Ensure surface tension has reasonable value
    if (sigma <= 0 || Double.isNaN(sigma)) {
      sigma = 0.02; // Default value for hydrocarbon-gas interface
    }

    return detectFlowRegime(gasVelocity, liquidVelocity, pipeDiameter, inclination, rhoG, rhoL, muG,
        muL, sigma);
  }

  /**
   * <p>
   * Detects the flow regime for horizontal two-phase flow using Taitel-Dukler criteria.
   * </p>
   *
   * @param gasVelocity superficial gas velocity in m/s
   * @param liquidVelocity superficial liquid velocity in m/s
   * @param pipeDiameter pipe inner diameter in m
   * @param inclination pipe inclination from horizontal in degrees (positive = uphill)
   * @param rhoG gas density in kg/m3
   * @param rhoL liquid density in kg/m3
   * @param muG gas viscosity in Pa·s
   * @param muL liquid viscosity in Pa·s
   * @param surfaceTension surface tension in N/m
   * @return the detected flow regime
   */
  public static FlowRegime detectFlowRegime(double gasVelocity, double liquidVelocity,
      double pipeDiameter, double inclination, double rhoG, double rhoL, double muG, double muL,
      double surfaceTension) {

    // Calculate dimensionless parameters

    // Mixture velocity
    double vmix = gasVelocity + liquidVelocity;

    // Gas void fraction (homogeneous model)
    double lambda = gasVelocity / (gasVelocity + liquidVelocity + 1e-10);

    // Froude numbers
    double frG = gasVelocity / Math.sqrt(GRAVITY * pipeDiameter);
    double frL = liquidVelocity / Math.sqrt(GRAVITY * pipeDiameter);
    double frMix = vmix / Math.sqrt(GRAVITY * pipeDiameter);

    // Weber number (gas phase)
    double weG = rhoG * gasVelocity * gasVelocity * pipeDiameter / surfaceTension;

    // Lockhart-Martinelli parameter
    double x = calculateLockhartMartinelli(gasVelocity, liquidVelocity, pipeDiameter, rhoG, rhoL,
        muG, muL);

    // Kutateladze number for gas
    double kuG =
        gasVelocity * Math.sqrt(rhoG) / Math.pow(GRAVITY * surfaceTension * (rhoL - rhoG), 0.25);

    // Inclination effects
    double inclinationRad = Math.toRadians(inclination);
    boolean isUphill = inclination > 1.0;
    boolean isDownhill = inclination < -1.0;
    boolean isHorizontal = !isUphill && !isDownhill;

    // ===== Flow regime boundaries (Taitel-Dukler based) =====

    // 1. Check for dispersed bubble flow
    // High liquid rates cause gas to break into small bubbles
    if (isDispersedBubble(frL, frMix, rhoL, rhoG, surfaceTension, pipeDiameter)) {
      return FlowRegime.BUBBLE;
    }

    // 2. Check for annular/mist flow
    // Very high gas rates entrain liquid as film or droplets
    if (isAnnularMist(kuG, frG, lambda)) {
      if (lambda > 0.99) {
        return FlowRegime.DROPLET;
      }
      return FlowRegime.ANNULAR;
    }

    // 3. For horizontal/near-horizontal pipes
    if (isHorizontal) {
      // Check stratified vs intermittent boundary
      if (isStratified(frG, x, pipeDiameter, rhoG, rhoL, muG, muL)) {
        // Stratified - check for waves
        if (isStratifiedWavy(frG, rhoG, rhoL)) {
          return FlowRegime.STRATIFIED_WAVY;
        }
        return FlowRegime.STRATIFIED_SMOOTH;
      } else {
        // Intermittent (slug) flow
        return FlowRegime.SLUG;
      }
    }

    // 4. For inclined pipes
    if (isUphill) {
      // Uphill flow favors slug and annular
      if (kuG > 3.1) {
        return FlowRegime.ANNULAR;
      }
      return FlowRegime.SLUG;
    }

    if (isDownhill) {
      // Downhill flow favors stratified
      if (frG < 0.5) {
        return FlowRegime.STRATIFIED_SMOOTH;
      } else if (frG < 2.0) {
        return FlowRegime.STRATIFIED_WAVY;
      }
      return FlowRegime.ANNULAR;
    }

    // Default to slug for intermediate cases
    return FlowRegime.SLUG;
  }

  /**
   * Calculates the Lockhart-Martinelli parameter.
   *
   * @param uSG superficial gas velocity [m/s]
   * @param uSL superficial liquid velocity [m/s]
   * @param d pipe diameter [m]
   * @param rhoG gas density [kg/m³]
   * @param rhoL liquid density [kg/m³]
   * @param muG gas viscosity [Pa·s]
   * @param muL liquid viscosity [Pa·s]
   * @return Lockhart-Martinelli parameter [-]
   */
  private static double calculateLockhartMartinelli(double uSG, double uSL, double d, double rhoG,
      double rhoL, double muG, double muL) {
    // Reynolds numbers
    double reG = rhoG * uSG * d / muG;
    double reL = rhoL * uSL * d / muL;

    // Friction factors (Blasius)
    double fG = (reG < 2000) ? 16 / reG : 0.046 * Math.pow(reG, -0.2);
    double fL = (reL < 2000) ? 16 / reL : 0.046 * Math.pow(reL, -0.2);

    // Pressure gradients
    double dpG = 2 * fG * rhoG * uSG * uSG / d;
    double dpL = 2 * fL * rhoL * uSL * uSL / d;

    return Math.sqrt(dpL / (dpG + 1e-10));
  }

  /**
   * Checks if flow is dispersed bubble regime.
   *
   * @param frL liquid Froude number
   * @param frMix mixture Froude number
   * @param rhoL liquid density [kg/m³]
   * @param rhoG gas density [kg/m³]
   * @param sigma surface tension [N/m]
   * @param d pipe diameter [m]
   * @return true if dispersed bubble flow
   */
  private static boolean isDispersedBubble(double frL, double frMix, double rhoL, double rhoG,
      double sigma, double d) {
    // Barnea (1987) criterion for bubble breakup
    double criticalVelocity = 4.0 * Math.pow(sigma * GRAVITY * (rhoL - rhoG) / (rhoL * rhoL), 0.25);
    return frL > 4.0 && frMix * Math.sqrt(GRAVITY * d) > criticalVelocity;
  }

  /**
   * Checks if flow is annular/mist regime.
   *
   * @param kuG gas Kutateladze number
   * @param frG gas Froude number
   * @param lambda gas void fraction (no-slip)
   * @return true if annular or mist flow
   */
  private static boolean isAnnularMist(double kuG, double frG, double lambda) {
    // Taitel-Dukler criterion: gas can support liquid film on wall
    return kuG > 3.1 || (frG > 1.5 && lambda > 0.85);
  }

  /**
   * Checks if flow is stratified (vs intermittent).
   *
   * @param frG gas Froude number
   * @param x Lockhart-Martinelli parameter
   * @param d pipe diameter [m]
   * @param rhoG gas density [kg/m³]
   * @param rhoL liquid density [kg/m³]
   * @param muG gas viscosity [Pa·s]
   * @param muL liquid viscosity [Pa·s]
   * @return true if stratified flow
   */
  private static boolean isStratified(double frG, double x, double d, double rhoG, double rhoL,
      double muG, double muL) {
    // Simplified Taitel-Dukler criterion
    // Stratified when gas velocity too low to pick up liquid waves
    double densityRatio = rhoL / (rhoG + 1e-10);

    // Critical Froude number depends on liquid level
    double criticalFr = 0.5 * Math.pow(densityRatio, 0.25) / (x + 0.1);

    return frG < criticalFr && frG < 1.0;
  }

  /**
   * Checks if stratified flow has waves.
   *
   * @param frG gas Froude number
   * @param rhoG gas density [kg/m³]
   * @param rhoL liquid density [kg/m³]
   * @return true if stratified wavy flow
   */
  private static boolean isStratifiedWavy(double frG, double rhoG, double rhoL) {
    // Kelvin-Helmholtz instability criterion
    double densityRatio = rhoG / rhoL;
    double criticalFr = 0.1 * Math.pow(densityRatio, -0.5);
    return frG > criticalFr;
  }

  /**
   * <p>
   * Gets the flow node type name for the detected regime.
   * </p>
   *
   * @param system the thermodynamic system
   * @param gasVelocity superficial gas velocity in m/s
   * @param liquidVelocity superficial liquid velocity in m/s
   * @param pipeDiameter pipe inner diameter in m
   * @param inclination pipe inclination from horizontal in degrees
   * @return flow pattern name for use with setInitialFlowPattern()
   */
  public static String detectFlowPatternName(SystemInterface system, double gasVelocity,
      double liquidVelocity, double pipeDiameter, double inclination) {
    FlowRegime regime =
        detectFlowRegime(system, gasVelocity, liquidVelocity, pipeDiameter, inclination);
    return regime.getNodeName();
  }
}
