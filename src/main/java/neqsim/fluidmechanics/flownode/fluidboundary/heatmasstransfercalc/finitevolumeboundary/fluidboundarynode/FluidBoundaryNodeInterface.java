/*
 * FluidBoundaryNodeInterface.java
 *
 * Created on 8. august 2001, 14:49
 */

package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarynode;

import neqsim.thermo.system.SystemInterface;

/**
 * FluidBoundaryNodeInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface FluidBoundaryNodeInterface {
  /**
   * getBulkSystem.
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getBulkSystem();
}
