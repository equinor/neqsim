/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */

package neqsim.physicalproperties.methods.gasphysicalproperties.conductivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.gasphysicalproperties.GasPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.ConductivityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Abstract class Conductivity for gases.
 *
 * @author Even Solbraa
 */
public abstract class Conductivity extends GasPhysicalPropertyMethod
    implements ConductivityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Conductivity.class);

  double conductivity = 0;

  /**
   * <p>
   * Constructor for Conductivity.
   * </p>
   *
   * @param gasPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Conductivity(PhysicalProperties gasPhase) {
    super(gasPhase);
  }

  /** {@inheritDoc} */
  @Override
  public Conductivity clone() {
    Conductivity properties = null;

    try {
      properties = (Conductivity) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return properties;
  }
}
