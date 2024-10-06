package neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.viscosity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * * Abstract class for Viscosity property.
 *
 * @author Even Solbraa
 */
abstract class Viscosity extends
    neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.GasPhysicalPropertyMethod
    implements
    neqsim.physicalproperties.physicalpropertymethods.methodinterface.ViscosityInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Viscosity.class);

  /**
   * <p>
   * Constructor for Viscosity.
   * </p>
   *
   * @param gasPhase a
   *        {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface}
   *        object
   */
  public Viscosity(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface gasPhase) {
    super(gasPhase);
  }

  /** {@inheritDoc} */
  @Override
  public Viscosity clone() {
    Viscosity properties = null;

    try {
      properties = (Viscosity) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return properties;
  }
}
