package neqsim.fluidmechanics.flownode;

/**
 * Utility class for calculating interfacial area per unit volume in two-phase flow.
 *
 * <p>
 * The interfacial area per unit volume (a, in m²/m³ = 1/m) is critical for interphase mass and heat
 * transfer calculations. This class provides models for different flow patterns.
 * </p>
 *
 * <p>
 * <b>Flow Pattern Models:</b>
 * </p>
 * <ul>
 * <li><b>Stratified:</b> Flat interface: a = S_i / A where S_i is interface chord length</li>
 * <li><b>Annular:</b> Film interface with core gas flow</li>
 * <li><b>Slug:</b> Weighted average of Taylor bubble and liquid slug regions</li>
 * <li><b>Bubble:</b> Based on Sauter mean diameter: a = 6·α_G / d_32</li>
 * <li><b>Droplet:</b> Based on droplet size from Weber number</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class InterfacialAreaCalculator {

  /** Gravitational acceleration (m/s²). */
  private static final double G = 9.81;

  /**
   * Private constructor to prevent instantiation.
   */
  private InterfacialAreaCalculator() {}

  /**
   * Calculates the interfacial area per unit volume for the given flow pattern.
   *
   * @param flowPattern the flow pattern
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup (volume fraction, 0-1)
   * @param rhoG gas density (kg/m³)
   * @param rhoL liquid density (kg/m³)
   * @param usg superficial gas velocity (m/s)
   * @param usl superficial liquid velocity (m/s)
   * @param sigma surface tension (N/m)
   * @return interfacial area per unit volume (1/m)
   */
  public static double calculateInterfacialArea(FlowPattern flowPattern, double diameter,
      double liquidHoldup, double rhoG, double rhoL, double usg, double usl, double sigma) {
    switch (flowPattern) {
      case STRATIFIED:
      case STRATIFIED_WAVY:
        return calculateStratifiedArea(diameter, liquidHoldup);
      case ANNULAR:
        return calculateAnnularArea(diameter, liquidHoldup);
      case SLUG:
        return calculateSlugArea(diameter, liquidHoldup, rhoG, rhoL, usg, usl, sigma);
      case BUBBLE:
      case DISPERSED_BUBBLE:
        return calculateBubbleArea(diameter, liquidHoldup, rhoG, rhoL, sigma);
      case DROPLET:
        return calculateDropletArea(diameter, liquidHoldup, rhoG, usg, sigma);
      case CHURN:
        return calculateChurnArea(diameter, liquidHoldup);
      default:
        return calculateStratifiedArea(diameter, liquidHoldup);
    }
  }

  /**
   * Calculates interfacial area for stratified flow.
   *
   * <p>
   * For stratified flow, the interface is approximately flat. The interfacial area per unit volume
   * is calculated from the chord length at the gas-liquid interface.
   * </p>
   *
   * <pre>
   * For a circular pipe with liquid level h:
   * θ = 2·arccos(1 - 2h/D)
   * S_i = D·sin(θ/2) (interface chord length)
   * A = π·D²/4 (pipe cross-sectional area)
   * a = S_i / A = 4·sin(θ/2) / (π·D)
   * </pre>
   *
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup
   * @return interfacial area per unit volume (1/m)
   */
  public static double calculateStratifiedArea(double diameter, double liquidHoldup) {
    if (liquidHoldup <= 0.0 || liquidHoldup >= 1.0) {
      return 0.0;
    }

    // Calculate liquid level from holdup
    // Using approximation: h/D ≈ holdup for small angles
    double hOverD = calculateLevelFromHoldup(liquidHoldup);

    // Calculate angle subtended by liquid
    double theta = 2.0 * Math.acos(1.0 - 2.0 * hOverD);

    // Interface chord length
    double Si = diameter * Math.sin(theta / 2.0);

    // Pipe cross-sectional area
    double A = Math.PI * diameter * diameter / 4.0;

    return Si / A;
  }

  /**
   * Calculates interfacial area for annular flow.
   *
   * <p>
   * In annular flow, liquid forms a film on the pipe wall with gas flowing in the core. The
   * interfacial area is the inner surface of the liquid film.
   * </p>
   *
   * <pre>
   * Film thickness: δ = D/2 · (1 - sqrt(1 - α_L))
   * Core diameter: D_c = D - 2δ = D·sqrt(1 - α_L)
   * Interfacial area: a = 4/(D·sqrt(1 - α_L))
   * </pre>
   *
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup
   * @return interfacial area per unit volume (1/m)
   */
  public static double calculateAnnularArea(double diameter, double liquidHoldup) {
    if (liquidHoldup <= 0.0 || liquidHoldup >= 1.0) {
      return 0.0;
    }

    // Gas void fraction
    double alphaG = 1.0 - liquidHoldup;

    // Core diameter ratio (D_core / D)
    double coreDiameterRatio = Math.sqrt(alphaG);

    // Interfacial area per unit volume
    // a = (perimeter of core) / (pipe area) = π·D_c / (π·D²/4) = 4/D · (D_c/D)
    return 4.0 / (diameter * coreDiameterRatio);
  }

  /**
   * Calculates interfacial area for slug flow.
   *
   * <p>
   * Slug flow consists of alternating Taylor bubbles and liquid slugs. The total interfacial area
   * is a weighted average of the two regions.
   * </p>
   *
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup
   * @param rhoG gas density (kg/m³)
   * @param rhoL liquid density (kg/m³)
   * @param usg superficial gas velocity (m/s)
   * @param usl superficial liquid velocity (m/s)
   * @param sigma surface tension (N/m)
   * @return interfacial area per unit volume (1/m)
   */
  public static double calculateSlugArea(double diameter, double liquidHoldup, double rhoG,
      double rhoL, double usg, double usl, double sigma) {
    if (liquidHoldup <= 0.0 || liquidHoldup >= 1.0) {
      return 0.0;
    }

    // Estimate Taylor bubble fraction
    double voidFraction = 1.0 - liquidHoldup;
    double bubbleFraction = Math.min(0.8, voidFraction / 0.7); // Typical void in Taylor bubble ~0.7

    // Taylor bubble region - similar to annular
    double aTaylor = 4.0 / diameter; // Approximate for thin film

    // Liquid slug region - contains small dispersed bubbles
    double slugVoid = Math.max(0.0, (voidFraction - bubbleFraction * 0.7) / (1.0 - bubbleFraction));
    double aSlug = calculateBubbleArea(diameter, 1.0 - slugVoid, rhoG, rhoL, sigma);

    // Weighted average
    return bubbleFraction * aTaylor + (1.0 - bubbleFraction) * aSlug;
  }

  /**
   * Calculates interfacial area for bubble flow.
   *
   * <p>
   * In bubble flow, small gas bubbles are dispersed in the liquid. The interfacial area is
   * calculated from the Sauter mean diameter of bubbles.
   * </p>
   *
   * <pre>
   * a = 6·α_G / d_32
   * </pre>
   *
   * <p>
   * The maximum stable bubble size is calculated using Hinze theory:
   * </p>
   *
   * <pre>
   * d_max = 0.725·(σ/ρ_L)^0.6 · ε^(-0.4)
   * </pre>
   *
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup
   * @param rhoG gas density (kg/m³)
   * @param rhoL liquid density (kg/m³)
   * @param sigma surface tension (N/m)
   * @return interfacial area per unit volume (1/m)
   */
  public static double calculateBubbleArea(double diameter, double liquidHoldup, double rhoG,
      double rhoL, double sigma) {
    if (liquidHoldup <= 0.0 || liquidHoldup >= 1.0) {
      return 0.0;
    }

    double voidFraction = 1.0 - liquidHoldup;

    // Estimate turbulent energy dissipation rate (simplified)
    // ε ≈ u³/D for fully turbulent pipe flow
    // Using mixture velocity estimate
    double uMix = 2.0; // Typical value, m/s
    double epsilon = Math.pow(uMix, 3) / diameter;

    // Maximum stable bubble diameter using Hinze correlation
    double dMax = 0.725 * Math.pow(sigma / rhoL, 0.6) * Math.pow(epsilon, -0.4);

    // Limit bubble size to reasonable range
    dMax = Math.min(dMax, diameter / 4.0);
    dMax = Math.max(dMax, 0.001); // Minimum 1 mm

    // Sauter mean diameter is typically smaller than max
    double d32 = 0.6 * dMax;

    // Interfacial area per unit volume
    return 6.0 * voidFraction / d32;
  }

  /**
   * Calculates interfacial area for droplet/mist flow.
   *
   * <p>
   * In mist flow, liquid droplets are entrained in the gas core. The droplet size is determined by
   * the critical Weber number criterion.
   * </p>
   *
   * <pre>
   * d_max = We_crit · σ / (ρ_G · u_G²)
   * a = 6·α_L / d_32
   * </pre>
   *
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup
   * @param rhoG gas density (kg/m³)
   * @param usg superficial gas velocity (m/s)
   * @param sigma surface tension (N/m)
   * @return interfacial area per unit volume (1/m)
   */
  public static double calculateDropletArea(double diameter, double liquidHoldup, double rhoG,
      double usg, double sigma) {
    if (liquidHoldup <= 0.0 || liquidHoldup >= 1.0) {
      return 0.0;
    }

    // Critical Weber number for droplet breakup
    double WeCrit = 12.0;

    // Maximum stable droplet diameter
    double dMax = WeCrit * sigma / (rhoG * usg * usg + 1e-10);

    // Limit droplet size to reasonable range
    dMax = Math.min(dMax, diameter / 10.0);
    dMax = Math.max(dMax, 0.0001); // Minimum 0.1 mm

    // Sauter mean diameter
    double d32 = 0.5 * dMax;

    // Interfacial area per unit volume
    return 6.0 * liquidHoldup / d32;
  }

  /**
   * Calculates interfacial area for churn flow.
   *
   * <p>
   * Churn flow is a transitional regime with highly irregular interface. Uses an intermediate value
   * between annular and bubble.
   * </p>
   *
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup
   * @return interfacial area per unit volume (1/m)
   */
  public static double calculateChurnArea(double diameter, double liquidHoldup) {
    // Use average of annular and high bubble area
    double aAnnular = calculateAnnularArea(diameter, liquidHoldup);
    double aBubble = 4.0 / diameter; // Simplified high turbulence value
    return 0.5 * (aAnnular + aBubble);
  }

  /**
   * Calculates liquid level from holdup for stratified flow in a circular pipe.
   *
   * <p>
   * Uses an iterative solution to the geometric relationship between liquid level and holdup in a
   * circular cross-section.
   * </p>
   *
   * @param holdup liquid holdup (0-1)
   * @return liquid level as fraction of diameter (h/D)
   */
  private static double calculateLevelFromHoldup(double holdup) {
    // Use Newton-Raphson iteration to solve:
    // holdup = (θ - sin(θ)) / (2π) where θ = 2·arccos(1 - 2h/D)

    // Initial guess using linear approximation
    double hOverD = holdup;

    // For low holdups, use better initial guess
    if (holdup < 0.1) {
      hOverD = Math.pow(3.0 * Math.PI * holdup / 4.0, 2.0 / 3.0) / 2.0;
    }

    // Newton-Raphson iteration (usually converges in 3-5 iterations)
    for (int i = 0; i < 10; i++) {
      double theta = 2.0 * Math.acos(Math.max(-1.0, Math.min(1.0, 1.0 - 2.0 * hOverD)));
      double f = (theta - Math.sin(theta)) / (2.0 * Math.PI) - holdup;
      double dfdh = 4.0 * Math.sin(theta / 2.0) / Math.PI; // Derivative

      if (Math.abs(dfdh) < 1e-10) {
        break;
      }

      double correction = f / dfdh;
      hOverD = hOverD - correction;

      // Bound the result
      hOverD = Math.max(0.001, Math.min(0.999, hOverD));

      if (Math.abs(correction) < 1e-8) {
        break;
      }
    }

    return hOverD;
  }

  /**
   * Calculates the Sauter mean diameter for dispersed bubbles or droplets.
   *
   * @param rhoDispersed density of dispersed phase (kg/m³)
   * @param rhoContinuous density of continuous phase (kg/m³)
   * @param sigma surface tension (N/m)
   * @param epsilon turbulent energy dissipation rate (m²/s³)
   * @return Sauter mean diameter d_32 (m)
   */
  public static double calculateSauterDiameter(double rhoDispersed, double rhoContinuous,
      double sigma, double epsilon) {
    // Hinze (1955) correlation for maximum stable droplet/bubble size
    double dMax = 0.725 * Math.pow(sigma / rhoContinuous, 0.6) * Math.pow(epsilon, -0.4);

    // Sauter mean is typically 0.5-0.7 of max
    return 0.6 * dMax;
  }
}
