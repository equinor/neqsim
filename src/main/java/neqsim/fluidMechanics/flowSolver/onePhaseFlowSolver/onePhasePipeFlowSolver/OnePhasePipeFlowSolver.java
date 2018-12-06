/*
 * OnePhasePipeFlowSolver.java
 *
 * Created on 17. januar 2001, 21:05
 */

package neqsim.fluidMechanics.flowSolver.onePhaseFlowSolver.onePhasePipeFlowSolver;


import Jama.*;
import neqsim.fluidMechanics.flowSystem.onePhaseFlowSystem.pipeFlowSystem.PipeFlowSystem;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class OnePhasePipeFlowSolver extends neqsim.fluidMechanics.flowSolver.onePhaseFlowSolver.OnePhaseFlowSolver {

    private static final long serialVersionUID = 1000;
    
    
    protected double[] PbArray; // = new double[100];
    protected Matrix solMatrix;
    protected Matrix sol2Matrix;
    protected Matrix sol3Matrix;
    protected Matrix[] sol4Matrix;
    protected double a[];
    protected double b[];
    protected double c[];
    protected double r[];
    protected double length;
    protected PipeFlowSystem pipe;
    
    
    
    
    /** Creates new OnePhasePipeFlowSolver */
    public OnePhasePipeFlowSolver() {
    }
    
    public OnePhasePipeFlowSolver(PipeFlowSystem pipe, double length, int nodes) {
        this.pipe = pipe;
        this.length = length;
        this.numberOfNodes=nodes;
        PbArray = new double[nodes];
        solMatrix = new Matrix(PbArray,1).transpose();
        sol2Matrix = new Matrix(PbArray,1).transpose();
        sol3Matrix = new Matrix(PbArray,1).transpose();
        sol4Matrix = new Matrix[pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()];
        for(int k=0;k<pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents();k++){
            sol4Matrix[k] = new Matrix(PbArray,1).transpose();
        }
        a = new double[nodes];
        b = new double[nodes];
        c = new double[nodes];
        r = new double[nodes];
    }
    
    public Object clone(){
        OnePhasePipeFlowSolver clonedSystem = null;
        try{
            clonedSystem = (OnePhasePipeFlowSolver) super.clone();
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
        }
        
        return clonedSystem;
    }
    
    
    
}
