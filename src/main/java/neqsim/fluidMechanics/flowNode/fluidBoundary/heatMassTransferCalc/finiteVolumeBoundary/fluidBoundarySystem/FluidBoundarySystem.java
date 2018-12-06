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
 * @author  esol
 * @version
 */
public class FluidBoundarySystem implements FluidBoundarySystemInterface{

    private static final long serialVersionUID = 1000;
    
    protected FluidBoundaryInterface boundary;
    protected int numberOfNodes=10;
    protected double filmThickness=0.01;
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
    
    public void addBoundary(FluidBoundaryInterface boundary) {
        this.boundary = boundary;
    }
    
    public void setNumberOfNodes(int nodes){
        this.numberOfNodes = nodes;
    }
    
    public int getNumberOfNodes(){
        return numberOfNodes;
    }
    
    public FluidBoundaryNodeInterface getNode(int i){
        return nodes[i];
    }
    
    public void setFilmThickness(double filmThickness){
        this.filmThickness = filmThickness;
    }
    
    public double getNodeLength(){
        return this.filmThickness/this.numberOfNodes;
    }
    
    public double getFilmThickness(){
        return filmThickness;
    }
    
    public FluidBoundaryInterface getFluidBoundary(){
        return boundary;
    }
    
    public void createSystem(){
    }
    
    
    public void solve(){
        solver = new FluidBoundarySolver(this, reactive);
        solver.solve();
    }
    
}
