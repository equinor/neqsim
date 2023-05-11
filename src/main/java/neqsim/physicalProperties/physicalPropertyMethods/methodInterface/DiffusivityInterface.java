package neqsim.physicalProperties.physicalPropertyMethods.methodInterface;

import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>
 * DiffusivityInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface DiffusivityInterface extends ThermodynamicConstantsInterface,
    neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethodInterface {
  /**
   * <p>
   * calcBinaryDiffusionCoefficient.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param method a int
   * @return a double
   */
  public double calcBinaryDiffusionCoefficient(int i, int j, int method);

  /**
   * <p>
   * calcDiffusionCoefficients.
   * </p>
   *
   * @param binaryDiffusionCoefficientMethod a int
   * @param multicomponentDiffusionMethod a int
   * @return an array of {@link double} objects
   */
  public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod);

  /**
   * <p>
   * getFickBinaryDiffusionCoefficient.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getFickBinaryDiffusionCoefficient(int i, int j);

  /**
   * <p>
   * getMaxwellStefanBinaryDiffusionCoefficient.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getMaxwellStefanBinaryDiffusionCoefficient(int i, int j);

  /**
   * <p>
   * getEffectiveDiffusionCoefficient.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getEffectiveDiffusionCoefficient(int i);

  /**
   * <p>
   * calcEffectiveDiffusionCoefficients.
   * </p>
   */
  public void calcEffectiveDiffusionCoefficients();

  /** {@inheritDoc} */
  @Override
  public DiffusivityInterface clone();
}
