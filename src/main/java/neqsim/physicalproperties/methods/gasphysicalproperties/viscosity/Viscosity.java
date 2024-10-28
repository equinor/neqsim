package neqsim.physicalproperties.methods.gasphysicalproperties.viscosity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.gasphysicalproperties.GasPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.ViscosityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

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
   * @param gasPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
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
