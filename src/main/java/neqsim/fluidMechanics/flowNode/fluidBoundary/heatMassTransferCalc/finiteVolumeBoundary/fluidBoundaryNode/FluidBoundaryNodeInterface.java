/*
 * FluidBoundaryNodeInterface.java
 *
 * Created on 8. august 2001, 14:49
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * FluidBoundaryNodeInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface FluidBoundaryNodeInterface {
  /**
   * <p>
   * getBulkSystem.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getBulkSystem();
}
