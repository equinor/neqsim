package neqsim.process.equipment.distillation.internals;

import java.io.Serializable;

/**
 * Gravity-drainage criterion for a column or vessel that drains liquid downwards against a gas back-pressure.
 *
 * <p>
 * A packed stripper, a seal leg or a boot that drains by gravity into a downstream vessel can only do so while the
 * available liquid static head exceeds the gas-side pressure difference it has to push against. When a bed fouls, the
 * gas-side pressure drop rises; once it exceeds &rho;<sub>L</sub>&nbsp;g&nbsp;h the liquid stops draining, backs up and
 * floods the section above. The gas flow then has to be stopped to let the liquid down, which is the operating symptom
 * this class quantifies.
 * </p>
 *
 * <p>
 * The criterion is a static one. It gives the drainage limit and the margin to it; it does not describe the transient
 * of the back-up, any frictional loss in the drain line itself, or two-phase effects in the drain leg. Those reduce the
 * usable head further, so the criterion is optimistic with respect to drainage.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class GravityDrainageMargin implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Standard acceleration of gravity [m/s2]. */
  public static final double STANDARD_GRAVITY = 9.80665;

  /** Liquid density [kg/m3]. */
  private final double liquidDensity;

  /** Available static liquid head [m]. */
  private final double availableStaticHead;

  /** Gas-side pressure difference opposing drainage [Pa]. */
  private final double gasPressureDrop;

  /**
   * Create a gravity-drainage criterion.
   *
   * @param liquidDensity liquid density [kg/m3], must be finite and positive
   * @param availableStaticHead static liquid head available to drive drainage [m], must be finite and non-negative
   * @param gasPressureDrop gas-side pressure difference the liquid must drain against [Pa], must be finite and
   * non-negative
   * @throws IllegalArgumentException if any argument is not finite or outside its valid range
   */
  public GravityDrainageMargin(double liquidDensity, double availableStaticHead, double gasPressureDrop) {
    if (!isFinite(liquidDensity) || liquidDensity <= 0.0) {
      throw new IllegalArgumentException("liquidDensity must be finite and positive");
    }
    if (!isFinite(availableStaticHead) || availableStaticHead < 0.0) {
      throw new IllegalArgumentException("availableStaticHead must be finite and non-negative");
    }
    if (!isFinite(gasPressureDrop) || gasPressureDrop < 0.0) {
      throw new IllegalArgumentException("gasPressureDrop must be finite and non-negative");
    }
    this.liquidDensity = liquidDensity;
    this.availableStaticHead = availableStaticHead;
    this.gasPressureDrop = gasPressureDrop;
  }

  /**
   * Get the liquid density.
   *
   * @return liquid density [kg/m3]
   */
  public double getLiquidDensity() {
    return liquidDensity;
  }

  /**
   * Get the available static liquid head.
   *
   * @return available static head [m]
   */
  public double getAvailableStaticHead() {
    return availableStaticHead;
  }

  /**
   * Get the gas-side pressure difference opposing drainage.
   *
   * @return gas-side pressure drop [Pa]
   */
  public double getGasPressureDrop() {
    return gasPressureDrop;
  }

  /**
   * Get the pressure equivalent of the available static head.
   *
   * @return available head pressure [Pa]
   */
  public double getAvailableHeadPressure() {
    return criticalPressureDrop(liquidDensity, availableStaticHead);
  }

  /**
   * Get the pressure margin to the drainage limit.
   *
   * <p>
   * Positive values mean the liquid still drains.
   * </p>
   *
   * @return available head pressure minus the gas-side pressure drop [Pa]
   */
  public double getMarginPressure() {
    return getAvailableHeadPressure() - gasPressureDrop;
  }

  /**
   * Get the ratio of the available head pressure to the gas-side pressure drop.
   *
   * @return available head pressure divided by the gas-side pressure drop [-], or {@link Double#POSITIVE_INFINITY} when
   * there is no gas-side pressure drop
   */
  public double getMarginRatio() {
    if (gasPressureDrop <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    return getAvailableHeadPressure() / gasPressureDrop;
  }

  /**
   * Get the fraction of the available head that the gas-side pressure drop already consumes.
   *
   * @return gas-side pressure drop divided by the available head pressure [-], or {@link Double#POSITIVE_INFINITY} when
   * no head is available
   */
  public double getHeadUtilisation() {
    double available = getAvailableHeadPressure();
    if (available <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    return gasPressureDrop / available;
  }

  /**
   * Report whether the liquid can still drain by gravity.
   *
   * @return true when the available head pressure is at least the gas-side pressure drop
   */
  public boolean canDrain() {
    return getAvailableHeadPressure() >= gasPressureDrop;
  }

  /**
   * Get the static head that the present gas-side pressure drop would require.
   *
   * @return required static head [m]
   */
  public double getRequiredStaticHead() {
    return criticalStaticHead(liquidDensity, gasPressureDrop);
  }

  /**
   * Get the largest gas-side pressure drop the present head can drain against.
   *
   * @return maximum allowable gas-side pressure drop [Pa]
   */
  public double getMaximumAllowableGasPressureDrop() {
    return getAvailableHeadPressure();
  }

  /**
   * Pressure equivalent of a static liquid head.
   *
   * @param liquidDensity liquid density [kg/m3], must be finite and positive
   * @param staticHead static liquid head [m], must be finite and non-negative
   * @return head pressure [Pa]
   * @throws IllegalArgumentException if an argument is not finite or outside its valid range
   */
  public static double criticalPressureDrop(double liquidDensity, double staticHead) {
    if (!isFinite(liquidDensity) || liquidDensity <= 0.0) {
      throw new IllegalArgumentException("liquidDensity must be finite and positive");
    }
    if (!isFinite(staticHead) || staticHead < 0.0) {
      throw new IllegalArgumentException("staticHead must be finite and non-negative");
    }
    return liquidDensity * STANDARD_GRAVITY * staticHead;
  }

  /**
   * Static liquid head required to drain against a gas-side pressure drop.
   *
   * @param liquidDensity liquid density [kg/m3], must be finite and positive
   * @param gasPressureDrop gas-side pressure drop [Pa], must be finite and non-negative
   * @return required static head [m]
   * @throws IllegalArgumentException if an argument is not finite or outside its valid range
   */
  public static double criticalStaticHead(double liquidDensity, double gasPressureDrop) {
    if (!isFinite(liquidDensity) || liquidDensity <= 0.0) {
      throw new IllegalArgumentException("liquidDensity must be finite and positive");
    }
    if (!isFinite(gasPressureDrop) || gasPressureDrop < 0.0) {
      throw new IllegalArgumentException("gasPressureDrop must be finite and non-negative");
    }
    return gasPressureDrop / (liquidDensity * STANDARD_GRAVITY);
  }

  /**
   * Java 8 compatible finiteness test.
   *
   * @param value value to check
   * @return true when the value is neither NaN nor infinite
   */
  private static boolean isFinite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "GravityDrainageMargin[head=" + availableStaticHead + " m, dP=" + gasPressureDrop + " Pa, canDrain="
        + canDrain() + "]";
  }
}
