package neqsim.physicalproperties.methods.methodinterface;

import neqsim.physicalproperties.methods.PhysicalPropertyMethodInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * DensityInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface DensityInterface extends ThermodynamicConstantsInterface, PhysicalPropertyMethodInterface {
  /**
   * Returns the density of the phase. Unit: kg/m^3
   *
   * @return The density of the phase in kg/m^3.
   */
  public double calcDensity();

  /** {@inheritDoc} */
  @Override
  public DensityInterface clone();
}
