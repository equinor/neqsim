package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarynode.fluidboundarynonreactivenode;

import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarynode.FluidBoundaryNode;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * FluidBoundaryNodeNonReactive class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FluidBoundaryNodeNonReactive extends FluidBoundaryNode {
  /**
   * <p>
   * Constructor for FluidBoundaryNodeNonReactive.
   * </p>
   */
  public FluidBoundaryNodeNonReactive() {}

  /**
   * <p>
   * Constructor for FluidBoundaryNodeNonReactive.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FluidBoundaryNodeNonReactive(SystemInterface system) {
    super(system);
  }
}
