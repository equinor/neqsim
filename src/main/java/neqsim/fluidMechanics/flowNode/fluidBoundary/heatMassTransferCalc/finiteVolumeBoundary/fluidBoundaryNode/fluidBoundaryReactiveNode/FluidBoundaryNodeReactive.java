/*
 * FluidBoundaryNodeReactive.java
 *
 * Created on 8. august 2001, 14:50
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.fluidBoundaryReactiveNode;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.FluidBoundaryNode;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author esol
 * @version
 */
public class FluidBoundaryNodeReactive extends FluidBoundaryNode {

    private static final long serialVersionUID = 1000;

    /** Creates new FluidBoundaryNodeReactive */
    public FluidBoundaryNodeReactive() {}

    public FluidBoundaryNodeReactive(SystemInterface system) {
        super(system);
    }

}
