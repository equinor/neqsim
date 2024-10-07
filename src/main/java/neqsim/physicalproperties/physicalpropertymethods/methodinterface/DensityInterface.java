package neqsim.physicalproperties.physicalpropertymethods.methodinterface;

import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>
 * DensityInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface DensityInterface extends ThermodynamicConstantsInterface,
    neqsim.physicalproperties.physicalpropertymethods.PhysicalPropertyMethodInterface {
  /**
   * <p>
   * calcDensity.
   * </p>
   *
   * @return a double
   */
  public double calcDensity();

  /** {@inheritDoc} */
  @Override
  public DensityInterface clone();
}
