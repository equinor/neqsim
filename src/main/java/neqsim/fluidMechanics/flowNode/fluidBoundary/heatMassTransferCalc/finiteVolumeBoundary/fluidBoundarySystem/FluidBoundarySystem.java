/*
 * FiniteVolumeBoundary.java
 *
 * Created on 8. august 2001, 13:46
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.FluidBoundaryNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySolver.FluidBoundarySolver;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySolver.FluidBoundarySolverInterface;

/**
 *
 * @author esol
 * @version
 */
public class FluidBoundarySystem implements FluidBoundarySystemInterface {

    private static final long serialVersionUID = 1000;

    protected FluidBoundaryInterface boundary;
    protected int numberOfNodes = 10;
    protected double filmThickness = 0.01;
    protected FluidBoundaryNodeInterface[] nodes;
    protected boolean reactive = false;
    protected FluidBoundarySolverInterface solver;

    /** Creates new FiniteVolumeBoundary */
    public FluidBoundarySystem() {
    }

    public FluidBoundarySystem(FluidBoundaryInterface boundary) {
        this.boundary = boundary;
        reactive = false;
    }

    @Override
	public void addBoundary(FluidBoundaryInterface boundary) {
        this.boundary = boundary;
    }

    @Override
	public void setNumberOfNodes(int nodes) {
        this.numberOfNodes = nodes;
    }

    @Override
	public int getNumberOfNodes() {
        return numberOfNodes;
    }

    @Override
	public FluidBoundaryNodeInterface getNode(int i) {
        return nodes[i];
    }

    @Override
	public void setFilmThickness(double filmThickness) {
        this.filmThickness = filmThickness;
    }

    @Override
	public double getNodeLength() {
        return this.filmThickness / this.numberOfNodes;
    }

    @Override
	public double getFilmThickness() {
        return filmThickness;
    }

    @Override
	public FluidBoundaryInterface getFluidBoundary() {
        return boundary;
    }

    @Override
	public void createSystem() {
    }

    @Override
	public void solve() {
        solver = new FluidBoundarySolver(this, reactive);
        solver.solve();
    }

}
