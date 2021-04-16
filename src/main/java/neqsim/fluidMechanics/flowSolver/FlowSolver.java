/*
 * FlowSolver.java
 *
 * Created on 17. januar 2001, 20:58
 */

package neqsim.fluidMechanics.flowSolver;

/**
 *
 * @author Even Solbraa
 * @version
 */
public abstract class FlowSolver extends java.lang.Object implements FlowSolverInterface, java.io.Serializable {

    private static final long serialVersionUID = 1000;

    protected int numberOfVelocityNodes = 0;
    protected int numberOfNodes;
    protected boolean dynamic = false;
    protected double timeStep = 100;
    protected int solverType = 0;

    /** Creates new FlowSolver */
    public FlowSolver() {
    }

    public void solve() {
    }

    public void setDynamic(boolean ans) {
        dynamic = ans;
    }

    public void setSolverType(int type) {
        solverType = type;
    }

    public void setTimeStep(double timeStep) {
        this.timeStep = timeStep;
    }

    public void setBoundarySpecificationType(int type) {
        if (type == 0) {
            numberOfVelocityNodes = numberOfNodes - 2;
        }
    }

    public void solveTDMA() {
    }
}
