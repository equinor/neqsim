package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarynode.fluidboundarynonreactivenode;

import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarynode.FluidBoundaryNode;
import neqsim.thermo.system.SystemInterface;

/**
 * FluidBoundaryNodeNonReactive class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class FluidBoundaryNodeNonReactive extends FluidBoundaryNode {
  /**
   * Constructor for FluidBoundaryNodeNonReactive.
   */
  public FluidBoundaryNodeNonReactive() {
  }

  /**
   * Constructor for FluidBoundaryNodeNonReactive.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FluidBoundaryNodeNonReactive(SystemInterface system) {
    super(system);
  }
}
