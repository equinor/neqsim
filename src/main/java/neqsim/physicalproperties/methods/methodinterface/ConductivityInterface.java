package neqsim.physicalproperties.methods.methodinterface;

import neqsim.physicalproperties.methods.PhysicalPropertyMethodInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * ConductivityInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ConductivityInterface extends ThermodynamicConstantsInterface, PhysicalPropertyMethodInterface {
  /**
   * calcConductivity.
   *
   * @return a double
   */
  public double calcConductivity();

  /** {@inheritDoc} */
  @Override
  public ConductivityInterface clone();
}
