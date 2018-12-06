/*
 * FiniteVolumeBoundaryInterface.java
 *
 * Created on 8. august 2001, 13:47
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.FluidBoundaryNodeInterface;

/**
 *
 * @author  esol
 * @version
 */
public interface FluidBoundarySystemInterface {
    public void addBoundary(FluidBoundaryInterface boundary);
    public void setNumberOfNodes(int nodes);
    public int getNumberOfNodes();
    public void createSystem();
    public void setFilmThickness(double filmThickness);
    public double getFilmThickness();
    public FluidBoundaryNodeInterface getNode(int i);
    public FluidBoundaryInterface getFluidBoundary();
    public double getNodeLength();
    public void solve();
}

