package neqsim.fluidmechanics.geometrydefinitions.pipe;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhasePipeFlowSolver;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinition;
import neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.PipeWall;

/**
 * <p>
 * PipeData class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PipeData extends GeometryDefinition {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PipeData.class);

  /**
   * <p>
   * Constructor for PipeData.
   * </p>
   */
  public PipeData() {
    this.wall = new PipeWall();
  }

  /**
   * <p>
   * Constructor for PipeData.
   * </p>
   *
   * @param diameter a double
   */
  public PipeData(double diameter) {
    super(diameter);
    wall = new PipeWall();
  }

  /**
   * <p>
   * Constructor for PipeData.
   * </p>
   *
   * @param diameter a double
   * @param roughness a double
   */
  public PipeData(double diameter, double roughness) {
    super(diameter, roughness);
    wall = new PipeWall();
  }

  /** {@inheritDoc} */
  @Override
  public PipeData clone() {
    PipeData clonedPipe = null;
    try {
      clonedPipe = (PipeData) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());;
    }
    return clonedPipe;
  }
}
