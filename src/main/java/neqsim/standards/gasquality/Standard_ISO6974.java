package neqsim.standards.gasquality;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Standard_ISO6974 class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Standard_ISO6974 extends GasChromotograpyhBase {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for Standard_ISO6974.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_ISO6974(SystemInterface thermoSystem) {
    super(thermoSystem);
    setName("ISO6974");
  }
}
