package neqsim.fluidmechanics.flownode;

/**
 * Utility class for calculating heat transfer coefficients in two-phase pipe flow.
 *
 * <p>
 * Implements flow pattern-specific Nusselt number correlations for calculating interphase and wall
 * heat transfer coefficients. The correlations are based on well-established literature sources.
 * </p>
 *
 * <p>
 * <b>Correlations by Flow Pattern:</b>
 * </p>
 * <ul>
 * <li><b>Stratified:</b> Nu = f(Re, Pr, geometry) - Solbraa (2002)</li>
 * <li><b>Annular:</b> Nu = f(Re_film, Pr, wave amplitude) - Hewitt &amp; Hall-Taylor</li>
 * <li><b>Slug:</b> Nu_bubble + Nu_slug weighted - Fernandes et al.</li>
 * <li><b>Bubble:</b> Nu = 2 + 0.6·Re^0.5·Pr^0.33 - Ranz-Marshall</li>
 * <li><b>Droplet:</b> Nu = 2 + 0.6·Re^0.5·Pr^0.33 - Ranz-Marshall</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class HeatTransferCoefficientCalculator {

  /** Gravitational acceleration (m/s²). */
  private static final double G = 9.81;

  /**
   * Private constructor to prevent instantiation.
   */
  private HeatTransferCoefficientCalculator() {}

  /**
   * Calculates the liquid-side interphase heat transfer coefficient.
   *
   * @param flowPattern the flow pattern
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup
   * @param usg superficial gas velocity (m/s)
   * @param usl superficial liquid velocity (m/s)
   * @param rhoL liquid density (kg/m³)
   * @param muL liquid viscosity (Pa·s)
   * @param cpL liquid heat capacity (J/(kg·K))
   * @param kL liquid thermal conductivity (W/(m·K))
   * @return liquid-side heat transfer coefficient h_L (W/(m²·K)), always non-negative
   */
  public static double calculateLiquidHeatTransferCoefficient(FlowPattern flowPattern,
      double diameter, double liquidHoldup, double usg, double usl, double rhoL, double muL,
      double cpL, double kL) {

    // Validate inputs - return 0 for invalid values
    if (diameter <= 0 || kL <= 0 || rhoL <= 0 || muL <= 0 || cpL <= 0) {
      return 0.0;
    }

    // Calculate liquid Prandtl number
    double prL = cpL * muL / kL;
    if (prL <= 0 || Double.isNaN(prL)) {
      return 0.0;
    }

    double hL;
    switch (flowPattern) {
      case STRATIFIED:
      case STRATIFIED_WAVY:
        hL = calculateStratifiedHL(diameter, liquidHoldup, usl, rhoL, muL, cpL, kL, prL);
        break;
      case ANNULAR:
        hL = calculateAnnularHL(diameter, liquidHoldup, usl, rhoL, muL, cpL, kL, prL);
        break;
      case SLUG:
        hL = calculateSlugHL(diameter, liquidHoldup, usg, usl, rhoL, muL, cpL, kL, prL);
        break;
      case BUBBLE:
      case DISPERSED_BUBBLE:
        hL = calculateBubbleHL(diameter, liquidHoldup, usg, usl, rhoL, muL, cpL, kL, prL);
        break;
      case DROPLET:
        hL = calculateDropletHL(diameter, usg, rhoL, muL, cpL, kL, prL);
        break;
      case CHURN:
        hL = calculateChurnHL(diameter, liquidHoldup, usg, usl, rhoL, muL, cpL, kL, prL);
        break;
      default:
        hL = calculateStratifiedHL(diameter, liquidHoldup, usl, rhoL, muL, cpL, kL, prL);
    }

    return Math.max(0.0, hL);
  }

  /**
   * Calculates the gas-side interphase heat transfer coefficient.
   *
   * @param flowPattern the flow pattern
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup
   * @param usg superficial gas velocity (m/s)
   * @param rhoG gas density (kg/m³)
   * @param muG gas viscosity (Pa·s)
   * @param cpG gas heat capacity (J/(kg·K))
   * @param kG gas thermal conductivity (W/(m·K))
   * @return gas-side heat transfer coefficient h_G (W/(m²·K)), always non-negative
   */
  public static double calculateGasHeatTransferCoefficient(FlowPattern flowPattern, double diameter,
      double liquidHoldup, double usg, double rhoG, double muG, double cpG, double kG) {

    // Validate inputs - return 0 for invalid values
    if (diameter <= 0 || kG <= 0 || rhoG <= 0 || muG <= 0 || cpG <= 0) {
      return 0.0;
    }

    // Calculate gas Prandtl number
    double prG = cpG * muG / kG;
    if (prG <= 0 || Double.isNaN(prG)) {
      return 0.0;
    }

    // Gas void fraction
    double voidFraction = 1.0 - liquidHoldup;

    // Effective gas velocity
    double uG = usg / Math.max(voidFraction, 0.01);

    double hG;
    switch (flowPattern) {
      case STRATIFIED:
      case STRATIFIED_WAVY:
        hG = calculateStratifiedHG(diameter, voidFraction, uG, rhoG, muG, cpG, kG, prG);
        break;
      case ANNULAR:
        hG = calculateAnnularHG(diameter, voidFraction, uG, rhoG, muG, cpG, kG, prG);
        break;
      case BUBBLE:
      case DISPERSED_BUBBLE:
      case SLUG:
        hG = calculateBubbleHG(diameter, uG, rhoG, muG, cpG, kG, prG);
        break;
      case DROPLET:
        hG = calculateDropletHG(diameter, uG, rhoG, muG, cpG, kG, prG);
        break;
      case CHURN:
        hG = calculateChurnHG(diameter, uG, rhoG, muG, cpG, kG, prG);
        break;
      default:
        hG = calculateStratifiedHG(diameter, voidFraction, uG, rhoG, muG, cpG, kG, prG);
    }

    return Math.max(0.0, hG);
  }

  /**
   * Calculates the overall interphase heat transfer coefficient using resistance in series model.
   *
   * <pre>
   * 1/U_interphase = 1/h_L + 1/h_G
   * </pre>
   *
   * @param hL liquid-side heat transfer coefficient (W/(m²·K))
   * @param hG gas-side heat transfer coefficient (W/(m²·K))
   * @return overall interphase heat transfer coefficient (W/(m²·K))
   */
  public static double calculateOverallInterphaseCoefficient(double hL, double hG) {
    if (hL <= 0 || hG <= 0) {
      return Math.max(hL, hG);
    }
    return 1.0 / (1.0 / hL + 1.0 / hG);
  }

  /**
   * Calculates Nusselt number using the Dittus-Boelter correlation for turbulent flow.
   *
   * <pre>
   * Nu = 0.023 · Re^0.8 · Pr^n
   * where n = 0.4 for heating (fluid being heated)
   *       n = 0.3 for cooling (fluid being cooled)
   * </pre>
   *
   * @param re Reynolds number
   * @param pr Prandtl number
   * @param heating true if fluid is being heated, false if being cooled
   * @return Nusselt number
   */
  public static double calculateDittusBoelterNusselt(double re, double pr, boolean heating) {
    double n = heating ? 0.4 : 0.3;
    return 0.023 * Math.pow(Math.abs(re), 0.8) * Math.pow(Math.abs(pr), n);
  }

  /**
   * Calculates Nusselt number for laminar flow in a pipe.
   *
   * <p>
   * For fully developed laminar flow with constant wall temperature: Nu = 3.66 For constant heat
   * flux: Nu = 4.36
   * </p>
   *
   * @param constantWallTemp true for constant wall temperature, false for constant heat flux
   * @return Nusselt number
   */
  public static double calculateLaminarNusselt(boolean constantWallTemp) {
    return constantWallTemp ? 3.66 : 4.36;
  }

  /**
   * Calculates Nusselt number using Gnielinski correlation for transitional and turbulent flow.
   *
   * <pre>
   * Nu = (f/8)(Re-1000)Pr / [1 + 12.7(f/8)^0.5(Pr^(2/3) - 1)]
   * Valid for: 3000 &lt; Re &lt; 5×10^6, 0.5 &lt; Pr &lt; 2000
   * </pre>
   *
   * @param re Reynolds number
   * @param pr Prandtl number
   * @param frictionFactor Darcy friction factor
   * @return Nusselt number
   */
  public static double calculateGnielinskiNusselt(double re, double pr, double frictionFactor) {
    if (re < 3000 || pr < 0.5) {
      return calculateLaminarNusselt(true);
    }

    double f = frictionFactor;
    double numerator = (f / 8.0) * (re - 1000.0) * pr;
    double denominator = 1.0 + 12.7 * Math.sqrt(f / 8.0) * (Math.pow(pr, 2.0 / 3.0) - 1.0);

    return Math.max(3.66, numerator / denominator);
  }

  /**
   * Calculates Nusselt number for condensation on a vertical surface (Nusselt theory).
   *
   * <pre>
   * Nu = 0.943 · [ρ_L(ρ_L-ρ_G)g·h_fg·L³ / (μ_L·k_L·ΔT)]^0.25
   * </pre>
   *
   * @param rhoL liquid density (kg/m³)
   * @param rhoG gas density (kg/m³)
   * @param hfg latent heat of vaporization (J/kg)
   * @param length characteristic length (m)
   * @param muL liquid viscosity (Pa·s)
   * @param kL liquid thermal conductivity (W/(m·K))
   * @param deltaT temperature difference (K)
   * @return Nusselt number
   */
  public static double calculateCondensationNusselt(double rhoL, double rhoG, double hfg,
      double length, double muL, double kL, double deltaT) {

    if (deltaT <= 0 || length <= 0 || muL <= 0 || kL <= 0) {
      return 0.0;
    }

    double term =
        rhoL * (rhoL - rhoG) * G * hfg * Math.pow(length, 3) / (muL * kL * Math.abs(deltaT));

    return 0.943 * Math.pow(term, 0.25);
  }

  // ==================== STRATIFIED FLOW ====================

  private static double calculateStratifiedHL(double diameter, double liquidHoldup, double usl,
      double rhoL, double muL, double cpL, double kL, double prL) {

    // Calculate liquid hydraulic diameter
    double dHL = 4.0 * liquidHoldup * Math.PI * diameter * diameter / 4.0
        / (Math.PI * diameter * liquidHoldup + diameter);

    // Effective liquid velocity
    double uL = usl / Math.max(liquidHoldup, 0.01);

    // Liquid Reynolds number based on hydraulic diameter
    double reL = rhoL * Math.abs(uL) * dHL / muL;

    // Calculate Nusselt number
    double nu;
    if (reL > 10000) {
      // Turbulent - Dittus-Boelter
      nu = calculateDittusBoelterNusselt(reL, prL, true);
    } else if (reL > 2300) {
      // Transition
      double nuLam = 3.66;
      double nuTurb = calculateDittusBoelterNusselt(10000, prL, true);
      double f = (reL - 2300) / (10000 - 2300);
      nu = nuLam + f * (nuTurb - nuLam);
    } else {
      // Laminar
      nu = 3.66;
    }

    return nu * kL / dHL;
  }

  private static double calculateStratifiedHG(double diameter, double voidFraction, double uG,
      double rhoG, double muG, double cpG, double kG, double prG) {

    // Calculate gas hydraulic diameter
    double dHG = 4.0 * voidFraction * Math.PI * diameter * diameter / 4.0
        / (Math.PI * diameter * voidFraction + diameter);

    // Gas Reynolds number
    double reG = rhoG * Math.abs(uG) * dHG / muG;

    // Calculate Nusselt number
    double nu;
    if (reG > 10000) {
      nu = calculateDittusBoelterNusselt(reG, prG, true);
    } else if (reG > 2300) {
      double nuLam = 3.66;
      double nuTurb = calculateDittusBoelterNusselt(10000, prG, true);
      double f = (reG - 2300) / (10000 - 2300);
      nu = nuLam + f * (nuTurb - nuLam);
    } else {
      nu = 3.66;
    }

    return nu * kG / dHG;
  }

  // ==================== ANNULAR FLOW ====================

  private static double calculateAnnularHL(double diameter, double liquidHoldup, double usl,
      double rhoL, double muL, double cpL, double kL, double prL) {

    // Film thickness
    double delta = diameter / 2.0 * (1.0 - Math.sqrt(1.0 - liquidHoldup));

    // Film velocity (assuming uniform film)
    double uFilm = usl / Math.max(liquidHoldup, 0.01);

    // Film Reynolds number
    double reFilm = rhoL * Math.abs(uFilm) * delta / muL;

    // Use film model correlation
    double nu;
    if (reFilm > 400) {
      // Turbulent wavy film - enhanced heat transfer
      // Hewitt & Hall-Taylor correlation
      nu = 0.0265 * Math.pow(reFilm, 0.8) * Math.pow(prL, 0.4);
    } else {
      // Laminar falling film
      nu = 0.9 * Math.pow(reFilm, 1.0 / 3.0) * Math.pow(prL, 1.0 / 3.0);
    }

    return nu * kL / delta;
  }

  private static double calculateAnnularHG(double diameter, double voidFraction, double uG,
      double rhoG, double muG, double cpG, double kG, double prG) {

    // Core diameter
    double dCore = diameter * Math.sqrt(voidFraction);

    // Gas Reynolds number
    double reG = rhoG * Math.abs(uG) * dCore / muG;

    // Dittus-Boelter for core flow
    double nu = calculateDittusBoelterNusselt(reG, prG, true);

    return nu * kG / dCore;
  }

  // ==================== SLUG FLOW ====================

  private static double calculateSlugHL(double diameter, double liquidHoldup, double usg,
      double usl, double rhoL, double muL, double cpL, double kL, double prL) {

    // Mixture velocity
    double uM = usg + usl;

    // Liquid slug Reynolds number
    double reSlug = rhoL * Math.abs(uM) * diameter / muL;

    // Nusselt number for slug region (turbulent mixed)
    double nuSlug = calculateDittusBoelterNusselt(reSlug, prL, true);

    // Film around Taylor bubble (laminar falling film)
    double reTB = rhoL * Math.abs(usl) * diameter / muL;
    double nuTB = 0.9 * Math.pow(Math.max(reTB, 100), 1.0 / 3.0) * Math.pow(prL, 1.0 / 3.0);

    // Weighted average (50% slug, 50% Taylor bubble region)
    double nu = 0.5 * nuSlug + 0.5 * nuTB;

    return nu * kL / diameter;
  }

  // ==================== BUBBLE FLOW ====================

  private static double calculateBubbleHL(double diameter, double liquidHoldup, double usg,
      double usl, double rhoL, double muL, double cpL, double kL, double prL) {

    // Liquid velocity
    double uL = usl / Math.max(liquidHoldup, 0.01);

    // Continuous phase Reynolds number
    double reL = rhoL * Math.abs(uL) * diameter / muL;

    // Base Nusselt number
    double nu = calculateDittusBoelterNusselt(reL, prL, true);

    // Enhancement due to bubble agitation
    double voidFraction = 1.0 - liquidHoldup;
    double enhancement = 1.0 + 0.5 * voidFraction;

    return enhancement * nu * kL / diameter;
  }

  private static double calculateBubbleHG(double diameter, double uG, double rhoG, double muG,
      double cpG, double kG, double prG) {

    // Ranz-Marshall for bubbles
    // Nu = 2 + 0.6·Re^0.5·Pr^0.33
    double reBubble = rhoG * Math.abs(uG) * diameter / muG;
    double nu = 2.0 + 0.6 * Math.pow(reBubble, 0.5) * Math.pow(prG, 0.33);

    return nu * kG / diameter;
  }

  // ==================== DROPLET/MIST FLOW ====================

  private static double calculateDropletHL(double diameter, double usg, double rhoL, double muL,
      double cpL, double kL, double prL) {

    // Droplet Reynolds number
    double reDroplet = rhoL * Math.abs(usg) * diameter * 0.01 / muL;

    // Ranz-Marshall
    double nu = 2.0 + 0.6 * Math.pow(Math.max(reDroplet, 1), 0.5) * Math.pow(prL, 0.33);

    // Use droplet diameter (estimate as 0.01 * pipe diameter)
    return nu * kL / (diameter * 0.01);
  }

  private static double calculateDropletHG(double diameter, double uG, double rhoG, double muG,
      double cpG, double kG, double prG) {

    // Gas continuous phase
    double reG = rhoG * Math.abs(uG) * diameter / muG;
    double nu = calculateDittusBoelterNusselt(reG, prG, true);

    return nu * kG / diameter;
  }

  // ==================== CHURN FLOW ====================

  private static double calculateChurnHL(double diameter, double liquidHoldup, double usg,
      double usl, double rhoL, double muL, double cpL, double kL, double prL) {

    // Highly turbulent - use enhanced correlation
    double uM = usg + usl;
    double reM = rhoL * Math.abs(uM) * diameter / muL;
    double nu = 0.035 * Math.pow(reM, 0.8) * Math.pow(prL, 0.4);

    return nu * kL / diameter;
  }

  private static double calculateChurnHG(double diameter, double uG, double rhoG, double muG,
      double cpG, double kG, double prG) {

    double reG = rhoG * Math.abs(uG) * diameter / muG;
    double nu = calculateDittusBoelterNusselt(reG, prG, true);

    return nu * kG / diameter;
  }

  /**
   * Calculates the Stanton number for heat transfer.
   *
   * <pre>
   * St = h / (ρ · u · Cp) = Nu / (Re · Pr)
   * </pre>
   *
   * @param h heat transfer coefficient (W/(m²·K))
   * @param rho density (kg/m³)
   * @param u velocity (m/s)
   * @param cp heat capacity (J/(kg·K))
   * @return Stanton number (dimensionless)
   */
  public static double calculateStantonNumber(double h, double rho, double u, double cp) {
    double denominator = rho * Math.abs(u) * cp;
    if (denominator <= 0) {
      return 0.0;
    }
    return h / denominator;
  }

  /**
   * Estimates the condensation heat transfer coefficient using the Chato correlation for horizontal
   * tube condensation.
   *
   * <pre>
   * h = 0.555 · [g·ρ_L(ρ_L-ρ_G)·k_L³·h_fg' / (μ_L·D·ΔT)]^0.25
   * where h_fg' = h_fg + 0.68·cp_L·ΔT (modified latent heat)
   * </pre>
   *
   * @param rhoL liquid density (kg/m³)
   * @param rhoG gas density (kg/m³)
   * @param kL liquid thermal conductivity (W/(m·K))
   * @param hfg latent heat of vaporization (J/kg)
   * @param cpL liquid heat capacity (J/(kg·K))
   * @param muL liquid viscosity (Pa·s)
   * @param diameter tube diameter (m)
   * @param deltaT temperature difference between saturation and wall (K)
   * @return condensation heat transfer coefficient (W/(m²·K))
   */
  public static double calculateCondensationHTC(double rhoL, double rhoG, double kL, double hfg,
      double cpL, double muL, double diameter, double deltaT) {

    if (deltaT <= 0 || diameter <= 0 || muL <= 0) {
      return 0.0;
    }

    // Modified latent heat
    double hfgPrime = hfg + 0.68 * cpL * Math.abs(deltaT);

    double term =
        G * rhoL * (rhoL - rhoG) * Math.pow(kL, 3) * hfgPrime / (muL * diameter * Math.abs(deltaT));

    return 0.555 * Math.pow(term, 0.25);
  }

  /**
   * Estimates the evaporation heat transfer coefficient for nucleate pool boiling using the
   * Rohsenow correlation.
   *
   * @param rhoL liquid density (kg/m³)
   * @param rhoG gas density (kg/m³)
   * @param cpL liquid heat capacity (J/(kg·K))
   * @param hfg latent heat of vaporization (J/kg)
   * @param sigma surface tension (N/m)
   * @param muL liquid viscosity (Pa·s)
   * @param prL liquid Prandtl number
   * @param qFlux heat flux (W/m²)
   * @return evaporation heat transfer coefficient (W/(m²·K))
   */
  public static double calculateEvaporationHTC(double rhoL, double rhoG, double cpL, double hfg,
      double sigma, double muL, double prL, double qFlux) {

    if (qFlux <= 0 || sigma <= 0 || hfg <= 0) {
      return 0.0;
    }

    // Rohsenow correlation constant (typical value)
    double cSF = 0.013; // For water/copper, varies for other fluid/surface combinations
    double n = 1.0; // Exponent, typically 1.0 for water, 1.7 for other fluids

    // Calculate wall superheat from Rohsenow
    double lc = Math.sqrt(sigma / (G * (rhoL - rhoG))); // Capillary length
    double deltaTWall =
        cpL * Math.pow(qFlux / (muL * hfg), 0.33) * Math.pow(lc, 0.33) * cSF * Math.pow(prL, n);

    if (deltaTWall > 0) {
      return qFlux / deltaTWall;
    }
    return 0.0;
  }
}
