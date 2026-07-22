package neqsim.physicalproperties.methods.methodinterface;

import neqsim.physicalproperties.methods.PhysicalPropertyMethodInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * DiffusivityInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface DiffusivityInterface extends ThermodynamicConstantsInterface, PhysicalPropertyMethodInterface {
  /**
   * calcBinaryDiffusionCoefficient.
   *
   * @param i a int
   * @param j a int
   * @param method a int
   * @return a double
   */
  public double calcBinaryDiffusionCoefficient(int i, int j, int method);

  /**
   * calcDiffusionCoefficients.
   *
   * @param binaryDiffusionCoefficientMethod a int
   * @param multicomponentDiffusionMethod a int
   * @return an array of type double
   */
  public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod, int multicomponentDiffusionMethod);

  /**
   * getFickBinaryDiffusionCoefficient.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getFickBinaryDiffusionCoefficient(int i, int j);

  /**
   * getMaxwellStefanBinaryDiffusionCoefficient.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getMaxwellStefanBinaryDiffusionCoefficient(int i, int j);

  /**
   * getEffectiveDiffusionCoefficient.
   *
   * @param i a int
   * @return a double
   */
  public double getEffectiveDiffusionCoefficient(int i);

  /**
   * calcEffectiveDiffusionCoefficients.
   */
  public void calcEffectiveDiffusionCoefficients();

  /** {@inheritDoc} */
  @Override
  public DiffusivityInterface clone();
}
