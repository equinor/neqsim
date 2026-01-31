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

  // ==================== ENHANCED MODELS ====================

  /**
   * Calculates interfacial area for stratified wavy flow with wave enhancement.
   *
   * <p>
   * Accounts for the increased interfacial area due to wave formation at the gas-liquid interface.
   * Based on Kelvin-Helmholtz instability analysis.
   * </p>
   *
   * <p>
   * Reference: Tzotzi, C., Andritsos, N. (2013). Interfacial shear stress in wavy stratified
   * gas-liquid flow. Chemical Engineering Science, 86, 49-57.
   * </p>
   *
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup (0-1)
   * @param usg superficial gas velocity (m/s)
   * @param usl superficial liquid velocity (m/s)
   * @param rhoG gas density (kg/m³)
   * @param rhoL liquid density (kg/m³)
   * @param sigma surface tension (N/m)
   * @return interfacial area per unit volume with wave enhancement (1/m)
   */
  public static double calculateStratifiedWavyArea(double diameter, double liquidHoldup, double usg,
      double usl, double rhoG, double rhoL, double sigma) {
    // Base stratified area
    double aFlat = calculateStratifiedArea(diameter, liquidHoldup);

    if (aFlat <= 0 || sigma <= 0) {
      return aFlat;
    }

    // Calculate actual phase velocities
    double alphaL = liquidHoldup;
    double alphaG = 1.0 - liquidHoldup;

    if (alphaL < 0.01 || alphaG < 0.01) {
      return aFlat;
    }

    double uL = usl / alphaL;
    double uG = usg / alphaG;

    // Kelvin-Helmholtz critical velocity for wave onset
    // V_crit = sqrt(sigma * (rhoL - rhoG) * g / (rhoG * rhoL))^0.5 * correction
    double deltaRho = Math.abs(rhoL - rhoG);
    double criticalVelocity = Math.sqrt(sigma * deltaRho * G / (rhoG * rhoL + 1e-10));

    // Relative velocity between phases
    double relativeVelocity = Math.abs(uG - uL);

    // Wave enhancement factor (Tzotzi & Andritsos, 2013)
    double enhancementFactor = 1.0;
    if (relativeVelocity > criticalVelocity && criticalVelocity > 0) {
      // Wave amplitude estimation
      double velocityRatio = relativeVelocity / criticalVelocity;
      double waveAmplitude = 0.02 * diameter * (velocityRatio - 1.0);

      // Wave frequency factor (increases with velocity)
      double waveFrequencyFactor = 1.0 + 0.5 * Math.log(velocityRatio);

      // Combined enhancement: area increase due to wavy interface
      enhancementFactor = 1.0 + 2.0 * Math.PI * waveAmplitude * waveFrequencyFactor / diameter;

      // Cap enhancement at reasonable limit (literature suggests max 3-4x)
      enhancementFactor = Math.min(enhancementFactor, 3.5);
    }

    return aFlat * enhancementFactor;
  }

  /**
   * Calculates interfacial area for annular flow including droplet entrainment.
   *
   * <p>
   * Accounts for both the film interface and entrained droplets in the gas core. The entrainment
   * correlation is based on Ishii and Mishima (1989).
   * </p>
   *
   * <p>
   * Reference: Ishii, M., Mishima, K. (1989). Droplet entrainment correlation in annular two-phase
   * flow. International Journal of Heat and Mass Transfer, 32(10), 1835-1846.
   * </p>
   *
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup (0-1)
   * @param rhoG gas density (kg/m³)
   * @param rhoL liquid density (kg/m³)
   * @param usg superficial gas velocity (m/s)
   * @param muL liquid viscosity (Pa·s)
   * @param sigma surface tension (N/m)
   * @return interfacial area per unit volume including entrainment (1/m)
   */
  public static double calculateAnnularAreaWithEntrainment(double diameter, double liquidHoldup,
      double rhoG, double rhoL, double usg, double muL, double sigma) {
    // Film interface area
    double aFilm = calculateAnnularArea(diameter, liquidHoldup);

    if (aFilm <= 0 || sigma <= 0 || rhoG <= 0 || usg <= 0) {
      return aFilm;
    }

    // Gas Weber number
    double weG = rhoG * usg * usg * diameter / sigma;

    // Entrainment onset Weber number (Ishii & Mishima)
    double weOnset = 1.0;

    if (weG < weOnset) {
      return aFilm; // No entrainment below onset
    }

    // Entrainment fraction correlation (Ishii & Mishima, 1989)
    // E = tanh(7.25e-7 * We_G^1.25 * Re_L^0.25)
    double reL = rhoL * usg * diameter / (muL + 1e-10);
    double entrainmentFraction = Math.tanh(7.25e-7 * Math.pow(weG, 1.25) * Math.pow(reL, 0.25));

    // Limit entrainment to reasonable values
    entrainmentFraction = Math.min(entrainmentFraction, 0.95);

    if (entrainmentFraction < 0.01) {
      return aFilm;
    }

    // Droplet Sauter mean diameter (Azzopardi correlation)
    // d_32 / D = 0.069 * We_G^(-0.5)
    double d32Droplet = 0.069 * diameter * Math.pow(weG, -0.5);
    d32Droplet = Math.max(d32Droplet, 1e-5); // Minimum 10 microns
    d32Droplet = Math.min(d32Droplet, diameter / 20.0); // Maximum D/20

    // Droplet holdup (entrained liquid)
    double dropletHoldup = liquidHoldup * entrainmentFraction;

    // Droplet interfacial area: a = 6 * alpha / d_32
    double aDroplet = 6.0 * dropletHoldup / d32Droplet;

    // Film area should be reduced due to entrainment
    double filmHoldup = liquidHoldup * (1.0 - entrainmentFraction);
    double aFilmReduced = 0.0;
    if (filmHoldup > 0.001) {
      aFilmReduced = calculateAnnularArea(diameter, filmHoldup);
    }

    return aFilmReduced + aDroplet;
  }

  /**
   * Calculates enhanced interfacial area using the most appropriate model.
   *
   * <p>
   * This method automatically selects and applies enhancement factors based on flow pattern and
   * fluid properties. It should be used when accurate mass transfer calculations are needed.
   * </p>
   *
   * @param flowPattern the flow pattern
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup (0-1)
   * @param rhoG gas density (kg/m³)
   * @param rhoL liquid density (kg/m³)
   * @param usg superficial gas velocity (m/s)
   * @param usl superficial liquid velocity (m/s)
   * @param muL liquid viscosity (Pa·s)
   * @param sigma surface tension (N/m)
   * @param includeWaveEnhancement whether to include wave effects for stratified flow
   * @param includeEntrainment whether to include droplet entrainment for annular flow
   * @return interfacial area per unit volume (1/m)
   */
  public static double calculateEnhancedInterfacialArea(FlowPattern flowPattern, double diameter,
      double liquidHoldup, double rhoG, double rhoL, double usg, double usl, double muL,
      double sigma, boolean includeWaveEnhancement, boolean includeEntrainment) {

    switch (flowPattern) {
      case STRATIFIED_WAVY:
        if (includeWaveEnhancement) {
          return calculateStratifiedWavyArea(diameter, liquidHoldup, usg, usl, rhoG, rhoL, sigma);
        }
        return calculateStratifiedArea(diameter, liquidHoldup);

      case STRATIFIED:
        // Smooth stratified - may still have small waves at higher velocities
        if (includeWaveEnhancement) {
          // Apply reduced wave enhancement for nominally smooth stratified
          double aBase = calculateStratifiedArea(diameter, liquidHoldup);
          double aWavy =
              calculateStratifiedWavyArea(diameter, liquidHoldup, usg, usl, rhoG, rhoL, sigma);
          return 0.3 * aWavy + 0.7 * aBase; // Blend
        }
        return calculateStratifiedArea(diameter, liquidHoldup);

      case ANNULAR:
        if (includeEntrainment) {
          return calculateAnnularAreaWithEntrainment(diameter, liquidHoldup, rhoG, rhoL, usg, muL,
              sigma);
        }
        return calculateAnnularArea(diameter, liquidHoldup);

      case SLUG:
        return calculateSlugArea(diameter, liquidHoldup, rhoG, rhoL, usg, usl, sigma);

      case BUBBLE:
      case DISPERSED_BUBBLE:
        return calculateBubbleArea(diameter, liquidHoldup, rhoG, rhoL, sigma);

      case DROPLET:
        return calculateDropletArea(diameter, liquidHoldup, rhoG, usg, sigma);

      case CHURN:
        // Churn flow benefits from both wave and entrainment effects
        double aChurnBase = calculateChurnArea(diameter, liquidHoldup);
        if (includeEntrainment) {
          double aEntrained = calculateAnnularAreaWithEntrainment(diameter, liquidHoldup, rhoG,
              rhoL, usg, muL, sigma);
          return 0.5 * (aChurnBase + aEntrained);
        }
        return aChurnBase;

      default:
        return calculateStratifiedArea(diameter, liquidHoldup);
    }
  }

  // ==================== LITERATURE VALIDATION DATA ====================

  /**
   * Returns the expected interfacial area range for validation against literature data.
   *
   * <p>
   * Based on experimental data from various sources including:
   * </p>
   * <ul>
   * <li>Hewitt, G.F., Hall-Taylor, N.S. (1970). Annular Two-Phase Flow.</li>
   * <li>Taitel, Y., Dukler, A.E. (1976). Model for predicting flow regime transitions.</li>
   * <li>Ishii, M., Hibiki, T. (2011). Thermo-Fluid Dynamics of Two-Phase Flow.</li>
   * </ul>
   *
   * @param flowPattern the flow pattern
   * @param diameter pipe diameter (m)
   * @return array containing [min, typical, max] interfacial area (1/m)
   */
  public static double[] getExpectedInterfacialAreaRange(FlowPattern flowPattern, double diameter) {
    double[] range = new double[3]; // [min, typical, max]

    switch (flowPattern) {
      case STRATIFIED:
      case STRATIFIED_WAVY:
        // Typical stratified: a = 4/D to 10/D depending on waves
        range[0] = 2.0 / diameter;
        range[1] = 5.0 / diameter;
        range[2] = 15.0 / diameter;
        break;

      case ANNULAR:
        // Annular with entrainment: 10/D to 200/D
        range[0] = 8.0 / diameter;
        range[1] = 50.0 / diameter;
        range[2] = 300.0 / diameter;
        break;

      case SLUG:
        // Slug flow: highly variable
        range[0] = 5.0 / diameter;
        range[1] = 30.0 / diameter;
        range[2] = 100.0 / diameter;
        break;

      case BUBBLE:
      case DISPERSED_BUBBLE:
        // Bubble flow: depends strongly on bubble size
        range[0] = 50.0;
        range[1] = 200.0;
        range[2] = 1000.0;
        break;

      case DROPLET:
        // Mist/droplet flow: very high area
        range[0] = 100.0;
        range[1] = 500.0;
        range[2] = 2000.0;
        break;

      case CHURN:
        // Churn flow: intermediate
        range[0] = 20.0 / diameter;
        range[1] = 80.0 / diameter;
        range[2] = 200.0 / diameter;
        break;

      default:
        range[0] = 1.0 / diameter;
        range[1] = 10.0 / diameter;
        range[2] = 100.0 / diameter;
    }

    return range;
  }
}

