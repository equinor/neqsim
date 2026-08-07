package neqsim.process.safety.selfheating;

/**
 * Body shapes supported by the Frank-Kamenetskii steady-state criticality analysis.
 *
 * <p>
 * Each shape carries its critical Frank-Kamenetskii parameter {@code deltaCrit}, the value of the dimensionless
 * criticality parameter above which no steady-state temperature profile exists and the body must self-ignite. The
 * characteristic dimension {@code r} to which {@code deltaCrit} refers differs by shape and is given by
 * {@link #getDimensionDescription()}.
 * </p>
 *
 * <p>
 * The one-dimensional shapes additionally carry the Laplacian shape factor {@code j} used by
 * {@link SelfHeatingInductionSolver}, where the radial Laplacian is {@code d2T/dr2 + (j / r) * dT/dr} with
 * {@code j = 0} for a slab, {@code 1} for a cylinder and {@code 2} for a sphere. Shapes that are not one-dimensional
 * can still be used for steady-state criticality but not for transient induction.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public enum SelfHeatingGeometry {
  /** Infinite slab; characteristic dimension is the half-thickness. */
  SLAB(0.878, 0, true, "half-thickness"),
  /** Infinite cylinder; characteristic dimension is the radius. */
  INFINITE_CYLINDER(2.000, 1, true, "radius"),
  /** Sphere; characteristic dimension is the radius. */
  SPHERE(3.322, 2, true, "radius"),
  /** Cube; characteristic dimension is the half-side length. */
  CUBE(2.520, 0, false, "half-side length"),
  /** Equicylinder with height equal to diameter; characteristic dimension is the radius. */
  EQUICYLINDER(2.760, 1, false, "radius");

  /** Critical Frank-Kamenetskii parameter for this shape. */
  private final double deltaCrit;

  /** Laplacian shape factor used by the one-dimensional transient solver. */
  private final int shapeFactor;

  /** Whether this shape can be solved by the one-dimensional transient solver. */
  private final boolean oneDimensional;

  /** Description of the characteristic dimension that {@code deltaCrit} refers to. */
  private final String dimensionDescription;

  /**
   * Construct a geometry entry.
   *
   * @param deltaCrit critical Frank-Kamenetskii parameter for the shape
   * @param shapeFactor Laplacian shape factor (0 slab, 1 cylinder, 2 sphere)
   * @param oneDimensional true if the shape is supported by the transient solver
   * @param dimensionDescription description of the characteristic dimension
   */
  SelfHeatingGeometry(double deltaCrit, int shapeFactor, boolean oneDimensional, String dimensionDescription) {
    this.deltaCrit = deltaCrit;
    this.shapeFactor = shapeFactor;
    this.oneDimensional = oneDimensional;
    this.dimensionDescription = dimensionDescription;
  }

  /**
   * Gets the critical Frank-Kamenetskii parameter for this shape.
   *
   * @return critical value of delta, dimensionless
   */
  public double getDeltaCrit() {
    return deltaCrit;
  }

  /**
   * Gets the Laplacian shape factor used by the transient solver.
   *
   * @return 0 for a slab, 1 for a cylinder, 2 for a sphere
   */
  public int getShapeFactor() {
    return shapeFactor;
  }

  /**
   * Reports whether this shape can be solved by {@link SelfHeatingInductionSolver}.
   *
   * @return true if the shape reduces to a one-dimensional conduction problem
   */
  public boolean isOneDimensional() {
    return oneDimensional;
  }

  /**
   * Gets a description of the characteristic dimension that the critical parameter refers to.
   *
   * @return description of the characteristic dimension, for example "half-thickness"
   */
  public String getDimensionDescription() {
    return dimensionDescription;
  }
}
