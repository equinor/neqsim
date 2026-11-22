package neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;

/**
 * TwoPhasePipeFlowSolver class.
 *
 * <p>
 * Protected phase and component solution columns use EJML. Serialized solver state from versions using Jama matrix
 * fields is not compatible with this representation.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TwoPhasePipeFlowSolver extends neqsim.fluidmechanics.flowsolver.onephaseflowsolver.OnePhaseFlowSolver {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TwoPhasePipeFlowSolver.class);

  protected double[] PbArray; // = new double[100];
  protected DMatrixRMaj[] solMatrix;
  protected DMatrixRMaj[][] solMolFracMatrix;
  protected DMatrixRMaj[] solPhaseConsMatrix;
  protected DMatrixRMaj sol2Matrix;
  protected DMatrixRMaj[] sol3Matrix;
  protected DMatrixRMaj[] sol4Matrix;

  protected double[] a;
  protected double[] b;
  protected double[] c;
  protected double[] r;
  protected double length;
  protected FlowSystemInterface pipe;
  protected int numberOfNodes;

  /**
   * Constructor for TwoPhasePipeFlowSolver.
   */
  public TwoPhasePipeFlowSolver() {
  }

  /**
   * Constructor for TwoPhasePipeFlowSolver.
   *
   * @param pipe a {@link neqsim.fluidmechanics.flowsystem.FlowSystemInterface} object
   * @param length a double
   * @param nodes a int
   */
  public TwoPhasePipeFlowSolver(FlowSystemInterface pipe, double length, int nodes) {
    this.pipe = pipe;
    this.length = length;
    this.numberOfNodes = nodes;
    PbArray = new double[nodes];
    solMatrix = new DMatrixRMaj[2];
    sol3Matrix = new DMatrixRMaj[2];
    solPhaseConsMatrix = new DMatrixRMaj[2];
    solMolFracMatrix = new DMatrixRMaj[2][pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()];
    solMatrix[0] = new DMatrixRMaj(nodes, 1);
    solMatrix[1] = new DMatrixRMaj(nodes, 1);
    for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
      for (int i = 0; i < pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
        solMolFracMatrix[phaseNum][i] = new DMatrixRMaj(nodes, 1);
      }
    }
    sol3Matrix[0] = new DMatrixRMaj(nodes, 1);
    sol3Matrix[1] = new DMatrixRMaj(nodes, 1);
    solPhaseConsMatrix[0] = new DMatrixRMaj(nodes, 1);
    solPhaseConsMatrix[1] = new DMatrixRMaj(nodes, 1);
    sol2Matrix = new DMatrixRMaj(nodes, 1);
    a = new double[nodes];
    b = new double[nodes];
    c = new double[nodes];
    r = new double[nodes];
  }

  /** {@inheritDoc} */
  @Override
  public TwoPhasePipeFlowSolver clone() {
    TwoPhasePipeFlowSolver clonedSystem = null;
    try {
      clonedSystem = (TwoPhasePipeFlowSolver) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    return clonedSystem;
  }
}
