/*
 * FluidBoundaryNode.java
 *
 * Created on 8. august 2001, 14:49
 */

package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarynode;

import neqsim.thermo.system.SystemInterface;

/**
 * FluidBoundaryNode class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class FluidBoundaryNode implements FluidBoundaryNodeInterface {
  protected SystemInterface system;

  /**
   * Constructor for FluidBoundaryNode.
   */
  public FluidBoundaryNode() {
  }

  /**
   * Constructor for FluidBoundaryNode.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FluidBoundaryNode(SystemInterface system) {
    this.system = system.clone();
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getBulkSystem() {
    return system;
  }
}
