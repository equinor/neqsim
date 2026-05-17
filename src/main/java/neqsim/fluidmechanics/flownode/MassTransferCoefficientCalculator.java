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
   *
   * @param diameter pipe diameter [m]
   * @param uG gas velocity [m/s]
   * @param rhoG gas density [kg/m³]
   * @param muG gas dynamic viscosity [Pa·s]
   * @param diffG gas diffusivity [m²/s]
   * @param scG gas Schmidt number [-]
   * @return gas-side mass transfer coefficient [m/s]
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
   *
   * @param diameter pipe diameter [m]
   * @param liquidHoldup liquid holdup fraction [-]
   * @return hydraulic diameter for liquid phase [m]
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
   *
   * @param diameter pipe diameter [m]
   * @param voidFraction void fraction [-]
   * @return hydraulic diameter for gas phase [m]
   */
  private static double calculateGasHydraulicDiameter(double diameter, double voidFraction) {
    return calculateLiquidHydraulicDiameter(diameter, 1.0 - voidFraction);
  }

  // ==================== ENHANCED MODELS ====================

  /**
   * Calculates the liquid-side mass transfer coefficient with turbulence enhancement.
   *
   * <p>
   * Applies a turbulence correction factor based on the turbulent intensity, which increases mass
   * transfer in highly turbulent conditions.
   * </p>
   *
   * <p>
   * Reference: Lamont, J.C., Scott, D.S. (1970). An eddy cell model of mass transfer into the
   * surface of a turbulent liquid. AIChE Journal, 16(4), 513-519.
   * </p>
   *
   * @param flowPattern the flow pattern
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup
   * @param usg superficial gas velocity (m/s)
   * @param usl superficial liquid velocity (m/s)
   * @param rhoL liquid density (kg/m³)
   * @param muL liquid viscosity (Pa·s)
   * @param diffL liquid diffusivity (m²/s)
   * @param turbulentIntensity turbulent intensity (0-1, typical 0.05-0.2)
   * @return enhanced liquid-side mass transfer coefficient (m/s)
   */
  public static double calculateLiquidMassTransferCoefficientWithTurbulence(FlowPattern flowPattern,
      double diameter, double liquidHoldup, double usg, double usl, double rhoL, double muL,
      double diffL, double turbulentIntensity) {

    // Get base coefficient
    double kLBase = calculateLiquidMassTransferCoefficient(flowPattern, diameter, liquidHoldup, usg,
        usl, rhoL, muL, diffL);

    if (kLBase <= 0 || turbulentIntensity <= 0) {
      return Math.max(0.0, kLBase);
    }

    // Calculate liquid Reynolds number
    double hydraulicDiam = calculateLiquidHydraulicDiameter(diameter, liquidHoldup);
    double uL = usl / Math.max(liquidHoldup, 0.01);
    double reL = rhoL * uL * hydraulicDiam / muL;

    // Turbulence enhancement factor (Lamont & Scott, 1970)
    // Enhancement proportional to (turbulent diffusivity / molecular diffusivity)^n
    // Simplified: enhancement = 1 + C * Tu * sqrt(Re)
    double enhancement = 1.0 + 2.5 * turbulentIntensity * Math.sqrt(Math.max(0, reL));

    // Cap enhancement at reasonable limit
    enhancement = Math.min(enhancement, 5.0);

    return kLBase * enhancement;
  }

  /**
   * Applies Marangoni correction to mass transfer coefficient.
   *
   * <p>
   * The Marangoni effect occurs when surface-active components create surface tension gradients
   * that oppose interfacial motion, reducing mass transfer rates.
   * </p>
   *
   * <p>
   * Reference: Springer, T.G., Pigford, R.L. (1970). Influence of surface turbulence and
   * surfactants on gas transport through liquid interfaces. Ind. Eng. Chem. Fundam., 9(3), 458-465.
   * </p>
   *
   * @param kLBase base mass transfer coefficient (m/s)
   * @param surfaceTensionGradient gradient of surface tension with concentration (N·m/mol)
   * @param diffL liquid diffusivity (m²/s)
   * @param muL liquid viscosity (Pa·s)
   * @return corrected mass transfer coefficient (m/s)
   */
  public static double applyMarangoniCorrection(double kLBase, double surfaceTensionGradient,
      double diffL, double muL) {

    if (kLBase <= 0 || Math.abs(surfaceTensionGradient) < 1e-10) {
      return Math.max(0.0, kLBase);
    }

    // Marangoni number: Ma = (dσ/dc) * D / (μ * k²)
    double ma = Math.abs(surfaceTensionGradient) * diffL / (muL * kLBase * kLBase + 1e-20);

    // Correction factor (Springer & Pigford, 1970)
    // k_corrected = k_base / (1 + 0.35 * sqrt(|Ma|))
    double correctionFactor = 1.0 / (1.0 + 0.35 * Math.sqrt(ma));

    // Correction should not reduce kL by more than 90%
    correctionFactor = Math.max(correctionFactor, 0.1);

    return kLBase * correctionFactor;
  }

  /**
   * Calculates the enhanced mass transfer coefficient incorporating multiple effects.
   *
   * <p>
   * This method combines turbulence enhancement and Marangoni correction for a comprehensive mass
   * transfer coefficient calculation.
   * </p>
   *
   * @param flowPattern the flow pattern
   * @param diameter pipe diameter (m)
   * @param liquidHoldup liquid holdup
   * @param usg superficial gas velocity (m/s)
   * @param usl superficial liquid velocity (m/s)
   * @param rhoL liquid density (kg/m³)
   * @param muL liquid viscosity (Pa·s)
   * @param diffL liquid diffusivity (m²/s)
   * @param turbulentIntensity turbulent intensity (0-1)
   * @param surfaceTensionGradient surface tension gradient (N·m/mol), 0 to disable
   * @param includeTurbulence whether to include turbulence effects
   * @param includeMarangoni whether to include Marangoni correction
   * @return enhanced mass transfer coefficient (m/s)
   */
  public static double calculateEnhancedLiquidMassTransferCoefficient(FlowPattern flowPattern,
      double diameter, double liquidHoldup, double usg, double usl, double rhoL, double muL,
      double diffL, double turbulentIntensity, double surfaceTensionGradient,
      boolean includeTurbulence, boolean includeMarangoni) {

    double kL;

    // Apply turbulence enhancement if enabled
    if (includeTurbulence && turbulentIntensity > 0) {
      kL = calculateLiquidMassTransferCoefficientWithTurbulence(flowPattern, diameter, liquidHoldup,
          usg, usl, rhoL, muL, diffL, turbulentIntensity);
    } else {
      kL = calculateLiquidMassTransferCoefficient(flowPattern, diameter, liquidHoldup, usg, usl,
          rhoL, muL, diffL);
    }

    // Apply Marangoni correction if enabled
    if (includeMarangoni && Math.abs(surfaceTensionGradient) > 1e-10) {
      kL = applyMarangoniCorrection(kL, surfaceTensionGradient, diffL, muL);
    }

    return Math.max(0.0, kL);
  }

  /**
   * Estimates turbulent intensity based on flow pattern and Reynolds number.
   *
   * <p>
   * Provides a reasonable estimate when turbulent intensity is not directly available.
   * </p>
   *
   * @param flowPattern the flow pattern
   * @param re Reynolds number
   * @return estimated turbulent intensity (0-1)
   */
  public static double estimateTurbulentIntensity(FlowPattern flowPattern, double re) {
    // Base turbulent intensity from pipe flow correlation
    // Tu ≈ 0.16 * Re^(-1/8) for developed turbulent flow
    double tuBase = 0.0;
    if (re > 2300) {
      tuBase = 0.16 * Math.pow(re, -0.125);
    }

    // Adjust based on flow pattern
    switch (flowPattern) {
      case STRATIFIED:
        return tuBase * 0.8;
      case STRATIFIED_WAVY:
        return tuBase * 1.2;
      case ANNULAR:
        return tuBase * 1.5;
      case SLUG:
        return tuBase * 2.0; // High turbulence in slug mixing
      case CHURN:
        return tuBase * 2.5; // Very high turbulence
      case BUBBLE:
      case DISPERSED_BUBBLE:
        return tuBase * 1.8;
      case DROPLET:
        return tuBase * 1.0;
      default:
        return tuBase;
    }
  }

  // ==================== LITERATURE VALIDATION ====================

  /**
   * Returns expected mass transfer coefficient ranges for validation against literature data.
   *
   * <p>
   * Based on experimental data from multiple sources including:
   * </p>
   * <ul>
   * <li>Solbraa, E. (2002). PhD thesis, NTNU - CO2 absorption data</li>
   * <li>Hewitt, G.F. (1998). Heat Exchanger Design Handbook</li>
   * <li>Perry's Chemical Engineers' Handbook, 8th Ed.</li>
   * </ul>
   *
   * @param flowPattern the flow pattern
   * @param phase 0 for gas, 1 for liquid
   * @return array containing [min, typical, max] mass transfer coefficient (m/s)
   */
  public static double[] getExpectedMassTransferCoefficientRange(FlowPattern flowPattern,
      int phase) {
    double[] range = new double[3]; // [min, typical, max]

    if (phase == 1) { // Liquid side
      switch (flowPattern) {
        case STRATIFIED:
        case STRATIFIED_WAVY:
          // kL typically 10^-5 to 10^-4 m/s
          range[0] = 1e-6;
          range[1] = 5e-5;
          range[2] = 2e-4;
          break;
        case ANNULAR:
          // Higher due to thin film
          range[0] = 5e-5;
          range[1] = 2e-4;
          range[2] = 1e-3;
          break;
        case SLUG:
          // Variable but often high
          range[0] = 1e-5;
          range[1] = 1e-4;
          range[2] = 5e-4;
          break;
        case BUBBLE:
        case DISPERSED_BUBBLE:
          // Depends on bubble size
          range[0] = 1e-5;
          range[1] = 5e-5;
          range[2] = 3e-4;
          break;
        default:
          range[0] = 1e-6;
          range[1] = 1e-5;
          range[2] = 1e-4;
      }
    } else { // Gas side
      switch (flowPattern) {
        case STRATIFIED:
        case STRATIFIED_WAVY:
          // kG typically 10^-3 to 10^-2 m/s
          range[0] = 1e-4;
          range[1] = 5e-3;
          range[2] = 5e-2;
          break;
        case ANNULAR:
          range[0] = 1e-3;
          range[1] = 1e-2;
          range[2] = 1e-1;
          break;
        default:
          range[0] = 1e-4;
          range[1] = 5e-3;
          range[2] = 5e-2;
      }
    }

    return range;
  }

  /**
   * Validates calculated mass transfer coefficient against literature correlations.
   *
   * @param calculated calculated kL or kG value (m/s)
   * @param flowPattern the flow pattern
   * @param phase 0 for gas, 1 for liquid
   * @return true if within expected range, false otherwise
   */
  public static boolean validateAgainstLiterature(double calculated, FlowPattern flowPattern,
      int phase) {
    double[] range = getExpectedMassTransferCoefficientRange(flowPattern, phase);
    return calculated >= range[0] && calculated <= range[2];
  }
}

