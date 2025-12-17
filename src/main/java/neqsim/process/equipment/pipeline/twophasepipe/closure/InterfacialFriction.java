package neqsim.process.equipment.pipeline.twophasepipe.closure;

import java.io.Serializable;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator.StratifiedGeometry;

/**
 * Interfacial friction correlations for two-fluid multiphase pipe flow.
 *
 * <p>
 * Calculates the shear stress at the gas-liquid interface. This is a critical closure relation for
 * the two-fluid model momentum equations. The interfacial friction affects the slip between phases
 * and pressure drop distribution.
 * </p>
 *
 * <h2>Correlations by Flow Regime</h2>
 * <ul>
 * <li><b>Stratified Smooth:</b> Taitel-Dukler (1976) - smooth interface assumption</li>
 * <li><b>Stratified Wavy:</b> Andritsos-Hanratty (1987) - accounts for wave roughness</li>
 * <li><b>Annular:</b> Wallis (1969) - film-core interaction</li>
 * <li><b>Slug:</b> Oliemans (1986) - bubble zone friction</li>
 * </ul>
 *
 * <h2>Sign Convention</h2>
 * <p>
 * Positive interfacial shear acts to accelerate the liquid and decelerate the gas (gas faster than
 * liquid). The shear stress is defined as: τ_i = 0.5 * f_i * ρ_G * (v_G - v_L) * |v_G - v_L|
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class InterfacialFriction implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final double GRAVITY = 9.81;

  /** Geometry calculator for stratified flow. */
  private GeometryCalculator geometryCalc;

  /**
   * Result container for interfacial friction calculations.
   */
  public static class InterfacialFrictionResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Interfacial shear stress (Pa). Positive = gas pushes liquid. */
    public double interfacialShear;

    /** Interfacial friction factor. */
    public double frictionFactor;

    /** Slip velocity (v_G - v_L) (m/s). */
    public double slipVelocity;

    /** Interfacial area per unit length (m²/m = m). */
    public double interfacialAreaPerLength;
  }

  /**
   * Constructor.
   */
  public InterfacialFriction() {
    this.geometryCalc = new GeometryCalculator();
  }

  /**
   * Calculate interfacial friction for the current flow conditions.
   *
   * @param flowRegime Current flow regime
   * @param gasVelocity Gas velocity (m/s)
   * @param liquidVelocity Liquid velocity (m/s)
   * @param gasDensity Gas density (kg/m³)
   * @param liquidDensity Liquid density (kg/m³)
   * @param gasViscosity Gas dynamic viscosity (Pa·s)
   * @param liquidViscosity Liquid dynamic viscosity (Pa·s)
   * @param liquidHoldup Liquid holdup (0-1)
   * @param diameter Pipe diameter (m)
   * @param surfaceTension Surface tension (N/m)
   * @return Interfacial friction result
   */
  public InterfacialFrictionResult calculate(FlowRegime flowRegime, double gasVelocity,
      double liquidVelocity, double gasDensity, double liquidDensity, double gasViscosity,
      double liquidViscosity, double liquidHoldup, double diameter, double surfaceTension) {

    switch (flowRegime) {
      case STRATIFIED_SMOOTH:
        return calcStratifiedSmooth(gasVelocity, liquidVelocity, gasDensity, liquidDensity,
            gasViscosity, liquidViscosity, liquidHoldup, diameter);

      case STRATIFIED_WAVY:
        return calcStratifiedWavy(gasVelocity, liquidVelocity, gasDensity, liquidDensity,
            gasViscosity, liquidViscosity, liquidHoldup, diameter, surfaceTension);

      case ANNULAR:
      case MIST:
        return calcAnnular(gasVelocity, liquidVelocity, gasDensity, liquidDensity, gasViscosity,
            liquidViscosity, liquidHoldup, diameter, surfaceTension);

      case SLUG:
        return calcSlug(gasVelocity, liquidVelocity, gasDensity, liquidDensity, gasViscosity,
            liquidViscosity, liquidHoldup, diameter);

      case CHURN:
        return calcChurn(gasVelocity, liquidVelocity, gasDensity, liquidDensity, gasViscosity,
            liquidViscosity, liquidHoldup, diameter, surfaceTension);

      case BUBBLE:
      case DISPERSED_BUBBLE:
        return calcBubble(gasVelocity, liquidVelocity, gasDensity, liquidDensity, gasViscosity,
            liquidViscosity, liquidHoldup, diameter);

      case SINGLE_PHASE_GAS:
      case SINGLE_PHASE_LIQUID:
        // No interface in single-phase flow
        return new InterfacialFrictionResult();

      default:
        return calcStratifiedSmooth(gasVelocity, liquidVelocity, gasDensity, liquidDensity,
            gasViscosity, liquidViscosity, liquidHoldup, diameter);
    }
  }

  /**
   * Calculate interfacial friction for stratified smooth flow.
   *
   * <p>
   * Uses Taitel-Dukler (1976) approach: treats interface as smooth wall with gas-side friction.
   * </p>
   *
   * @param vG gas velocity in m/s
   * @param vL liquid velocity in m/s
   * @param rhoG gas density in kg/m3
   * @param rhoL liquid density in kg/m3
   * @param muG gas viscosity in Pa.s
   * @param muL liquid viscosity in Pa.s
   * @param alphaL liquid holdup fraction
   * @param D pipe diameter in m
   * @return interfacial friction result
   */
  private InterfacialFrictionResult calcStratifiedSmooth(double vG, double vL, double rhoG,
      double rhoL, double muG, double muL, double alphaL, double D) {

    InterfacialFrictionResult result = new InterfacialFrictionResult();

    // Get geometry
    StratifiedGeometry geom = geometryCalc.calculateFromHoldup(alphaL, D);

    // Slip velocity
    result.slipVelocity = vG - vL;

    // Interfacial width per unit length
    result.interfacialAreaPerLength = geom.interfacialWidth;

    if (Math.abs(result.slipVelocity) < 1e-10 || geom.interfacialWidth < 1e-10) {
      return result;
    }

    // Gas-side Reynolds number based on slip velocity
    // Use gas hydraulic diameter for characteristic length
    double D_G = geom.gasHydraulicDiameter;
    if (D_G < 1e-10) {
      D_G = D * (1 - alphaL);
    }

    double Re_G = rhoG * Math.abs(result.slipVelocity) * D_G / muG;

    // Smooth interface friction factor (same as smooth pipe)
    if (Re_G < 2300) {
      result.frictionFactor = 16.0 / Math.max(Re_G, 1.0);
    } else {
      // Blasius correlation for turbulent smooth pipe
      result.frictionFactor = 0.079 / Math.pow(Re_G, 0.25);
    }

    // Interfacial shear stress
    // τ_i = 0.5 * f_i * ρ_G * (v_G - v_L) * |v_G - v_L|
    result.interfacialShear =
        0.5 * result.frictionFactor * rhoG * result.slipVelocity * Math.abs(result.slipVelocity);

    return result;
  }

  /**
   * Calculate interfacial friction for stratified wavy flow.
   *
   * <p>
   * Uses Andritsos-Hanratty (1987) correlation which accounts for wave-induced roughness.
   * </p>
   *
   * @param vG gas velocity in m/s
   * @param vL liquid velocity in m/s
   * @param rhoG gas density in kg/m³
   * @param rhoL liquid density in kg/m³
   * @param muG gas viscosity in Pa·s
   * @param muL liquid viscosity in Pa·s
   * @param alphaL liquid holdup (volume fraction)
   * @param D pipe diameter in m
   * @param sigma surface tension in N/m
   * @return interfacial friction calculation result
   */
  private InterfacialFrictionResult calcStratifiedWavy(double vG, double vL, double rhoG,
      double rhoL, double muG, double muL, double alphaL, double D, double sigma) {

    InterfacialFrictionResult result = new InterfacialFrictionResult();

    // Get geometry
    StratifiedGeometry geom = geometryCalc.calculateFromHoldup(alphaL, D);

    result.slipVelocity = vG - vL;
    result.interfacialAreaPerLength = geom.interfacialWidth;

    if (Math.abs(result.slipVelocity) < 1e-10 || geom.interfacialWidth < 1e-10) {
      return result;
    }

    // First get smooth interface friction factor
    double D_G = geom.gasHydraulicDiameter;
    if (D_G < 1e-10) {
      D_G = D * (1 - alphaL);
    }

    double Re_G = rhoG * Math.abs(result.slipVelocity) * D_G / muG;
    double f_smooth;
    if (Re_G < 2300) {
      f_smooth = 16.0 / Math.max(Re_G, 1.0);
    } else {
      f_smooth = 0.079 / Math.pow(Re_G, 0.25);
    }

    // Andritsos-Hanratty enhancement factor
    // f_i = f_smooth * (1 + 15 * sqrt(h_L/D) * (v_G/v_G,t - 1)) for v_G > v_G,t
    // where v_G,t is transition velocity to wavy flow

    // Transition gas velocity (simplified)
    double vG_t = 5.0 * Math.sqrt(rhoL / rhoG); // Approximate transition velocity

    double enhancementFactor = 1.0;
    if (Math.abs(vG) > vG_t && geom.liquidLevel > 1e-10) {
      double sqrtHD = Math.sqrt(geom.liquidLevel / D);
      enhancementFactor = 1.0 + 15.0 * sqrtHD * (Math.abs(vG) / vG_t - 1.0);
      enhancementFactor = Math.min(enhancementFactor, 20.0); // Cap enhancement
    }

    result.frictionFactor = f_smooth * enhancementFactor;

    // Interfacial shear stress
    result.interfacialShear =
        0.5 * result.frictionFactor * rhoG * result.slipVelocity * Math.abs(result.slipVelocity);

    return result;
  }

  /**
   * Calculate interfacial friction for annular flow.
   *
   * <p>
   * Uses Wallis (1969) correlation for gas-core / liquid-film interaction.
   * </p>
   *
   * @param vG gas velocity in m/s
   * @param vL liquid velocity in m/s
   * @param rhoG gas density in kg/m³
   * @param rhoL liquid density in kg/m³
   * @param muG gas viscosity in Pa·s
   * @param muL liquid viscosity in Pa·s
   * @param alphaL liquid holdup (volume fraction)
   * @param D pipe diameter in m
   * @param sigma surface tension in N/m
   * @return interfacial friction calculation result
   */
  private InterfacialFrictionResult calcAnnular(double vG, double vL, double rhoG, double rhoL,
      double muG, double muL, double alphaL, double D, double sigma) {

    InterfacialFrictionResult result = new InterfacialFrictionResult();

    // Film thickness
    double delta = geometryCalc.calcAnnularFilmThickness(alphaL, D);

    // Core diameter
    double D_core = D - 2.0 * delta;
    if (D_core < 1e-10) {
      return result;
    }

    result.slipVelocity = vG - vL;
    result.interfacialAreaPerLength = Math.PI * D_core; // Perimeter of core

    if (Math.abs(result.slipVelocity) < 1e-10) {
      return result;
    }

    // Gas Reynolds number
    double Re_G = rhoG * Math.abs(vG) * D_core / muG;

    // Wallis correlation: f_i = f_G * (1 + 300 * δ/D)
    // Base friction factor
    double f_G;
    if (Re_G < 2300) {
      f_G = 16.0 / Math.max(Re_G, 1.0);
    } else {
      f_G = 0.079 / Math.pow(Re_G, 0.25);
    }

    // Enhancement due to wavy film
    double wallisEnhancement = 1.0 + 300.0 * delta / D;
    wallisEnhancement = Math.min(wallisEnhancement, 50.0); // Cap

    result.frictionFactor = f_G * wallisEnhancement;

    // Interfacial shear
    result.interfacialShear =
        0.5 * result.frictionFactor * rhoG * result.slipVelocity * Math.abs(result.slipVelocity);

    return result;
  }

  /**
   * Calculate interfacial friction for slug flow.
   *
   * <p>
   * Uses Oliemans (1986) approach for Taylor bubble zone.
   * </p>
   *
   * @param vG gas velocity (m/s)
   * @param vL liquid velocity (m/s)
   * @param rhoG gas density (kg/m3)
   * @param rhoL liquid density (kg/m3)
   * @param muG gas viscosity (Pa.s)
   * @param muL liquid viscosity (Pa.s)
   * @param alphaL liquid holdup fraction
   * @param D pipe diameter (m)
   * @return interfacial friction result
   */
  private InterfacialFrictionResult calcSlug(double vG, double vL, double rhoG, double rhoL,
      double muG, double muL, double alphaL, double D) {

    InterfacialFrictionResult result = new InterfacialFrictionResult();

    result.slipVelocity = vG - vL;

    // In slug body, phases are mixed - use bubble swarm approach
    // Interfacial area based on bubble diameter (Hinze correlation for max stable bubble)
    double d_b = 2.0 * Math.pow(0.725 * 0.02 / ((rhoL - rhoG) * GRAVITY), 0.5); // ~5mm bubbles
    d_b = Math.min(d_b, D / 10.0);

    // Interfacial area concentration for dispersed bubbles: a = 6 * (1-alphaL) / d_b
    double alphaG = 1.0 - alphaL;
    double a_i = 6.0 * alphaG / d_b;
    result.interfacialAreaPerLength = a_i * Math.PI * D * D / 4.0;

    if (Math.abs(result.slipVelocity) < 1e-10) {
      return result;
    }

    // Bubble drag coefficient (Ishii-Zuber)
    double Re_b = rhoL * Math.abs(result.slipVelocity) * d_b / muL;
    double C_D;
    if (Re_b < 1000) {
      C_D = 24.0 / Math.max(Re_b, 0.1) * (1.0 + 0.15 * Math.pow(Re_b, 0.687));
    } else {
      C_D = 0.44;
    }

    // Convert to friction factor: f_i = C_D * d_b / (4 * D)
    result.frictionFactor = C_D * d_b / (4.0 * D);

    // Interfacial shear
    result.interfacialShear =
        0.5 * result.frictionFactor * rhoG * result.slipVelocity * Math.abs(result.slipVelocity);

    return result;
  }

  /**
   * Calculate interfacial friction for churn flow.
   *
   * <p>
   * Uses enhanced annular-type correlation.
   * </p>
   *
   * @param vG gas velocity (m/s)
   * @param vL liquid velocity (m/s)
   * @param rhoG gas density (kg/m3)
   * @param rhoL liquid density (kg/m3)
   * @param muG gas viscosity (Pa.s)
   * @param muL liquid viscosity (Pa.s)
   * @param alphaL liquid holdup fraction
   * @param D pipe diameter (m)
   * @param sigma surface tension (N/m)
   * @return interfacial friction result
   */
  private InterfacialFrictionResult calcChurn(double vG, double vL, double rhoG, double rhoL,
      double muG, double muL, double alphaL, double D, double sigma) {

    // Use annular correlation with higher enhancement
    InterfacialFrictionResult result = calcAnnular(vG, vL, rhoG, rhoL, muG, muL, alphaL, D, sigma);

    // Enhance for chaotic flow
    result.frictionFactor *= 1.5;
    result.interfacialShear *= 1.5;

    return result;
  }

  /**
   * Calculate interfacial friction for bubble flow.
   *
   * <p>
   * Uses drag on individual bubbles in liquid continuum.
   * </p>
   *
   * @param vG gas velocity in m/s
   * @param vL liquid velocity in m/s
   * @param rhoG gas density in kg/m³
   * @param rhoL liquid density in kg/m³
   * @param muG gas viscosity in Pa·s
   * @param muL liquid viscosity in Pa·s
   * @param alphaL liquid holdup (volume fraction)
   * @param D pipe diameter in m
   * @return interfacial friction calculation result
   */
  private InterfacialFrictionResult calcBubble(double vG, double vL, double rhoG, double rhoL,
      double muG, double muL, double alphaL, double D) {

    InterfacialFrictionResult result = new InterfacialFrictionResult();

    result.slipVelocity = vG - vL;

    // Bubble diameter (Hinze)
    double sigma = 0.02; // Assume typical surface tension if not provided
    double d_b = 2.0 * Math.pow(0.725 * sigma / ((rhoL - rhoG) * GRAVITY), 0.5);
    d_b = Math.min(d_b, D / 5.0);

    // Interfacial area concentration
    double alphaG = 1.0 - alphaL;
    if (alphaG < 1e-10) {
      return result;
    }

    double a_i = 6.0 * alphaG / d_b;
    result.interfacialAreaPerLength = a_i * Math.PI * D * D / 4.0;

    if (Math.abs(result.slipVelocity) < 1e-10) {
      return result;
    }

    // Bubble Reynolds number
    double Re_b = rhoL * Math.abs(result.slipVelocity) * d_b / muL;

    // Drag coefficient (Schiller-Naumann)
    double C_D;
    if (Re_b < 0.1) {
      C_D = 240.0; // Stokes limit with factor
    } else if (Re_b < 1000) {
      C_D = 24.0 / Re_b * (1.0 + 0.15 * Math.pow(Re_b, 0.687));
    } else {
      C_D = 0.44;
    }

    // Friction factor
    result.frictionFactor = C_D * d_b / (4.0 * D);

    // Interfacial shear (drag force per unit volume * characteristic length)
    result.interfacialShear =
        0.5 * result.frictionFactor * rhoL * result.slipVelocity * Math.abs(result.slipVelocity);

    return result;
  }

  /**
   * Calculate the interfacial friction force per unit pipe length.
   *
   * <p>
   * This is the force that appears in the momentum equations: F_i = τ_i * S_i where S_i is the
   * interfacial width/perimeter per unit length.
   * </p>
   *
   * @param flowRegime Current flow regime
   * @param gasVelocity Gas velocity (m/s)
   * @param liquidVelocity Liquid velocity (m/s)
   * @param gasDensity Gas density (kg/m³)
   * @param liquidDensity Liquid density (kg/m³)
   * @param gasViscosity Gas viscosity (Pa·s)
   * @param liquidViscosity Liquid viscosity (Pa·s)
   * @param liquidHoldup Liquid holdup (0-1)
   * @param diameter Pipe diameter (m)
   * @param surfaceTension Surface tension (N/m)
   * @return Interfacial friction force per unit length (N/m)
   */
  public double calcInterfacialForce(FlowRegime flowRegime, double gasVelocity,
      double liquidVelocity, double gasDensity, double liquidDensity, double gasViscosity,
      double liquidViscosity, double liquidHoldup, double diameter, double surfaceTension) {

    InterfacialFrictionResult result =
        calculate(flowRegime, gasVelocity, liquidVelocity, gasDensity, liquidDensity, gasViscosity,
            liquidViscosity, liquidHoldup, diameter, surfaceTension);

    return result.interfacialShear * result.interfacialAreaPerLength;
  }
}
