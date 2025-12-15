package neqsim.process.equipment.pipeline.twophasepipe.closure;

import java.io.Serializable;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator.StratifiedGeometry;

/**
 * Wall friction correlations for two-fluid multiphase pipe flow.
 *
 * <p>
 * Provides separate wall friction calculations for gas and liquid phases based on flow regime. The
 * wall shear stress is calculated as: τ_w = 0.5 * f * ρ * v² where f is the Fanning friction
 * factor.
 * </p>
 *
 * <h2>Correlations by Flow Regime</h2>
 * <ul>
 * <li><b>Stratified:</b> Taitel-Dukler (separate phases with hydraulic diameters)</li>
 * <li><b>Slug:</b> Slug body + film friction</li>
 * <li><b>Annular:</b> Core + film model</li>
 * <li><b>Single-phase:</b> Haaland/Colebrook-White</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class WallFriction implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Default pipe roughness (m). */
  private double defaultRoughness = 4.6e-5;

  /** Geometry calculator for stratified flow. */
  private GeometryCalculator geometryCalc;

  /**
   * Result container for wall friction calculations.
   */
  public static class WallFrictionResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Gas wall shear stress (Pa). */
    public double gasWallShear;

    /** Liquid wall shear stress (Pa). */
    public double liquidWallShear;

    /** Gas Fanning friction factor. */
    public double gasFrictionFactor;

    /** Liquid Fanning friction factor. */
    public double liquidFrictionFactor;

    /** Gas Reynolds number. */
    public double gasReynolds;

    /** Liquid Reynolds number. */
    public double liquidReynolds;
  }

  /**
   * Constructor.
   */
  public WallFriction() {
    this.geometryCalc = new GeometryCalculator();
  }

  /**
   * Calculate wall friction for both phases.
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
   * @param roughness Pipe wall roughness (m)
   * @return Wall friction results for both phases
   */
  public WallFrictionResult calculate(FlowRegime flowRegime, double gasVelocity,
      double liquidVelocity, double gasDensity, double liquidDensity, double gasViscosity,
      double liquidViscosity, double liquidHoldup, double diameter, double roughness) {

    switch (flowRegime) {
      case STRATIFIED_SMOOTH:
      case STRATIFIED_WAVY:
        return calcStratifiedFriction(gasVelocity, liquidVelocity, gasDensity, liquidDensity,
            gasViscosity, liquidViscosity, liquidHoldup, diameter, roughness);

      case SLUG:
        return calcSlugFriction(gasVelocity, liquidVelocity, gasDensity, liquidDensity,
            gasViscosity, liquidViscosity, liquidHoldup, diameter, roughness);

      case ANNULAR:
      case MIST:
        return calcAnnularFriction(gasVelocity, liquidVelocity, gasDensity, liquidDensity,
            gasViscosity, liquidViscosity, liquidHoldup, diameter, roughness);

      case BUBBLE:
      case DISPERSED_BUBBLE:
        return calcBubbleFriction(gasVelocity, liquidVelocity, gasDensity, liquidDensity,
            gasViscosity, liquidViscosity, liquidHoldup, diameter, roughness);

      case CHURN:
        return calcChurnFriction(gasVelocity, liquidVelocity, gasDensity, liquidDensity,
            gasViscosity, liquidViscosity, liquidHoldup, diameter, roughness);

      case SINGLE_PHASE_GAS:
        return calcSinglePhaseGasFriction(gasVelocity, gasDensity, gasViscosity, diameter,
            roughness);

      case SINGLE_PHASE_LIQUID:
        return calcSinglePhaseLiquidFriction(liquidVelocity, liquidDensity, liquidViscosity,
            diameter, roughness);

      default:
        // Default to stratified
        return calcStratifiedFriction(gasVelocity, liquidVelocity, gasDensity, liquidDensity,
            gasViscosity, liquidViscosity, liquidHoldup, diameter, roughness);
    }
  }

  /**
   * Calculate wall friction for stratified flow using Taitel-Dukler approach.
   *
   * <p>
   * Each phase uses its hydraulic diameter for friction calculation.
   * </p>
   */
  private WallFrictionResult calcStratifiedFriction(double vG, double vL, double rhoG, double rhoL,
      double muG, double muL, double alphaL, double D, double eps) {

    WallFrictionResult result = new WallFrictionResult();

    // Get geometry
    StratifiedGeometry geom = geometryCalc.calculateFromHoldup(alphaL, D);

    // Gas phase friction
    double D_G = geom.gasHydraulicDiameter;
    if (D_G > 1e-10 && Math.abs(vG) > 1e-10) {
      result.gasReynolds = rhoG * Math.abs(vG) * D_G / muG;
      result.gasFrictionFactor = calcFanningFrictionFactor(result.gasReynolds, eps, D_G);
      result.gasWallShear = 0.5 * result.gasFrictionFactor * rhoG * vG * Math.abs(vG);
    }

    // Liquid phase friction
    double D_L = geom.liquidHydraulicDiameter;
    if (D_L > 1e-10 && Math.abs(vL) > 1e-10) {
      result.liquidReynolds = rhoL * Math.abs(vL) * D_L / muL;
      result.liquidFrictionFactor = calcFanningFrictionFactor(result.liquidReynolds, eps, D_L);
      result.liquidWallShear = 0.5 * result.liquidFrictionFactor * rhoL * vL * Math.abs(vL);
    }

    return result;
  }

  /**
   * Calculate wall friction for slug flow.
   *
   * <p>
   * Uses mixture properties in slug body, stratified approach in film region.
   * </p>
   */
  private WallFrictionResult calcSlugFriction(double vG, double vL, double rhoG, double rhoL,
      double muG, double muL, double alphaL, double D, double eps) {

    WallFrictionResult result = new WallFrictionResult();

    // Slug body: treat as homogeneous mixture
    double alphaG = 1.0 - alphaL;
    double rhoMix = alphaG * rhoG + alphaL * rhoL;
    double vMix = alphaG * vG + alphaL * vL;

    // Mixture viscosity (Cicchitti correlation)
    double muMix = alphaG * muG + alphaL * muL;

    double Re = rhoMix * Math.abs(vMix) * D / muMix;
    double f = calcFanningFrictionFactor(Re, eps, D);

    double tauMix = 0.5 * f * rhoMix * vMix * Math.abs(vMix);

    // Distribute to phases by volume fraction
    result.gasWallShear = alphaG * tauMix;
    result.liquidWallShear = alphaL * tauMix;
    result.gasFrictionFactor = f;
    result.liquidFrictionFactor = f;
    result.gasReynolds = Re;
    result.liquidReynolds = Re;

    return result;
  }

  /**
   * Calculate wall friction for annular flow.
   *
   * <p>
   * Liquid film on wall, gas in core. Film friction dominates.
   * </p>
   */
  private WallFrictionResult calcAnnularFriction(double vG, double vL, double rhoG, double rhoL,
      double muG, double muL, double alphaL, double D, double eps) {

    WallFrictionResult result = new WallFrictionResult();

    // Film thickness
    double delta = geometryCalc.calcAnnularFilmThickness(alphaL, D);

    // Liquid film: use film thickness as characteristic length
    if (delta > 1e-10 && Math.abs(vL) > 1e-10) {
      // Film Reynolds number based on film thickness
      result.liquidReynolds = rhoL * Math.abs(vL) * delta / muL;

      // For thin films, use laminar or wavy film correlation
      if (result.liquidReynolds < 1000) {
        result.liquidFrictionFactor = 16.0 / Math.max(result.liquidReynolds, 1.0);
      } else {
        result.liquidFrictionFactor = calcFanningFrictionFactor(result.liquidReynolds, eps, delta);
      }
      result.liquidWallShear = 0.5 * result.liquidFrictionFactor * rhoL * vL * Math.abs(vL);
    }

    // Gas core: essentially no wall contact
    // Gas friction is via interfacial friction (handled separately)
    double D_core = D - 2.0 * delta;
    if (D_core > 1e-10 && Math.abs(vG) > 1e-10) {
      result.gasReynolds = rhoG * Math.abs(vG) * D_core / muG;
      // Very small wall friction for gas in annular (contact via entrained droplets)
      result.gasFrictionFactor = 0.001; // Minimal
      result.gasWallShear = 0;
    }

    return result;
  }

  /**
   * Calculate wall friction for bubble/dispersed bubble flow.
   *
   * <p>
   * Liquid-continuous flow with dispersed gas bubbles. Use liquid properties.
   * </p>
   */
  private WallFrictionResult calcBubbleFriction(double vG, double vL, double rhoG, double rhoL,
      double muG, double muL, double alphaL, double D, double eps) {

    WallFrictionResult result = new WallFrictionResult();

    // Mixture velocity and density (liquid-continuous)
    double alphaG = 1.0 - alphaL;
    double rhoMix = alphaG * rhoG + alphaL * rhoL;
    double vMix = alphaG * vG + alphaL * vL;

    // Use liquid viscosity (continuous phase)
    result.liquidReynolds = rhoMix * Math.abs(vMix) * D / muL;
    result.liquidFrictionFactor = calcFanningFrictionFactor(result.liquidReynolds, eps, D);

    double tauWall = 0.5 * result.liquidFrictionFactor * rhoMix * vMix * Math.abs(vMix);

    // All friction assigned to liquid (wall contact)
    result.liquidWallShear = tauWall;
    result.gasWallShear = 0;
    result.gasReynolds = result.liquidReynolds;
    result.gasFrictionFactor = 0;

    return result;
  }

  /**
   * Calculate wall friction for churn flow.
   *
   * <p>
   * Similar to slug but more chaotic. Use mixture approach.
   * </p>
   */
  private WallFrictionResult calcChurnFriction(double vG, double vL, double rhoG, double rhoL,
      double muG, double muL, double alphaL, double D, double eps) {
    // Treat similar to slug
    return calcSlugFriction(vG, vL, rhoG, rhoL, muG, muL, alphaL, D, eps);
  }

  /**
   * Calculate wall friction for single-phase gas flow.
   */
  private WallFrictionResult calcSinglePhaseGasFriction(double vG, double rhoG, double muG,
      double D, double eps) {

    WallFrictionResult result = new WallFrictionResult();

    if (Math.abs(vG) > 1e-10) {
      result.gasReynolds = rhoG * Math.abs(vG) * D / muG;
      result.gasFrictionFactor = calcFanningFrictionFactor(result.gasReynolds, eps, D);
      result.gasWallShear = 0.5 * result.gasFrictionFactor * rhoG * vG * Math.abs(vG);
    }

    return result;
  }

  /**
   * Calculate wall friction for single-phase liquid flow.
   */
  private WallFrictionResult calcSinglePhaseLiquidFriction(double vL, double rhoL, double muL,
      double D, double eps) {

    WallFrictionResult result = new WallFrictionResult();

    if (Math.abs(vL) > 1e-10) {
      result.liquidReynolds = rhoL * Math.abs(vL) * D / muL;
      result.liquidFrictionFactor = calcFanningFrictionFactor(result.liquidReynolds, eps, D);
      result.liquidWallShear = 0.5 * result.liquidFrictionFactor * rhoL * vL * Math.abs(vL);
    }

    return result;
  }

  /**
   * Calculate Fanning friction factor using Haaland equation.
   *
   * <p>
   * Explicit approximation to Colebrook-White equation, valid for turbulent flow. For laminar flow
   * (Re &lt; 2300), uses f = 16/Re.
   * </p>
   *
   * @param reynolds Reynolds number
   * @param roughness Pipe roughness (m)
   * @param diameter Pipe diameter (m)
   * @return Fanning friction factor
   */
  public double calcFanningFrictionFactor(double reynolds, double roughness, double diameter) {
    if (reynolds < 1e-10) {
      return 0;
    }

    // Relative roughness
    double epsD = roughness / diameter;

    if (reynolds < 2300) {
      // Laminar flow
      return 16.0 / reynolds;
    } else if (reynolds < 4000) {
      // Transition region - interpolate
      double fLam = 16.0 / reynolds;
      double fTurb = calcTurbulentFanning(reynolds, epsD);
      double x = (reynolds - 2300) / 1700.0;
      return fLam * (1 - x) + fTurb * x;
    } else {
      // Turbulent flow - Haaland equation
      return calcTurbulentFanning(reynolds, epsD);
    }
  }

  /**
   * Haaland equation for turbulent Fanning friction factor.
   */
  private double calcTurbulentFanning(double Re, double epsD) {
    // Haaland equation (gives Darcy friction factor)
    // 1/sqrt(f_D) = -1.8 * log10((ε/D/3.7)^1.11 + 6.9/Re)
    // Fanning = Darcy / 4

    double term1 = Math.pow(epsD / 3.7, 1.11);
    double term2 = 6.9 / Re;
    double logArg = term1 + term2;

    if (logArg <= 0) {
      return 0.005; // Fallback
    }

    double invSqrtFd = -1.8 * Math.log10(logArg);
    if (invSqrtFd <= 0) {
      return 0.005; // Fallback
    }

    double fDarcy = 1.0 / (invSqrtFd * invSqrtFd);
    return fDarcy / 4.0; // Convert to Fanning
  }

  /**
   * Calculate Colebrook-White friction factor iteratively.
   *
   * <p>
   * More accurate than Haaland for edge cases.
   * </p>
   *
   * @param reynolds Reynolds number
   * @param roughness Pipe roughness (m)
   * @param diameter Pipe diameter (m)
   * @return Fanning friction factor
   */
  public double calcColebrookFanning(double reynolds, double roughness, double diameter) {
    if (reynolds < 2300) {
      return 16.0 / reynolds;
    }

    double epsD = roughness / diameter;

    // Initial guess from Haaland
    double f = calcTurbulentFanning(reynolds, epsD) * 4.0; // Darcy

    // Newton-Raphson for Colebrook
    for (int i = 0; i < 20; i++) {
      double sqrtF = Math.sqrt(f);
      double rhs = -2.0 * Math.log10(epsD / 3.7 + 2.51 / (reynolds * sqrtF));
      double fNew = 1.0 / (rhs * rhs);

      if (Math.abs(fNew - f) < 1e-10) {
        break;
      }
      f = fNew;
    }

    return f / 4.0; // Convert to Fanning
  }

  /**
   * Get default roughness value.
   *
   * @return Default roughness (m)
   */
  public double getDefaultRoughness() {
    return defaultRoughness;
  }

  /**
   * Set default roughness value.
   *
   * @param roughness Default roughness (m)
   */
  public void setDefaultRoughness(double roughness) {
    this.defaultRoughness = roughness;
  }
}
