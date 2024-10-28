package neqsim.physicalproperties.methods.methodinterface;

import neqsim.physicalproperties.methods.PhysicalPropertyMethodInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>
 * DensityInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface DensityInterface
    extends ThermodynamicConstantsInterface, PhysicalPropertyMethodInterface {
  /**
   * <p>
   * Returns the density of the phase. Unit: kg/m^3
   * </p>
   *
   * @return The density of the phase in kg/m^3.
   */
  public double calcDensity();

  /** {@inheritDoc} */
  @Override
  public DensityInterface clone();
}
