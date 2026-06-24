/*
 * FlowSolverInterface.java
 *
 * Created on 17. januar 2001, 20:56
 */

package neqsim.fluidmechanics.flowsolver;

/**
 * FlowSolverInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface FlowSolverInterface {
  /**
   * solve.
   */
  public void solve();

  /**
   * setBoundarySpecificationType.
   *
   * @param type a int
   */
  public void setBoundarySpecificationType(int type);

  /**
   * solveTDMA.
   */
  public void solveTDMA();

  /**
   * setDynamic.
   *
   * @param type a boolean
   */
  public void setDynamic(boolean type);

  /**
   * setTimeStep.
   *
   * @param timeStep a double
   */
  public void setTimeStep(double timeStep);

  /**
   * setSolverType.
   *
   * @param type a int
   */
  public void setSolverType(int type);
}
