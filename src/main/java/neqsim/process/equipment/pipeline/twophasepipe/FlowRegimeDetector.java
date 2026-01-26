package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Mechanistic flow regime detector based on Taitel-Dukler and Barnea models.
 *
 * <p>
 * Determines the flow pattern in two-phase pipe flow based on local conditions including fluid
 * properties, velocities, pipe geometry and inclination.
 *
 * <p>
 * Supports two detection methods:
 * <ul>
 * <li>MECHANISTIC: Uses Taitel-Dukler (1976) and Barnea (1987) transition criteria</li>
 * <li>MINIMUM_SLIP: Selects flow regime with minimum slip ratio (closest to homogeneous)</li>
 * </ul>
 *
 * <p>
 * References:
 * <ul>
 * <li>Taitel, Y. and Dukler, A.E. (1976) - A Model for Predicting Flow Regime Transitions in
 * Horizontal and Near Horizontal Gas-Liquid Flow</li>
 * <li>Barnea, D. (1987) - A Unified Model for Predicting Flow-Pattern Transitions for the Whole
 * Range of Pipe Inclinations</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FlowRegimeDetector implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final double GRAVITY = 9.81;
  private static final double PI = Math.PI;

  /**
   * Flow regime detection method.
   */
  public enum DetectionMethod {
    /** Mechanistic criteria based on Taitel-Dukler and Barnea models. */
    MECHANISTIC,
    /** Select flow regime that gives minimum slip ratio (closest to 1). */
    MINIMUM_SLIP
  }

  /** Current detection method. */
  private DetectionMethod detectionMethod = DetectionMethod.MECHANISTIC;

  /** Drift flux model for slip calculations. */
  private transient DriftFluxModel driftFluxModel;

  /**
   * Get the current detection method.
   *
   * @return detection method
   */
  public DetectionMethod getDetectionMethod() {
    return detectionMethod;
  }

  /**
   * Set the detection method for flow regime identification.
   *
   * @param method detection method to use
   */
  public void setDetectionMethod(DetectionMethod method) {
    this.detectionMethod = method;
  }

  /**
   * Check if minimum slip criterion is used for flow regime detection.
   *
   * @return true if minimum slip criterion is used
   */
  public boolean isUseMinimumSlipCriterion() {
    return detectionMethod == DetectionMethod.MINIMUM_SLIP;
  }

  /**
   * Enable or disable minimum slip criterion for flow regime detection.
   *
   * <p>
   * When enabled, the detector calculates the slip ratio for each feasible flow regime and selects
   * the one with minimum slip (closest to 1). This approach assumes the physical system naturally
   * tends toward the flow pattern with minimum phase velocity difference.
   * </p>
   *
   * @param useMinimumSlip true to use minimum slip criterion, false for mechanistic approach
   */
  public void setUseMinimumSlipCriterion(boolean useMinimumSlip) {
    this.detectionMethod =
        useMinimumSlip ? DetectionMethod.MINIMUM_SLIP : DetectionMethod.MECHANISTIC;
  }

  /**
   * Get or create drift flux model for slip calculations.
   *
   * @return the drift flux model instance
   */
  private DriftFluxModel getDriftFluxModel() {
    if (driftFluxModel == null) {
      driftFluxModel = new DriftFluxModel();
    }
    return driftFluxModel;
  }

  /**
   * Detect flow regime for a pipe section.
   *
   * <p>
   * Uses superficial velocity ratios rather than holdup for single-phase detection. This correctly
   * identifies gas-dominant flows with low liquid loading as two-phase rather than single-phase
   * gas, ensuring proper slip and accumulation calculations.
   * </p>
   *
   * @param section The pipe section with current state
   * @return Detected flow regime
   */
  public FlowRegime detectFlowRegime(PipeSection section) {
    double U_SL = section.getSuperficialLiquidVelocity();
    double U_SG = section.getSuperficialGasVelocity();
    double U_M = U_SL + U_SG;

    // Single phase checks using superficial velocity ratios (not holdup)
    // This correctly identifies low liquid loading as two-phase, not single-phase gas
    // Critical: Using holdup (alpha_L < 0.001) causes premature single-phase classification
    // for lean gas systems where liquid accumulation detection is needed
    double liquidFraction = (U_M > 1e-10) ? U_SL / U_M : 0.0;
    double gasFraction = (U_M > 1e-10) ? U_SG / U_M : 0.0;

    // Only classify as single-phase if truly negligible flow of the other phase
    // Use 1e-6 m/s threshold (essentially zero flow) instead of 0.1% holdup
    if (U_SL < 1e-6 && U_SG > 1e-6) {
      return FlowRegime.SINGLE_PHASE_GAS;
    }
    if (U_SG < 1e-6 && U_SL > 1e-6) {
      return FlowRegime.SINGLE_PHASE_LIQUID;
    }
    if (U_M < 1e-6) {
      // No flow - default to single phase liquid (static condition)
      return FlowRegime.SINGLE_PHASE_LIQUID;
    }

    // Use minimum slip criterion if selected
    if (detectionMethod == DetectionMethod.MINIMUM_SLIP) {
      return detectFlowRegimeByMinimumSlip(section);
    }

    double D = section.getDiameter();
    double theta = section.getInclination();

    double rho_L = section.getLiquidDensity();
    double rho_G = section.getGasDensity();
    double mu_L = section.getLiquidViscosity();
    double sigma = section.getSurfaceTension();

    // Use Barnea's unified model for inclined pipes
    if (Math.abs(theta) > Math.toRadians(10)) {
      return detectInclinedFlowRegime(U_SL, U_SG, D, theta, rho_L, rho_G, mu_L, sigma);
    } else {
      return detectHorizontalFlowRegime(U_SL, U_SG, D, theta, rho_L, rho_G, mu_L, sigma);
    }
  }

  /**
   * Detect flow regime using minimum slip criterion.
   *
   * <p>
   * Calculates the slip ratio for each feasible flow regime and selects the one with the minimum
   * slip (closest to 1). This approach is based on the principle that the physical system tends
   * toward the flow pattern with minimum phase velocity difference.
   * </p>
   *
   * @param section Pipe section with current state
   * @return Flow regime with minimum slip
   */
  private FlowRegime detectFlowRegimeByMinimumSlip(PipeSection section) {
    double theta = section.getInclination();
    boolean isUpward = theta > 0;
    boolean isNearHorizontal = Math.abs(theta) <= Math.toRadians(10);

    // Candidate flow regimes based on pipe orientation
    FlowRegime[] candidates;
    if (isNearHorizontal) {
      candidates = new FlowRegime[] {FlowRegime.STRATIFIED_SMOOTH, FlowRegime.STRATIFIED_WAVY,
          FlowRegime.SLUG, FlowRegime.ANNULAR, FlowRegime.DISPERSED_BUBBLE};
    } else if (isUpward) {
      candidates = new FlowRegime[] {FlowRegime.BUBBLE, FlowRegime.SLUG, FlowRegime.CHURN,
          FlowRegime.ANNULAR, FlowRegime.DISPERSED_BUBBLE};
    } else {
      // Downward flow
      candidates = new FlowRegime[] {FlowRegime.STRATIFIED_SMOOTH, FlowRegime.STRATIFIED_WAVY,
          FlowRegime.SLUG, FlowRegime.ANNULAR};
    }

    FlowRegime bestRegime = candidates[0];
    double minSlipDeviation = Double.MAX_VALUE;

    PipeSection testSection = section.clone();
    DriftFluxModel model = getDriftFluxModel();

    for (FlowRegime regime : candidates) {
      testSection.setFlowRegime(regime);
      DriftFluxModel.DriftFluxParameters params = model.calculateDriftFlux(testSection);

      // Slip deviation from unity (homogeneous flow)
      double slipDeviation = Math.abs(params.slipRatio - 1.0);

      if (slipDeviation < minSlipDeviation) {
        minSlipDeviation = slipDeviation;
        bestRegime = regime;
      }
    }

    return bestRegime;
  }

  /**
   * Detect flow regime for horizontal or near-horizontal pipes.
   *
   * <p>
   * Uses Taitel-Dukler (1976) flow regime map.
   * </p>
   *
   * @param U_SL Superficial liquid velocity (m/s)
   * @param U_SG Superficial gas velocity (m/s)
   * @param D Diameter (m)
   * @param theta Inclination (radians)
   * @param rho_L Liquid density (kg/m³)
   * @param rho_G Gas density (kg/m³)
   * @param mu_L Liquid viscosity (Pa·s)
   * @param sigma Surface tension (N/m)
   * @return Flow regime
   */
  private FlowRegime detectHorizontalFlowRegime(double U_SL, double U_SG, double D, double theta,
      double rho_L, double rho_G, double mu_L, double sigma) {
    double U_M = U_SL + U_SG;

    // Dimensionless parameters
    double X = calcMartinelliParameter(U_SL, U_SG, D, rho_L, rho_G, mu_L, sigma);
    double F = calcFroudeNumber(U_SG, D, rho_L, rho_G);
    double K = calcKelvinHelmholtzParameter(U_SG, D, rho_L, rho_G, sigma);
    double T = calcTurbulenceParameter(U_SL, D, rho_L, rho_G, mu_L);

    // Transition boundaries
    // A: Stratified to non-stratified (Kelvin-Helmholtz instability)
    // B: Intermittent to annular
    // C: Intermittent to dispersed bubble
    // D: Stratified smooth to wavy

    // Check for dispersed bubble flow first (high liquid rate)
    if (isDispersedBubble(U_SL, U_SG, D, rho_L, rho_G, sigma)) {
      return FlowRegime.DISPERSED_BUBBLE;
    }

    // Check for annular/mist flow (high gas rate)
    if (isAnnularFlow(U_SL, U_SG, D, rho_L, rho_G, sigma)) {
      return FlowRegime.ANNULAR;
    }

    // Check for slug vs stratified transition
    double h_L = estimateStratifiedLiquidLevel(U_SL, U_SG, D, rho_L, rho_G, mu_L, theta);

    if (isKelvinHelmholtzUnstable(U_SG, h_L, D, rho_L, rho_G)) {
      // Intermittent (slug) flow
      return FlowRegime.SLUG;
    }

    // Stratified flow - check smooth vs wavy transition
    if (isWavyTransition(U_SG, h_L, D, rho_L, rho_G, mu_L)) {
      return FlowRegime.STRATIFIED_WAVY;
    }

    return FlowRegime.STRATIFIED_SMOOTH;
  }

  /**
   * Detect flow regime for inclined pipes (upward or downward).
   *
   * <p>
   * Uses Barnea (1987) unified model.
   * </p>
   *
   * @param U_SL Superficial liquid velocity (m/s)
   * @param U_SG Superficial gas velocity (m/s)
   * @param D Diameter (m)
   * @param theta Inclination (radians, positive = upward)
   * @param rho_L Liquid density (kg/m³)
   * @param rho_G Gas density (kg/m³)
   * @param mu_L Liquid viscosity (Pa·s)
   * @param sigma Surface tension (N/m)
   * @return Flow regime
   */
  private FlowRegime detectInclinedFlowRegime(double U_SL, double U_SG, double D, double theta,
      double rho_L, double rho_G, double mu_L, double sigma) {
    boolean isUpward = theta > 0;

    // Check for dispersed bubble
    if (isDispersedBubble(U_SL, U_SG, D, rho_L, rho_G, sigma)) {
      return FlowRegime.DISPERSED_BUBBLE;
    }

    // Check for annular/churn
    if (isAnnularFlow(U_SL, U_SG, D, rho_L, rho_G, sigma)) {
      if (isUpward && U_SL > 0.1) {
        return FlowRegime.CHURN;
      }
      return FlowRegime.ANNULAR;
    }

    if (isUpward) {
      // Upward flow: bubble, slug, churn, annular
      double U_bubble = calcBubbleRiseVelocity(D, rho_L, rho_G, sigma);

      // Bubble to slug transition
      double alpha_G_crit = 0.25; // Critical void fraction for bubble coalescence

      double alpha_G = U_SG / (U_SG + U_SL + U_bubble);
      if (alpha_G < alpha_G_crit) {
        return FlowRegime.BUBBLE;
      }

      return FlowRegime.SLUG;

    } else {
      // Downward flow: stratified, slug, annular
      double h_L = estimateStratifiedLiquidLevel(U_SL, U_SG, D, rho_L, rho_G, mu_L, theta);

      if (isKelvinHelmholtzUnstable(U_SG, h_L, D, rho_L, rho_G)) {
        return FlowRegime.SLUG;
      }

      if (isWavyTransition(U_SG, h_L, D, rho_L, rho_G, mu_L)) {
        return FlowRegime.STRATIFIED_WAVY;
      }

      return FlowRegime.STRATIFIED_SMOOTH;
    }
  }

  /**
   * Calculate Martinelli parameter X.
   *
   * @param U_SL superficial liquid velocity [m/s]
   * @param U_SG superficial gas velocity [m/s]
   * @param D pipe diameter [m]
   * @param rho_L liquid density [kg/m³]
   * @param rho_G gas density [kg/m³]
   * @param mu_L liquid viscosity [Pa·s]
   * @param sigma surface tension [N/m]
   * @return Martinelli parameter X
   */
  private double calcMartinelliParameter(double U_SL, double U_SG, double D, double rho_L,
      double rho_G, double mu_L, double sigma) {
    if (U_SG < 1e-6) {
      return 1e6;
    }
    if (U_SL < 1e-6) {
      return 0;
    }

    // Simplified Lockhart-Martinelli
    double Re_SL = rho_L * U_SL * D / mu_L;
    double Re_SG = rho_G * U_SG * D / (mu_L * 0.01); // Approximate gas viscosity

    double f_L = Re_SL > 2000 ? 0.316 * Math.pow(Re_SL, -0.25) : 16.0 / Re_SL;
    double f_G = Re_SG > 2000 ? 0.316 * Math.pow(Re_SG, -0.25) : 16.0 / Re_SG;

    double dpdx_L = 2 * f_L * rho_L * U_SL * U_SL / D;
    double dpdx_G = 2 * f_G * rho_G * U_SG * U_SG / D;

    return Math.sqrt(dpdx_L / Math.max(dpdx_G, 1e-10));
  }

  /**
   * Calculate modified Froude number.
   *
   * @param U_SG superficial gas velocity [m/s]
   * @param D pipe diameter [m]
   * @param rho_L liquid density [kg/m3]
   * @param rho_G gas density [kg/m3]
   * @return modified Froude number [-]
   */
  private double calcFroudeNumber(double U_SG, double D, double rho_L, double rho_G) {
    double deltaRho = rho_L - rho_G;
    if (deltaRho < 1e-6) {
      return 0;
    }
    return U_SG * Math.sqrt(rho_G / (deltaRho * GRAVITY * D));
  }

  /**
   * Calculate Kelvin-Helmholtz stability parameter.
   *
   * @param U_SG superficial gas velocity [m/s]
   * @param D pipe diameter [m]
   * @param rho_L liquid density [kg/m3]
   * @param rho_G gas density [kg/m3]
   * @param sigma interfacial tension [N/m]
   * @return Kelvin-Helmholtz parameter [-]
   */
  private double calcKelvinHelmholtzParameter(double U_SG, double D, double rho_L, double rho_G,
      double sigma) {
    double deltaRho = rho_L - rho_G;
    if (deltaRho < 1e-6) {
      return 0;
    }
    return U_SG * Math.sqrt(rho_G * rho_L / (deltaRho * sigma));
  }

  /**
   * Calculate turbulence parameter T.
   *
   * @param U_SL superficial liquid velocity [m/s]
   * @param D pipe diameter [m]
   * @param rho_L liquid density [kg/m3]
   * @param rho_G gas density [kg/m3]
   * @param mu_L liquid viscosity [Pa.s]
   * @return turbulence parameter T [-]
   */
  private double calcTurbulenceParameter(double U_SL, double D, double rho_L, double rho_G,
      double mu_L) {
    double deltaRho = rho_L - rho_G;
    if (deltaRho < 1e-6) {
      return 0;
    }
    double Re_SL = rho_L * U_SL * D / mu_L;
    double f_SL = Re_SL > 2000 ? 0.316 * Math.pow(Re_SL, -0.25) : 16.0 / Math.max(Re_SL, 1);

    return Math.sqrt(2 * f_SL * rho_L * U_SL * U_SL / (deltaRho * GRAVITY * D));
  }

  /**
   * Default interfacial tension for gas-oil systems [N/m]. Typical values: 0.015-0.030 N/m for
   * hydrocarbon systems.
   */
  private static final double DEFAULT_GAS_OIL_IFT = 0.020;

  /**
   * Default interfacial tension for gas-water systems [N/m]. Typical value: ~0.072 N/m at 25°C.
   */
  private static final double DEFAULT_GAS_WATER_IFT = 0.072;

  /**
   * Default interfacial tension for oil-water systems [N/m]. Typical values: 0.020-0.050 N/m
   * depending on oil type.
   */
  private static final double DEFAULT_OIL_WATER_IFT = 0.030;

  /**
   * Check if flow is in dispersed bubble regime.
   *
   * @param U_SL superficial liquid velocity [m/s]
   * @param U_SG superficial gas velocity [m/s]
   * @param D pipe diameter [m]
   * @param rho_L liquid density [kg/m³]
   * @param rho_G gas density [kg/m³]
   * @param sigma surface tension [N/m]
   * @return true if flow is in dispersed bubble regime
   */
  private boolean isDispersedBubble(double U_SL, double U_SG, double D, double rho_L, double rho_G,
      double sigma) {
    double U_M = U_SL + U_SG;

    // Use default surface tension if not available (assume gas-oil for typical multiphase)
    double sigmaEffective = sigma < 1e-6 ? DEFAULT_GAS_OIL_IFT : sigma;

    // Weber number criterion - turbulence breaks up bubbles
    double We = rho_L * U_M * U_M * D / sigmaEffective;

    // Critical velocity for bubble dispersion (Taitel et al.)
    double d_crit = 2.0 * Math.sqrt(sigmaEffective / (GRAVITY * (rho_L - rho_G)));
    double U_crit = 0.725 + 4.15 * Math.sqrt(U_SG);

    return U_M > U_crit && We > 20 && U_SG / U_M < 0.52;
  }

  /**
   * Check if flow is in annular regime.
   *
   * <p>
   * Uses Taitel-Dukler (1976) criterion for annular flow transition. The critical gas velocity is
   * based on the balance between aerodynamic lift and gravity forces on the liquid film.
   * </p>
   *
   * @param U_SL superficial liquid velocity [m/s]
   * @param U_SG superficial gas velocity [m/s]
   * @param D pipe diameter [m]
   * @param rho_L liquid density [kg/m³]
   * @param rho_G gas density [kg/m³]
   * @param sigma surface tension [N/m]
   * @return true if flow is in annular regime
   */
  private boolean isAnnularFlow(double U_SL, double U_SG, double D, double rho_L, double rho_G,
      double sigma) {
    // Minimum gas velocity for annular flow (Taitel-Dukler)
    double deltaRho = rho_L - rho_G;
    if (deltaRho < 1e-6) {
      return false;
    }

    // If surface tension is not available or very small, use a default value
    // Typical gas-oil IFT is 0.015-0.030 N/m, gas-water is ~0.072 N/m
    double sigmaEffective = sigma;
    if (sigmaEffective < 1e-6) {
      // Use typical gas-oil surface tension as default
      sigmaEffective = DEFAULT_GAS_OIL_IFT;
    }

    double U_SG_crit = 3.1 * Math.pow(sigmaEffective * GRAVITY * deltaRho / (rho_G * rho_G), 0.25);

    return U_SG > U_SG_crit;
  }

  /**
   * Estimate liquid level in stratified flow.
   *
   * @param U_SL superficial liquid velocity [m/s]
   * @param U_SG superficial gas velocity [m/s]
   * @param D pipe diameter [m]
   * @param rho_L liquid density [kg/m3]
   * @param rho_G gas density [kg/m3]
   * @param mu_L liquid viscosity [Pa.s]
   * @param theta pipe inclination angle [rad]
   * @return estimated liquid level [m]
   */
  private double estimateStratifiedLiquidLevel(double U_SL, double U_SG, double D, double rho_L,
      double rho_G, double mu_L, double theta) {
    // Simplified momentum balance for stratified flow
    // Iterative solution for liquid level h_L

    double h_L = 0.5 * D; // Initial guess

    for (int iter = 0; iter < 20; iter++) {
      double h_prev = h_L;

      // Geometric parameters
      double beta = 2.0 * Math.acos(1.0 - 2.0 * h_L / D);
      double A_L = D * D / 8.0 * (beta - Math.sin(beta));
      double A_G = PI * D * D / 4.0 - A_L;
      double S_L = D * beta / 2.0; // Wetted perimeter liquid
      double S_G = D * (PI - beta / 2.0); // Wetted perimeter gas
      double S_i = D * Math.sin(beta / 2.0); // Interface width

      if (A_L < 1e-10 || A_G < 1e-10) {
        break;
      }

      double U_L = U_SL * PI * D * D / 4.0 / A_L;
      double U_G = U_SG * PI * D * D / 4.0 / A_G;

      // Friction factors
      double D_hL = 4.0 * A_L / S_L;
      double D_hG = 4.0 * A_G / (S_G + S_i);

      double Re_L = rho_L * Math.abs(U_L) * D_hL / mu_L;
      double Re_G = rho_G * Math.abs(U_G) * D_hG / (mu_L * 0.01);

      double f_L = Re_L > 2000 ? 0.046 * Math.pow(Re_L, -0.2) : 16.0 / Math.max(Re_L, 1);
      double f_G = Re_G > 2000 ? 0.046 * Math.pow(Re_G, -0.2) : 16.0 / Math.max(Re_G, 1);
      double f_i = f_G; // Interface friction

      // Shear stresses
      double tau_wL = f_L * rho_L * U_L * Math.abs(U_L) / 2.0;
      double tau_wG = f_G * rho_G * U_G * Math.abs(U_G) / 2.0;
      double tau_i = f_i * rho_G * (U_G - U_L) * Math.abs(U_G - U_L) / 2.0;

      // Combined momentum balance
      double deltaRho = rho_L - rho_G;
      double gravity_term = deltaRho * GRAVITY * Math.sin(theta);

      // Residual
      double resid =
          (-tau_wL * S_L + tau_i * S_i) / A_L - (-tau_wG * S_G - tau_i * S_i) / A_G - gravity_term;

      // Adjust h_L
      h_L = h_L - 0.1 * D * Math.signum(resid);
      h_L = Math.max(0.01 * D, Math.min(0.99 * D, h_L));

      if (Math.abs(h_L - h_prev) < 1e-6 * D) {
        break;
      }
    }

    return h_L;
  }

  /**
   * Check Kelvin-Helmholtz instability for slug transition.
   *
   * @param U_SG superficial gas velocity
   * @param h_L liquid height
   * @param D pipe diameter
   * @param rho_L liquid density
   * @param rho_G gas density
   * @return true if Kelvin-Helmholtz unstable condition exists
   */
  private boolean isKelvinHelmholtzUnstable(double U_SG, double h_L, double D, double rho_L,
      double rho_G) {
    if (h_L < 0.01 * D || h_L > 0.99 * D) {
      return false;
    }

    double beta = 2.0 * Math.acos(1.0 - 2.0 * h_L / D);
    double A_G = PI * D * D / 4.0 - D * D / 8.0 * (beta - Math.sin(beta));
    double S_i = D * Math.sin(beta / 2.0);

    if (A_G < 1e-10) {
      return false;
    }

    double U_G = U_SG * PI * D * D / 4.0 / A_G;
    double h_G = D - h_L;

    // Kelvin-Helmholtz criterion
    double deltaRho = rho_L - rho_G;
    double U_G_crit = Math.sqrt(deltaRho * GRAVITY * h_G * A_G / (rho_G * S_i));

    return U_G > U_G_crit;
  }

  /**
   * Check transition from smooth to wavy stratified.
   *
   * @param U_SG superficial gas velocity
   * @param h_L liquid height
   * @param D pipe diameter
   * @param rho_L liquid density
   * @param rho_G gas density
   * @param mu_L liquid viscosity
   * @return true if transition from smooth to wavy stratified occurs
   */
  private boolean isWavyTransition(double U_SG, double h_L, double D, double rho_L, double rho_G,
      double mu_L) {
    if (h_L < 0.01 * D || h_L > 0.99 * D) {
      return false;
    }

    // Jeffreys' sheltering criterion for wave generation
    double s = 0.01; // Sheltering coefficient

    double beta = 2.0 * Math.acos(1.0 - 2.0 * h_L / D);
    double A_G = PI * D * D / 4.0 - D * D / 8.0 * (beta - Math.sin(beta));
    double U_G = U_SG * PI * D * D / 4.0 / A_G;

    double deltaRho = rho_L - rho_G;
    double mu_G = mu_L * 0.01;

    // Wave speed and critical velocity
    double U_G_crit = Math.sqrt(4.0 * mu_L * deltaRho * GRAVITY / (s * rho_G * rho_G));

    return U_G > U_G_crit;
  }

  /**
   * Calculate bubble rise velocity using Harmathy correlation.
   *
   * @param D pipe diameter (m)
   * @param rho_L liquid density (kg/m³)
   * @param rho_G gas density (kg/m³)
   * @param sigma surface tension (N/m)
   * @return bubble rise velocity (m/s)
   */
  private double calcBubbleRiseVelocity(double D, double rho_L, double rho_G, double sigma) {
    double deltaRho = rho_L - rho_G;
    if (deltaRho < 1e-6) {
      return 0;
    }
    return 1.53 * Math.pow(GRAVITY * sigma * deltaRho / (rho_L * rho_L), 0.25);
  }

  /**
   * Get flow regime transition map for visualization/debugging.
   *
   * @param section Pipe section with fluid properties
   * @param U_SL_max Maximum superficial liquid velocity (m/s)
   * @param U_SG_max Maximum superficial gas velocity (m/s)
   * @param resolution Grid resolution
   * @return 2D array of flow regimes
   */
  public FlowRegime[][] getFlowRegimeMap(PipeSection section, double U_SL_max, double U_SG_max,
      int resolution) {
    FlowRegime[][] map = new FlowRegime[resolution][resolution];
    PipeSection testSection = section.clone();

    for (int i = 0; i < resolution; i++) {
      for (int j = 0; j < resolution; j++) {
        double U_SL = (i + 0.5) * U_SL_max / resolution;
        double U_SG = (j + 0.5) * U_SG_max / resolution;

        double U_M = U_SL + U_SG;
        if (U_M > 1e-6) {
          testSection.setLiquidHoldup(U_SL / U_M);
          testSection.setGasHoldup(U_SG / U_M);
          testSection.setLiquidVelocity(U_SL / Math.max(U_SL / U_M, 0.01));
          testSection.setGasVelocity(U_SG / Math.max(U_SG / U_M, 0.01));
          testSection.updateDerivedQuantities();

          map[i][j] = detectFlowRegime(testSection);
        } else {
          map[i][j] = FlowRegime.SINGLE_PHASE_LIQUID;
        }
      }
    }

    return map;
  }
}
