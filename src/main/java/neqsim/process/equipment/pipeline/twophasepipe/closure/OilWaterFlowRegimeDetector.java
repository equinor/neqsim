package neqsim.process.equipment.pipeline.twophasepipe.closure;

import java.io.Serializable;

/**
 * Oil-water flow regime detector for three-phase pipe flow.
 *
 * <p>
 * Predicts the oil-water flow configuration (stratified, dispersed oil-in-water, dispersed
 * water-in-oil, or dual dispersion) based on phase velocities, fluid properties, and pipe geometry.
 * This is critical for:
 * </p>
 * <ul>
 * <li>Corrosion prediction (water wetting vs oil wetting of pipe wall)</li>
 * <li>Effective viscosity calculation (emulsion viscosity depends on continuous phase)</li>
 * <li>Produced water management (water dropout in low-velocity sections)</li>
 * <li>Phase inversion prediction (the point where continuous phase switches)</li>
 * </ul>
 *
 * <h2>Flow Regime Classification</h2>
 * <p>
 * Based on the work of Trallero (1995), Angeli and Hewitt (2000), and Brauner (2003):
 * </p>
 * <ul>
 * <li><b>Stratified:</b> Separate oil and water layers (low velocity)</li>
 * <li><b>Stratified with mixing:</b> Stratified layers with interfacial waves/mixing zone</li>
 * <li><b>Dispersed oil-in-water (O/W):</b> Oil droplets in continuous water phase</li>
 * <li><b>Dispersed water-in-oil (W/O):</b> Water droplets in continuous oil phase</li>
 * <li><b>Dual dispersion:</b> Both O/W and W/O regions coexist</li>
 * <li><b>Annular:</b> Oil core with water annulus (or vice versa)</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Trallero, J.L. (1995). Oil-Water Flow Patterns in Horizontal Pipes. PhD Thesis, U. Tulsa.
 * </li>
 * <li>Brauner, N. (2003). Liquid-Liquid Two-Phase Flow Systems. In Modelling and Experimentation in
 * Two-Phase Flow, Springer, pp. 221-279.</li>
 * <li>Angeli, P. and Hewitt, G.F. (2000). Flow structure in horizontal oil-water flow. Int. J.
 * Multiphase Flow, 26(7), 1117-1140.</li>
 * <li>Decarre, S. and Fabre, J. (1997). Phase inversion prediction study. Oil &amp; Gas Science and
 * Technology, 52(4), 415-424.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class OilWaterFlowRegimeDetector implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Gravitational acceleration (m/s2). */
  private static final double GRAVITY = 9.81;

  /**
   * Oil-water flow regime enumeration.
   */
  public enum OilWaterFlowRegime {
    /** Fully stratified: separate oil and water layers. */
    STRATIFIED,
    /** Stratified with interfacial mixing zone. */
    STRATIFIED_WITH_MIXING,
    /** Dispersed oil-in-water (water is continuous phase). */
    DISPERSED_OIL_IN_WATER,
    /** Dispersed water-in-oil (oil is continuous phase). */
    DISPERSED_WATER_IN_OIL,
    /**
     * Dual dispersion: both O/W and W/O regions coexist. Typically occurs near the inversion point.
     */
    DUAL_DISPERSION,
    /** Annular flow: oil core with water annulus or vice versa. */
    ANNULAR,
    /** Single phase (only oil or only water present). */
    SINGLE_PHASE
  }

  /**
   * Result of oil-water flow regime detection.
   */
  public static class OilWaterResult {
    /** Detected flow regime. */
    public final OilWaterFlowRegime regime;
    /** True if water wets the pipe wall (corrosion concern). */
    public final boolean waterWetting;
    /** Effective emulsion viscosity (Pa.s). */
    public final double effectiveViscosity;
    /** Phase inversion water fraction (volume fraction at which continuous phase switches). */
    public final double inversionWaterFraction;
    /** Critical mixture velocity for full dispersion (m/s). */
    public final double criticalDispersionVelocity;
    /** Maximum stable droplet diameter (m) using Hinze (1955) criterion. */
    public final double maxDropletDiameter;
    /** True if oil is the continuous phase. */
    public final boolean oilContinuous;
    /** Water dropout risk: true if water may separate and accumulate. */
    public final boolean waterDropoutRisk;

    /**
     * Constructor for OilWaterResult.
     *
     * @param regime the detected flow regime
     * @param waterWetting whether water wets the pipe wall
     * @param effectiveViscosity effective emulsion viscosity (Pa.s)
     * @param inversionWaterFraction phase inversion water fraction
     * @param criticalDispersionVelocity critical velocity for full dispersion (m/s)
     * @param maxDropletDiameter maximum stable droplet diameter (m)
     * @param oilContinuous whether oil is the continuous phase
     * @param waterDropoutRisk whether there is risk of water dropout
     */
    public OilWaterResult(OilWaterFlowRegime regime, boolean waterWetting,
        double effectiveViscosity, double inversionWaterFraction, double criticalDispersionVelocity,
        double maxDropletDiameter, boolean oilContinuous, boolean waterDropoutRisk) {
      this.regime = regime;
      this.waterWetting = waterWetting;
      this.effectiveViscosity = effectiveViscosity;
      this.inversionWaterFraction = inversionWaterFraction;
      this.criticalDispersionVelocity = criticalDispersionVelocity;
      this.maxDropletDiameter = maxDropletDiameter;
      this.oilContinuous = oilContinuous;
      this.waterDropoutRisk = waterDropoutRisk;
    }
  }

  /** Critical Weber number for droplet breakup (Hinze, 1955). */
  private double criticalWeber = 1.17;

  /** Empirical constant for inversion model (Decarre and Fabre, 1997). */
  private double inversionConstant = 0.5;

  /**
   * Default constructor.
   */
  public OilWaterFlowRegimeDetector() {}

  /**
   * Detect oil-water flow regime and compute associated properties.
   *
   * @param waterCut water volume fraction in liquid (0-1)
   * @param mixtureVelocity superficial mixture liquid velocity (m/s)
   * @param rhoOil oil density (kg/m3)
   * @param rhoWater water density (kg/m3)
   * @param muOil oil dynamic viscosity (Pa.s)
   * @param muWater water dynamic viscosity (Pa.s)
   * @param sigmaOW oil-water interfacial tension (N/m)
   * @param pipeDiameter pipe internal diameter (m)
   * @param inclination pipe inclination (radians, positive = uphill)
   * @return OilWaterResult with regime classification and properties
   */
  public OilWaterResult detect(double waterCut, double mixtureVelocity, double rhoOil,
      double rhoWater, double muOil, double muWater, double sigmaOW, double pipeDiameter,
      double inclination) {

    // Handle trivial cases
    if (waterCut < 0.005) {
      return new OilWaterResult(OilWaterFlowRegime.SINGLE_PHASE, false, muOil, 0.5, 0.0, 0.0, true,
          false);
    }
    if (waterCut > 0.995) {
      return new OilWaterResult(OilWaterFlowRegime.SINGLE_PHASE, true, muWater, 0.5, 0.0, 0.0,
          false, false);
    }

    // 1. Calculate phase inversion point (Decarre and Fabre, 1997)
    // The inversion occurs when the free energy of both dispersions is equal
    double muRatio = muOil / Math.max(muWater, 1e-10);
    double inversionWC = calcInversionWaterFraction(muRatio, rhoOil, rhoWater);

    // 2. Determine continuous phase based on water cut vs inversion point
    boolean oilContinuous = (waterCut < inversionWC);

    // 3. Check for stratified flow (Trallero criterion)
    // Stratification occurs when gravity dominates over turbulent mixing
    double deltaRho = Math.abs(rhoWater - rhoOil);
    double rhoMix = waterCut * rhoWater + (1.0 - waterCut) * rhoOil;
    double muCont = oilContinuous ? muOil : muWater;
    double rhoCont = oilContinuous ? rhoOil : rhoWater;

    // Critical velocity for transition from stratified to dispersed (Brauner, 2003)
    double criticalVelocity =
        calcCriticalDispersionVelocity(waterCut, rhoOil, rhoWater, muCont, sigmaOW, pipeDiameter);

    // 4. Maximum stable droplet diameter (Hinze, 1955)
    double dMax = calcMaxDropletDiameter(mixtureVelocity, rhoCont, sigmaOW, pipeDiameter, muCont);

    // 5. Determine flow regime
    OilWaterFlowRegime regime;
    boolean waterWetting;
    boolean waterDropoutRisk = false;

    double absVel = Math.abs(mixtureVelocity);

    if (absVel < 0.1 * criticalVelocity) {
      // Very low velocity: fully stratified
      regime = OilWaterFlowRegime.STRATIFIED;
      waterWetting = true; // Water always wets bottom wall in stratified
      waterDropoutRisk = true;
    } else if (absVel < 0.5 * criticalVelocity) {
      // Low velocity: stratified with mixing at interface
      regime = OilWaterFlowRegime.STRATIFIED_WITH_MIXING;
      waterWetting = true;
      waterDropoutRisk = (waterCut > 0.1); // Risk if significant water
    } else if (absVel < criticalVelocity) {
      // Transition: near-inversion, dual dispersion possible
      if (Math.abs(waterCut - inversionWC) < 0.1) {
        regime = OilWaterFlowRegime.DUAL_DISPERSION;
        waterWetting = true; // Conservative for corrosion
        waterDropoutRisk = false;
      } else if (oilContinuous) {
        regime = OilWaterFlowRegime.DISPERSED_WATER_IN_OIL;
        waterWetting = false; // Oil wets the wall
        waterDropoutRisk = dMax > 0.5 * pipeDiameter; // Droplets too large to suspend
      } else {
        regime = OilWaterFlowRegime.DISPERSED_OIL_IN_WATER;
        waterWetting = true;
        waterDropoutRisk = false;
      }
    } else {
      // High velocity: fully dispersed
      if (oilContinuous) {
        regime = OilWaterFlowRegime.DISPERSED_WATER_IN_OIL;
        waterWetting = false;
      } else {
        regime = OilWaterFlowRegime.DISPERSED_OIL_IN_WATER;
        waterWetting = true;
      }
      waterDropoutRisk = false;
    }

    // Uphill inclination increases water dropout risk in oil-continuous flow
    if (inclination > 0.05 && oilContinuous && waterCut > 0.05) {
      waterDropoutRisk = true;
    }

    // 6. Effective viscosity
    double muEff = calcEffectiveViscosity(waterCut, muOil, muWater, oilContinuous);

    return new OilWaterResult(regime, waterWetting, muEff, inversionWC, criticalVelocity, dMax,
        oilContinuous, waterDropoutRisk);
  }

  /**
   * Calculate the phase inversion water fraction.
   *
   * <p>
   * Uses the model of Decarre and Fabre (1997) where inversion occurs when the
   * surface-energy-weighted viscosity ratio reaches equilibrium. For equal viscosity fluids,
   * inversion is near 50%.
   * </p>
   *
   * @param muRatio oil-to-water viscosity ratio (mu_o/mu_w)
   * @param rhoOil oil density (kg/m3)
   * @param rhoWater water density (kg/m3)
   * @return water volume fraction at inversion point (0-1)
   */
  public double calcInversionWaterFraction(double muRatio, double rhoOil, double rhoWater) {
    // Decarre-Fabre model: phi_w,inv = 1 / (1 + (mu_o/mu_w)^0.5)
    // Modified with density correction for significant density differences
    double phiBase = 1.0 / (1.0 + Math.pow(muRatio, inversionConstant));

    // Density correction: heavier phase tends to be continuous at slightly lower fraction
    double rhoRatio = rhoWater / Math.max(rhoOil, 1.0);
    double densityCorrection = 0.05 * (rhoRatio - 1.0);
    double phiInv = phiBase + densityCorrection;

    // Clamp to physical range
    return Math.max(0.1, Math.min(0.9, phiInv));
  }

  /**
   * Calculate the critical mixture velocity for transition from stratified to fully dispersed
   * oil-water flow (Brauner, 2003).
   *
   * <p>
   * Below this velocity, gravity dominates and the phases stratify. Above this velocity, turbulent
   * mixing disperses one phase into the other.
   * </p>
   *
   * @param waterCut water volume fraction (0-1)
   * @param rhoOil oil density (kg/m3)
   * @param rhoWater water density (kg/m3)
   * @param muCont viscosity of continuous phase (Pa.s)
   * @param sigmaOW oil-water interfacial tension (N/m)
   * @param diameter pipe diameter (m)
   * @return critical mixture velocity (m/s)
   */
  public double calcCriticalDispersionVelocity(double waterCut, double rhoOil, double rhoWater,
      double muCont, double sigmaOW, double diameter) {

    double deltaRho = Math.abs(rhoWater - rhoOil);
    if (deltaRho < 1.0 || sigmaOW < 1e-6 || diameter < 0.01) {
      return 0.1; // Very low threshold if densities are similar
    }

    // Brauner (2003) criterion based on balance between gravity and turbulent forces
    // V_crit ~ [8 * deltaRho * g * d * sigma / (rho_c * f_m * d_max)]^0.5
    // Simplified: V_crit = C * [deltaRho * g * diameter / rho_cont]^0.5
    double rhoCont = (waterCut > 0.5) ? rhoWater : rhoOil;
    double Eo = deltaRho * GRAVITY * diameter * diameter / Math.max(sigmaOW, 1e-6);

    // Empirical fit for horizontal pipe: higher Eotvos = easier to stratify
    double vCrit = 1.8 * Math.pow(deltaRho * GRAVITY * diameter / Math.max(rhoCont, 100.0), 0.5);

    // Correct for Eotvos number (large Eo -> harder to keep dispersed)
    if (Eo > 100) {
      vCrit *= 1.5;
    }

    return Math.max(0.1, vCrit);
  }

  /**
   * Calculate the maximum stable droplet diameter using the Hinze (1955) criterion.
   *
   * <p>
   * d_max = We_crit^{3/5} * (sigma / rho_c)^{3/5} * epsilon^{-2/5} where epsilon is the turbulent
   * energy dissipation rate estimated from the mixture velocity.
   * </p>
   *
   * @param velocity mixture velocity (m/s)
   * @param rhoCont density of continuous phase (kg/m3)
   * @param sigmaOW oil-water interfacial tension (N/m)
   * @param diameter pipe diameter (m)
   * @param muCont viscosity of continuous phase (Pa.s)
   * @return maximum stable droplet diameter (m)
   */
  public double calcMaxDropletDiameter(double velocity, double rhoCont, double sigmaOW,
      double diameter, double muCont) {
    double absVel = Math.abs(velocity);
    if (absVel < 0.01 || rhoCont < 0.1 || sigmaOW < 1e-8) {
      return diameter; // At zero flow, droplets can be as large as the pipe
    }

    // Turbulent energy dissipation rate: epsilon ~ 2 * f * v^3 / D
    // where f is the Fanning friction factor
    double Re = rhoCont * absVel * diameter / Math.max(muCont, 1e-8);
    double fFanning = (Re < 2100) ? 16.0 / Math.max(Re, 1.0) : 0.046 * Math.pow(Re, -0.2);
    double epsilon = 2.0 * fFanning * absVel * absVel * absVel / diameter;
    epsilon = Math.max(epsilon, 1e-6);

    // Hinze (1955): d_max = C * (sigma/rho_c)^(3/5) * epsilon^(-2/5)
    // where C = We_crit^(3/5) ~ 1.17^(3/5) ~ 1.1
    double C = Math.pow(criticalWeber, 0.6);
    double dMax = C * Math.pow(sigmaOW / rhoCont, 0.6) * Math.pow(epsilon, -0.4);

    // Cannot exceed pipe diameter
    return Math.min(dMax, diameter);
  }

  /**
   * Calculate effective viscosity of the oil-water mixture.
   *
   * <p>
   * Uses the Brinkman (1952) equation for dilute dispersions and the Pal and Rhodes (1989) model
   * for concentrated dispersions:
   * </p>
   * <ul>
   * <li>Dilute (phi &lt; 0.3): mu_eff = mu_c * (1 - phi)^{-2.5}</li>
   * <li>Concentrated: mu_eff = mu_c * (1 - phi/phi_max)^{-2.5}</li>
   * </ul>
   * where phi is the dispersed phase volume fraction and phi_max ~ 0.74 is maximum packing.
   *
   * @param waterCut water volume fraction (0-1)
   * @param muOil oil viscosity (Pa.s)
   * @param muWater water viscosity (Pa.s)
   * @param oilContinuous true if oil is the continuous phase
   * @return effective mixture viscosity (Pa.s)
   */
  public double calcEffectiveViscosity(double waterCut, double muOil, double muWater,
      boolean oilContinuous) {
    double muCont;
    double phiDisp; // volume fraction of dispersed phase
    double phiMax = 0.74; // maximum packing fraction (random close packing spheres)

    if (oilContinuous) {
      muCont = muOil;
      phiDisp = waterCut;
    } else {
      muCont = muWater;
      phiDisp = 1.0 - waterCut;
    }

    // Pal-Rhodes (1989) model
    double phiRatio = phiDisp / phiMax;
    if (phiRatio >= 0.99) {
      // At or above max packing: very high viscosity (limit)
      return muCont * 1000.0;
    }

    double muEff = muCont * Math.pow(1.0 - phiRatio, -2.5);

    // Ensure at least continuous phase viscosity
    return Math.max(muEff, muCont);
  }

  /**
   * Check if water dropout is likely in a low-point section.
   *
   * <p>
   * Water dropout occurs when the settling velocity of water droplets exceeds the turbulent
   * re-entrainment capacity. This is particularly relevant in:
   * </p>
   * <ul>
   * <li>Low-velocity sections (turndown conditions)</li>
   * <li>Pipe low-points (valleys) where water can accumulate</li>
   * <li>After uphill sections where water may slide back</li>
   * </ul>
   *
   * @param waterCut water volume fraction (0-1)
   * @param mixtureVelocity mixture velocity (m/s)
   * @param rhoOil oil density (kg/m3)
   * @param rhoWater water density (kg/m3)
   * @param muOil oil viscosity (Pa.s)
   * @param diameter pipe diameter (m)
   * @param dropletDiameter water droplet diameter (m)
   * @return true if water dropout is likely
   */
  public boolean isWaterDropoutLikely(double waterCut, double mixtureVelocity, double rhoOil,
      double rhoWater, double muOil, double diameter, double dropletDiameter) {

    if (waterCut < 0.005 || waterCut > 0.995) {
      return false; // No two-phase liquid present
    }

    // Stokes settling velocity for water droplet in oil
    double deltaRho = rhoWater - rhoOil;
    if (deltaRho <= 0) {
      return false; // Water is lighter (unusual but possible with heavy brines)
    }

    double dDrop = Math.min(dropletDiameter, diameter);
    double vSettle = deltaRho * GRAVITY * dDrop * dDrop / (18.0 * Math.max(muOil, 1e-6));

    // Turbulent velocity fluctuation (approximately 5% of mean velocity)
    double vTurb = 0.05 * Math.abs(mixtureVelocity);

    // Dropout if settling exceeds turbulent re-suspension
    return (vSettle > vTurb);
  }

  /**
   * Set the critical Weber number for droplet breakup.
   *
   * @param we critical Weber number (default 1.17 from Hinze, 1955)
   */
  public void setCriticalWeber(double we) {
    this.criticalWeber = Math.max(0.1, we);
  }

  /**
   * Get the critical Weber number.
   *
   * @return critical Weber number
   */
  public double getCriticalWeber() {
    return criticalWeber;
  }

  /**
   * Set the inversion model constant.
   *
   * @param constant exponent for viscosity ratio in inversion model (default 0.5)
   */
  public void setInversionConstant(double constant) {
    this.inversionConstant = Math.max(0.1, Math.min(1.0, constant));
  }

  /**
   * Get the inversion model constant.
   *
   * @return inversion constant
   */
  public double getInversionConstant() {
    return inversionConstant;
  }
}
