/*
 * FluidBoundaryNodeInterface.java
 *
 * Created on 8. august 2001, 14:49
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author esol
 * @version
 */
public interface FluidBoundaryNodeInterface {
    public SystemInterface getBulkSystem();
}
