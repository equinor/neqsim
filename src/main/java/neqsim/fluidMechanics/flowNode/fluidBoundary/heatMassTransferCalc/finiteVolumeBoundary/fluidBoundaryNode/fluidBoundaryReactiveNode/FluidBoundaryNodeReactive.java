/*
 * FluidBoundaryNodeReactive.java
 *
 * Created on 8. august 2001, 14:50
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.fluidBoundaryReactiveNode;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.FluidBoundaryNode;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>FluidBoundaryNodeReactive class.</p>
 *
 * @author esol
 */
public class FluidBoundaryNodeReactive extends FluidBoundaryNode {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new FluidBoundaryNodeReactive
     */
    public FluidBoundaryNodeReactive() {}

    /**
     * <p>Constructor for FluidBoundaryNodeReactive.</p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public FluidBoundaryNodeReactive(SystemInterface system) {
        super(system);
    }

}
