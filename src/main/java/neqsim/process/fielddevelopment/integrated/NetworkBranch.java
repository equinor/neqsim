package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;

/**
 * A directed branch in an integrated production network.
 *
 * <p>
 * A branch connects an upstream node to a downstream node and defines the volumetric flow that passes from the upstream
 * node to the downstream node as a function of the two node pressures. The sign convention is positive when flow goes
 * from the upstream node to the downstream node. The branch hides whatever internal physics it uses (well IPR + VLP,
 * flowline hydraulics, choke, pump/compressor boost) behind a single fast {@link #flow(double, double)} evaluation so
 * that {@link NetworkNewtonSolver} can build a Jacobian cheaply.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see NetworkNewtonSolver
 * @see WellBranch
 * @see FlowlineBranch
 */
public interface NetworkBranch extends Serializable {

  /**
   * Returns the branch name.
   *
   * @return branch name
   */
  String getName();

  /**
   * Returns the upstream node name.
   *
   * @return upstream node name
   */
  String getFromNode();

  /**
   * Returns the downstream node name.
   *
   * @return downstream node name
   */
  String getToNode();

  /**
   * Computes the volumetric flow through the branch.
   *
   * @param upstreamPressureBara upstream node pressure in bara
   * @param downstreamPressureBara downstream node pressure in bara
   * @return volumetric surface flow in Sm3/day, positive from upstream to downstream
   */
  double flow(double upstreamPressureBara, double downstreamPressureBara);
}
