/*
 * FluidBoundaryNodeReactive.java
 *
 * Created on 8. august 2001, 14:50
 */
package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.fluidBoundaryNonReactiveNode;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.FluidBoundaryNode;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * FluidBoundaryNodeNonReactive class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FluidBoundaryNodeNonReactive extends FluidBoundaryNode {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for FluidBoundaryNodeNonReactive.
     * </p>
     */
    public FluidBoundaryNodeNonReactive() {}

    /**
     * <p>
     * Constructor for FluidBoundaryNodeNonReactive.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public FluidBoundaryNodeNonReactive(SystemInterface system) {
        super(system);
    }
}
