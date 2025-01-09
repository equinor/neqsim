package neqsim.fluidmechanics.geometrydefinitions.stirredcell;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinition;

/**
 * <p>
 * StirredCell class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class StirredCell extends GeometryDefinition {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(StirredCell.class);

  /**
   * <p>
   * Constructor for StirredCell.
   * </p>
   */
  public StirredCell() {}

  /**
   * <p>
   * Constructor for StirredCell.
   * </p>
   *
   * @param diameter a double
   */
  public StirredCell(double diameter) {
    super(diameter);
  }

  /**
   * <p>
   * Constructor for StirredCell.
   * </p>
   *
   * @param diameter a double
   * @param roughness a double
   */
  public StirredCell(double diameter, double roughness) {
    super(diameter, roughness);
  }

  /** {@inheritDoc} */
  @Override
  public StirredCell clone() {
    StirredCell clonedPipe = null;
    try {
      clonedPipe = (StirredCell) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());;
    }
    return clonedPipe;
  }
}
