package neqsim.physicalproperties.methods.methodinterface;

import neqsim.physicalproperties.methods.PhysicalPropertyMethodInterface;

/**
 * ViscosityInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ViscosityInterface extends PhysicalPropertyMethodInterface {
  /**
   * calcViscosity.
   *
   * @return a double
   */
  public double calcViscosity();

  /**
   * getPureComponentViscosity.
   *
   * @param i a int
   * @return a double
   */
  public double getPureComponentViscosity(int i);

  /** {@inheritDoc} */
  @Override
  public ViscosityInterface clone();
}
