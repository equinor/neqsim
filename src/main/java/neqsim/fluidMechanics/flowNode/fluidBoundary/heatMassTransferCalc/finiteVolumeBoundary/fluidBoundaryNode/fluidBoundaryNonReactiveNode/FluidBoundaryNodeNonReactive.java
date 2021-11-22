/*
 * FluidBoundaryNodeReactive.java
 *
 * Created on 8. august 2001, 14:50
 */
package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.fluidBoundaryNonReactiveNode;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.FluidBoundaryNode;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author esol
 * @version
 */
public class FluidBoundaryNodeNonReactive extends FluidBoundaryNode {
    private static final long serialVersionUID = 1000;

    /** Creates new FluidBoundaryNodeReactive */
    public FluidBoundaryNodeNonReactive() {}

    public FluidBoundaryNodeNonReactive(SystemInterface system) {
        super(system);
    }
}
