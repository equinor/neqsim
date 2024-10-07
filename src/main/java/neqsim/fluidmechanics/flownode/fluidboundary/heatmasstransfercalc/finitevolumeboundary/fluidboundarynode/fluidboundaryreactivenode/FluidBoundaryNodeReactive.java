/*
 * FluidBoundaryNodeReactive.java
 *
 * Created on 8. august 2001, 14:50
 */

package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarynode.fluidboundaryreactivenode;

import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarynode.FluidBoundaryNode;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * FluidBoundaryNodeReactive class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FluidBoundaryNodeReactive extends FluidBoundaryNode {
  /**
   * <p>
   * Constructor for FluidBoundaryNodeReactive.
   * </p>
   */
  public FluidBoundaryNodeReactive() {}

  /**
   * <p>
   * Constructor for FluidBoundaryNodeReactive.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FluidBoundaryNodeReactive(SystemInterface system) {
    super(system);
  }
}
