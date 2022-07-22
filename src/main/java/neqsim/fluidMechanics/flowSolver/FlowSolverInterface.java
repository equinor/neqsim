/*
 * FlowSolverInterface.java
 *
 * Created on 17. januar 2001, 20:56
 */

package neqsim.fluidMechanics.flowSolver;

/**
 * <p>
 * FlowSolverInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface FlowSolverInterface {
    /**
     * <p>
     * solve.
     * </p>
     */
    public void solve();

    /**
     * <p>
     * setBoundarySpecificationType.
     * </p>
     *
     * @param type a int
     */
    public void setBoundarySpecificationType(int type);

    /**
     * <p>
     * solveTDMA.
     * </p>
     */
    public void solveTDMA();

    /**
     * <p>
     * setDynamic.
     * </p>
     *
     * @param type a boolean
     */
    public void setDynamic(boolean type);

    /**
     * <p>
     * setTimeStep.
     * </p>
     *
     * @param timeStep a double
     */
    public void setTimeStep(double timeStep);

    /**
     * <p>
     * setSolverType.
     * </p>
     *
     * @param type a int
     */
    public void setSolverType(int type);
}
