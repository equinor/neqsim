package neqsim.fluidmechanics.flowsolver;

/**
 * Conservative advection schemes for component transport in one-phase pipe flow.
 *
 * <p>
 * This type is deliberately separate from {@link AdvectionScheme}. The latter configures the legacy staged composition
 * equation, while this enum selects the finite-volume component-inventory method used by the validated conservative
 * species path.
 * </p>
 */
public enum SpeciesAdvectionScheme {
  /**
   * Fully implicit first-order upwind transport.
   *
   * <p>
   * This is the compatibility default. It is unconditionally stable and monotone, but finite pulses can be strongly
   * broadened by numerical diffusion on coarse grids.
   * </p>
   */
  FIRST_ORDER_IMPLICIT("First-order implicit upwind", 1, false, Double.POSITIVE_INFINITY),

  /**
   * MUSCL reconstruction with a Van Leer limiter and two-stage SSP Runge-Kutta integration.
   *
   * <p>
   * The implementation uses automatic conservative substeps so that the local mass Courant number does not exceed the
   * documented stability limit. It is total-variation diminishing for the validated positive-flow scalar transport
   * problem and does not clip or normalize component fractions.
   * </p>
   */
  TVD_VAN_LEER_SSP_RK2("TVD Van Leer SSP-RK2", 2, true, 0.45);

  private final String displayName;
  private final int order;
  private final boolean highResolution;
  private final double maximumCourantNumber;

  SpeciesAdvectionScheme(String displayName, int order, boolean highResolution, double maximumCourantNumber) {
    this.displayName = displayName;
    this.order = order;
    this.highResolution = highResolution;
    this.maximumCourantNumber = maximumCourantNumber;
  }

  /** @return human-readable method name */
  public String getDisplayName() {
    return displayName;
  }

  /** @return formal order in smooth constant-coefficient regions */
  public int getOrder() {
    return order;
  }

  /** @return true when the scheme uses bounded high-resolution reconstruction */
  public boolean isHighResolution() {
    return highResolution;
  }

  /**
   * Get the maximum local mass Courant number used for an explicit substep.
   *
   * @return finite stability limit for explicit schemes, or positive infinity for the implicit default
   */
  public double getMaximumCourantNumber() {
    return maximumCourantNumber;
  }
}
