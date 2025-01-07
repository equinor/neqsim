package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.CommonPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.ConductivityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Abstract class for Conductivity.
 *
 * @author Even Solbraa
 */
public abstract class Conductivity extends CommonPhysicalPropertyMethod
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
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Conductivity(PhysicalProperties phase) {
    super(phase);
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
