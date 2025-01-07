/*
 * FlowSolver.java
 *
 * Created on 17. januar 2001, 20:58
 */

package neqsim.fluidmechanics.flowsolver;

/**
 * <p>
 * Abstract FlowSolver class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class FlowSolver implements FlowSolverInterface, java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected int numberOfVelocityNodes = 0;
  protected int numberOfNodes;
  protected boolean dynamic = false;
  protected double timeStep = 100;
  protected int solverType = 0;

  /**
   * <p>
   * Constructor for FlowSolver.
   * </p>
   */
  public FlowSolver() {}

  /** {@inheritDoc} */
  @Override
  public void solve() {}

  /** {@inheritDoc} */
  @Override
  public void setDynamic(boolean ans) {
    dynamic = ans;
  }

  /** {@inheritDoc} */
  @Override
  public void setSolverType(int type) {
    solverType = type;
  }

  /** {@inheritDoc} */
  @Override
  public void setTimeStep(double timeStep) {
    this.timeStep = timeStep;
  }

  /** {@inheritDoc} */
  @Override
  public void setBoundarySpecificationType(int type) {
    if (type == 0) {
      numberOfVelocityNodes = numberOfNodes - 2;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void solveTDMA() {}
}
