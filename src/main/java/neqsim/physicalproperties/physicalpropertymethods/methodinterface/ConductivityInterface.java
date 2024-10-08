package neqsim.physicalproperties.physicalpropertymethods.methodinterface;

import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>
 * ConductivityInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ConductivityInterface extends ThermodynamicConstantsInterface,
    neqsim.physicalproperties.physicalpropertymethods.PhysicalPropertyMethodInterface {
  /**
   * <p>
   * calcConductivity.
   * </p>
   *
   * @return a double
   */
  public double calcConductivity();

  /** {@inheritDoc} */
  @Override
  public ConductivityInterface clone();
}
