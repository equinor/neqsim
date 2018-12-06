/*
 * FlowSolverInterface.java
 *
 * Created on 17. januar 2001, 20:56
 */

package neqsim.fluidMechanics.flowSolver;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public interface FlowSolverInterface {
    public void solve();
    public void setBoundarySpecificationType(int type);
    public void solveTDMA();
    public void setDynamic(boolean type);
    public void setTimeStep(double timeStep);
    public void setSolverType(int type);
}

