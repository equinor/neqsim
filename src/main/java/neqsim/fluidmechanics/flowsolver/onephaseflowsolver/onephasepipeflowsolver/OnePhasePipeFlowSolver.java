/*
 * OnePhasePipeFlowSolver.java
 *
 * Created on 17. januar 2001, 21:05
 */

package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;

/**
 * <p>
 * OnePhasePipeFlowSolver class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class OnePhasePipeFlowSolver
    extends neqsim.fluidmechanics.flowsolver.onephaseflowsolver.OnePhaseFlowSolver {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(OnePhasePipeFlowSolver.class);

  protected double[] PbArray; // = new double[100];
  protected Matrix solMatrix;
  protected Matrix sol2Matrix;
  protected Matrix sol3Matrix;
  protected Matrix[] sol4Matrix;
  protected double[] a;
  protected double[] b;
  protected double[] c;
  protected double[] r;
  protected double length;
  protected PipeFlowSystem pipe;

  /**
   * <p>
   * Constructor for OnePhasePipeFlowSolver.
   * </p>
   */
  public OnePhasePipeFlowSolver() {}

  /**
   * <p>
   * Constructor for OnePhasePipeFlowSolver.
   * </p>
   *
   * @param pipe a
   *        {@link neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem}
   *        object
   * @param length a double
   * @param nodes a int
   */
  public OnePhasePipeFlowSolver(PipeFlowSystem pipe, double length, int nodes) {
    this.pipe = pipe;
    this.length = length;
    this.numberOfNodes = nodes;
    PbArray = new double[nodes];
    solMatrix = new Matrix(PbArray, 1).transpose();
    sol2Matrix = new Matrix(PbArray, 1).transpose();
    sol3Matrix = new Matrix(PbArray, 1).transpose();
    sol4Matrix = new Matrix[pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()];
    for (int k = 0; k < pipe.getNode(0).getBulkSystem().getPhases()[0]
        .getNumberOfComponents(); k++) {
      sol4Matrix[k] = new Matrix(PbArray, 1).transpose();
    }
    a = new double[nodes];
    b = new double[nodes];
    c = new double[nodes];
    r = new double[nodes];
  }

  /** {@inheritDoc} */
  @Override
  public OnePhasePipeFlowSolver clone() {
    OnePhasePipeFlowSolver clonedSystem = null;
    try {
      clonedSystem = (OnePhasePipeFlowSolver) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());;
    }

    return clonedSystem;
  }
}
