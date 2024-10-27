package neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties.conductivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties.CommonPhysicalPropertyMethod;
import neqsim.physicalproperties.physicalpropertymethods.methodinterface.ConductivityInterface;

/**
 * Abstract class for Conductivity.
 *
 * @author Even Solbraa
 */
public abstract class Conductivity extends CommonPhysicalPropertyMethod
    implements ConductivityInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Conductivity.class);

  double conductivity = 0;

  /**
   * <p>
   * Constructor for Conductivity.
   * </p>
   *
   * @param phase a
   *        {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface}
   *        object
   */
  public Conductivity(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface phase) {
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
