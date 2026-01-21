package neqsim.process.equipment.pipeline.twophasepipe.closure;

import java.io.Serializable;

/**
 * Geometry calculations for stratified two-phase flow in circular pipes.
 *
 * <p>
 * Provides methods to calculate wetted perimeters, interfacial width, and phase areas based on
 * liquid level in stratified flow. These geometric parameters are essential for the two-fluid model
 * closure relations.
 * </p>
 *
 * <h2>Geometry Definition</h2>
 * <p>
 * For a circular pipe of diameter D with liquid level h_L (measured from bottom):
 * </p>
 * <ul>
 * <li>A_L = liquid cross-sectional area</li>
 * <li>A_G = gas cross-sectional area</li>
 * <li>S_L = wetted perimeter (liquid-wall contact)</li>
 * <li>S_G = wetted perimeter (gas-wall contact)</li>
 * <li>S_i = interfacial width (gas-liquid interface)</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class GeometryCalculator implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * Result container for stratified flow geometry calculations.
   */
  public static class StratifiedGeometry implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Liquid cross-sectional area (m²). */
    public double liquidArea;

    /** Gas cross-sectional area (m²). */
    public double gasArea;

    /** Wetted perimeter - liquid contact with wall (m). */
    public double liquidWettedPerimeter;

    /** Wetted perimeter - gas contact with wall (m). */
    public double gasWettedPerimeter;

    /** Interfacial width - gas-liquid interface length (m). */
    public double interfacialWidth;

    /** Liquid hydraulic diameter (m). */
    public double liquidHydraulicDiameter;

    /** Gas hydraulic diameter (m). */
    public double gasHydraulicDiameter;

    /** Half-angle subtended by liquid at pipe center (radians). */
    public double liquidAngle;

    /** Liquid level from bottom of pipe (m). */
    public double liquidLevel;

    /** Liquid holdup (volume fraction). */
    public double liquidHoldup;
  }

  /**
   * Calculate stratified flow geometry from liquid level.
   *
   * <p>
   * Uses circular segment geometry formulas.
   * </p>
   *
   * @param liquidLevel Height of liquid from pipe bottom (m)
   * @param diameter Pipe inner diameter (m)
   * @return StratifiedGeometry with all geometric parameters
   */
  public StratifiedGeometry calculateFromLiquidLevel(double liquidLevel, double diameter) {
    StratifiedGeometry geom = new StratifiedGeometry();
    geom.liquidLevel = liquidLevel;

    double R = diameter / 2.0;
    double h = Math.max(0, Math.min(diameter, liquidLevel)); // Clamp to [0, D]

    // Half-angle subtended by liquid (from pipe center)
    // cos(theta) = (R - h) / R = 1 - h/R
    double cosTheta = 1.0 - h / R;
    cosTheta = Math.max(-1.0, Math.min(1.0, cosTheta)); // Clamp for numerical safety
    double theta = Math.acos(cosTheta);
    geom.liquidAngle = theta;

    // Total pipe area
    double totalArea = Math.PI * R * R;

    // Liquid area (circular segment)
    // A_L = R² * (theta - sin(theta)*cos(theta)) = R² * (theta - 0.5*sin(2*theta))
    geom.liquidArea = R * R * (theta - Math.sin(theta) * Math.cos(theta));

    // Gas area
    geom.gasArea = totalArea - geom.liquidArea;

    // Liquid holdup
    geom.liquidHoldup = geom.liquidArea / totalArea;

    // Wetted perimeters
    // S_L = 2 * R * theta (arc length subtended by liquid)
    geom.liquidWettedPerimeter = 2.0 * R * theta;

    // S_G = 2 * R * (pi - theta)
    geom.gasWettedPerimeter = 2.0 * R * (Math.PI - theta);

    // Interfacial width (chord length)
    // S_i = 2 * R * sin(theta)
    geom.interfacialWidth = 2.0 * R * Math.sin(theta);

    // Hydraulic diameters
    // D_L = 4 * A_L / (S_L + S_i)
    double perimeterL = geom.liquidWettedPerimeter + geom.interfacialWidth;
    if (perimeterL > 1e-10) {
      geom.liquidHydraulicDiameter = 4.0 * geom.liquidArea / perimeterL;
    } else {
      geom.liquidHydraulicDiameter = 0;
    }

    // D_G = 4 * A_G / (S_G + S_i)
    double perimeterG = geom.gasWettedPerimeter + geom.interfacialWidth;
    if (perimeterG > 1e-10) {
      geom.gasHydraulicDiameter = 4.0 * geom.gasArea / perimeterG;
    } else {
      geom.gasHydraulicDiameter = 0;
    }

    return geom;
  }

  /**
   * Calculate stratified flow geometry from liquid holdup.
   *
   * <p>
   * Iteratively solves for liquid level that gives the specified holdup.
   * </p>
   *
   * @param liquidHoldup Target liquid holdup (0-1)
   * @param diameter Pipe inner diameter (m)
   * @return StratifiedGeometry with all geometric parameters
   */
  public StratifiedGeometry calculateFromHoldup(double liquidHoldup, double diameter) {
    // Clamp holdup to valid range
    double alpha_L = Math.max(0, Math.min(1, liquidHoldup));

    // Use Newton-Raphson to find liquid level
    double h = diameter * alpha_L; // Initial guess
    double R = diameter / 2.0;
    double targetArea = alpha_L * Math.PI * R * R;

    // Newton-Raphson iteration
    for (int iter = 0; iter < 50; iter++) {
      h = Math.max(1e-10, Math.min(diameter - 1e-10, h));

      double cosTheta = 1.0 - h / R;
      cosTheta = Math.max(-1.0, Math.min(1.0, cosTheta));
      double theta = Math.acos(cosTheta);

      // Current area
      double area = R * R * (theta - Math.sin(theta) * Math.cos(theta));

      // Error
      double error = area - targetArea;
      if (Math.abs(error) < 1e-12 * Math.PI * R * R) {
        break;
      }

      // Derivative dA/dh
      // dA/dh = R² * (dθ/dh - cos(2θ) * dθ/dh) = R² * dθ/dh * (1 - cos(2θ))
      // where dθ/dh = 1 / (R * sin(θ))
      double sinTheta = Math.sin(theta);
      if (Math.abs(sinTheta) < 1e-10) {
        // Near empty or full pipe, use linear approximation
        h += (targetArea - area) / (2.0 * R);
      } else {
        double dThetaDh = 1.0 / (R * sinTheta);
        double dAdh = R * R * dThetaDh * (1.0 - Math.cos(2.0 * theta));
        h -= error / dAdh;
      }
    }

    return calculateFromLiquidLevel(h, diameter);
  }

  /**
   * Calculate liquid level from holdup using direct formula (approximate).
   *
   * <p>
   * Uses Hart's approximation for faster computation when high accuracy is not required.
   * </p>
   *
   * @param holdup Liquid holdup (0-1)
   * @param diameter Pipe diameter (m)
   * @return Approximate liquid level (m)
   */
  public double approximateLiquidLevel(double holdup, double diameter) {
    // Hart's approximation (1989)
    // h/D ≈ 0.5 * (1 - cos(pi * holdup^0.36))
    double normalizedLevel = 0.5 * (1.0 - Math.cos(Math.PI * Math.pow(holdup, 0.36)));
    return normalizedLevel * diameter;
  }

  /**
   * Calculate gas-wall wetted perimeter for annular flow.
   *
   * <p>
   * In annular flow, gas occupies the core while liquid forms a film on the wall.
   * </p>
   *
   * @param filmThickness Liquid film thickness (m)
   * @param diameter Pipe diameter (m)
   * @return Gas-interface perimeter (core perimeter)
   */
  public double calcAnnularGasPerimeter(double filmThickness, double diameter) {
    double coreRadius = diameter / 2.0 - filmThickness;
    if (coreRadius < 0) {
      return 0;
    }
    return 2.0 * Math.PI * coreRadius;
  }

  /**
   * Calculate liquid film thickness from holdup in annular flow.
   *
   * <p>
   * Assumes uniform film around pipe circumference.
   * </p>
   *
   * @param liquidHoldup Liquid holdup (0-1)
   * @param diameter Pipe diameter (m)
   * @return Film thickness (m)
   */
  public double calcAnnularFilmThickness(double liquidHoldup, double diameter) {
    // A_L = pi * (R² - r²) where r is core radius
    // α_L = (R² - r²) / R²
    // r² = R² * (1 - α_L)
    // r = R * sqrt(1 - α_L)
    // δ = R - r = R * (1 - sqrt(1 - α_L))
    double R = diameter / 2.0;
    double alpha_L = Math.max(0, Math.min(1, liquidHoldup));
    return R * (1.0 - Math.sqrt(1.0 - alpha_L));
  }

  /**
   * Calculate the derivative of liquid area with respect to liquid level.
   *
   * <p>
   * Useful for stability analysis and Kelvin-Helmholtz criterion.
   * </p>
   *
   * @param liquidLevel Liquid level (m)
   * @param diameter Pipe diameter (m)
   * @return dA_L/dh (m)
   */
  public double calcAreaDerivative(double liquidLevel, double diameter) {
    double R = diameter / 2.0;
    double h = Math.max(1e-10, Math.min(diameter - 1e-10, liquidLevel));

    double cosTheta = 1.0 - h / R;
    cosTheta = Math.max(-1.0, Math.min(1.0, cosTheta));
    double theta = Math.acos(cosTheta);
    double sinTheta = Math.sin(theta);

    if (Math.abs(sinTheta) < 1e-10) {
      // Near empty or full
      return 2.0 * R;
    }

    // dA/dh = 2 * R * sin(theta) = S_i (interfacial width)
    return 2.0 * R * sinTheta;
  }

  /**
   * Check if flow can be stratified based on Kelvin-Helmholtz stability.
   *
   * <p>
   * Stratified flow becomes unstable when gas velocity exceeds critical value.
   * </p>
   *
   * @param gasVelocity Gas velocity (m/s)
   * @param liquidLevel Liquid level (m)
   * @param diameter Pipe diameter (m)
   * @param gasDensity Gas density (kg/m³)
   * @param liquidDensity Liquid density (kg/m³)
   * @param inclination Pipe inclination (radians, positive = uphill)
   * @return true if stratified flow is stable
   */
  public boolean isStratifiedStable(double gasVelocity, double liquidLevel, double diameter,
      double gasDensity, double liquidDensity, double inclination) {
    StratifiedGeometry geom = calculateFromLiquidLevel(liquidLevel, diameter);

    if (geom.gasArea < 1e-10 || geom.liquidArea < 1e-10) {
      return false;
    }

    // Kelvin-Helmholtz criterion (Taitel-Dukler)
    // v_G < v_G,crit where:
    // v_G,crit² = (ρ_L - ρ_G) * g * cos(θ) * A_G / (ρ_G * (dA_L/dh))
    double g = 9.81;
    double dAdh = calcAreaDerivative(liquidLevel, diameter);

    if (dAdh < 1e-10) {
      return true; // Near full or empty, not relevant
    }

    double deltaRho = liquidDensity - gasDensity;
    double critVelSquared =
        deltaRho * g * Math.cos(inclination) * geom.gasArea / (gasDensity * dAdh);

    if (critVelSquared < 0) {
      return false; // Downhill flow, always unstable at some velocity
    }

    double critVel = Math.sqrt(critVelSquared);
    return Math.abs(gasVelocity) < critVel;
  }
}
