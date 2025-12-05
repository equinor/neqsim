package neqsim.process.equipment.pipeline.twophasepipe;

import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Drift-flux model for two-phase pipe flow.
 *
 * <p>
 * Implements the drift-flux model equations for calculating slip between phases, pressure drop, and
 * holdup. The model uses flow-regime-dependent closure relations.
 * </p>
 *
 * <p>
 * Key equations:
 * <ul>
 * <li>v_G = C_0 * v_m + v_d (drift-flux relation)</li>
 * <li>C_0 = distribution coefficient (1.0 - 1.5 typically)</li>
 * <li>v_d = drift velocity (depends on flow regime)</li>
 * </ul>
 * </p>
 *
 * <p>
 * References:
 * <ul>
 * <li>Zuber, N. and Findlay, J.A. (1965) - Average Volumetric Concentration in Two-Phase Flow
 * Systems</li>
 * <li>Bendiksen, K.H. (1984) - An Experimental Investigation of the Motion of Long Bubbles in
 * Inclined Tubes</li>
 * </ul>
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class DriftFluxModel {

  private static final double GRAVITY = 9.81;

  /**
   * Drift-flux parameters for a pipe section.
   */
  public static class DriftFluxParameters {
    /** Distribution coefficient C_0. */
    public double C0;
    /** Drift velocity (m/s). */
    public double driftVelocity;
    /** Gas velocity (m/s). */
    public double gasVelocity;
    /** Liquid velocity (m/s). */
    public double liquidVelocity;
    /** Slip ratio (v_G / v_L). */
    public double slipRatio;
    /** Void fraction. */
    public double voidFraction;
    /** Liquid holdup. */
    public double liquidHoldup;
  }

  /**
   * Calculate drift-flux parameters for a section.
   *
   * @param section Pipe section with current state
   * @return Drift-flux parameters
   */
  public DriftFluxParameters calculateDriftFlux(PipeSection section) {
    DriftFluxParameters params = new DriftFluxParameters();

    double U_SL = section.getSuperficialLiquidVelocity();
    double U_SG = section.getSuperficialGasVelocity();
    double U_M = U_SL + U_SG;

    double D = section.getDiameter();
    double theta = section.getInclination();

    double rho_L = section.getLiquidDensity();
    double rho_G = section.getGasDensity();
    double sigma = section.getSurfaceTension();
    double mu_L = section.getLiquidViscosity();

    FlowRegime regime = section.getFlowRegime();

    // Calculate C0 and drift velocity based on flow regime
    switch (regime) {
      case BUBBLE:
      case DISPERSED_BUBBLE:
        calculateBubbleFlowParameters(params, U_M, D, theta, rho_L, rho_G, sigma);
        break;

      case SLUG:
        calculateSlugFlowParameters(params, U_M, D, theta, rho_L, rho_G, sigma, mu_L);
        break;

      case ANNULAR:
      case CHURN:
        calculateAnnularFlowParameters(params, U_M, D, theta, rho_L, rho_G, sigma);
        break;

      case STRATIFIED_SMOOTH:
      case STRATIFIED_WAVY:
        calculateStratifiedFlowParameters(params, U_SL, U_SG, D, theta, rho_L, rho_G, mu_L);
        break;

      default:
        // Default drift-flux parameters
        params.C0 = 1.0;
        params.driftVelocity = 0;
        break;
    }

    // Calculate actual velocities and holdup from drift-flux relation
    // v_G = C0 * v_m + v_d
    if (U_M > 1e-10) {
      // Void fraction from drift-flux: α_G = U_SG / (C0 * U_M + v_d)
      double denominator = params.C0 * U_M + params.driftVelocity;
      if (denominator > 1e-10) {
        params.voidFraction = U_SG / denominator;
      } else {
        params.voidFraction = U_SG > 0 ? 1.0 : 0.0;
      }

      params.voidFraction = Math.max(0, Math.min(1, params.voidFraction));
      params.liquidHoldup = 1.0 - params.voidFraction;

      // Phase velocities
      if (params.voidFraction > 1e-10) {
        params.gasVelocity = U_SG / params.voidFraction;
      }
      if (params.liquidHoldup > 1e-10) {
        params.liquidVelocity = U_SL / params.liquidHoldup;
      }

      // Slip ratio
      if (params.liquidVelocity > 1e-10) {
        params.slipRatio = params.gasVelocity / params.liquidVelocity;
      }
    }

    return params;
  }

  /**
   * Calculate parameters for bubble flow.
   */
  private void calculateBubbleFlowParameters(DriftFluxParameters params, double U_M, double D,
      double theta, double rho_L, double rho_G, double sigma) {

    // Zuber-Findlay (1965) for bubble flow
    params.C0 = 1.2; // Typical value for turbulent flow

    // Harmathy bubble rise velocity
    double deltaRho = rho_L - rho_G;
    double U_bubble = 1.53 * Math.pow(GRAVITY * sigma * Math.abs(deltaRho) / (rho_L * rho_L), 0.25);

    // Include inclination effect
    params.driftVelocity = U_bubble * Math.sin(theta);
    if (theta < 0) {
      // Downward flow - bubbles still rise relative to liquid
      params.driftVelocity = Math.abs(params.driftVelocity);
    }
  }

  /**
   * Calculate parameters for slug flow.
   *
   * <p>
   * Uses Bendiksen (1984) correlation for Taylor bubble velocity.
   * </p>
   */
  private void calculateSlugFlowParameters(DriftFluxParameters params, double U_M, double D,
      double theta, double rho_L, double rho_G, double sigma, double mu_L) {

    // Bendiksen (1984) distribution coefficient
    double Fr_M = U_M / Math.sqrt(GRAVITY * D);

    if (Fr_M > 3.5) {
      params.C0 = 1.2;
    } else {
      params.C0 = 1.05 + 0.15 * Math.sin(theta);
    }

    // Taylor bubble drift velocity
    double deltaRho = rho_L - rho_G;

    // Horizontal component (Zukoski)
    double U_d_horiz = 0.54 * Math.sqrt(GRAVITY * D * deltaRho / rho_L);

    // Vertical component (Dumitrescu, Davies-Taylor)
    double U_d_vert = 0.35 * Math.sqrt(GRAVITY * D * deltaRho / rho_L);

    // Bendiksen interpolation for inclined pipes
    double absTheta = Math.abs(theta);
    if (absTheta < Math.PI / 6) {
      // Near horizontal
      params.driftVelocity = U_d_horiz * Math.cos(theta) + U_d_vert * Math.sin(theta);
    } else if (absTheta > Math.PI / 3) {
      // Near vertical
      params.driftVelocity = U_d_vert * Math.sin(theta);
    } else {
      // Interpolation
      double w = (absTheta - Math.PI / 6) / (Math.PI / 6);
      params.driftVelocity = (1 - w) * U_d_horiz * Math.cos(theta) + w * U_d_vert * Math.sin(theta);
    }
  }

  /**
   * Calculate parameters for annular flow.
   */
  private void calculateAnnularFlowParameters(DriftFluxParameters params, double U_M, double D,
      double theta, double rho_L, double rho_G, double sigma) {

    // Annular flow: thin liquid film, gas core
    // Less slip, more homogeneous
    params.C0 = 1.0;

    // Drift velocity based on film drainage
    double deltaRho = rho_L - rho_G;
    params.driftVelocity =
        0.2 * Math.sqrt(GRAVITY * D * Math.abs(deltaRho) / rho_L) * Math.sin(theta);
  }

  /**
   * Calculate parameters for stratified flow.
   *
   * <p>
   * Stratified flow doesn't follow simple drift-flux; use momentum balance.
   * </p>
   */
  private void calculateStratifiedFlowParameters(DriftFluxParameters params, double U_SL,
      double U_SG, double D, double theta, double rho_L, double rho_G, double mu_L) {

    // For stratified flow, C0 and drift velocity concept doesn't apply directly
    // Instead, calculate holdup from momentum balance

    double h_L = estimateStratifiedLevel(U_SL, U_SG, D, theta, rho_L, rho_G, mu_L);
    double alpha_L = calculateHoldupFromLevel(h_L, D);

    params.liquidHoldup = alpha_L;
    params.voidFraction = 1.0 - alpha_L;

    // Calculate equivalent C0 and drift velocity
    if (U_SL + U_SG > 1e-10 && params.voidFraction > 1e-10) {
      // v_G = U_SG / alpha_G = C0 * v_m + v_d
      params.gasVelocity = U_SG / params.voidFraction;
      params.liquidVelocity = U_SL / Math.max(params.liquidHoldup, 1e-10);

      // Back-calculate C0 assuming v_d = 0 for stratified
      params.C0 = params.gasVelocity / (U_SL + U_SG);
      params.driftVelocity = 0;
    } else {
      params.C0 = 1.0;
      params.driftVelocity = 0;
    }
  }

  /**
   * Estimate liquid level in stratified flow.
   */
  private double estimateStratifiedLevel(double U_SL, double U_SG, double D, double theta,
      double rho_L, double rho_G, double mu_L) {

    // Iterative solution of momentum balance
    double h = 0.5 * D;

    for (int iter = 0; iter < 20; iter++) {
      double alpha_L = calculateHoldupFromLevel(h, D);
      double alpha_G = 1.0 - alpha_L;

      if (alpha_L < 0.01 || alpha_G < 0.01) {
        break;
      }

      double A = Math.PI * D * D / 4.0;
      double A_L = alpha_L * A;
      double A_G = alpha_G * A;

      double U_L = U_SL * A / A_L;
      double U_G = U_SG * A / A_G;

      // Simplified momentum balance
      double tau_ratio = (rho_G * U_G * U_G) / (rho_L * U_L * U_L + 1e-10);
      double gravity_effect =
          (rho_L - rho_G) * GRAVITY * Math.sin(theta) * D / (rho_L * U_L * U_L + 1e-10);

      // Adjust level based on balance
      double adjustment = 0.05 * D * (tau_ratio - 1.0 - gravity_effect);
      h = Math.max(0.01 * D, Math.min(0.99 * D, h - adjustment));
    }

    return h;
  }

  /**
   * Calculate holdup from liquid level in circular pipe.
   */
  private double calculateHoldupFromLevel(double h, double D) {
    if (h <= 0) {
      return 0;
    }
    if (h >= D) {
      return 1;
    }
    double theta = 2.0 * Math.acos(1.0 - 2.0 * h / D);
    return (theta - Math.sin(theta)) / (2.0 * Math.PI);
  }

  /**
   * Calculate pressure gradient for drift-flux model.
   *
   * @param section Pipe section
   * @param params Drift-flux parameters
   * @return Total pressure gradient (Pa/m), negative means pressure decreases in flow direction
   */
  public double calculatePressureGradient(PipeSection section, DriftFluxParameters params) {
    double D = section.getDiameter();
    double theta = section.getInclination();
    double roughness = section.getRoughness();

    double rho_L = section.getLiquidDensity();
    double rho_G = section.getGasDensity();
    double mu_L = section.getLiquidViscosity();
    double mu_G = section.getGasViscosity();

    double alpha_L = params.liquidHoldup;
    double alpha_G = params.voidFraction;

    // Mixture density
    double rho_m = alpha_L * rho_L + alpha_G * rho_G;

    // Gravity component
    double dP_gravity = -rho_m * GRAVITY * Math.sin(theta);

    // Friction component - use appropriate correlation based on regime
    double dP_friction = calculateFrictionGradient(section, params, roughness);

    // Acceleration component (usually small for steady flow)
    double dP_accel = 0; // Neglected for now

    section.setGravityPressureGradient(dP_gravity);
    section.setFrictionPressureGradient(dP_friction);

    return dP_gravity + dP_friction + dP_accel;
  }

  /**
   * Calculate friction pressure gradient.
   */
  private double calculateFrictionGradient(PipeSection section, DriftFluxParameters params,
      double roughness) {

    double D = section.getDiameter();
    double rho_L = section.getLiquidDensity();
    double rho_G = section.getGasDensity();
    double mu_L = section.getLiquidViscosity();
    double mu_G = section.getGasViscosity();

    double U_SL = section.getSuperficialLiquidVelocity();
    double U_SG = section.getSuperficialGasVelocity();
    double U_M = U_SL + U_SG;

    FlowRegime regime = section.getFlowRegime();

    switch (regime) {
      case STRATIFIED_SMOOTH:
      case STRATIFIED_WAVY:
        return calculateStratifiedFriction(section, params, roughness);

      case ANNULAR:
        return calculateAnnularFriction(section, params, roughness);

      default:
        // Use homogeneous model as default
        return calculateHomogeneousFriction(section, params, roughness);
    }
  }

  /**
   * Calculate friction for homogeneous model (bubble, slug, dispersed).
   */
  private double calculateHomogeneousFriction(PipeSection section, DriftFluxParameters params,
      double roughness) {

    double D = section.getDiameter();
    double U_M = section.getSuperficialGasVelocity() + section.getSuperficialLiquidVelocity();

    double alpha_L = params.liquidHoldup;
    double alpha_G = params.voidFraction;

    // Mixture properties
    double rho_m = alpha_L * section.getLiquidDensity() + alpha_G * section.getGasDensity();
    double mu_m = alpha_L * section.getLiquidViscosity() + alpha_G * section.getGasViscosity();

    // Reynolds number
    double Re = rho_m * U_M * D / mu_m;

    // Friction factor (Haaland)
    double f = calcFrictionFactor(Re, roughness, D);

    // Darcy-Weisbach
    return -f * rho_m * U_M * Math.abs(U_M) / (2.0 * D);
  }

  /**
   * Calculate friction for stratified flow (two-fluid approach).
   */
  private double calculateStratifiedFriction(PipeSection section, DriftFluxParameters params,
      double roughness) {

    double D = section.getDiameter();
    double h_L = params.liquidHoldup * D; // Approximate

    // Calculate wetted perimeters and hydraulic diameters
    double beta = 2.0 * Math.acos(1.0 - 2.0 * h_L / D);
    double A_L = D * D / 8.0 * (beta - Math.sin(beta));
    double A_G = Math.PI * D * D / 4.0 - A_L;
    double S_L = D * beta / 2.0;
    double S_G = D * (Math.PI - beta / 2.0);

    if (A_L < 1e-10 || A_G < 1e-10) {
      return calculateHomogeneousFriction(section, params, roughness);
    }

    double U_L = params.liquidVelocity;
    double U_G = params.gasVelocity;

    double D_hL = 4.0 * A_L / S_L;
    double D_hG = 4.0 * A_G / S_G;

    double rho_L = section.getLiquidDensity();
    double rho_G = section.getGasDensity();
    double mu_L = section.getLiquidViscosity();
    double mu_G = section.getGasViscosity();

    // Friction factors
    double Re_L = rho_L * Math.abs(U_L) * D_hL / mu_L;
    double Re_G = rho_G * Math.abs(U_G) * D_hG / mu_G;

    double f_L = calcFrictionFactor(Re_L, roughness, D_hL);
    double f_G = calcFrictionFactor(Re_G, roughness, D_hG);

    // Combined pressure gradient
    double dP_L = f_L * rho_L * U_L * Math.abs(U_L) / (2.0 * D_hL);
    double dP_G = f_G * rho_G * U_G * Math.abs(U_G) / (2.0 * D_hG);

    // Weight by flow area
    double A_total = Math.PI * D * D / 4.0;
    return -(A_L * dP_L + A_G * dP_G) / A_total;
  }

  /**
   * Calculate friction for annular flow.
   */
  private double calculateAnnularFriction(PipeSection section, DriftFluxParameters params,
      double roughness) {

    double D = section.getDiameter();
    double alpha_L = params.liquidHoldup;

    // Film thickness (thin film assumption)
    double delta = D / 2.0 * (1.0 - Math.sqrt(1.0 - alpha_L));

    // Gas core diameter
    double D_core = D - 2.0 * delta;

    if (D_core < 0.1 * D) {
      return calculateHomogeneousFriction(section, params, roughness);
    }

    // Gas core pressure drop dominates
    double rho_G = section.getGasDensity();
    double mu_G = section.getGasViscosity();
    double U_G = params.gasVelocity;

    double Re_G = rho_G * Math.abs(U_G) * D_core / mu_G;

    // Enhanced roughness due to wavy interface
    double k_eff = roughness + 0.5 * delta;
    double f_G = calcFrictionFactor(Re_G, k_eff, D_core);

    return -f_G * rho_G * U_G * Math.abs(U_G) / (2.0 * D_core);
  }

  /**
   * Calculate Darcy friction factor using Haaland correlation.
   */
  private double calcFrictionFactor(double Re, double roughness, double D) {
    if (Re < 10) {
      return 6.4; // Very low Re
    }
    if (Re < 2300) {
      return 64.0 / Re; // Laminar
    }

    // Haaland (1983)
    double relRough = roughness / D;
    double term = Math.pow(relRough / 3.7, 1.11) + 6.9 / Re;
    double f = Math.pow(-1.8 * Math.log10(term), -2);

    return Math.max(f, 0.001);
  }
}
