/*
 * OnePhasePipeFlowSolver.java
 *
 * Created on 17. januar 2001, 21:05
 */

package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;

/**
 * OnePhasePipeFlowSolver class.
 *
 * <p>
 * Protected solution columns use EJML. Serialized solver state from versions using Jama matrix fields is not compatible
 * with this representation.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class OnePhasePipeFlowSolver extends neqsim.fluidmechanics.flowsolver.onephaseflowsolver.OnePhaseFlowSolver {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(OnePhasePipeFlowSolver.class);

  protected double[] PbArray; // = new double[100];
  protected DMatrixRMaj solMatrix;
  protected DMatrixRMaj sol2Matrix;
  protected DMatrixRMaj sol3Matrix;
  protected DMatrixRMaj[] sol4Matrix;
  protected double[] a;
  protected double[] b;
  protected double[] c;
  protected double[] r;
  protected double length;
  protected PipeFlowSystem pipe;

  /**
   * Constructor for OnePhasePipeFlowSolver.
   */
  public OnePhasePipeFlowSolver() {
  }

  /**
   * Constructor for OnePhasePipeFlowSolver.
   *
   * @param pipe a {@link neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem} object
   * @param length a double
   * @param nodes a int
   */
  public OnePhasePipeFlowSolver(PipeFlowSystem pipe, double length, int nodes) {
    this.pipe = pipe;
    this.length = length;
    this.numberOfNodes = nodes;
    PbArray = new double[nodes];
    solMatrix = new DMatrixRMaj(nodes, 1);
    sol2Matrix = new DMatrixRMaj(nodes, 1);
    sol3Matrix = new DMatrixRMaj(nodes, 1);
    sol4Matrix = new DMatrixRMaj[pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()];
    for (int k = 0; k < pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents(); k++) {
      sol4Matrix[k] = new DMatrixRMaj(nodes, 1);
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
      logger.error(ex.getMessage());
    }

    return clonedSystem;
  }
}
