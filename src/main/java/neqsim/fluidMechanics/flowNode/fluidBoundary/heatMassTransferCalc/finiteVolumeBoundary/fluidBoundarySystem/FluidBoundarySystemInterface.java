/*
 * FiniteVolumeBoundaryInterface.java
 *
 * Created on 8. august 2001, 13:47
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.FluidBoundaryNodeInterface;

/**
 * <p>FluidBoundarySystemInterface interface.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface FluidBoundarySystemInterface {
    /**
     * <p>addBoundary.</p>
     *
     * @param boundary a {@link neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface} object
     */
    public void addBoundary(FluidBoundaryInterface boundary);

    /**
     * <p>setNumberOfNodes.</p>
     *
     * @param nodes a int
     */
    public void setNumberOfNodes(int nodes);

    /**
     * <p>getNumberOfNodes.</p>
     *
     * @return a int
     */
    public int getNumberOfNodes();

    /**
     * <p>createSystem.</p>
     */
    public void createSystem();

    /**
     * <p>setFilmThickness.</p>
     *
     * @param filmThickness a double
     */
    public void setFilmThickness(double filmThickness);

    /**
     * <p>getFilmThickness.</p>
     *
     * @return a double
     */
    public double getFilmThickness();

    /**
     * <p>getNode.</p>
     *
     * @param i a int
     * @return a {@link neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.FluidBoundaryNodeInterface} object
     */
    public FluidBoundaryNodeInterface getNode(int i);

    /**
     * <p>getFluidBoundary.</p>
     *
     * @return a {@link neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface} object
     */
    public FluidBoundaryInterface getFluidBoundary();

    /**
     * <p>getNodeLength.</p>
     *
     * @return a double
     */
    public double getNodeLength();

    /**
     * <p>solve.</p>
     */
    public void solve();
}
