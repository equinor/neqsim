package neqsim.process.safety.release;

import java.io.Serializable;
import neqsim.thermo.system.SystemInterface;

/** Immutable SI input for a short-opening release, independent of process and transport layers. */
public final class ReleaseFlowRequest implements Serializable {
  private static final long serialVersionUID = 1L;
  private final SystemInterface fluid;
  private final double diameterM;
  private final double dischargeCoefficient;
  private final double backPressurePa;

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
    if (fluid == null) {
      throw new IllegalArgumentException("Upstream fluid is required");
    }
    positive(diameterM, "diameterM");
    positive(dischargeCoefficient, "dischargeCoefficient");
    positive(backPressurePa, "backPressurePa");
    if (dischargeCoefficient > 1.0 || !Double.isFinite(Math.PI * diameterM * diameterM / 4.0)
        || Math.PI * diameterM * diameterM / 4.0 == 0.0) {
      throw new IllegalArgumentException("Coefficient must be <= 1 and opening area finite and positive");
    }
    this.fluid = fluid.clone();
    this.diameterM = diameterM;
    this.dischargeCoefficient = dischargeCoefficient;
    this.backPressurePa = backPressurePa;
  }

  static double positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
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

  /** @return physical circular area in m2 */
  public double getAreaM2() {
    return Math.PI * diameterM * diameterM / 4.0;
  }

  /** @return discharge coefficient multiplied by physical area, in m2 */
  public double getEffectiveAreaM2() {
    return dischargeCoefficient * getAreaM2();
  }
}
