package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;

/**
 * Structural screening calculations for umbilicals: tube burst and collapse, helical armour wire capacity, free-hanging
 * catenary tension and cross-section packing.
 *
 * <p>
 * Tube wall thickness for internal pressure follows the ASME B31.3 style expression
 * {@code t = P ID / (2 (S + P Y - P))} with {@code S = usage factor * SMYS} and {@code Y = 0.4}. Collapse under
 * external hydrostatic pressure uses the DNV-ST-F101 combined elastic/plastic collapse equation with initial ovality.
 * Armour wires are helical with lay angle alpha measured from the umbilical axis, so a layer holds
 * {@code n = pi D cos(alpha) / d_w} wires, each carrying {@code A_w sigma cos(alpha)} axially. The catenary is the
 * classical inextensible free-hanging solution with a horizontal tangent at touchdown. All values are screening level;
 * standard safety factors must be confirmed against the governing standard (API 17E / ISO 13628-5, DNV-ST-F101).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class UmbilicalStructuralCalculator {

  /** Seawater density in kg/m3. */
  public static final double SEAWATER_DENSITY = 1025.0;

  /** Gravitational acceleration in m/s2. */
  public static final double GRAVITY = 9.80665;

  /** Poisson ratio of steel. */
  private static final double POISSON_RATIO = 0.3;

  /**
   * Free-hanging catenary result.
   */
  public static class CatenaryResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Horizontal tension in kN. */
    private final double horizontalTensionKN;
    /** Top tension in kN. */
    private final double topTensionKN;
    /** Suspended length in m. */
    private final double suspendedLengthM;
    /** Top angle from vertical in degrees. */
    private final double topAngleFromVerticalDeg;
    /** Bend radius at touchdown in m. */
    private final double touchdownBendRadiusM;

    /**
     * Constructor.
     *
     * @param horizontalTensionKN horizontal tension in kN
     * @param topTensionKN top tension in kN
     * @param suspendedLengthM suspended length in m
     * @param topAngleFromVerticalDeg top angle from vertical in degrees
     * @param touchdownBendRadiusM touchdown bend radius in m
     */
    public CatenaryResult(double horizontalTensionKN, double topTensionKN, double suspendedLengthM,
        double topAngleFromVerticalDeg, double touchdownBendRadiusM) {
      this.horizontalTensionKN = horizontalTensionKN;
      this.topTensionKN = topTensionKN;
      this.suspendedLengthM = suspendedLengthM;
      this.topAngleFromVerticalDeg = topAngleFromVerticalDeg;
      this.touchdownBendRadiusM = touchdownBendRadiusM;
    }

    /**
     * Get horizontal tension (constant along the catenary).
     *
     * @return tension in kN
     */
    public double getHorizontalTensionKN() {
      return horizontalTensionKN;
    }

    /**
     * Get top tension.
     *
     * @return tension in kN
     */
    public double getTopTensionKN() {
      return topTensionKN;
    }

    /**
     * Get suspended length.
     *
     * @return length in m
     */
    public double getSuspendedLengthM() {
      return suspendedLengthM;
    }

    /**
     * Get top angle from vertical.
     *
     * @return angle in degrees
     */
    public double getTopAngleFromVerticalDeg() {
      return topAngleFromVerticalDeg;
    }

    /**
     * Get bend radius at the touchdown point, H/w.
     *
     * @return radius in m
     */
    public double getTouchdownBendRadiusM() {
      return touchdownBendRadiusM;
    }
  }

  /**
   * Private constructor for utility class.
   */
  private UmbilicalStructuralCalculator() {
  }

  /**
   * Hydrostatic pressure of seawater.
   *
   * @param depthM water depth in m
   * @return pressure in MPa
   */
  public static double hydrostaticPressureMPa(double depthM) {
    return SEAWATER_DENSITY * GRAVITY * depthM / 1.0e6;
  }

  /**
   * Wall thickness needed for internal pressure.
   *
   * @param designPressureMPa net internal design pressure in MPa
   * @param innerDiameterMm inner diameter in mm
   * @param smysMPa specified minimum yield strength in MPa
   * @param usageFactor allowable fraction of SMYS (for example 0.6)
   * @return wall thickness in mm
   */
  public static double burstWallThicknessMm(double designPressureMPa, double innerDiameterMm, double smysMPa,
      double usageFactor) {
    if (designPressureMPa <= 0.0) {
      return 0.0;
    }
    double allowable = usageFactor * smysMPa;
    double y = 0.4;
    return designPressureMPa * innerDiameterMm / (2.0 * (allowable + designPressureMPa * y - designPressureMPa));
  }

  /**
   * Collapse pressure of a tube under external pressure (DNV-ST-F101 combined equation).
   *
   * @param outerDiameterMm outer diameter in mm
   * @param wallThicknessMm wall thickness in mm
   * @param smysMPa specified minimum yield strength in MPa
   * @param youngsModulusMPa Young's modulus in MPa
   * @param ovality initial ovality, (Dmax - Dmin) / D, typically 0.005 to 0.01
   * @return collapse pressure in MPa
   */
  public static double collapsePressureMPa(double outerDiameterMm, double wallThicknessMm, double smysMPa,
      double youngsModulusMPa, double ovality) {
    double tOverD = wallThicknessMm / outerDiameterMm;
    double pel = 2.0 * youngsModulusMPa * Math.pow(tOverD, 3) / (1.0 - POISSON_RATIO * POISSON_RATIO);
    double pp = smysMPa * 2.0 * tOverD;
    double lo = 0.0;
    double hi = Math.min(pel, pp);
    for (int i = 0; i < 200; i++) {
      double pc = 0.5 * (lo + hi);
      double g = (pc - pel) * (pc * pc - pp * pp) - pc * pel * pp * ovality / tOverD;
      if (g > 0.0) {
        lo = pc;
      } else {
        hi = pc;
      }
    }
    return 0.5 * (lo + hi);
  }

  /**
   * Wall thickness needed so that the collapse pressure divided by a safety factor exceeds the external pressure.
   *
   * @param externalPressureMPa external pressure in MPa
   * @param innerDiameterMm inner diameter in mm
   * @param smysMPa specified minimum yield strength in MPa
   * @param youngsModulusMPa Young's modulus in MPa
   * @param ovality initial ovality
   * @param safetyFactor collapse safety factor (for example 1.5)
   * @return wall thickness in mm
   */
  public static double collapseWallThicknessMm(double externalPressureMPa, double innerDiameterMm, double smysMPa,
      double youngsModulusMPa, double ovality, double safetyFactor) {
    if (externalPressureMPa <= 0.0) {
      return 0.0;
    }
    double lo = 0.01;
    double hi = innerDiameterMm;
    for (int i = 0; i < 100; i++) {
      double t = 0.5 * (lo + hi);
      double pc = collapsePressureMPa(innerDiameterMm + 2.0 * t, t, smysMPa, youngsModulusMPa, ovality);
      if (pc / safetyFactor < externalPressureMPa) {
        lo = t;
      } else {
        hi = t;
      }
    }
    return 0.5 * (lo + hi);
  }

  /**
   * Number of wires in one helical armour layer.
   *
   * @param layerDiameterMm pitch diameter of the layer in mm
   * @param wireDiameterMm wire diameter in mm
   * @param layAngleDeg lay angle from the umbilical axis in degrees
   * @return number of wires that fit in the layer
   */
  public static int armorWireCount(double layerDiameterMm, double wireDiameterMm, double layAngleDeg) {
    return (int) Math.floor(Math.PI * layerDiameterMm * Math.cos(Math.toRadians(layAngleDeg)) / wireDiameterMm);
  }

  /**
   * Axial tension capacity of helical armour layers.
   *
   * @param coreDiameterMm diameter under the first armour layer in mm
   * @param wireDiameterMm wire diameter in mm
   * @param layAngleDeg lay angle from the axis in degrees
   * @param layers number of armour layers
   * @param allowableStressMPa allowable wire stress in MPa
   * @return tension capacity in kN
   */
  public static double armorCapacityKN(double coreDiameterMm, double wireDiameterMm, double layAngleDeg, int layers,
      double allowableStressMPa) {
    double wireArea = Math.PI * wireDiameterMm * wireDiameterMm / 4.0;
    double cosA = Math.cos(Math.toRadians(layAngleDeg));
    double total = 0.0;
    for (int i = 0; i < layers; i++) {
      double layerDiameter = coreDiameterMm + wireDiameterMm * (1.0 + 2.0 * i);
      total += armorWireCount(layerDiameter, wireDiameterMm, layAngleDeg) * wireArea * allowableStressMPa * cosA
          / 1000.0;
    }
    return total;
  }

  /**
   * Steel mass per metre of umbilical length of helical armour layers (including the helix length factor 1/cos alpha).
   *
   * @param coreDiameterMm diameter under the first armour layer in mm
   * @param wireDiameterMm wire diameter in mm
   * @param layAngleDeg lay angle from the axis in degrees
   * @param layers number of armour layers
   * @param densityKgM3 wire density in kg/m3
   * @return mass in kg/m
   */
  public static double armorMassPerMeter(double coreDiameterMm, double wireDiameterMm, double layAngleDeg, int layers,
      double densityKgM3) {
    double wireArea = Math.PI * wireDiameterMm * wireDiameterMm / 4.0 * 1.0e-6;
    double cosA = Math.cos(Math.toRadians(layAngleDeg));
    double total = 0.0;
    for (int i = 0; i < layers; i++) {
      double layerDiameter = coreDiameterMm + wireDiameterMm * (1.0 + 2.0 * i);
      total += armorWireCount(layerDiameter, wireDiameterMm, layAngleDeg) * wireArea * densityKgM3 / cosA;
    }
    return total;
  }

  /**
   * Axial stiffness of helical armour layers, n E A cos^3(alpha).
   *
   * @param coreDiameterMm diameter under the first armour layer in mm
   * @param wireDiameterMm wire diameter in mm
   * @param layAngleDeg lay angle from the axis in degrees
   * @param layers number of armour layers
   * @param youngsModulusMPa wire Young's modulus in MPa
   * @return axial stiffness EA in kN
   */
  public static double armorAxialStiffnessKN(double coreDiameterMm, double wireDiameterMm, double layAngleDeg,
      int layers, double youngsModulusMPa) {
    double wireArea = Math.PI * wireDiameterMm * wireDiameterMm / 4.0;
    double cos3 = Math.pow(Math.cos(Math.toRadians(layAngleDeg)), 3);
    double total = 0.0;
    for (int i = 0; i < layers; i++) {
      double layerDiameter = coreDiameterMm + wireDiameterMm * (1.0 + 2.0 * i);
      total += armorWireCount(layerDiameter, wireDiameterMm, layAngleDeg) * wireArea * youngsModulusMPa * cos3 / 1000.0;
    }
    return total;
  }

  /**
   * Free-hanging catenary with a horizontal tangent at touchdown.
   *
   * @param submergedWeightNPerM submerged weight in N/m (must be positive)
   * @param depthM vertical distance from top to touchdown in m
   * @param horizontalOffsetM horizontal distance from the top to the touchdown point in m
   * @return catenary result
   * @throws IllegalArgumentException if the weight is not positive or depth is not positive
   */
  public static CatenaryResult freeHangingCatenary(double submergedWeightNPerM, double depthM,
      double horizontalOffsetM) {
    if (submergedWeightNPerM <= 0.0 || depthM <= 0.0) {
      throw new IllegalArgumentException(
          "Catenary needs positive submerged weight and depth; the umbilical may be buoyant");
    }
    double offset = Math.max(horizontalOffsetM, 1.0e-3 * depthM);
    double lo = 1.0e-4 * depthM;
    double hi = 1.0e7;
    for (int i = 0; i < 200; i++) {
      double a = Math.sqrt(lo * hi);
      double x = a * acosh(1.0 + depthM / a);
      if (x > offset) {
        hi = a;
      } else {
        lo = a;
      }
    }
    double a = Math.sqrt(lo * hi);
    double w = submergedWeightNPerM;
    double suspended = Math.sqrt(depthM * depthM + 2.0 * a * depthM);
    double horizontal = a * w;
    double top = w * (a + depthM);
    double topAngleFromVertical = Math.toDegrees(Math.atan2(horizontal, w * suspended));
    return new CatenaryResult(horizontal / 1000.0, top / 1000.0, suspended, topAngleFromVertical, a);
  }

  /**
   * Profile of a free-hanging catenary from touchdown (index 0) to the top.
   *
   * @param submergedWeightNPerM submerged weight in N/m
   * @param touchdownBendRadiusM catenary parameter a = H/w in m
   * @param depthM vertical distance from touchdown to the top in m
   * @param points number of points, at least 2
   * @return rows: horizontal distance from touchdown in m, height above seabed in m, tension in kN
   */
  public static double[][] catenaryProfile(double submergedWeightNPerM, double touchdownBendRadiusM, double depthM,
      int points) {
    int n = Math.max(2, points);
    double a = touchdownBendRadiusM;
    double xMax = a * acosh(1.0 + depthM / a);
    double[][] profile = new double[3][n];
    for (int i = 0; i < n; i++) {
      double x = xMax * i / (n - 1.0);
      profile[0][i] = x;
      profile[1][i] = a * (Math.cosh(x / a) - 1.0);
      profile[2][i] = submergedWeightNPerM * a * Math.cosh(x / a) / 1000.0;
    }
    return profile;
  }

  /**
   * Inverse hyperbolic cosine.
   *
   * @param x argument, at least 1
   * @return acosh(x)
   */
  private static double acosh(double x) {
    return Math.log(x + Math.sqrt(x * x - 1.0));
  }

  /**
   * Fraction of a circle that a bundle of circular elements fills (screening packing efficiency).
   *
   * @param count number of elements
   * @param mixedSizes true if element sizes differ
   * @return packing fraction between 0 and 1
   */
  public static double packingFraction(int count, boolean mixedSizes) {
    double[] table = {1.0, 0.5, 0.646, 0.686, 0.685, 0.667, 0.778};
    double phi;
    if (count <= 0) {
      return 1.0;
    } else if (count <= table.length) {
      phi = table[count - 1];
    } else if (count >= 19) {
      phi = 0.80;
    } else {
      phi = 0.72 + 0.08 * (count - 8) / 11.0;
    }
    return mixedSizes && count > 1 ? phi * 0.85 : phi;
  }

  /**
   * Diameter of the smallest circle enclosing a bundle of circular elements, from packing efficiency and the
   * two-largest-elements lower bound.
   *
   * @param elementDiametersMm outer diameters of the elements in mm
   * @return bundle diameter in mm
   */
  public static double bundleDiameterMm(double[] elementDiametersMm) {
    int n = elementDiametersMm.length;
    if (n == 0) {
      return 0.0;
    }
    double area = 0.0;
    double largest = 0.0;
    double second = 0.0;
    double smallest = Double.MAX_VALUE;
    for (double d : elementDiametersMm) {
      area += Math.PI * d * d / 4.0;
      if (d > largest) {
        second = largest;
        largest = d;
      } else if (d > second) {
        second = d;
      }
      smallest = Math.min(smallest, d);
    }
    if (n == 1) {
      return largest;
    }
    boolean mixed = largest > 1.05 * smallest;
    double fromArea = Math.sqrt(4.0 * area / (Math.PI * packingFraction(n, mixed)));
    return Math.max(fromArea, largest + second);
  }
}
