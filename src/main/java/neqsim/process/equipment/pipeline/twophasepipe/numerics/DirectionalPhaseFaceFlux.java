package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.Serializable;

/**
 * Immutable directional decomposition of one phase's conservative face transport.
 *
 * <p>
 * Forward and reverse mass flows are nonnegative and satisfy {@code signedMass = forward - reverse}. Advective momentum
 * is assigned to the same directional donor as mass; pressure traction is retained separately. Donor mass and momentum
 * are conservative quantities per unit pipe length. This is the transactional free-flux data required by a later mixed
 * complementarity solve; this value object does not alter or limit transport.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class DirectionalPhaseFaceFlux implements Serializable {
  private static final long serialVersionUID = 1L;
  private final double forwardMassFlowKgPerSecond;
  private final double reverseMassFlowKgPerSecond;
  private final double forwardAdvectiveMomentumN;
  private final double reverseAdvectiveMomentumN;
  private final double pressureTractionN;
  private final double leftDonorMassKgPerM;
  private final double rightDonorMassKgPerM;
  private final double leftDonorMomentumKgPerSecond;
  private final double rightDonorMomentumKgPerSecond;

  /**
   * Build an exact directional representation of a signed face flux.
   *
   * @param signedMassFlowKgPerSecond signed mass flow, positive from left to right
   * @param advectiveMomentumN signed-mass times donor velocity, excluding pressure traction
   * @param pressureTractionN phase pressure traction at the face
   * @param leftDonorMassKgPerM left cell phase mass per length
   * @param rightDonorMassKgPerM right cell phase mass per length
   * @param leftDonorMomentumKgPerSecond left cell phase momentum per length
   * @param rightDonorMomentumKgPerSecond right cell phase momentum per length
   */
  public DirectionalPhaseFaceFlux(double signedMassFlowKgPerSecond, double advectiveMomentumN, double pressureTractionN,
      double leftDonorMassKgPerM, double rightDonorMassKgPerM, double leftDonorMomentumKgPerSecond,
      double rightDonorMomentumKgPerSecond) {
    this(Math.max(0.0, signedMassFlowKgPerSecond), Math.max(0.0, -signedMassFlowKgPerSecond),
        signedMassFlowKgPerSecond >= 0.0 ? advectiveMomentumN : 0.0,
        signedMassFlowKgPerSecond < 0.0 ? advectiveMomentumN : 0.0, pressureTractionN, leftDonorMassKgPerM,
        rightDonorMassKgPerM, leftDonorMomentumKgPerSecond, rightDonorMomentumKgPerSecond);
  }

  /**
   * Build an explicit two-donor directional representation.
   *
   * <p>
   * This overload retains simultaneous counter-transport such as the two one-sided contributions of a Rusanov face. The
   * signed conservative flow remains {@code forward - reverse}; neither donor stream is inferred from that net.
   * </p>
   *
   * @param forwardMassFlowKgPerSecond nonnegative left-to-right mass flow
   * @param reverseMassFlowKgPerSecond nonnegative right-to-left mass flow
   * @param forwardAdvectiveMomentumN left-donor momentum contribution
   * @param reverseAdvectiveMomentumN right-donor momentum contribution with signed face orientation
   * @param pressureTractionN phase pressure traction at the face
   * @param leftDonorMassKgPerM left cell phase mass per length
   * @param rightDonorMassKgPerM right cell phase mass per length
   * @param leftDonorMomentumKgPerSecond left cell phase momentum per length
   * @param rightDonorMomentumKgPerSecond right cell phase momentum per length
   */
  public DirectionalPhaseFaceFlux(double forwardMassFlowKgPerSecond, double reverseMassFlowKgPerSecond,
      double forwardAdvectiveMomentumN, double reverseAdvectiveMomentumN, double pressureTractionN,
      double leftDonorMassKgPerM, double rightDonorMassKgPerM, double leftDonorMomentumKgPerSecond,
      double rightDonorMomentumKgPerSecond) {
    requireFinite(forwardMassFlowKgPerSecond, "forward mass flow");
    requireFinite(reverseMassFlowKgPerSecond, "reverse mass flow");
    requireFinite(forwardAdvectiveMomentumN, "forward advective momentum flux");
    requireFinite(reverseAdvectiveMomentumN, "reverse advective momentum flux");
    requireFinite(pressureTractionN, "pressure traction");
    requireFinite(leftDonorMassKgPerM, "left donor mass");
    requireFinite(rightDonorMassKgPerM, "right donor mass");
    requireFinite(leftDonorMomentumKgPerSecond, "left donor momentum");
    requireFinite(rightDonorMomentumKgPerSecond, "right donor momentum");
    if (forwardMassFlowKgPerSecond < 0.0 || reverseMassFlowKgPerSecond < 0.0 || leftDonorMassKgPerM < 0.0
        || rightDonorMassKgPerM < 0.0) {
      throw new IllegalArgumentException("Directional flows and donor phase masses must be nonnegative");
    }
    this.forwardMassFlowKgPerSecond = forwardMassFlowKgPerSecond;
    this.reverseMassFlowKgPerSecond = reverseMassFlowKgPerSecond;
    this.forwardAdvectiveMomentumN = forwardAdvectiveMomentumN;
    this.reverseAdvectiveMomentumN = reverseAdvectiveMomentumN;
    this.pressureTractionN = pressureTractionN;
    this.leftDonorMassKgPerM = leftDonorMassKgPerM;
    this.rightDonorMassKgPerM = rightDonorMassKgPerM;
    this.leftDonorMomentumKgPerSecond = leftDonorMomentumKgPerSecond;
    this.rightDonorMomentumKgPerSecond = rightDonorMomentumKgPerSecond;
  }

  /** @return nonnegative left-to-right mass flow in kg/s */
  public double getForwardMassFlowKgPerSecond() {
    return forwardMassFlowKgPerSecond;
  }

  /** @return nonnegative right-to-left mass flow in kg/s */
  public double getReverseMassFlowKgPerSecond() {
    return reverseMassFlowKgPerSecond;
  }

  /** @return forward donor advective momentum flux in N */
  public double getForwardAdvectiveMomentumN() {
    return forwardAdvectiveMomentumN;
  }

  /** @return reverse donor advective momentum flux in N */
  public double getReverseAdvectiveMomentumN() {
    return reverseAdvectiveMomentumN;
  }

  /** @return phase pressure traction in N */
  public double getPressureTractionN() {
    return pressureTractionN;
  }

  /** @return signed mass flow in kg/s */
  public double getSignedMassFlowKgPerSecond() {
    return forwardMassFlowKgPerSecond - reverseMassFlowKgPerSecond;
  }

  /** @return total advective momentum flux in N, excluding pressure traction */
  public double getAdvectiveMomentumN() {
    return forwardAdvectiveMomentumN + reverseAdvectiveMomentumN;
  }

  /** @return left donor phase mass per length in kg/m */
  public double getLeftDonorMassKgPerM() {
    return leftDonorMassKgPerM;
  }

  /** @return right donor phase mass per length in kg/m */
  public double getRightDonorMassKgPerM() {
    return rightDonorMassKgPerM;
  }

  /** @return left donor phase momentum per length in kg/s */
  public double getLeftDonorMomentumKgPerSecond() {
    return leftDonorMomentumKgPerSecond;
  }

  /** @return right donor phase momentum per length in kg/s */
  public double getRightDonorMomentumKgPerSecond() {
    return rightDonorMomentumKgPerSecond;
  }

  /**
   * Evaluate the normalized present-left-donor relation without dividing by trace inventory.
   *
   * @return {@code G+ - F+ qL/mL} in N, or {@code G+} when the donor is absent
   */
  public double getForwardDonorResidualN() {
    return leftDonorMassKgPerM == 0.0 ? forwardAdvectiveMomentumN
        : forwardAdvectiveMomentumN - forwardMassFlowKgPerSecond * leftDonorMomentumKgPerSecond / leftDonorMassKgPerM;
  }

  /**
   * Evaluate the normalized present-right-donor relation without dividing by trace inventory.
   *
   * @return {@code G- + F- qR/mR} in N, or {@code G-} when the donor is absent
   */
  public double getReverseDonorResidualN() {
    return rightDonorMassKgPerM == 0.0 ? reverseAdvectiveMomentumN
        : reverseAdvectiveMomentumN + reverseMassFlowKgPerSecond * rightDonorMomentumKgPerSecond / rightDonorMassKgPerM;
  }

  /** Reject nonfinite ledger inputs before they enter a nonlinear residual. */
  private static void requireFinite(double value, String label) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(label + " must be finite");
    }
  }
}
