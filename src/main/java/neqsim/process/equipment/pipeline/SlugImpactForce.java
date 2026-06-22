package neqsim.process.equipment.pipeline;

import java.util.Locale;

/**
 * Standalone liquid-slug momentum impact-force utility for pipework loading screening.
 *
 * <p>
 * Converts a slug (liquid density, optional gas void fraction, flow area, arrival velocity) into a momentum impact
 * force on a bend or fitting, with an optional dynamic load factor (DLF) to produce a design force. Unlike
 * {@link TransientForceCalculator}, this utility does not require a full Method-of-Characteristics transient solution
 * and is intended for fast screening.
 * </p>
 *
 * <p>
 * <b>Governing relations:</b>
 * </p>
 *
 * <pre>
 *   F_slug   = rho_eff A v^2                       (axial momentum force)
 *   F_bend   = 2 rho_eff A v^2 sin(theta/2)        (resultant reaction on a bend)
 *   F_design = DLF * F_slug
 *   rho_eff  = (1 - GVF) rho_liquid + GVF rho_gas  (homogeneous slug density)
 * </pre>
 *
 * <p>
 * <b>Standards basis:</b> Energy Institute <i>Guidelines for the avoidance of vibration-induced fatigue failure in
 * process pipework</i>, company flow-induced-vibration (FIV) control guidelines for production systems, and company
 * multiphase-flow-analysis guidance (slug characterisation and GVF).
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class SlugImpactForce {
  /**
   * Non-instantiable static utility class.
   */
  private SlugImpactForce() {
    throw new AssertionError("SlugImpactForce is a static utility and must not be instantiated");
  }

  /**
   * Homogeneous (no-slip) effective slug density from liquid density, gas density, and gas void fraction.
   *
   * @param liquidDensity liquid density [kg/m3]; must be positive
   * @param gasDensity gas density [kg/m3]; must be non-negative
   * @param gasVoidFraction gas void fraction in the range [0, 1]
   * @return effective slug density [kg/m3]
   * @throws IllegalArgumentException if inputs are out of physical range
   */
  public static double effectiveSlugDensity(double liquidDensity, double gasDensity, double gasVoidFraction) {
    if (liquidDensity <= 0.0) {
      throw new IllegalArgumentException("liquidDensity must be positive");
    }
    if (gasDensity < 0.0) {
      throw new IllegalArgumentException("gasDensity must be non-negative");
    }
    if (gasVoidFraction < 0.0 || gasVoidFraction > 1.0) {
      throw new IllegalArgumentException("gasVoidFraction must be in [0, 1]");
    }
    return (1.0 - gasVoidFraction) * liquidDensity + gasVoidFraction * gasDensity;
  }

  /**
   * Axial momentum impact force for a fully liquid slug.
   *
   * @param density slug density [kg/m3]; must be positive
   * @param area flow area [m2]; must be positive
   * @param velocity slug arrival velocity [m/s]
   * @return momentum impact force [N]
   * @throws IllegalArgumentException if density or area is non-positive
   */
  public static double momentumForce(double density, double area, double velocity) {
    if (density <= 0.0) {
      throw new IllegalArgumentException("density must be positive");
    }
    if (area <= 0.0) {
      throw new IllegalArgumentException("area must be positive");
    }
    return density * area * velocity * velocity;
  }

  /**
   * Resultant reaction force on a bend turning through {@code bendAngleDeg}.
   *
   * @param density slug density [kg/m3]; must be positive
   * @param area flow area [m2]; must be positive
   * @param velocity slug arrival velocity [m/s]
   * @param bendAngleDeg bend turning angle [deg] in the range [0, 180]
   * @return resultant bend reaction force magnitude [N]
   * @throws IllegalArgumentException if density or area is non-positive
   */
  public static double bendForce(double density, double area, double velocity, double bendAngleDeg) {
    double axial = momentumForce(density, area, velocity);
    return 2.0 * axial * Math.sin(Math.toRadians(bendAngleDeg) / 2.0);
  }

  /**
   * GVF- and DLF-corrected design force for a two-phase slug.
   *
   * @param liquidDensity liquid density [kg/m3]; must be positive
   * @param gasDensity gas density [kg/m3]; must be non-negative
   * @param area flow area [m2]; must be positive
   * @param velocity slug arrival velocity [m/s]
   * @param gasVoidFraction gas void fraction in the range [0, 1]
   * @param dynamicLoadFactor dynamic load factor (typically 1 to 2)
   * @return design impact force [N]
   * @throws IllegalArgumentException if inputs are out of physical range
   */
  public static double designForce(double liquidDensity, double gasDensity, double area, double velocity,
      double gasVoidFraction, double dynamicLoadFactor) {
    double rhoEff = effectiveSlugDensity(liquidDensity, gasDensity, gasVoidFraction);
    return dynamicLoadFactor * momentumForce(rhoEff, area, velocity);
  }

  /**
   * Flow area from a pipe internal diameter.
   *
   * @param diameter pipe internal diameter [m]; must be positive
   * @return flow area [m2]
   * @throws IllegalArgumentException if diameter is non-positive
   */
  public static double areaFromDiameter(double diameter) {
    if (diameter <= 0.0) {
      throw new IllegalArgumentException("diameter must be positive");
    }
    return 0.25 * Math.PI * diameter * diameter;
  }

  /**
   * Convert a force in Newtons to a requested force unit.
   *
   * @param forceNewton force value [N]
   * @param unit force unit, one of "N", "kN", or "lbf" (case-insensitive); null defaults to N
   * @return force in the requested unit
   */
  public static double convertForce(double forceNewton, String unit) {
    if (unit == null) {
      return forceNewton;
    }
    switch (unit.toLowerCase(Locale.ROOT)) {
    case "kn":
      return forceNewton * 1.0e-3;
    case "lbf":
      return forceNewton / 4.4482216152605;
    case "n":
    default:
      return forceNewton;
    }
  }
}
