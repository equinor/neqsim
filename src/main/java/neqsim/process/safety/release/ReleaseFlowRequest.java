package neqsim.process.safety.release;

import java.io.Serializable;
import neqsim.thermo.system.SystemInterface;

/** Immutable SI input for a release, independent of process orchestration and transport layers. */
public final class ReleaseFlowRequest implements Serializable {
  private static final long serialVersionUID = 1L;
  private final SystemInterface fluid;
  private final double diameterM;
  private final double dischargeCoefficient;
  private final double backPressurePa;
  private final double flowPathLengthM;
  private final double darcyFrictionFactor;

  /**
   * Creates a request with a defensive fluid copy.
   *
   * @param fluid upstream stagnation state; amount sets composition, not the release rate
   * @param diameterM circular opening diameter in m
   * @param dischargeCoefficient effective-area factor in (0, 1]
   * @param backPressurePa receiving absolute pressure in Pa
   * @throws IllegalArgumentException for absent fluid or invalid geometry/boundary conditions
   */
  public ReleaseFlowRequest(SystemInterface fluid, double diameterM, double dischargeCoefficient,
      double backPressurePa) {
    this(fluid, diameterM, dischargeCoefficient, backPressurePa, 0.0, 0.0);
  }

  /**
   * Creates a request for a constant-area pipe release with a defensive fluid copy.
   *
   * @param fluid upstream stagnation state; amount sets composition, not the release rate
   * @param diameterM pipe internal diameter in m
   * @param dischargeCoefficient effective full-bore area factor in (0, 1]
   * @param backPressurePa receiving absolute pressure in Pa
   * @param flowPathLengthM pipe length from inventory boundary to release plane in m
   * @param darcyFrictionFactor specified Darcy friction factor, dimensionless
   * @throws IllegalArgumentException for absent fluid or invalid geometry/boundary conditions
   */
  public ReleaseFlowRequest(SystemInterface fluid, double diameterM, double dischargeCoefficient, double backPressurePa,
      double flowPathLengthM, double darcyFrictionFactor) {
    if (fluid == null) {
      throw new IllegalArgumentException("Upstream fluid is required");
    }
    positive(diameterM, "diameterM");
    positive(dischargeCoefficient, "dischargeCoefficient");
    positive(backPressurePa, "backPressurePa");
    nonnegative(flowPathLengthM, "flowPathLengthM");
    nonnegative(darcyFrictionFactor, "darcyFrictionFactor");
    if ((flowPathLengthM == 0.0) != (darcyFrictionFactor == 0.0)) {
      throw new IllegalArgumentException("Pipe length and Darcy friction factor must both be zero or positive");
    }
    if (darcyFrictionFactor > 1.0) {
      throw new IllegalArgumentException("Darcy friction factor must be <= 1");
    }
    if (dischargeCoefficient > 1.0 || !Double.isFinite(Math.PI * diameterM * diameterM / 4.0)
        || Math.PI * diameterM * diameterM / 4.0 == 0.0) {
      throw new IllegalArgumentException("Coefficient must be <= 1 and opening area finite and positive");
    }
    this.fluid = fluid.clone();
    this.diameterM = diameterM;
    this.dischargeCoefficient = dischargeCoefficient;
    this.backPressurePa = backPressurePa;
    this.flowPathLengthM = flowPathLengthM;
    this.darcyFrictionFactor = darcyFrictionFactor;
  }

  static double positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
    return value;
  }

  static double nonnegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
    return value;
  }

  /** @return independent upstream fluid copy */
  public SystemInterface getFluid() {
    return fluid.clone();
  }

  /** @return opening diameter in m */
  public double getDiameterM() {
    return diameterM;
  }

  /** @return dimensionless effective-area factor */
  public double getDischargeCoefficient() {
    return dischargeCoefficient;
  }

  /** @return receiving absolute pressure in Pa */
  public double getBackPressurePa() {
    return backPressurePa;
  }

  /** @return true when this request defines a finite constant-area pipe path */
  public boolean hasFlowPath() {
    return flowPathLengthM > 0.0;
  }

  /** @return pipe flow-path length in m, or zero for a short opening */
  public double getFlowPathLengthM() {
    return flowPathLengthM;
  }

  /** @return specified Darcy friction factor, or zero for a short opening */
  public double getDarcyFrictionFactor() {
    return darcyFrictionFactor;
  }

  void requireShortOpening() {
    if (hasFlowPath()) {
      throw new UnsupportedOperationException("Short-opening model cannot ignore configured pipe geometry");
    }
  }

  /** @return physical circular area in m2 */
  public double getAreaM2() {
    return Math.PI * diameterM * diameterM / 4.0;
  }

  /** @return discharge coefficient multiplied by physical area, in m2 */
  public double getEffectiveAreaM2() {
    return dischargeCoefficient * getAreaM2();
  }
}
