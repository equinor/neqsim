package neqsim.process.equipment.lng;

import java.io.Serializable;

/**
 * Defines the physical geometry of an LNG cargo tank.
 *
 * <p>
 * Tank geometry determines the heat transfer zones, liquid surface area, wetted wall area at a
 * given fill level, and how liquid height relates to volume. Commercial LNG simulators (Cargo
 * Expert, LNGMAP) use detailed geometric models for each containment type.
 * </p>
 *
 * <p>
 * Four containment types are supported:
 * </p>
 * <ul>
 * <li><b>Membrane (GTT NO96 / Mark III)</b> — prismatic box integrated into ship hull</li>
 * <li><b>Moss (Moss Maritime)</b> — spherical self-supporting tank</li>
 * <li><b>Type C (IMO Type C)</b> — horizontal cylindrical pressure vessel</li>
 * <li><b>SPB (IHI Self-supporting Prismatic)</b> — prismatic self-supporting tank</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TankGeometry implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1020L;

  /**
   * Tank containment type enumeration.
   */
  public enum ContainmentType {
    /** GTT membrane (NO96 or Mark III) — prismatic box in hull. */
    MEMBRANE,
    /** Moss Maritime — spherical self-supporting. */
    MOSS,
    /** IMO Type C — horizontal cylindrical pressure vessel. */
    TYPE_C,
    /** IHI SPB — self-supporting prismatic type B. */
    SPB
  }

  /** Containment type. */
  private ContainmentType containmentType = ContainmentType.MEMBRANE;

  /** Total tank volume (m3). */
  private double totalVolume = 140000.0;

  /** Tank length (m). For membrane/SPB: fore-aft length. For Type C: cylinder length. */
  private double length = 50.0;

  /** Tank width/breadth (m). For membrane: athwartship. For Moss/Type C: diameter. */
  private double width = 45.0;

  /** Tank height (m). For membrane: depth from bottom to roof. For Moss: diameter. */
  private double height = 27.0;

  /** Tank inner diameter (m). For Moss and Type C. */
  private double innerDiameter = 40.0;

  /** Insulation thickness (m). Typical: membrane 0.27m, Moss 0.30m. */
  private double insulationThickness = 0.27;

  /** Insulation thermal conductivity (W/(m*K)). */
  private double insulationConductivity = 0.04;

  /**
   * Default constructor. Creates a typical 140,000 m3 membrane tank.
   */
  public TankGeometry() {}

  /**
   * Constructor with containment type.
   *
   * @param type containment type
   * @param totalVolume total tank volume (m3)
   */
  public TankGeometry(ContainmentType type, double totalVolume) {
    this.containmentType = type;
    this.totalVolume = totalVolume;
    setDefaultDimensionsForType(type, totalVolume);
  }

  /**
   * Set default dimensions based on containment type and total volume.
   *
   * @param type containment type
   * @param volume total volume (m3)
   */
  private void setDefaultDimensionsForType(ContainmentType type, double volume) {
    switch (type) {
      case MEMBRANE:
        // Typical membrane tank: L x W x H with chamfered corners
        // Net volume ~ 0.92 * L * W * H (chamfer factor)
        double chamferFactor = 0.92;
        double aspectLW = 1.1; // L/W ratio
        double aspectHW = 0.6; // H/W ratio
        width = Math.pow(volume / (chamferFactor * aspectLW * aspectHW), 1.0 / 3.0);
        length = width * aspectLW;
        height = width * aspectHW;
        insulationThickness = 0.27;
        insulationConductivity = 0.04;
        break;

      case MOSS:
        // Spherical tank: V = (4/3) * pi * r^3
        double radius = Math.pow(3.0 * volume / (4.0 * Math.PI), 1.0 / 3.0);
        innerDiameter = 2.0 * radius;
        length = innerDiameter;
        width = innerDiameter;
        height = innerDiameter;
        insulationThickness = 0.30;
        insulationConductivity = 0.04;
        break;

      case TYPE_C:
        // Horizontal cylinder: V = pi * r^2 * L, typical L/D = 4
        double ldRatio = 4.0;
        double d = Math.pow(4.0 * volume / (Math.PI * ldRatio), 1.0 / 3.0);
        innerDiameter = d;
        length = d * ldRatio;
        width = d;
        height = d;
        insulationThickness = 0.20;
        insulationConductivity = 0.045;
        break;

      case SPB:
        // Similar to membrane but self-supporting
        double spbFactor = 0.95;
        double spbAspectLW = 1.2;
        double spbAspectHW = 0.55;
        width = Math.pow(volume / (spbFactor * spbAspectLW * spbAspectHW), 1.0 / 3.0);
        length = width * spbAspectLW;
        height = width * spbAspectHW;
        insulationThickness = 0.25;
        insulationConductivity = 0.042;
        break;

      default:
        break;
    }
  }

  /**
   * Get the bottom area of the tank (contact with bottom insulation/ballast).
   *
   * @return bottom area (m2)
   */
  public double getBottomArea() {
    switch (containmentType) {
      case MEMBRANE:
      case SPB:
        return length * width;
      case MOSS:
        // Sphere bottom contact ~ pi * r^2 (great circle)
        return Math.PI * Math.pow(innerDiameter / 2.0, 2);
      case TYPE_C:
        // Saddle contact area ~ 0.4 * pi * D * L
        return 0.4 * Math.PI * innerDiameter * length;
      default:
        return length * width;
    }
  }

  /**
   * Get the roof area of the tank (exposed to ambient/deck).
   *
   * @return roof area (m2)
   */
  public double getRoofArea() {
    switch (containmentType) {
      case MEMBRANE:
      case SPB:
        return length * width;
      case MOSS:
        // Upper hemisphere ~ 2 * pi * r^2
        return 2.0 * Math.PI * Math.pow(innerDiameter / 2.0, 2);
      case TYPE_C:
        // Top half of cylinder ~ pi * D/2 * L
        return Math.PI * (innerDiameter / 2.0) * length;
      default:
        return length * width;
    }
  }

  /**
   * Get the sidewall area of the tank.
   *
   * @return sidewall area (m2)
   */
  public double getSidewallArea() {
    switch (containmentType) {
      case MEMBRANE:
      case SPB:
        return 2.0 * (length * height + width * height);
      case MOSS:
        // Equatorial band area
        return Math.PI * innerDiameter * innerDiameter;
      case TYPE_C:
        // Cylindrical shell + end caps
        return Math.PI * innerDiameter * length + 2.0 * Math.PI * Math.pow(innerDiameter / 2.0, 2);
      default:
        return 2.0 * (length * height + width * height);
    }
  }

  /**
   * Get the total external surface area of the tank.
   *
   * @return total surface area (m2)
   */
  public double getTotalSurfaceArea() {
    return getBottomArea() + getRoofArea() + getSidewallArea();
  }

  /**
   * Get the wetted wall area for a given fill level.
   *
   * <p>
   * The wetted area determines the effective area for wall-to-liquid heat transfer.
   * </p>
   *
   * @param fillFraction fill level (0.0 to 1.0)
   * @return wetted wall area (m2)
   */
  public double getWettedWallArea(double fillFraction) {
    fillFraction = Math.max(0.0, Math.min(1.0, fillFraction));
    switch (containmentType) {
      case MEMBRANE:
      case SPB:
        // Bottom always wetted, plus sidewalls up to liquid height
        double liquidHeight = fillFraction * height;
        return getBottomArea() + 2.0 * (length + width) * liquidHeight;
      case MOSS:
        // Spherical cap area = 2 * pi * R * h
        double r = innerDiameter / 2.0;
        double h = fillFraction * innerDiameter;
        return 2.0 * Math.PI * r * h;
      case TYPE_C:
        // Wetted perimeter of circular cross-section * length
        double rC = innerDiameter / 2.0;
        double halfAngle = Math.acos(1.0 - 2.0 * fillFraction);
        double wettedPerimeter = 2.0 * rC * halfAngle;
        return wettedPerimeter * length;
      default:
        return getBottomArea() + 2.0 * (length + width) * fillFraction * height;
    }
  }

  /**
   * Get the liquid height for a given fill volume.
   *
   * @param liquidVolume liquid volume (m3)
   * @return liquid height (m)
   */
  public double getLiquidHeight(double liquidVolume) {
    double fillFraction = liquidVolume / totalVolume;
    fillFraction = Math.max(0.0, Math.min(1.0, fillFraction));

    switch (containmentType) {
      case MEMBRANE:
      case SPB:
        return fillFraction * height;
      case MOSS:
        // Spherical cap: V = pi*h^2*(3R - h)/3
        // Approximate with fill fraction * diameter
        return fillFraction * innerDiameter;
      case TYPE_C:
        // Horizontal cylinder segment
        return fillFraction * innerDiameter;
      default:
        return fillFraction * height;
    }
  }

  /**
   * Get the liquid surface area at a given fill level (free surface for evaporation).
   *
   * @param fillFraction fill level (0.0 to 1.0)
   * @return liquid surface area (m2)
   */
  public double getLiquidSurfaceArea(double fillFraction) {
    fillFraction = Math.max(0.0, Math.min(1.0, fillFraction));
    switch (containmentType) {
      case MEMBRANE:
      case SPB:
        return length * width; // Constant for prismatic
      case MOSS:
        // Circular cross-section at height h
        double r = innerDiameter / 2.0;
        double h = fillFraction * innerDiameter;
        double chordR = Math.sqrt(Math.max(0, 2.0 * r * h - h * h));
        return Math.PI * chordR * chordR; // Approximate as circle
      case TYPE_C:
        // Rectangular surface: chord width * length
        double rC = innerDiameter / 2.0;
        double hC = fillFraction * innerDiameter;
        double chordW = 2.0 * Math.sqrt(Math.max(0, 2.0 * rC * hC - hC * hC));
        return chordW * length;
      default:
        return length * width;
    }
  }

  /**
   * Get the overall heat transfer coefficient for the insulation.
   *
   * <p>
   * U = k / t, where k is insulation conductivity and t is thickness.
   * </p>
   *
   * @return U-value (W/(m2*K))
   */
  public double getInsulationUValue() {
    if (insulationThickness > 0) {
      return insulationConductivity / insulationThickness;
    }
    return 0.04; // default
  }

  // ─── Factory methods ───

  /**
   * Create a typical 174,000 m3 Q-Max membrane tank.
   *
   * @return tank geometry for Q-Max
   */
  public static TankGeometry createQMax() {
    TankGeometry geom = new TankGeometry(ContainmentType.MEMBRANE, 174000.0);
    geom.length = 53.6;
    geom.width = 46.9;
    geom.height = 27.0;
    return geom;
  }

  /**
   * Create a typical 36,000 m3 Moss tank (one sphere of a 145,000 m3 Moss carrier).
   *
   * @return tank geometry for single Moss sphere
   */
  public static TankGeometry createMossSingle() {
    TankGeometry geom = new TankGeometry(ContainmentType.MOSS, 36000.0);
    geom.innerDiameter = 41.0;
    return geom;
  }

  /**
   * Create a typical small-scale Type C tank (e.g., 7500 m3 for small LNG carrier).
   *
   * @return tank geometry for Type C
   */
  public static TankGeometry createTypeC() {
    return new TankGeometry(ContainmentType.TYPE_C, 7500.0);
  }

  // ─── Getters and setters ───

  /**
   * Get containment type.
   *
   * @return containment type
   */
  public ContainmentType getContainmentType() {
    return containmentType;
  }

  /**
   * Set containment type.
   *
   * @param containmentType containment type
   */
  public void setContainmentType(ContainmentType containmentType) {
    this.containmentType = containmentType;
  }

  /**
   * Get total volume.
   *
   * @return total volume (m3)
   */
  public double getTotalVolume() {
    return totalVolume;
  }

  /**
   * Set total volume.
   *
   * @param totalVolume total volume (m3)
   */
  public void setTotalVolume(double totalVolume) {
    this.totalVolume = totalVolume;
  }

  /**
   * Get tank length.
   *
   * @return length (m)
   */
  public double getLength() {
    return length;
  }

  /**
   * Set tank length.
   *
   * @param length length (m)
   */
  public void setLength(double length) {
    this.length = length;
  }

  /**
   * Get tank width.
   *
   * @return width (m)
   */
  public double getWidth() {
    return width;
  }

  /**
   * Set tank width.
   *
   * @param width width (m)
   */
  public void setWidth(double width) {
    this.width = width;
  }

  /**
   * Get tank height.
   *
   * @return height (m)
   */
  public double getHeight() {
    return height;
  }

  /**
   * Set tank height.
   *
   * @param height height (m)
   */
  public void setHeight(double height) {
    this.height = height;
  }

  /**
   * Get inner diameter.
   *
   * @return inner diameter (m)
   */
  public double getInnerDiameter() {
    return innerDiameter;
  }

  /**
   * Set inner diameter.
   *
   * @param innerDiameter inner diameter (m)
   */
  public void setInnerDiameter(double innerDiameter) {
    this.innerDiameter = innerDiameter;
  }

  /**
   * Get insulation thickness.
   *
   * @return insulation thickness (m)
   */
  public double getInsulationThickness() {
    return insulationThickness;
  }

  /**
   * Set insulation thickness.
   *
   * @param insulationThickness insulation thickness (m)
   */
  public void setInsulationThickness(double insulationThickness) {
    this.insulationThickness = insulationThickness;
  }

  /**
   * Get insulation conductivity.
   *
   * @return thermal conductivity (W/(m*K))
   */
  public double getInsulationConductivity() {
    return insulationConductivity;
  }

  /**
   * Set insulation conductivity.
   *
   * @param insulationConductivity thermal conductivity (W/(m*K))
   */
  public void setInsulationConductivity(double insulationConductivity) {
    this.insulationConductivity = insulationConductivity;
  }
}
