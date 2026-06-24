package neqsim.fluidmechanics.geometrydefinitions.stirredcell;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinition;

/**
 * StirredCell class.
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
   * Constructor for StirredCell.
   */
  public StirredCell() {
  }

  /**
   * Constructor for StirredCell.
   *
   * @param diameter a double
   */
  public StirredCell(double diameter) {
    super(diameter);
  }

  /**
   * Constructor for StirredCell.
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
      logger.error(ex.getMessage());
    }
    return clonedPipe;
  }
}
