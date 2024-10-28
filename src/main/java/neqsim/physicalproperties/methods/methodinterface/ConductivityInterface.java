package neqsim.physicalproperties.methods.methodinterface;

import neqsim.physicalproperties.methods.PhysicalPropertyMethodInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>
 * ConductivityInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ConductivityInterface
    extends ThermodynamicConstantsInterface, PhysicalPropertyMethodInterface {
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
