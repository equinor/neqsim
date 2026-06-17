package neqsim.process.equipment.separator.entrainment;

import java.io.Serializable;

/**
 * Grade efficiency curves for separator internals (mist eliminators, inlet devices, gravity
 * sections).
 *
 * <p>
 * A grade efficiency curve defines the fractional removal efficiency as a function of droplet
 * diameter. The overall separation efficiency is obtained by integrating the grade efficiency over
 * the droplet size distribution:
 * </p>
 *
 * $$ \eta_{overall} = \int_0^\infty \eta(d) \cdot f(d) \, dd = \sum_i \eta(d_i) \cdot \Delta F_i $$
 *
 * <p>
 * This class provides published correlations for common separator internals. All correlations are
 * from the open literature.
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class GradeEfficiencyCurve implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Types of separator internals with published grade efficiency correlations.
   */
  public enum InternalsType {
    /** Gravity settling section (no internals). */
    GRAVITY,
    /** Wire mesh demister pad. */
    WIRE_MESH,
    /** Vane pack (chevron) mist eliminator. */
    VANE_PACK,
    /** Axial-flow cyclone separator. */
    AXIAL_CYCLONE,
    /** Plate pack (parallel plate) coalescer. */
    PLATE_PACK,
    /** Custom user-defined grade efficiency curve. */
    CUSTOM
  }

  /** Type of internals. */
  private InternalsType type;

  /** Cut diameter d_50 [m] at which 50% efficiency is achieved. */
  private double cutDiameter;

  /** Sharpness parameter controlling the steepness of the efficiency curve. */
  private double sharpness;

  /** Maximum efficiency (accounts for re-entrainment effects). */
  private double maxEfficiency = 1.0;

  /** Minimum efficiency (leakage floor). */
  private double minEfficiency = 0.0;

  /** User-defined lookup table: {diameter[m], efficiency[0-1]}. */
  private double[][] customCurve;

  /**
   * Creates a grade efficiency curve for gravity settling.
   *
   * <p>
   * Gravity efficiency follows a squared relationship for the Stokes regime:
   * </p>
   *
   * $$ \eta(d) = \min\left(1, \left(\frac{d}{d_{cut}}\right)^2\right) $$
   *
   * <p>
   * where $d_{cut}$ is the critical diameter from the geometry and residence time. See Arnold and
   * Stewart (2008), <i>Surface Production Operations</i>, Vol. 1, Gulf Professional Publishing.
   * </p>
   *
   * @param cutDiameter critical diameter for 100% removal [m]
   * @return a new GradeEfficiencyCurve
   */
  public static GradeEfficiencyCurve gravity(double cutDiameter) {
    GradeEfficiencyCurve curve = new GradeEfficiencyCurve();
    curve.type = InternalsType.GRAVITY;
    curve.cutDiameter = cutDiameter;
    curve.sharpness = 2.0; // Stokes: eta ~ (d/d_cut)^2
    return curve;
  }

  /**
   * Creates a grade efficiency curve for a wire mesh demister.
   *
   * <p>
   * Modelled as a sigmoid (logistic) capture efficiency:
   * </p>
   *
   * $$ \eta(d) = \eta_{max} \cdot \left[1 - \exp\left(-0.693 \cdot
   * \left(\frac{d}{d_{50}}\right)^n\right)\right] $$
   *
   * <p>
   * Typical wire mesh parameters from Brunazzi and Paglianti (1998), <i>Chemical Engineering
   * Science</i>, 53(19), 3373-3380:
   * </p>
   *
   * <p>
   * d_50 = 3-10 um for standard pads at typical gas velocities, n = 2-3 (sharpness), eta_max =
   * 0.99-0.999 (below flooding).
   * </p>
   *
   * @param cutDiameter50 d_50 diameter at 50% efficiency [m]
   * @param sharpness n steepness parameter (typically 2.0-3.0)
   * @param maxEfficiency maximum efficiency below flooding (typically 0.99-0.999)
   * @return a new GradeEfficiencyCurve
   */
  public static GradeEfficiencyCurve wireMesh(double cutDiameter50, double sharpness,
      double maxEfficiency) {
    GradeEfficiencyCurve curve = new GradeEfficiencyCurve();
    curve.type = InternalsType.WIRE_MESH;
    curve.cutDiameter = cutDiameter50;
    curve.sharpness = sharpness;
    curve.maxEfficiency = maxEfficiency;
    return curve;
  }

  /**
   * Creates a grade efficiency curve for a wire mesh demister with typical default parameters.
   *
   * <p>
   * Defaults: d_50 = 5 um, sharpness = 2.5, max efficiency = 0.998.
   * </p>
   *
   * @return a new GradeEfficiencyCurve
   */
  public static GradeEfficiencyCurve wireMeshDefault() {
    return wireMesh(5.0e-6, 2.5, 0.998);
  }

  /**
   * Creates a grade efficiency curve for a vane pack (chevron) mist eliminator.
   *
   * <p>
   * Vane pack efficiency is modelled using inertial impaction. The capture probability depends on
   * the Stokes number $Stk = \rho_d d^2 V / (18 \mu_c W)$ where $W$ is the channel width. The
   * efficiency is approximated as:
   * </p>
   *
   * $$ \eta(d) = \eta_{max} \cdot \left[1 - \exp\left(-0.693 \cdot
   * \left(\frac{d}{d_{50}}\right)^n\right)\right] $$
   *
   * <p>
   * Typical values from Phillips and Listak (1996) and Verlaan (2001): d_50 = 8-20 um, n = 1.5-2.5,
   * eta_max = 0.99-0.995.
   * </p>
   *
   * @param cutDiameter50 d_50 diameter at 50% efficiency [m]
   * @param sharpness steepness parameter (typically 1.5-2.5)
   * @param maxEfficiency maximum efficiency (typically 0.99-0.995)
   * @return a new GradeEfficiencyCurve
   */
  public static GradeEfficiencyCurve vanePack(double cutDiameter50, double sharpness,
      double maxEfficiency) {
    GradeEfficiencyCurve curve = new GradeEfficiencyCurve();
    curve.type = InternalsType.VANE_PACK;
    curve.cutDiameter = cutDiameter50;
    curve.sharpness = sharpness;
    curve.maxEfficiency = maxEfficiency;
    return curve;
  }

  /**
   * Creates a grade efficiency curve for a vane pack with typical default parameters.
   *
   * <p>
   * Defaults: d_50 = 12 um, sharpness = 2.0, max efficiency = 0.995.
   * </p>
   *
   * @return a new GradeEfficiencyCurve
   */
  public static GradeEfficiencyCurve vanePackDefault() {
    return vanePack(12.0e-6, 2.0, 0.995);
  }

  /**
   * Creates a grade efficiency curve for an axial-flow cyclone separator.
   *
   * <p>
   * Cyclone efficiency follows the Barth collection efficiency model, approximated as a steep
   * sigmoid. Cyclone tubes have very sharp cut-off characteristics. From Hoffmann and Stein (2008),
   * <i>Gas Cyclones and Swirl Tubes: Principles, Design and Operation</i>, Springer:
   * </p>
   *
   * <p>
   * d_50 = 2-5 um for typical axial cyclone tubes at high gas velocity, n = 3-5 (very steep
   * cut-off), eta_max = 0.995-0.999.
   * </p>
   *
   * @param cutDiameter50 d_50 diameter at 50% efficiency [m]
   * @param sharpness steepness (typically 3.0-5.0)
   * @param maxEfficiency maximum efficiency (typically 0.995-0.999)
   * @return a new GradeEfficiencyCurve
   */
  public static GradeEfficiencyCurve axialCyclone(double cutDiameter50, double sharpness,
      double maxEfficiency) {
    GradeEfficiencyCurve curve = new GradeEfficiencyCurve();
    curve.type = InternalsType.AXIAL_CYCLONE;
    curve.cutDiameter = cutDiameter50;
    curve.sharpness = sharpness;
    curve.maxEfficiency = maxEfficiency;
    return curve;
  }

  /**
   * Creates a grade efficiency curve for an axial cyclone with typical default parameters.
   *
   * <p>
   * Defaults: d_50 = 3 um, sharpness = 4.0, max efficiency = 0.998.
   * </p>
   *
   * @return a new GradeEfficiencyCurve
   */
  public static GradeEfficiencyCurve axialCycloneDefault() {
    return axialCyclone(3.0e-6, 4.0, 0.998);
  }

  /**
   * Creates a grade efficiency curve for a plate pack (parallel plate) coalescer.
   *
   * <p>
   * Plate packs reduce the effective settling height, improving gravity separation. The effective
   * cut diameter is reduced by the ratio of plate spacing to vessel height. Modelled as gravity
   * separation with reduced cut diameter.
   * </p>
   *
   * <p>
   * See Polderman et al. (1997), "Design rules for plate pack/liquid-liquid coalescers", conference
   * paper. Typical: d_50 = 20-100 um for oil-water, sharpness = 2.0.
   * </p>
   *
   * @param cutDiameter50 effective d_50 for plate pack [m]
   * @param maxEfficiency maximum efficiency
   * @return a new GradeEfficiencyCurve
   */
  public static GradeEfficiencyCurve platePack(double cutDiameter50, double maxEfficiency) {
    GradeEfficiencyCurve curve = new GradeEfficiencyCurve();
    curve.type = InternalsType.PLATE_PACK;
    curve.cutDiameter = cutDiameter50;
    curve.sharpness = 2.0;
    curve.maxEfficiency = maxEfficiency;
    return curve;
  }

  /**
   * Creates a custom grade efficiency curve from a lookup table.
   *
   * @param diametersM diameter values [m], ascending order
   * @param efficiencies corresponding efficiency values [0-1]
   * @return a new GradeEfficiencyCurve
   */
  public static GradeEfficiencyCurve custom(double[] diametersM, double[] efficiencies) {
    if (diametersM.length != efficiencies.length || diametersM.length < 2) {
      throw new IllegalArgumentException(
          "Custom grade efficiency requires at least 2 matching diameter-efficiency pairs");
    }
    GradeEfficiencyCurve curve = new GradeEfficiencyCurve();
    curve.type = InternalsType.CUSTOM;
    curve.customCurve = new double[diametersM.length][2];
    for (int i = 0; i < diametersM.length; i++) {
      curve.customCurve[i][0] = diametersM[i];
      curve.customCurve[i][1] = Math.max(0.0, Math.min(1.0, efficiencies[i]));
    }
    curve.cutDiameter = 0.0;
    return curve;
  }

  /**
   * Calculates the fractional removal efficiency for a given droplet diameter.
   *
   * @param diameter droplet diameter [m]
   * @return efficiency [0-1]
   */
  public double getEfficiency(double diameter) {
    if (diameter <= 0) {
      return minEfficiency;
    }

    if (type == InternalsType.CUSTOM) {
      return interpolateCustom(diameter);
    }

    if (type == InternalsType.GRAVITY) {
      double ratio = diameter / cutDiameter;
      double eta = Math.min(1.0, ratio * ratio);
      return Math.max(minEfficiency, Math.min(maxEfficiency, eta));
    }

    // Sigmoid model for wire mesh, vane pack, cyclone, plate pack
    double ratio = diameter / cutDiameter;
    double eta = maxEfficiency * (1.0 - Math.exp(-0.693 * Math.pow(ratio, sharpness)));
    return Math.max(minEfficiency, Math.min(maxEfficiency, eta));
  }

  /**
   * Computes the overall separation efficiency by integrating the grade efficiency over a droplet
   * size distribution.
   *
   * @param dsd droplet size distribution
   * @return overall mass/volume separation efficiency [0-1]
   */
  public double calcOverallEfficiency(DropletSizeDistribution dsd) {
    double[][] classes = dsd.getDiscreteClasses();
    double totalEfficiency = 0.0;
    for (double[] cls : classes) {
      double midDiameter = cls[1];
      double volumeFraction = cls[2];
      totalEfficiency += getEfficiency(midDiameter) * volumeFraction;
    }
    return Math.max(0.0, Math.min(1.0, totalEfficiency));
  }

  /**
   * Linearly interpolates the custom efficiency curve.
   *
   * @param diameter droplet diameter [m]
   * @return interpolated efficiency [0-1]
   */
  private double interpolateCustom(double diameter) {
    if (customCurve == null || customCurve.length < 2) {
      return 0.0;
    }
    if (diameter <= customCurve[0][0]) {
      return customCurve[0][1];
    }
    if (diameter >= customCurve[customCurve.length - 1][0]) {
      return customCurve[customCurve.length - 1][1];
    }
    for (int i = 0; i < customCurve.length - 1; i++) {
      if (diameter >= customCurve[i][0] && diameter < customCurve[i + 1][0]) {
        double frac = (diameter - customCurve[i][0]) / (customCurve[i + 1][0] - customCurve[i][0]);
        return customCurve[i][1] + frac * (customCurve[i + 1][1] - customCurve[i][1]);
      }
    }
    return customCurve[customCurve.length - 1][1];
  }

  /**
   * Gets the internals type.
   *
   * @return type
   */
  public InternalsType getType() {
    return type;
  }

  /**
   * Gets the cut diameter d_50 [m].
   *
   * @return cutDiameter [m]
   */
  public double getCutDiameter() {
    return cutDiameter;
  }

  /**
   * Sets the cut diameter d_50 [m].
   *
   * @param cutDiameter [m]
   */
  public void setCutDiameter(double cutDiameter) {
    this.cutDiameter = cutDiameter;
  }

  /**
   * Gets the sharpness parameter.
   *
   * @return sharpness
   */
  public double getSharpness() {
    return sharpness;
  }

  /**
   * Sets the sharpness parameter.
   *
   * @param sharpness the sharpness parameter
   */
  public void setSharpness(double sharpness) {
    this.sharpness = sharpness;
  }

  /**
   * Gets the maximum efficiency.
   *
   * @return maxEfficiency
   */
  public double getMaxEfficiency() {
    return maxEfficiency;
  }

  /**
   * Sets the maximum efficiency.
   *
   * @param maxEfficiency max efficiency [0-1]
   */
  public void setMaxEfficiency(double maxEfficiency) {
    this.maxEfficiency = maxEfficiency;
  }

  /**
   * Gets the minimum efficiency (leakage floor).
   *
   * @return minEfficiency
   */
  public double getMinEfficiency() {
    return minEfficiency;
  }

  /**
   * Sets the minimum efficiency (leakage floor).
   *
   * @param minEfficiency min efficiency [0-1]
   */
  public void setMinEfficiency(double minEfficiency) {
    this.minEfficiency = minEfficiency;
  }
}
