package neqsim.physicalproperties.methods.methodinterface;

import neqsim.physicalproperties.methods.PhysicalPropertyMethodInterface;

/**
 * <p>
 * ViscosityInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ViscosityInterface extends PhysicalPropertyMethodInterface {
  /**
   * <p>
   * calcViscosity.
   * </p>
   *
   * @return a double
   */
  public double calcViscosity();

  /**
   * <p>
   * getPureComponentViscosity.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getPureComponentViscosity(int i);

  /** {@inheritDoc} */
  @Override
  public ViscosityInterface clone();
}
