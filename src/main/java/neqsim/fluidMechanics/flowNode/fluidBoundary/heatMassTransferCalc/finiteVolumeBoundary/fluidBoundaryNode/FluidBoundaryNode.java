/*
 * FluidBoundaryNode.java
 *
 * Created on 8. august 2001, 14:49
 */
package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * FluidBoundaryNode class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FluidBoundaryNode implements FluidBoundaryNodeInterface {
    private static final long serialVersionUID = 1000;
    protected SystemInterface system;

    /**
     * <p>
     * Constructor for FluidBoundaryNode.
     * </p>
     */
    public FluidBoundaryNode() {}

    /**
     * <p>
     * Constructor for FluidBoundaryNode.
     * </p>
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
