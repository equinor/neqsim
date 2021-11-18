

/*
 * FluidBoundaryNode.java
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
public class FluidBoundaryNode implements FluidBoundaryNodeInterface {
    private static final long serialVersionUID = 1000;
    protected SystemInterface system;

    /** Creates new FluidBoundaryNode */
    public FluidBoundaryNode() {}

    public FluidBoundaryNode(SystemInterface system) {
        this.system = (SystemInterface) system.clone();
    }

    @Override
    public SystemInterface getBulkSystem() {
        return system;
    }
}
