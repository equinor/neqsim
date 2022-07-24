package neqsim.fluidMechanics.geometryDefinitions.pipe;

import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinition;
import neqsim.fluidMechanics.geometryDefinitions.internalGeometry.wall.PipeWall;

/**
 * <p>
 * PipeData class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PipeData extends GeometryDefinition {
  private static final long serialVersionUID = 1000;

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
  public void init() {
    super.init();
  }

  /** {@inheritDoc} */
  @Override
  public PipeData clone() {
    PipeData clonedPipe = null;
    try {
      clonedPipe = (PipeData) super.clone();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return clonedPipe;
  }
}
