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
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Viscosity.class);

  /**
   * Constructor for Viscosity.
   *
   * @param gasPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Viscosity(PhysicalProperties gasPhase) {
    super(gasPhase);
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalStateException if a required part of the clone cannot be copied
   */
  @Override
  public Viscosity clone() {
    Viscosity properties = null;

    try {
      properties = (Viscosity) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
      throw new IllegalStateException("Unable to complete clone of Viscosity", ex);
    }

    return properties;
  }
}
