/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */

package neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.conductivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.GasPhysicalPropertyMethod;
import neqsim.physicalproperties.physicalpropertymethods.methodinterface.ConductivityInterface;

/**
 * Abstract class Conductivity for gases.
 *
 * @author Even Solbraa
 */
public abstract class Conductivity extends GasPhysicalPropertyMethod
    implements ConductivityInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Conductivity.class);

  double conductivity = 0;

  /**
   * <p>
   * Constructor for Conductivity.
   * </p>
   *
   * @param gasPhase a
   *        {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface}
   *        object
   */
  public Conductivity(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface gasPhase) {
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
