package neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.viscosity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.GasPhysicalPropertyMethod;
import neqsim.physicalproperties.physicalpropertymethods.methodinterface.ViscosityInterface;
import neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties;

/**
 * Abstract class for Viscosity property.
 *
 * @author Even Solbraa
 */
public abstract class Viscosity extends GasPhysicalPropertyMethod implements ViscosityInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Viscosity.class);

  /**
   * <p>
   * Constructor for Viscosity.
   * </p>
   *
   * @param gasPhase a {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties}
   *        object
   */
  public Viscosity(PhysicalProperties gasPhase) {
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
