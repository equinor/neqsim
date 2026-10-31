package neqsim.process.equipment.pipeline.twophasepipe.closure;

import java.io.Serializable;

/**
 * Geometry calculations for stratified two-phase flow in circular pipes.
 *
 * <p>
 * Provides methods to calculate wetted perimeters, interfacial width, and phase areas based on liquid level in
 * stratified flow. These geometric parameters are essential for the two-fluid model closure relations.
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

  /** Positive nodes of the sixteen-point Gauss-Legendre rule. */
  private static final double[] CELL_NODES = {0.09501250983763744, 0.2816035507792589, 0.4580167776572274,
      0.6178762444026438, 0.7554044083550030, 0.8656312023878318, 0.9445750230732326, 0.9894009349916499};
  /** Corresponding weights; symmetry supplies the negative nodes. */
  private static final double[] CELL_WEIGHTS = {0.1894506104550685, 0.1826034150449236, 0.1691565193950025,
      0.1495959888165767, 0.1246289712555339, 0.0951585116824928, 0.0622535239386479, 0.0271524594117541};

  /**
   * Calculate signed first pressure moments about a stratified interface in a circular section.
   *
   * <p>
   * For normal depth h and chord width w(y), the liquid moment is the integral of (h-y)w(y) from zero to h; the gas
   * moment integrates the same expression from h to D. Multiplication by phase density, gravity and cosine of
   * inclination gives the hydrostatic contribution to axial pressure traction in N. The pressure at the interface
   * contributes separately as interface pressure times phase area. Derivatives with respect to h are the corresponding
   * phase areas. Positive quadrature of the minority segment avoids cancellation at phase disappearance. This explicit
   * geometry query does not change solver forces or energy fluxes.
   * </p>
   *
   * @param liquidDepth interface depth normal to the pipe, in m, from zero to diameter inclusive
   * @param diameter internal diameter in m
   * @return immutable signed moments in m3 and phase areas in m2
   * @throws IllegalArgumentException if geometry or its moments cannot be represented
   */
  public StratifiedPressureMoments calculateStratifiedPressureMoments(double liquidDepth, double diameter) {
    if (!Double.isFinite(liquidDepth) || !Double.isFinite(diameter) || !(diameter > 0.0) || liquidDepth < 0.0
        || liquidDepth > diameter) {
      throw new IllegalArgumentException("Pressure moments require finite depth in [0, D] and positive diameter");
    }
    double area = Math.PI * diameter * diameter / 4.0;
    double minorityDepth = Math.min(liquidDepth, diameter - liquidDepth);
    double areaIntegral = 0.0;
    double momentIntegral = 0.0;
    if (minorityDepth > 0.0) {
      double fraction = minorityDepth / diameter;
      for (int node = 0; node < CELL_NODES.length; node++) {
        for (int sign = -1; sign <= 1; sign += 2) {
          double t = 0.5 * (1.0 + sign * CELL_NODES[node]);
          double tSquared = t * t;
          double integrand = tSquared * Math.sqrt(1.0 - fraction * tSquared);
          double weight = 0.5 * CELL_WEIGHTS[node];
          areaIntegral += weight * integrand;
          momentIntegral += weight * integrand * (1.0 - tSquared);
        }
      }
    }
    double scale = 4.0 * minorityDepth * Math.sqrt(minorityDepth * diameter);
    double minorityArea = scale * areaIntegral;
    double minorityMoment = scale * minorityDepth * momentIntegral;
    double wholeMoment = (liquidDepth - 0.5 * diameter) * area;
    boolean liquidIsMinority = liquidDepth <= 0.5 * diameter;
    double liquidArea = liquidIsMinority ? minorityArea : area - minorityArea;
    double gasArea = liquidIsMinority ? area - minorityArea : minorityArea;
    double liquidMoment = liquidIsMinority ? minorityMoment : wholeMoment + minorityMoment;
    double gasMoment = liquidIsMinority ? wholeMoment - minorityMoment : -minorityMoment;
    if (!Double.isFinite(area) || !(0.5 * diameter * area > 0.0) || !Double.isFinite(liquidMoment)
        || !Double.isFinite(gasMoment) || liquidMoment < 0.0 || gasMoment > 0.0
        || (minorityDepth > 0.0 && (!(minorityArea > 0.0) || !(minorityMoment > 0.0)))) {
      throw new IllegalArgumentException("Pressure moments exceed representable geometry");
    }
    return new StratifiedPressureMoments(liquidMoment, gasMoment, liquidArea, gasArea);
  }

  /**
   * Signed pressure moments for a circular stratified section; excludes the interface-pressure term.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class StratifiedPressureMoments implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double liquidMoment;
    private final double gasMoment;
    private final double liquidArea;
    private final double gasArea;

    /**
     * Store an independently owned pressure-moment result.
     *
     * @param liquidMoment nonnegative liquid first moment in m3
     * @param gasMoment nonpositive gas first moment in m3
     * @param liquidArea liquid cross-sectional area in m2
     * @param gasArea gas cross-sectional area in m2
     */
    private StratifiedPressureMoments(double liquidMoment, double gasMoment, double liquidArea, double gasArea) {
      this.liquidMoment = liquidMoment;
      this.gasMoment = gasMoment;
      this.liquidArea = liquidArea;
      this.gasArea = gasArea;
    }

    /**
     * Get the nonnegative liquid pressure moment about the interface.
     *
     * @return integral of interface depth minus height over liquid area, in m3
     */
    public double getLiquidMoment() {
      return liquidMoment;
    }

    /**
     * Get the nonpositive gas pressure moment about the interface.
     *
     * @return integral of interface depth minus height over gas area, in m3
     */
    public double getGasMoment() {
      return gasMoment;
    }

    /**
     * Get the occupied liquid cross-sectional area.
     *
     * @return liquid area, also the derivative of its moment with respect to depth, in m2
     */
    public double getLiquidArea() {
      return liquidArea;
    }

    /**
     * Get the occupied gas cross-sectional area.
     *
     * @return gas area, also the derivative of its moment with respect to depth, in m2
     */
    public double getGasArea() {
      return gasArea;
    }
  }

  /**
   * Reconstruct a horizontal free surface within one straight, constant-diameter cell.
   *
   * <p>
   * This is an explicit static geometry diagnostic, not a flowing-interface closure. It preserves the specified
   * cell-average liquid volume and changes no solver state. The coordinate is pipe arclength; depth is measured in the
   * cross-section normal to the pipe. Capillarity, curvature-volume corrections and separate oil/water surfaces are
   * excluded. Near-vertical cells and volumes not resolvable in double precision fail explicitly.
   * </p>
   *
   * @param liquidHoldup cell-average total liquid volume fraction, in [0, 1]
   * @param diameter internal diameter in metres
   * @param length cell arclength in metres
   * @param inclination signed angle above horizontal in radians, strictly between +/- pi/2
   * @return immutable volume-preserving static geometry
   * @throws IllegalArgumentException if inputs or the reconstruction are inadmissible
   */
  public HydrostaticCellGeometry calculateHydrostaticCell(double liquidHoldup, double diameter, double length,
      double inclination) {
    if (!Double.isFinite(liquidHoldup) || liquidHoldup < 0.0 || liquidHoldup > 1.0 || !Double.isFinite(diameter)
        || !(diameter > 0.0) || !Double.isFinite(length) || !(length > 0.0) || !Double.isFinite(inclination)
        || !(Math.abs(inclination) < Math.PI / 2.0) || Math.cos(inclination) < 1.0e-8) {
      throw new IllegalArgumentException("Static cell geometry requires finite admissible volume and geometry");
    }
    double area = Math.PI * diameter * diameter / 4.0;
    double volume = area * length;
    double wallArea = Math.PI * diameter * length;
    double halfRange = Math.abs(Math.tan(inclination)) * length / (2.0 * diameter);
    if (!Double.isFinite(volume) || !(volume > 0.0) || !Double.isFinite(wallArea) || !Double.isFinite(halfRange)) {
      throw new IllegalArgumentException("Static cell geometry exceeds representable dimensions");
    }
    if (liquidHoldup == 0.0 || liquidHoldup == 1.0) {
      double depth = liquidHoldup * diameter;
      return new HydrostaticCellGeometry(depth, depth, liquidHoldup * volume, 0.0, liquidHoldup * wallArea,
          (1.0 - liquidHoldup) * area, 0.0);
    }
    // Solve the minority volume to avoid subtracting a trace inventory from a full pipe.
    double fraction = Math.min(liquidHoldup, 1.0 - liquidHoldup);
    double target = fraction * Math.PI / 4.0;
    double lower = -halfRange;
    double upper = 0.5;
    double centre = 0.0;
    for (int iteration = 0; iteration < 120; iteration++) {
      centre = lower + (upper - lower) / 2.0;
      double occupied = cellAverage(centre, halfRange, 0);
      if (occupied < target) {
        lower = centre;
      } else {
        upper = centre;
      }
    }
    double occupied = cellAverage(centre, halfRange, 0);
    double residual = (occupied - target) / target;
    if (!Double.isFinite(residual) || Math.abs(residual) > 1.0e-9) {
      throw new IllegalArgumentException("Static liquid volume is not resolvable at this cell scale");
    }
    double wetWall = cellAverage(centre, halfRange, 2) * diameter * length;
    double interfaceArea = cellAverage(centre, halfRange, 1) * diameter * length / Math.cos(inclination);
    if (liquidHoldup > 0.5) {
      centre = 1.0 - centre;
      wetWall = wallArea - wetWall;
      residual = -residual * fraction / liquidHoldup;
    }
    double signedRange = Math.copySign(halfRange, inclination);
    double start = boundedDepth(centre + signedRange) * diameter;
    double end = boundedDepth(centre - signedRange) * diameter;
    // Evaluate the complementary segment directly, rather than subtracting nearly equal areas.
    double minimumGasArea = segmentMetric(1.0 - Math.max(start, end) / diameter, 0) * diameter * diameter;
    double reconstructedVolume = liquidHoldup * volume * (1.0 + residual);
    if (!Double.isFinite(interfaceArea) || !Double.isFinite(wetWall) || !Double.isFinite(reconstructedVolume)) {
      throw new IllegalArgumentException("Nonfinite static cell geometry result");
    }
    return new HydrostaticCellGeometry(start, end, reconstructedVolume, interfaceArea, wetWall, minimumGasArea,
        residual);
  }

  /**
   * Integrate a unit-diameter segment metric over normalized axial depth.
   *
   * @param centre normal depth at cell centre divided by diameter
   * @param halfRange positive axial half-range of normalized depth
   * @param metric area=0, interface width=1, wall perimeter=2
   * @return cell-average metric for a unit-diameter pipe
   */
  private static double cellAverage(double centre, double halfRange, int metric) {
    if (halfRange == 0.0) {
      return segmentMetric(centre, metric);
    }
    // For a nearly horizontal, entirely partial cell, integrate directly in arclength.
    // This avoids subtracting almost equal angular endpoints, including sub-ulp slopes.
    if (centre > halfRange && centre + halfRange < 1.0 && halfRange < 1.0e-3 * Math.min(centre, 1.0 - centre)) {
      double average = 0.0;
      for (int node = 0; node < CELL_NODES.length; node++) {
        average += 0.5 * CELL_WEIGHTS[node] * (segmentMetric(centre - halfRange * CELL_NODES[node], metric)
            + segmentMetric(centre + halfRange * CELL_NODES[node], metric));
      }
      return average;
    }
    double low = centre - halfRange;
    double high = centre + halfRange;
    double full = metric == 0 ? Math.PI / 4.0 : metric == 2 ? Math.PI : 0.0;
    double integral = Math.max(0.0, high - Math.max(1.0, low)) * full;
    double a = boundedDepth(low);
    double b = boundedDepth(high);
    if (b > a) {
      // h=(1-cos(theta))/2 removes the circular-segment endpoint singularity.
      double thetaA = 2.0 * Math.asin(Math.sqrt(a));
      double thetaB = 2.0 * Math.asin(Math.sqrt(b));
      double mid = (thetaA + thetaB) / 2.0;
      double radius = (thetaB - thetaA) / 2.0;
      for (int node = 0; node < CELL_NODES.length; node++) {
        for (int sign = -1; sign <= 1; sign += 2) {
          double theta = mid + sign * radius * CELL_NODES[node];
          double value = metric == 0 ? segmentAreaFromAngle(theta) : metric == 1 ? Math.sin(theta) : theta;
          integral += radius * CELL_WEIGHTS[node] * value * 0.5 * Math.sin(theta);
        }
      }
    }
    return integral / (2.0 * halfRange);
  }

  /**
   * Evaluate a unit-diameter circular-segment metric, including exact absence/fullness.
   *
   * @param depth normalized normal depth
   * @param metric area=0, interface width=1, wall perimeter=2
   * @return segment metric
   */
  private static double segmentMetric(double depth, int metric) {
    double h = boundedDepth(depth);
    if (h == 0.0) {
      return 0.0;
    }
    if (h == 1.0) {
      return metric == 0 ? Math.PI / 4.0 : metric == 2 ? Math.PI : 0.0;
    }
    double theta = 2.0 * Math.asin(Math.sqrt(h));
    return metric == 0 ? segmentAreaFromAngle(theta) : metric == 1 ? 2.0 * Math.sqrt(h * (1.0 - h)) : theta;
  }

  /**
   * Stable segment area for a unit diameter, using symmetry and the small-angle series.
   *
   * @param angle segment half-angle in radians
   * @return area in unit-diameter square units
   */
  private static double segmentAreaFromAngle(double angle) {
    if (angle > Math.PI / 2.0) {
      return Math.PI / 4.0 - segmentAreaFromAngle(Math.PI - angle);
    }
    if (angle < 0.01) {
      double squared = angle * angle;
      return angle * squared * (1.0 / 6.0 + squared * (-1.0 / 30.0 + squared * (1.0 / 315.0 - squared / 5670.0)));
    }
    return (angle - Math.sin(angle) * Math.cos(angle)) / 4.0;
  }

  /**
   * Intersect a geometric depth with the pipe cross-section; no material state is changed.
   *
   * @param depth normalized geometric plane depth
   * @return depth within the circular cross-section
   */
  private static double boundedDepth(double depth) {
    return Math.max(0.0, Math.min(1.0, depth));
  }

  /**
   * Immutable static diagnostic for one straight finite-volume cell.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class HydrostaticCellGeometry implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Normal depth at the cell start, metres. */
    private final double startDepth;
    /** Normal depth at the cell end, metres. */
    private final double endDepth;
    /** Integrated liquid volume, cubic metres. */
    private final double liquidVolume;
    /** Horizontal gas-liquid interface area, square metres. */
    private final double interfaceArea;
    /** Liquid contact area on the cylindrical wall, square metres. */
    private final double liquidWallArea;
    /** Smallest gas cross-section within the cell, square metres. */
    private final double minimumGasArea;
    /** Reconstructed minus requested volume divided by requested volume. */
    private final double relativeVolumeResidual;

    /**
     * Store a verified static reconstruction.
     *
     * @param startDepth normal start depth in metres
     * @param endDepth normal end depth in metres
     * @param liquidVolume integrated liquid volume in cubic metres
     * @param interfaceArea horizontal interface area in square metres
     * @param liquidWallArea liquid-wall contact area in square metres
     * @param minimumGasArea minimum gas cross-section in square metres
     * @param relativeVolumeResidual relative volume error
     */
    private HydrostaticCellGeometry(double startDepth, double endDepth, double liquidVolume, double interfaceArea,
        double liquidWallArea, double minimumGasArea, double relativeVolumeResidual) {
      this.startDepth = startDepth;
      this.endDepth = endDepth;
      this.liquidVolume = liquidVolume;
      this.interfaceArea = interfaceArea;
      this.liquidWallArea = liquidWallArea;
      this.minimumGasArea = minimumGasArea;
      this.relativeVolumeResidual = relativeVolumeResidual;
    }

    /** @return normal liquid depth at the cell start in metres */
    public double getStartDepth() {
      return startDepth;
    }

    /** @return normal liquid depth at the cell end in metres */
    public double getEndDepth() {
      return endDepth;
    }

    /** @return maximum local normal depth in metres */
    public double getMaximumDepth() {
      return Math.max(startDepth, endDepth);
    }

    /** @return minimum local normal depth in metres */
    public double getMinimumDepth() {
      return Math.min(startDepth, endDepth);
    }

    /** @return integrated liquid volume in cubic metres */
    public double getLiquidVolume() {
      return liquidVolume;
    }

    /** @return horizontal gas-liquid interface area in square metres */
    public double getInterfaceArea() {
      return interfaceArea;
    }

    /** @return cylindrical liquid-wall contact area in square metres */
    public double getLiquidWallArea() {
      return liquidWallArea;
    }

    /** @return minimum gas cross-section in square metres, not a flow-capacity prediction */
    public double getMinimumGasArea() {
      return minimumGasArea;
    }

    /** @return relative integrated volume residual */
    public double getRelativeVolumeResidual() {
      return relativeVolumeResidual;
    }
  }

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
  public boolean isStratifiedStable(double gasVelocity, double liquidLevel, double diameter, double gasDensity,
      double liquidDensity, double inclination) {
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
    double critVelSquared = deltaRho * g * Math.cos(inclination) * geom.gasArea / (gasDensity * dAdh);

    if (critVelSquared < 0) {
      return false; // Downhill flow, always unstable at some velocity
    }

    double critVel = Math.sqrt(critVelSquared);
    return Math.abs(gasVelocity) < critVel;
  }
}
