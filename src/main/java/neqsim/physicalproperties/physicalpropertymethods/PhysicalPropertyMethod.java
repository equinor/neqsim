/*
 * PhysicalPropertyMethod.java
 *
 * Created on 3. august 2001, 22:49
 */

package neqsim.physicalproperties.physicalpropertymethods;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties;

/**
 * <p>
 * Abstract PhysicalPropertyMethod class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public abstract class PhysicalPropertyMethod implements PhysicalPropertyMethodInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(PhysicalPropertyMethod.class);

  /**
   * <p>
   * Constructor for PhysicalPropertyMethod.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties} object
   */
  public PhysicalPropertyMethod(PhysicalProperties phase) {
    setPhase(phase);
  }

  /** {@inheritDoc} */
  @Override
  public PhysicalPropertyMethod clone() {
    PhysicalPropertyMethod properties = null;

    try {
      properties = (PhysicalPropertyMethod) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public void tuneModel(double val, double temperature, double pressure) {
    throw new UnsupportedOperationException("Unimplemented method 'tuneModel'");
  }
}
