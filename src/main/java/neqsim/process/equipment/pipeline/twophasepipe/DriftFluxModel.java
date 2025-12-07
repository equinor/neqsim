package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Drift-flux model for two-phase pipe flow.
 *
 * <p>
 * Implements the drift-flux model equations for calculating slip between phases, pressure drop, and
 * holdup. The model uses flow-regime-dependent closure relations.
 *
 * <p>
 * Key equations:
 * <ul>
 * <li>v_G = C_0 * v_m + v_d (drift-flux relation)</li>
 * <li>C_0 = distribution coefficient (1.0 - 1.5 typically)</li>
 * <li>v_d = drift velocity (depends on flow regime)</li>
 * </ul>
 *
 * <p>
 * References:
 * <ul>
 * <li>Zuber, N. and Findlay, J.A. (1965) - Average Volumetric Concentration in Two-Phase Flow
 * Systems</li>
 * <li>Bendiksen, K.H. (1984) - An Experimental Investigation of the Motion of Long Bubbles in
 * Inclined Tubes</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class DriftFluxModel implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final double GRAVITY = 9.81;

  /**
   * Drift-flux parameters for a pipe section.
   */
  public static class DriftFluxParameters implements Serializable {
    private static final long serialVersionUID = 1L;
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
   *
   * @param params output structure for drift-flux parameters
   * @param U_M mixture velocity [m/s]
   * @param D pipe diameter [m]
   * @param theta pipe inclination [rad]
   * @param rho_L liquid density [kg/m³]
   * @param rho_G gas density [kg/m³]
   * @param sigma surface tension [N/m]
   */
  private void calculateBubbleFlowParameters(DriftFluxParameters params, double U_M, double D,
      double theta, double rho_L, double rho_G, double sigma) {

    // Zuber-Findlay (1965) for bubble flow
    // C0 depends on void fraction and velocity profile
    // C0 = 1.2 for turbulent pipe flow, 1.0-1.1 for low void fraction
    params.C0 = 1.2;

    // Harmathy (1960) terminal rise velocity for single bubble
    // v_inf = 1.53 * (g*sigma*delta_rho / rho_L^2)^0.25
    double deltaRho = Math.max(rho_L - rho_G, 0.01);
    double U_bubble = 1.53 * Math.pow(GRAVITY * sigma * deltaRho / (rho_L * rho_L), 0.25);

    // Drift velocity in pipe direction
    // Bubbles rise vertically; component in pipe direction depends on inclination
    // For upward inclined pipe (theta > 0): v_d = v_bubble (bubbles assist flow)
    // For downward inclined (theta < 0): v_d = v_bubble * |sin(theta)| (drift against flow)
    // For horizontal (theta = 0): v_d approaches 0 (bubbles rise perpendicular to flow)
    double absTheta = Math.abs(theta);
    if (absTheta > 0.01) {
      // Inclined pipe - bubble drift contributes to flow in pipe direction
      params.driftVelocity = U_bubble * Math.abs(Math.sin(theta));
    } else {
      // Nearly horizontal - small drift due to bubble swarm effects
      params.driftVelocity = 0.1 * U_bubble;
    }
  }

  /**
   * Calculate parameters for slug flow.
   *
   * <p>
   * Uses Bendiksen (1984) correlation for Taylor bubble velocity.
   * </p>
   *
   * @param params output structure for drift-flux parameters
   * @param U_M mixture velocity [m/s]
   * @param D pipe diameter [m]
   * @param theta pipe inclination [rad]
   * @param rho_L liquid density [kg/m³]
   * @param rho_G gas density [kg/m³]
   * @param sigma surface tension [N/m]
   * @param mu_L liquid viscosity [Pa.s]
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
   *
   * @param params drift flux parameters to be calculated
   * @param U_M mixture velocity
   * @param D pipe diameter
   * @param theta pipe inclination angle
   * @param rho_L liquid density
   * @param rho_G gas density
   * @param sigma surface tension
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
   *
   * @param params drift flux parameters to be calculated
   * @param U_SL superficial liquid velocity
   * @param U_SG superficial gas velocity
   * @param D pipe diameter
   * @param theta pipe inclination angle
   * @param rho_L liquid density
   * @param rho_G gas density
   * @param mu_L liquid viscosity
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
   * 
   * @param U_SL superficial liquid velocity (m/s)
   * @param U_SG superficial gas velocity (m/s)
   * @param D pipe diameter (m)
   * @param theta pipe inclination angle (rad)
   * @param rho_L liquid density (kg/m³)
   * @param rho_G gas density (kg/m³)
   * @param mu_L liquid viscosity (Pa·s)
   * @return liquid level height (m)
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
      case SINGLE_PHASE_GAS:
        // Pure gas flow - use Darcy-Weisbach with gas properties
        return calculateSinglePhaseGasFriction(section, roughness);

      case SINGLE_PHASE_LIQUID:
        // Pure liquid flow - use Darcy-Weisbach with liquid properties
        return calculateSinglePhaseLiquidFriction(section, roughness);

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
   * Calculate friction for single-phase gas flow.
   */
  private double calculateSinglePhaseGasFriction(PipeSection section, double roughness) {
    double D = section.getDiameter();
    double rho_G = section.getGasDensity();
    double mu_G = section.getGasViscosity();
    double U_SG = section.getSuperficialGasVelocity();

    // Protect against zero/NaN values
    if (rho_G <= 0 || mu_G <= 0 || U_SG <= 0 || D <= 0) {
      return 0;
    }

    // Reynolds number
    double Re = rho_G * U_SG * D / mu_G;

    // Friction factor (Haaland)
    double f = calcFrictionFactor(Re, roughness, D);

    // Darcy-Weisbach: dP/dx = -f * (rho * U^2) / (2 * D)
    return -f * rho_G * U_SG * U_SG / (2.0 * D);
  }

  /**
   * Calculate friction for single-phase liquid flow.
   */
  private double calculateSinglePhaseLiquidFriction(PipeSection section, double roughness) {
    double D = section.getDiameter();
    double rho_L = section.getLiquidDensity();
    double mu_L = section.getLiquidViscosity();
    double U_SL = section.getSuperficialLiquidVelocity();

    // Protect against zero/NaN values
    if (rho_L <= 0 || mu_L <= 0 || U_SL <= 0 || D <= 0) {
      return 0;
    }

    // Reynolds number
    double Re = rho_L * U_SL * D / mu_L;

    // Friction factor (Haaland)
    double f = calcFrictionFactor(Re, roughness, D);

    // Darcy-Weisbach: dP/dx = -f * (rho * U^2) / (2 * D)
    return -f * rho_L * U_SL * U_SL / (2.0 * D);
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

  // ========== Energy Equation Methods ==========

  /**
   * Energy equation result containing temperature change and heat transfer components.
   */
  public static class EnergyEquationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** New temperature after energy balance (K). */
    public double newTemperature;
    /** Temperature change from Joule-Thomson effect (K). */
    public double jouleThomsonDeltaT;
    /** Temperature change from heat transfer to surroundings (K). */
    public double heatTransferDeltaT;
    /** Temperature change from friction heating (K). */
    public double frictionHeatingDeltaT;
    /** Temperature change from elevation work (K). */
    public double elevationWorkDeltaT;
    /** Total heat transfer rate to surroundings (W). */
    public double heatTransferRate;
    /** Friction heating power (W). */
    public double frictionHeatingPower;
  }

  /**
   * Calculate temperature change in fluid flow using the energy equation.
   *
   * <p>
   * The energy equation for steady-state pipe flow is:
   * </p>
   * 
   * <pre>
   * ṁ·Cp·dT/dx = Q̇_wall + Q̇_friction - ṁ·μ_JT·Cp·dP/dx - ṁ·g·sin(θ)·dz/dx
   * </pre>
   *
   * <p>
   * Where:
   * </p>
   * <ul>
   * <li>Q̇_wall = U·A·(T_ambient - T_fluid) - heat transfer to/from surroundings</li>
   * <li>Q̇_friction = |dP/dx|_friction · Q_vol - viscous dissipation heating</li>
   * <li>μ_JT = Joule-Thomson coefficient (K/Pa)</li>
   * <li>dP/dx = pressure gradient (Pa/m)</li>
   * </ul>
   *
   * <p>
   * For multiphase flow, mixture properties are used with appropriate averaging.
   * </p>
   *
   * @param section Current pipe section with fluid state
   * @param params Drift-flux parameters from calculateDriftFlux
   * @param dt Time step (s)
   * @param dx Spatial step (m)
   * @param ambientTemperature Ambient temperature (K)
   * @param overallHeatTransferCoeff Overall heat transfer coefficient U (W/(m²·K))
   * @param jouleThomsonCoeff Joule-Thomson coefficient (K/Pa), typically ~2e-6 for gas
   * @return EnergyEquationResult containing temperature change components
   */
  public EnergyEquationResult calculateEnergyEquation(PipeSection section,
      DriftFluxParameters params, double dt, double dx, double ambientTemperature,
      double overallHeatTransferCoeff, double jouleThomsonCoeff) {

    EnergyEquationResult result = new EnergyEquationResult();

    double T = section.getTemperature();
    double D = section.getDiameter();
    double theta = section.getInclination();

    // Phase properties
    double rho_L = section.getLiquidDensity();
    double rho_G = section.getGasDensity();
    double alpha_L = params.liquidHoldup;
    double alpha_G = params.voidFraction;

    // Mixture properties
    double rho_m = alpha_L * rho_L + alpha_G * rho_G;
    double Cp_mix = section.getMixtureHeatCapacity(); // J/(kg·K)
    if (Cp_mix <= 0) {
      Cp_mix = 2000.0; // Default fallback
    }

    // Velocities and flow rates
    double A = section.getArea();
    double U_M = section.getMixtureVelocity();
    double massFlowRate = rho_m * A * Math.abs(U_M);

    // Protect against zero mass flow
    if (massFlowRate < 1e-10) {
      result.newTemperature = T;
      return result;
    }

    // 1. Heat transfer to surroundings
    // Q̇_wall = U · π · D · dx · (T_ambient - T)
    double wallArea = Math.PI * D * dx;
    result.heatTransferRate = overallHeatTransferCoeff * wallArea * (ambientTemperature - T);

    // Temperature change from heat transfer: dT = Q̇ · dt / (m · Cp)
    double massInSection = rho_m * A * dx;
    if (massInSection > 1e-10) {
      result.heatTransferDeltaT = result.heatTransferRate * dt / (massInSection * Cp_mix);
    }

    // 2. Joule-Thomson effect (gas expansion/compression)
    // For expansion (pressure drop), gas cools: dT_JT = μ_JT · ΔP_drop
    // Where ΔP_drop is positive for pressure decrease along flow
    // dP/dx is negative for flow with pressure drop, so ΔP_drop = -dP/dx * dx
    double dPdx = calculatePressureGradient(section, params);
    double pressureDrop = -dPdx * dx; // Positive for expansion

    // Joule-Thomson effect is more significant for gas phase
    // Scale coefficient by gas fraction for two-phase flow
    double effectiveJT = jouleThomsonCoeff * alpha_G;
    // JT cooling: temperature decreases with pressure drop (expansion)
    result.jouleThomsonDeltaT = -effectiveJT * pressureDrop;

    // 3. Friction heating (viscous dissipation)
    // Q̇_friction = |dP_friction/dx| · Q_volumetric
    double dP_friction = section.getFrictionPressureGradient();
    double volumetricFlowRate = A * Math.abs(U_M);
    result.frictionHeatingPower = Math.abs(dP_friction) * volumetricFlowRate;

    // Temperature rise from friction: dT = Q̇_friction · dt / (m · Cp)
    if (massInSection > 1e-10) {
      result.frictionHeatingDeltaT = result.frictionHeatingPower * dt / (massInSection * Cp_mix);
    }

    // 4. Elevation work (potential energy change)
    // For uphill flow: fluid does work against gravity, temperature decreases
    // dT_elevation = -g · sin(θ) · dx / Cp
    // This is typically small compared to other effects
    result.elevationWorkDeltaT = -GRAVITY * Math.sin(theta) * dx / Cp_mix * (dt / dx * U_M);

    // Total temperature change
    double totalDeltaT = result.heatTransferDeltaT + result.jouleThomsonDeltaT
        + result.frictionHeatingDeltaT + result.elevationWorkDeltaT;

    // Limit temperature change per time step for stability
    double maxDeltaT = 10.0; // Maximum 10K change per step
    if (Math.abs(totalDeltaT) > maxDeltaT) {
      totalDeltaT = Math.signum(totalDeltaT) * maxDeltaT;
    }

    result.newTemperature = T + totalDeltaT;

    // Ensure physical bounds (100K to 500K typical range for hydrocarbon systems)
    result.newTemperature = Math.max(100.0, Math.min(500.0, result.newTemperature));

    return result;
  }

  /**
   * Simplified energy equation for steady-state temperature profile calculation.
   *
   * <p>
   * This method calculates the temperature at each section by marching from inlet to outlet,
   * considering:
   * </p>
   * <ul>
   * <li>Heat loss to surroundings (dominant effect for subsea/buried pipelines)</li>
   * <li>Joule-Thomson cooling for gas-dominated flow</li>
   * <li>Friction heating (usually small)</li>
   * </ul>
   *
   * @param section Pipe section
   * @param upstreamTemperature Temperature at upstream section (K)
   * @param dx Distance from upstream section (m)
   * @param ambientTemperature Ambient temperature (K)
   * @param overallHeatTransferCoeff Heat transfer coefficient U (W/(m²·K))
   * @param massFlowRate Mass flow rate (kg/s)
   * @param jouleThomsonCoeff Joule-Thomson coefficient (K/Pa)
   * @return New temperature at this section (K)
   */
  public double calculateSteadyStateTemperature(PipeSection section, double upstreamTemperature,
      double dx, double ambientTemperature, double overallHeatTransferCoeff, double massFlowRate,
      double jouleThomsonCoeff) {

    if (massFlowRate < 1e-10) {
      return upstreamTemperature;
    }

    double D = section.getDiameter();
    double Cp_mix = section.getMixtureHeatCapacity();
    if (Cp_mix <= 0) {
      Cp_mix = 2000.0;
    }

    // Heat transfer length constant: L = ṁ·Cp / (U·π·D)
    double heatTransferCoeff = overallHeatTransferCoeff * Math.PI * D;
    double lengthConstant = massFlowRate * Cp_mix / heatTransferCoeff;

    // Exponential temperature decay toward ambient
    // T(x) = T_ambient + (T_inlet - T_ambient) · exp(-x/L)
    double decayFactor = Math.exp(-dx / lengthConstant);
    double T_afterHeatTransfer =
        ambientTemperature + (upstreamTemperature - ambientTemperature) * decayFactor;

    // Add Joule-Thomson effect
    DriftFluxParameters params = calculateDriftFlux(section);
    double dPdx = calculatePressureGradient(section, params);
    double alpha_G = params.voidFraction;

    // Scale JT coefficient by gas fraction
    double effectiveJT = jouleThomsonCoeff * alpha_G;
    double deltaT_JT = -effectiveJT * dPdx * dx;

    // Add friction heating (small effect)
    double dP_friction = section.getFrictionPressureGradient();
    double deltaT_friction = Math.abs(dP_friction) * dx / (section.getMixtureDensity() * Cp_mix);

    double newTemperature = T_afterHeatTransfer + deltaT_JT + deltaT_friction;

    // Physical bounds
    return Math.max(100.0, Math.min(500.0, newTemperature));
  }

  /**
   * Estimate Joule-Thomson coefficient for natural gas.
   *
   * <p>
   * For ideal gas, μ_JT = 0. For real gases, typical values are:
   * </p>
   * <ul>
   * <li>Methane at 300K, 50 bar: ~4.5e-6 K/Pa (0.45 K/MPa)</li>
   * <li>Natural gas mixture: 2-6e-6 K/Pa depending on composition</li>
   * <li>Liquids: typically very small (~1e-8 K/Pa)</li>
   * </ul>
   *
   * @param temperature Temperature (K)
   * @param pressure Pressure (Pa)
   * @param gasMolWeight Gas molecular weight (g/mol)
   * @return Estimated Joule-Thomson coefficient (K/Pa)
   */
  public double estimateJouleThomsonCoefficient(double temperature, double pressure,
      double gasMolWeight) {
    // Approximate correlation for hydrocarbon gases
    // μ_JT ≈ (2a/RT - b) / Cp where a, b are van der Waals constants
    // Simplified empirical correlation for natural gas:
    // μ_JT ≈ 0.5 × 10^-5 × (300/T) × (P/5e6)^0.5 K/Pa

    double T_ref = 300.0; // Reference temperature (K)
    double P_ref = 5e6; // Reference pressure (Pa)

    double mu_JT = 0.5e-5 * (T_ref / temperature) * Math.sqrt(pressure / P_ref);

    // Scale by molecular weight (heavier gases have higher JT coefficient)
    mu_JT *= gasMolWeight / 16.0; // Normalize to methane

    return Math.max(0, mu_JT);
  }

  /**
   * Calculate mixture heat capacity for two-phase flow.
   *
   * <p>
   * Uses mass-weighted average of phase heat capacities.
   * </p>
   *
   * @param section Pipe section with phase properties
   * @param params Drift-flux parameters
   * @param Cp_gas Gas heat capacity (J/(kg·K))
   * @param Cp_liquid Liquid heat capacity (J/(kg·K))
   * @return Mixture heat capacity (J/(kg·K))
   */
  public double calculateMixtureHeatCapacity(PipeSection section, DriftFluxParameters params,
      double Cp_gas, double Cp_liquid) {

    double rho_G = section.getGasDensity();
    double rho_L = section.getLiquidDensity();
    double alpha_G = params.voidFraction;
    double alpha_L = params.liquidHoldup;

    double mass_G = rho_G * alpha_G;
    double mass_L = rho_L * alpha_L;
    double totalMass = mass_G + mass_L;

    if (totalMass < 1e-10) {
      return Cp_gas > 0 ? Cp_gas : 2000.0;
    }

    double massFraction_G = mass_G / totalMass;
    double massFraction_L = mass_L / totalMass;

    return massFraction_G * Cp_gas + massFraction_L * Cp_liquid;
  }
}
