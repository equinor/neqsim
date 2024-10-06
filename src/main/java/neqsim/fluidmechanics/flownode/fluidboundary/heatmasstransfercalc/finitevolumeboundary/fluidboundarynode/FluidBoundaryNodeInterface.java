/*
 * FluidBoundaryNodeInterface.java
 *
 * Created on 8. august 2001, 14:49
 */

package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarynode;

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
