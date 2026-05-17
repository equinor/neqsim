package neqsim.process.equipment.pipeline;

import neqsim.thermo.system.SystemInterface;

/**
 * CO2-specific correction factors for two-phase flow correlations. The standard Beggs and Brill
 * (1973) correlation was developed for oil-gas-water systems and may not accurately represent the
 * flow behaviour of CO2-dominated streams where the liquid phase is dense-phase CO2 rather than
 * conventional hydrocarbon liquid.
 *
 * <p>
 * This utility class provides correction factors for:
 * <ul>
 * <li>Liquid holdup: CO2 liquid has lower viscosity and higher density than typical oil, affecting
 * hold-up</li>
 * <li>Friction factor: dense-phase CO2 has different friction characteristics</li>
 * <li>Flow pattern transitions: CO2 liquid-gas transitions differ from oil-gas boundaries</li>
 * <li>Surface tension: CO2 liquid-vapour interfacial tension is typically much lower than
 * hydrocarbon systems</li>
 * </ul>
 *
 * <p>
 * Usage:
 *
 * <pre>
 * // Check if CO2 corrections should be applied
 * if (CO2FlowCorrections.isCO2DominatedFluid(system)) {
 *   double holdupFactor = CO2FlowCorrections.getLiquidHoldupCorrectionFactor(system);
 *   double frictionFactor = CO2FlowCorrections.getFrictionCorrectionFactor(system);
 *   correctedHoldup = baseHoldup * holdupFactor;
 *   correctedFriction = baseFriction * frictionFactor;
 * }
 * </pre>
 *
 * <p>
 * Reference: Peletiri, S.P., Rahmanian, N. and Mujtaba, I.M. (2018). CO2 Pipeline Design: A Review.
 * Energies, 11(9), 2184.
 * </p>
 *
 * @author neqsim
 * @version 1.0
 */
public final class CO2FlowCorrections {

  /** Minimum CO2 mole fraction to consider the fluid CO2-dominated. */
  private static final double CO2_THRESHOLD = 0.50;

  /** CO2 critical temperature in Kelvin. */
  private static final double CO2_TC = 304.13;

  /** CO2 critical pressure in bara. */
  private static final double CO2_PC = 73.77;

  /**
   * Private constructor to prevent instantiation.
   */
  private CO2FlowCorrections() {
    // Utility class
  }

  /**
   * Checks whether the fluid is CO2-dominated (more than 50 mol% CO2).
   *
   * @param system the thermodynamic system
   * @return true if the fluid contains more than 50 mol% CO2
   */
  public static boolean isCO2DominatedFluid(SystemInterface system) {
    return getCO2MoleFraction(system) > CO2_THRESHOLD;
  }

  /**
   * Gets the CO2 mole fraction in the overall fluid. Searches by component name for robustness.
   *
   * @param system the thermodynamic system
   * @return the CO2 overall mole fraction
   */
  public static double getCO2MoleFraction(SystemInterface system) {
    try {
      for (int i = 0; i < system.getNumberOfComponents(); i++) {
        String name = system.getPhase(0).getComponent(i).getComponentName();
        if ("CO2".equals(name)) {
          return system.getPhase(0).getComponent(i).getz();
        }
      }
      return 0.0;
    } catch (Exception e) {
      return 0.0;
    }
  }

  /**
   * Calculates a correction factor for liquid holdup in CO2-dominated two-phase flow. CO2 liquid
   * has lower viscosity and higher density ratio (liquid/gas) compared to typical oil, resulting in
   * lower liquid holdup for the same superficial velocities.
   *
   * <p>
   * The correction factor is based on the reduced temperature (T/Tc). Near the critical point, CO2
   * liquid and gas properties converge, reducing the holdup correction. Far below Tc, CO2 liquid
   * behaves more like a conventional dense liquid.
   * </p>
   *
   * @param system the thermodynamic system
   * @return the holdup correction factor (multiply by Beggs-Brill holdup). Returns 1.0 for non-CO2
   *         systems or single-phase conditions
   */
  public static double getLiquidHoldupCorrectionFactor(SystemInterface system) {
    if (!isCO2DominatedFluid(system) || system.getNumberOfPhases() <= 1) {
      return 1.0;
    }

    double Tr = system.getTemperature() / CO2_TC;

    // Near critical point (Tr > 0.9): liquid and gas properties converge,
    // standard holdup is less applicable — reduce holdup
    // Below critical (Tr < 0.8): dense CO2 liquid — moderate correction
    if (Tr > 0.95) {
      return 0.7; // Near-critical: significantly lower holdup
    } else if (Tr > 0.85) {
      // Linear interpolation between 0.7 and 0.85
      return 0.7 + (0.85 - 0.7) * (0.95 - Tr) / (0.95 - 0.85);
    } else {
      return 0.85; // Subcritical dense CO2: moderate reduction
    }
  }

  /**
   * Calculates a correction factor for friction in CO2-dominated two-phase flow. Dense-phase CO2
   * has lower viscosity than typical crude oil, generally resulting in lower friction factors.
   *
   * @param system the thermodynamic system
   * @return the friction correction factor (multiply by Beggs-Brill friction factor). Returns 1.0
   *         for non-CO2 systems
   */
  public static double getFrictionCorrectionFactor(SystemInterface system) {
    if (!isCO2DominatedFluid(system)) {
      return 1.0;
    }

    double Tr = system.getTemperature() / CO2_TC;
    double Pr = system.getPressure() / CO2_PC;

    // Near-critical conditions: friction characteristics differ significantly
    if (Tr > 0.9 && Pr > 0.9) {
      return 0.85; // Reduced friction in near-critical region
    }

    return 0.95; // Slight reduction for dense CO2 vs oil
  }

  /**
   * Estimates the CO2 liquid-vapour surface tension based on the reduced temperature. This can be
   * used to correct flow pattern transition criteria.
   *
   * <p>
   * Uses the Sugden correlation adapted for CO2: sigma = sigma_0 * (1 - Tr)^n where sigma_0 and n
   * are fitted parameters for CO2.
   * </p>
   *
   * @param system the thermodynamic system
   * @return the estimated surface tension in N/m, or 0 if above the critical point
   */
  public static double estimateCO2SurfaceTension(SystemInterface system) {
    double Tr = system.getTemperature() / CO2_TC;
    if (Tr >= 1.0) {
      return 0.0;
    }

    // CO2 surface tension parameters (Sugden-type correlation)
    // sigma_0 ~ 78.4 mN/m, n ~ 1.26 (from Hough & Stegemeier 1961)
    double sigma0 = 0.0784; // N/m
    double exponent = 1.26;
    return sigma0 * Math.pow(1.0 - Tr, exponent);
  }

  /**
   * Checks whether the fluid conditions are in the dense phase region (supercritical but
   * liquid-like). In this region, single-phase flow is expected but properties differ from
   * conventional gas.
   *
   * @param system the thermodynamic system
   * @return true if T is above Tc and P is above Pc (supercritical/dense phase)
   */
  public static boolean isDensePhase(SystemInterface system) {
    return system.getTemperature() > CO2_TC && system.getPressure() > CO2_PC;
  }

  /**
   * Gets the reduced temperature of the CO2 in the system.
   *
   * @param system the thermodynamic system
   * @return the reduced temperature T/Tc
   */
  public static double getReducedTemperature(SystemInterface system) {
    return system.getTemperature() / CO2_TC;
  }

  /**
   * Gets the reduced pressure of the CO2 in the system.
   *
   * @param system the thermodynamic system
   * @return the reduced pressure P/Pc
   */
  public static double getReducedPressure(SystemInterface system) {
    return system.getPressure() / CO2_PC;
  }
}
