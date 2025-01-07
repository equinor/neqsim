package neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;

/**
 * <p>
 * TwoPhasePipeFlowSolver class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TwoPhasePipeFlowSolver
    extends neqsim.fluidmechanics.flowsolver.onephaseflowsolver.OnePhaseFlowSolver {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TwoPhasePipeFlowSolver.class);

  protected double[] PbArray; // = new double[100];
  protected Matrix[] solMatrix;
  protected Matrix[][] solMolFracMatrix;
  protected Matrix[] solPhaseConsMatrix;
  protected Matrix sol2Matrix;
  protected Matrix[] sol3Matrix;
  protected Matrix[] sol4Matrix;

  protected double[] a;
  protected double[] b;
  protected double[] c;
  protected double[] r;
  protected double length;
  protected FlowSystemInterface pipe;
  protected int numberOfNodes;

  /**
   * <p>
   * Constructor for TwoPhasePipeFlowSolver.
   * </p>
   */
  public TwoPhasePipeFlowSolver() {}

  /**
   * <p>
   * Constructor for TwoPhasePipeFlowSolver.
   * </p>
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
    solMatrix = new Matrix[2];
    sol3Matrix = new Matrix[2];
    solPhaseConsMatrix = new Matrix[2];
    solMolFracMatrix =
        new Matrix[2][pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()];
    solMatrix[0] = new Matrix(PbArray, 1).transpose();
    solMatrix[1] = new Matrix(PbArray, 1).transpose();
    for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
      for (int i = 0; i < pipe.getNode(0).getBulkSystem().getPhases()[0]
          .getNumberOfComponents(); i++) {
        solMolFracMatrix[phaseNum][i] = new Matrix(PbArray, 1).transpose();
      }
    }
    sol3Matrix[0] = new Matrix(PbArray, 1).transpose();
    sol3Matrix[1] = new Matrix(PbArray, 1).transpose();
    solPhaseConsMatrix[0] = new Matrix(PbArray, 1).transpose();
    solPhaseConsMatrix[1] = new Matrix(PbArray, 1).transpose();
    sol2Matrix = new Matrix(PbArray, 1).transpose();
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
      logger.error(ex.getMessage());;
    }

    return clonedSystem;
  }
}
