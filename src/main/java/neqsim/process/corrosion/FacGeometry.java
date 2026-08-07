package neqsim.process.corrosion;

/**
 * Local geometry classes used by {@link FlowAcceleratedCorrosion}, with their turbulence enhancement factors.
 *
 * <p>
 * Flow-accelerated corrosion is not distributed uniformly. It concentrates wherever the local flow field differs from
 * the bulk, because the mass-transfer boundary layer is thinned there and dissolved iron is carried away faster. API RP
 * 571 notes that wall thinning frequently occurs immediately downstream of such disturbances.
 * </p>
 *
 * <p>
 * The enhancement factors below are the ratio of local to fully developed straight-pipe mass transfer, taken from the
 * ranges reported in the FAC literature. They are screening multipliers for ranking locations, not a substitute for
 * computational fluid dynamics on the actual internal geometry.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public enum FacGeometry {
  /** Fully developed flow in a straight run, the reference case. */
  STRAIGHT_PIPE(1.0, "fully developed straight run"),
  /** Long-radius elbow or bend. */
  ELBOW_BEND(2.5, "elbow or bend"),
  /** Circumferential weld whose root protrudes into the bore, acting as a local flow disturbance. */
  WELD_ROOT_PROTRUSION(3.0, "circumferential weld with protruding root"),
  /** Weld located immediately at a bend tangent, combining both disturbances. */
  WELD_AT_BEND(4.0, "circumferential weld at a bend outlet"),
  /** Tee or branch connection. */
  TEE(2.5, "tee or branch connection"),
  /** Reducer or expander. */
  REDUCER(2.0, "reducer or expander"),
  /** One to two diameters downstream of an orifice or restriction, where thinning typically peaks. */
  DOWNSTREAM_ORIFICE(6.0, "downstream of an orifice or restriction");

  private final double enhancementFactor;
  private final String description;

  /**
   * Construct a geometry entry.
   *
   * @param enhancementFactor local-to-straight-pipe mass-transfer enhancement, dimensionless
   * @param description human-readable description of the geometry
   */
  FacGeometry(double enhancementFactor, String description) {
    this.enhancementFactor = enhancementFactor;
    this.description = description;
  }

  /**
   * Gets the mass-transfer enhancement factor relative to a straight pipe.
   *
   * @return enhancement factor, dimensionless
   */
  public double getEnhancementFactor() {
    return enhancementFactor;
  }

  /**
   * Gets the description of the geometry.
   *
   * @return human-readable description
   */
  public String getDescription() {
    return description;
  }
}
