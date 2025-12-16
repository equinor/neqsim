package neqsim.fluidmechanics.flowsolver;

/**
 * Advection schemes for compositional tracking in pipeline transient simulations.
 *
 * <p>
 * Different schemes offer trade-offs between accuracy, stability, and computational cost.
 * Higher-order schemes reduce numerical dispersion but may introduce oscillations near
 * discontinuities unless combined with flux limiters (TVD schemes).
 * </p>
 *
 * <h2>Numerical Dispersion</h2>
 * <p>
 * For first-order upwind: D_num = (v × Δx / 2) × (1 - CFL)
 * </p>
 * <p>
 * Higher-order schemes can reduce this by 5-10× while maintaining monotonicity.
 * </p>
 *
 * @author ESOL
 */
public enum AdvectionScheme {

  /**
   * First-order upwind scheme (default).
   *
   * <p>
   * Properties:
   * </p>
   * <ul>
   * <li>Unconditionally stable</li>
   * <li>First-order accurate</li>
   * <li>High numerical dispersion: D_num = (v×Δx/2)×(1-CFL)</li>
   * <li>Never oscillates (monotone)</li>
   * </ul>
   */
  FIRST_ORDER_UPWIND("First-Order Upwind", 1, 1.0),

  /**
   * Second-order upwind scheme (Linear Upwind Differencing - LUD).
   *
   * <p>
   * Uses two upstream points for higher accuracy. Properties:
   * </p>
   * <ul>
   * <li>Stable for CFL ≤ 1</li>
   * <li>Second-order accurate</li>
   * <li>Much less numerical dispersion</li>
   * <li>May oscillate near discontinuities</li>
   * </ul>
   */
  SECOND_ORDER_UPWIND("Second-Order Upwind", 2, 0.5),

  /**
   * QUICK scheme (Quadratic Upstream Interpolation for Convective Kinematics).
   *
   * <p>
   * Uses quadratic interpolation with upstream bias. Properties:
   * </p>
   * <ul>
   * <li>Stable for CFL ≤ 0.5-1.0</li>
   * <li>Third-order accurate on uniform grids</li>
   * <li>Very low numerical dispersion</li>
   * <li>May produce overshoots/undershoots</li>
   * </ul>
   */
  QUICK("QUICK", 3, 0.5),

  /**
   * TVD with Van Leer flux limiter.
   *
   * <p>
   * Total Variation Diminishing scheme with Van Leer limiter. Properties:
   * </p>
   * <ul>
   * <li>Stable for CFL ≤ 1</li>
   * <li>Second-order in smooth regions</li>
   * <li>Reduces to first-order near discontinuities</li>
   * <li>Monotone (no new extrema)</li>
   * <li>Best balance of accuracy and robustness</li>
   * </ul>
   */
  TVD_VAN_LEER("TVD Van Leer", 2, 1.0),

  /**
   * TVD with Minmod flux limiter.
   *
   * <p>
   * Most diffusive of the TVD schemes. Properties:
   * </p>
   * <ul>
   * <li>Very stable, never oscillates</li>
   * <li>Second-order in smooth regions</li>
   * <li>More diffusive than Van Leer</li>
   * <li>Good for problems with strong shocks</li>
   * </ul>
   */
  TVD_MINMOD("TVD Minmod", 2, 1.0),

  /**
   * TVD with Superbee flux limiter.
   *
   * <p>
   * Least diffusive of the TVD schemes. Properties:
   * </p>
   * <ul>
   * <li>Most accurate for contacts/discontinuities</li>
   * <li>May be too compressive for smooth solutions</li>
   * <li>Best for tracking sharp composition fronts</li>
   * </ul>
   */
  TVD_SUPERBEE("TVD Superbee", 2, 1.0),

  /**
   * TVD with Van Albada flux limiter.
   *
   * <p>
   * Smooth limiter, good for general use. Properties:
   * </p>
   * <ul>
   * <li>Differentiable (good for optimization/adjoints)</li>
   * <li>Less aggressive than Superbee</li>
   * <li>Good balance between Van Leer and Minmod</li>
   * </ul>
   */
  TVD_VAN_ALBADA("TVD Van Albada", 2, 1.0),

  /**
   * MUSCL with Van Leer limiter.
   *
   * <p>
   * Monotone Upstream-centered Schemes for Conservation Laws. Properties:
   * </p>
   * <ul>
   * <li>Very popular in CFD</li>
   * <li>Second-order accurate</li>
   * <li>Good for composition tracking</li>
   * </ul>
   */
  MUSCL_VAN_LEER("MUSCL Van Leer", 2, 1.0);

  private final String displayName;
  private final int order;
  private final double maxCFL;

  AdvectionScheme(String displayName, int order, double maxCFL) {
    this.displayName = displayName;
    this.order = order;
    this.maxCFL = maxCFL;
  }

  /**
   * Get the display name of the scheme.
   *
   * @return human-readable name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Get the formal order of accuracy.
   *
   * @return order of accuracy (1, 2, or 3)
   */
  public int getOrder() {
    return order;
  }

  /**
   * Get the maximum recommended CFL number for stability.
   *
   * @return maximum CFL number
   */
  public double getMaxCFL() {
    return maxCFL;
  }

  /**
   * Check if this scheme uses a flux limiter.
   *
   * @return true if TVD or MUSCL scheme
   */
  public boolean usesTVD() {
    return this.name().startsWith("TVD_") || this.name().startsWith("MUSCL_");
  }

  /**
   * Estimate the numerical dispersion reduction factor compared to first-order upwind.
   * 
   * <p>
   * This is approximate and depends on the solution smoothness and CFL number.
   * </p>
   *
   * @return estimated reduction factor (e.g., 0.2 means 5× less dispersion)
   */
  public double getDispersionReductionFactor() {
    switch (this) {
      case FIRST_ORDER_UPWIND:
        return 1.0;
      case SECOND_ORDER_UPWIND:
        return 0.1; // ~10× reduction
      case QUICK:
        return 0.05; // ~20× reduction
      case TVD_MINMOD:
        return 0.3; // ~3× reduction (most diffusive TVD)
      case TVD_VAN_LEER:
        return 0.15; // ~7× reduction
      case TVD_VAN_ALBADA:
        return 0.2; // ~5× reduction
      case TVD_SUPERBEE:
        return 0.08; // ~12× reduction (least diffusive TVD)
      case MUSCL_VAN_LEER:
        return 0.15; // ~7× reduction
      default:
        return 1.0;
    }
  }

  @Override
  public String toString() {
    return displayName;
  }
}
