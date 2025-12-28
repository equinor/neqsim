package neqsim.fluidmechanics.flownode;

/**
 * Utility class for calculating mass transfer coefficients in two-phase pipe flow.
 *
 * <p>
 * Implements flow pattern-specific Sherwood number correlations for calculating liquid-side and
 * gas-side mass transfer coefficients.
 * </p>
 *
 * <p>
 * <b>Correlations by Flow Pattern:</b>
 * </p>
 * <ul>
 * <li><b>Stratified:</b> Sh = f(Re, Sc, geometry) - Solbraa (2002)</li>
 * <li><b>Annular:</b> Sh = f(Re_film, Sc, wave amplitude) - Hewitt &amp; Hall-Taylor</li>
 * <li><b>Slug:</b> Sh_bubble + Sh_slug weighted - Fernandes et al.</li>
 * <li><b>Bubble:</b> Sh = 2 + 0.6·Re^0.5·Sc^0.33 - Ranz-Marshall</li>
 * <li><b>Droplet:</b> Sh = 2 + 0.6·Re^0.5·Sc^0.33 - Ranz-Marshall</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class MassTransferCoefficientCalculator {

  /**
   * Private constructor to prevent instantiation.
   */
  private MassTransferCoefficientCalculator() {}

  /**
   * Calculates the liquid-side mass transfer coefficient.
   *
   * @param flowPattern the flow pattern
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup
   * @param usg superficial gas velocity (m/s)
   * @param usl superficial liquid velocity (m/s)
   * @param rhoL liquid density (kg/m³)
   * @param muL liquid viscosity (Pa·s)
   * @param diffL liquid diffusivity (m²/s)
   * @return liquid-side mass transfer coefficient k_L (m/s), always non-negative
   */
  public static double calculateLiquidMassTransferCoefficient(FlowPattern flowPattern,
      double diameter, double liquidHoldup, double usg, double usl, double rhoL, double muL,
      double diffL) {

    // Validate inputs - return 0 for invalid values
    if (diameter <= 0 || diffL <= 0 || rhoL <= 0 || muL <= 0) {
      return 0.0;
    }

    // Calculate liquid Schmidt number
    double scL = muL / (rhoL * diffL);
    if (scL <= 0 || Double.isNaN(scL)) {
      return 0.0;
    }

    double kL;
    switch (flowPattern) {
      case STRATIFIED:
      case STRATIFIED_WAVY:
        kL = calculateStratifiedKL(diameter, liquidHoldup, usl, rhoL, muL, diffL, scL);
        break;
      case ANNULAR:
        kL = calculateAnnularKL(diameter, liquidHoldup, usl, rhoL, muL, diffL, scL);
        break;
      case SLUG:
        kL = calculateSlugKL(diameter, liquidHoldup, usg, usl, rhoL, muL, diffL, scL);
        break;
      case BUBBLE:
      case DISPERSED_BUBBLE:
        kL = calculateBubbleKL(diameter, liquidHoldup, usg, usl, rhoL, muL, diffL, scL);
        break;
      case DROPLET:
        kL = calculateDropletKL(diameter, usg, rhoL, muL, diffL, scL);
        break;
      case CHURN:
        kL = calculateChurnKL(diameter, liquidHoldup, usg, usl, rhoL, muL, diffL, scL);
        break;
      default:
        kL = calculateStratifiedKL(diameter, liquidHoldup, usl, rhoL, muL, diffL, scL);
    }

    return Math.max(0.0, kL);
  }

  /**
   * Calculates the gas-side mass transfer coefficient.
   *
   * @param flowPattern the flow pattern
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup
   * @param usg superficial gas velocity (m/s)
   * @param rhoG gas density (kg/m³)
   * @param muG gas viscosity (Pa·s)
   * @param diffG gas diffusivity (m²/s)
   * @return gas-side mass transfer coefficient k_G (m/s), always non-negative
   */
  public static double calculateGasMassTransferCoefficient(FlowPattern flowPattern, double diameter,
      double liquidHoldup, double usg, double rhoG, double muG, double diffG) {

    // Validate inputs - return 0 for invalid values
    if (diameter <= 0 || diffG <= 0 || rhoG <= 0 || muG <= 0) {
      return 0.0;
    }

    // Calculate gas Schmidt number
    double scG = muG / (rhoG * diffG);
    if (scG <= 0 || Double.isNaN(scG)) {
      return 0.0;
    }

    // Gas void fraction
    double voidFraction = 1.0 - liquidHoldup;

    // Effective gas velocity
    double uG = usg / Math.max(voidFraction, 0.01);

    double kG;
    switch (flowPattern) {
      case STRATIFIED:
      case STRATIFIED_WAVY:
        kG = calculateStratifiedKG(diameter, voidFraction, uG, rhoG, muG, diffG, scG);
        break;
      case ANNULAR:
        kG = calculateAnnularKG(diameter, voidFraction, uG, rhoG, muG, diffG, scG);
        break;
      case BUBBLE:
      case DISPERSED_BUBBLE:
      case SLUG:
        kG = calculateBubbleKG(diameter, uG, rhoG, muG, diffG, scG);
        break;
      case DROPLET:
        kG = calculateDropletKG(diameter, uG, rhoG, muG, diffG, scG);
        break;
      case CHURN:
        kG = calculateChurnKG(diameter, uG, rhoG, muG, diffG, scG);
        break;
      default:
        kG = calculateStratifiedKG(diameter, voidFraction, uG, rhoG, muG, diffG, scG);
    }

    return Math.max(0.0, kG);
  }

  /**
   * Calculates Sherwood number using the Dittus-Boelter analogy.
   *
   * <pre>
   * Sh = 0.023 · Re^0.8 · Sc^0.33
   * </pre>
   *
   * @param re Reynolds number
   * @param sc Schmidt number
   * @return Sherwood number (minimum 0)
   */
  public static double calculateDittusBoelterSherwood(double re, double sc) {
    if (re <= 0 || sc <= 0 || Double.isNaN(re) || Double.isNaN(sc)) {
      return 0.0;
    }
    return 0.023 * Math.pow(re, 0.8) * Math.pow(sc, 0.33);
  }

  /**
   * Calculates Sherwood number using the Ranz-Marshall correlation for spheres.
   *
   * <pre>
   * Sh = 2 + 0.6 · Re^0.5 · Sc^0.33
   * </pre>
   *
   * @param re Reynolds number based on particle diameter
   * @param sc Schmidt number
   * @return Sherwood number (minimum 2 for still fluid)
   */
  public static double calculateRanzMarshallSherwood(double re, double sc) {
    if (re <= 0 || sc <= 0 || Double.isNaN(re) || Double.isNaN(sc)) {
      return 2.0; // Still fluid limit
    }
    return 2.0 + 0.6 * Math.pow(re, 0.5) * Math.pow(sc, 0.33);
  }

  // ============= Liquid-side correlations =============

  /**
   * Calculates liquid-side mass transfer coefficient for stratified flow.
   *
   * @param diameter pipe diameter [m]
   * @param liquidHoldup liquid holdup fraction [-]
   * @param usl superficial liquid velocity [m/s]
   * @param rhoL liquid density [kg/m³]
   * @param muL liquid viscosity [Pa·s]
   * @param diffL liquid diffusivity [m²/s]
   * @param scL liquid Schmidt number [-]
   * @return liquid-side mass transfer coefficient [m/s]
   */
  private static double calculateStratifiedKL(double diameter, double liquidHoldup, double usl,
      double rhoL, double muL, double diffL, double scL) {

    // Hydraulic diameter for liquid phase
    double dh = calculateLiquidHydraulicDiameter(diameter, liquidHoldup);

    // Liquid Reynolds number based on hydraulic diameter
    double uL = usl / Math.max(liquidHoldup, 0.01);
    double reL = rhoL * uL * dh / muL;

    // Use Dittus-Boelter for turbulent, laminar approximation for low Re
    double sh;
    if (reL > 2300) {
      sh = calculateDittusBoelterSherwood(reL, scL);
    } else {
      // Graetz-Leveque for laminar: Sh = 1.62 (Re·Sc·dh/L)^0.33
      // Simplified for developed flow: Sh ≈ 3.66
      sh = 3.66 + 0.0668 * reL * scL * dh / diameter;
    }

    return sh * diffL / dh;
  }

  /**
   * Calculates liquid-side mass transfer coefficient for annular flow.
   *
   * @param diameter pipe diameter [m]
   * @param liquidHoldup liquid holdup fraction [-]
   * @param usl superficial liquid velocity [m/s]
   * @param rhoL liquid density [kg/mA3]
   * @param muL liquid viscosity [PaAús]
   * @param diffL liquid diffusivity [mAı/s]
   * @param scL liquid Schmidt number [-]
   * @return liquid-side mass transfer coefficient [m/s]
   */
  private static double calculateAnnularKL(double diameter, double liquidHoldup, double usl,
      double rhoL, double muL, double diffL, double scL) {

    // Film thickness
    double delta = diameter / 2.0 * (1.0 - Math.sqrt(1.0 - liquidHoldup));

    // Film Reynolds number
    double uFilm = usl / Math.max(liquidHoldup, 0.01);
    double reFilm = rhoL * uFilm * delta / muL;

    // Sherwood for falling film (Vivian & Peaceman)
    double sh;
    if (reFilm > 400) {
      // Turbulent film: Sh = 0.0096 Re^0.87 Sc^0.5
      sh = 0.0096 * Math.pow(reFilm, 0.87) * Math.pow(scL, 0.5);
    } else {
      // Laminar film: Sh ≈ 3.0 (penetration theory)
      sh = 3.0 + 0.01 * reFilm * Math.pow(scL, 0.33);
    }

    return sh * diffL / delta;
  }

  /**
   * Calculates liquid-side mass transfer coefficient for slug flow.
   *
   * @param diameter pipe diameter [m]
   * @param liquidHoldup liquid holdup fraction [-]
   * @param usg superficial gas velocity [m/s]
   * @param usl superficial liquid velocity [m/s]
   * @param rhoL liquid density [kg/mA3]
   * @param muL liquid viscosity [PaAús]
   * @param diffL liquid diffusivity [mAı/s]
   * @param scL liquid Schmidt number [-]
   * @return liquid-side mass transfer coefficient [m/s]
   */
  private static double calculateSlugKL(double diameter, double liquidHoldup, double usg,
      double usl, double rhoL, double muL, double diffL, double scL) {

    // Slug flow is combination of film (in Taylor bubble region) and mixed (in slug)
    double voidFraction = 1.0 - liquidHoldup;
    double bubbleFraction = Math.min(0.8, voidFraction / 0.7);

    // Film region: use annular correlation
    double kL_film = calculateAnnularKL(diameter, liquidHoldup, usl, rhoL, muL, diffL, scL);

    // Slug region: enhanced mixing, use higher turbulence
    double uMix = usg + usl;
    double reMix = rhoL * uMix * diameter / muL;
    double sh_slug = 0.05 * Math.pow(reMix, 0.8) * Math.pow(scL, 0.33);
    double kL_slug = sh_slug * diffL / diameter;

    // Weighted average
    return bubbleFraction * kL_film + (1.0 - bubbleFraction) * kL_slug;
  }

  /**
   * Calculates liquid-side mass transfer coefficient for bubble flow.
   *
   * @param diameter pipe diameter [m]
   * @param liquidHoldup liquid holdup fraction [-]
   * @param usg superficial gas velocity [m/s]
   * @param usl superficial liquid velocity [m/s]
   * @param rhoL liquid density [kg/mA3]
   * @param muL liquid viscosity [PaAús]
   * @param diffL liquid diffusivity [mAı/s]
   * @param scL liquid Schmidt number [-]
   * @return liquid-side mass transfer coefficient [m/s]
   */
  private static double calculateBubbleKL(double diameter, double liquidHoldup, double usg,
      double usl, double rhoL, double muL, double diffL, double scL) {

    // For bubble flow, use Ranz-Marshall with bubble slip velocity
    double voidFraction = 1.0 - liquidHoldup;

    // Estimate bubble diameter using Hinze
    double dBubble = 0.01 * diameter; // Simplified estimate

    // Bubble slip velocity (Wallis correlation)
    double uSlip = 1.53 * Math.pow(9.81 * 0.072 * (rhoL - 100) / (rhoL * rhoL), 0.25);
    uSlip = Math.min(uSlip, 0.5); // Limit

    // Bubble Reynolds number
    double reBubble = rhoL * uSlip * dBubble / muL;

    // Ranz-Marshall
    double sh = calculateRanzMarshallSherwood(reBubble, scL);

    return sh * diffL / dBubble;
  }

  /**
   * Calculates liquid-side mass transfer coefficient for droplet flow.
   *
   * @param diameter pipe diameter [m]
   * @param usg superficial gas velocity [m/s]
   * @param rhoL liquid density [kg/mA3]
   * @param muL liquid viscosity [PaAús]
   * @param diffL liquid diffusivity [mAı/s]
   * @param scL liquid Schmidt number [-]
   * @return liquid-side mass transfer coefficient [m/s]
   */
  private static double calculateDropletKL(double diameter, double usg, double rhoL, double muL,
      double diffL, double scL) {

    // Internal circulation in droplets enhances mass transfer
    // Use Kronig-Brink for circulating drops: Sh ≈ 17.9

    double dDrop = 0.001; // Estimate 1mm droplet

    // Droplet Reynolds based on relative velocity
    double uRel = usg * 0.1; // Droplets follow gas closely
    double reDrop = rhoL * uRel * dDrop / muL;

    // Modified Ranz-Marshall with internal circulation factor
    double sh = calculateRanzMarshallSherwood(reDrop, scL);
    sh = sh * 1.5; // Enhancement due to internal circulation

    return sh * diffL / dDrop;
  }

  /**
   * Calculates liquid-side mass transfer coefficient for churn flow.
   */
  private static double calculateChurnKL(double diameter, double liquidHoldup, double usg,
      double usl, double rhoL, double muL, double diffL, double scL) {

    // Churn flow has high turbulence - use enhanced turbulent correlation
    double uMix = usg + usl;
    double reMix = rhoL * uMix * diameter / muL;

    // Enhanced Dittus-Boelter with factor for chaotic interface
    double sh = 0.04 * Math.pow(reMix, 0.8) * Math.pow(scL, 0.33);

    return sh * diffL / diameter;
  }

  // ============= Gas-side correlations =============

  /**
   * Calculates gas-side mass transfer coefficient for stratified flow.
   */
  private static double calculateStratifiedKG(double diameter, double voidFraction, double uG,
      double rhoG, double muG, double diffG, double scG) {

    // Hydraulic diameter for gas phase
    double dh = calculateGasHydraulicDiameter(diameter, voidFraction);

    // Gas Reynolds number
    double reG = rhoG * uG * dh / muG;

    // Dittus-Boelter
    double sh = calculateDittusBoelterSherwood(reG, scG);

    return sh * diffG / dh;
  }

  /**
   * Calculates gas-side mass transfer coefficient for annular flow.
   *
   * @param diameter pipe diameter [m]
   * @param voidFraction void fraction [-]
   * @param uG gas velocity [m/s]
   * @param rhoG gas density [kg/m³]
   * @param muG gas dynamic viscosity [Pa·s]
   * @param diffG gas diffusivity [m²/s]
   * @param scG gas Schmidt number [-]
   * @return gas-side mass transfer coefficient [m/s]
   */
  private static double calculateAnnularKG(double diameter, double voidFraction, double uG,
      double rhoG, double muG, double diffG, double scG) {

    // Core diameter
    double dCore = diameter * Math.sqrt(voidFraction);

    // Gas Reynolds number
    double reG = rhoG * uG * dCore / muG;

    // Enhanced Sherwood due to wavy interface
    double sh = 0.04 * Math.pow(reG, 0.8) * Math.pow(scG, 0.33);

    return sh * diffG / dCore;
  }

  /**
   * Calculates gas-side mass transfer coefficient for bubble flow.
   *
   * @param diameter pipe diameter [m]
   * @param uG gas velocity [m/s]
   * @param rhoG gas density [kg/m³]
   * @param muG gas dynamic viscosity [Pa·s]
   * @param diffG gas diffusivity [m²/s]
   * @param scG gas Schmidt number [-]
   * @return gas-side mass transfer coefficient [m/s]
   */
  private static double calculateBubbleKG(double diameter, double uG, double rhoG, double muG,
      double diffG, double scG) {

    // For gas side in bubbles, diffusion in stagnant bubble interior
    double dBubble = 0.01 * diameter;

    // Use Ranz-Marshall
    double reBubble = rhoG * uG * dBubble / muG;
    double sh = calculateRanzMarshallSherwood(reBubble, scG);

    return sh * diffG / dBubble;
  }

  /**
   * Calculates gas-side mass transfer coefficient for droplet flow.
   *
   * @param diameter pipe diameter [m]
   * @param uG gas velocity [m/s]
   * @param rhoG gas density [kg/m³]
   * @param muG gas dynamic viscosity [Pa·s]
   * @param diffG gas diffusivity [m²/s]
   * @param scG gas Schmidt number [-]
   * @return gas-side mass transfer coefficient [m/s]
   */
  private static double calculateDropletKG(double diameter, double uG, double rhoG, double muG,
      double diffG, double scG) {

    // Droplets in gas stream - use Ranz-Marshall
    double dDrop = 0.001;
    double uRel = uG * 0.1;

    double reDrop = rhoG * uRel * dDrop / muG;
    double sh = calculateRanzMarshallSherwood(reDrop, scG);

    return sh * diffG / dDrop;
  }

  /**
   * Calculates gas-side mass transfer coefficient for churn flow.
   */
  private static double calculateChurnKG(double diameter, double uG, double rhoG, double muG,
      double diffG, double scG) {

    double reG = rhoG * uG * diameter / muG;
    double sh = 0.04 * Math.pow(reG, 0.8) * Math.pow(scG, 0.33);

    return sh * diffG / diameter;
  }

  // ============= Helper methods =============

  /**
   * Calculates hydraulic diameter for liquid phase in stratified flow.
   */
  private static double calculateLiquidHydraulicDiameter(double diameter, double liquidHoldup) {
    if (liquidHoldup <= 0.01 || liquidHoldup >= 0.99) {
      return diameter;
    }

    // Approximate: Dh = 4A/P where A is liquid area, P is wetted perimeter
    // For stratified: Dh ≈ 4·holdup·D / (π + 2·interface_length/D)
    double theta = 2.0 * Math.acos(1.0 - 2.0 * liquidHoldup);
    double wettedPerimeter = diameter * theta / 2.0 + diameter * Math.sin(theta / 2.0);
    double area = diameter * diameter * (theta - Math.sin(theta)) / 8.0;

    return 4.0 * area / wettedPerimeter;
  }

  /**
   * Calculates hydraulic diameter for gas phase in stratified flow.
   */
  private static double calculateGasHydraulicDiameter(double diameter, double voidFraction) {
    return calculateLiquidHydraulicDiameter(diameter, 1.0 - voidFraction);
  }
}
