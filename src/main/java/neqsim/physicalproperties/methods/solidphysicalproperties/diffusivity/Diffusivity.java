package neqsim.physicalproperties.methods.solidphysicalproperties.diffusivity;

import neqsim.physicalproperties.methods.methodinterface.DiffusivityInterface;
import neqsim.physicalproperties.methods.solidphysicalproperties.SolidPhysicalPropertyMethod;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Diffusivity class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Diffusivity extends SolidPhysicalPropertyMethod implements DiffusivityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double[][] binaryDiffusionCoefficients;
  double[] effectiveDiffusionCoefficient;

  /**
   * Constructor for Diffusivity.
   *
   * @param solidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Diffusivity(PhysicalProperties solidPhase) {
    super(solidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(PhysicalProperties properties) {
    super.setPhase(properties);
    int count = properties.getPhase().getNumberOfComponents();
    if (binaryDiffusionCoefficients == null || binaryDiffusionCoefficients.length != count) {
      binaryDiffusionCoefficients = new double[count][count];
      effectiveDiffusionCoefficient = new double[count];
    }
  }

  /** {@inheritDoc} */
  @Override
  public Diffusivity clone() {
    Diffusivity properties = (Diffusivity) super.clone();
    properties.binaryDiffusionCoefficients = copyCoefficientMatrix(binaryDiffusionCoefficients);
    if (effectiveDiffusionCoefficient != null) {
      properties.effectiveDiffusionCoefficient = effectiveDiffusionCoefficient.clone();
    }
    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod, int multicomponentDiffusionMethod) {
    return binaryDiffusionCoefficients;
  }

  /** {@inheritDoc} */
  @Override
  public void calcEffectiveDiffusionCoefficients() {
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxwellStefanBinaryDiffusionCoefficient(int i, int j) {
    return binaryDiffusionCoefficients[i][j];
  }

  /** {@inheritDoc} */
  @Override
  public double getEffectiveDiffusionCoefficient(int i) {
    return effectiveDiffusionCoefficient[i];
  }

  /** {@inheritDoc} */
  @Override
  public double getFickBinaryDiffusionCoefficient(int i, int j) {
    return binaryDiffusionCoefficients[i][j];
  }

  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    throw new UnsupportedOperationException("Unimplemented method 'calcBinaryDiffusionCoefficient");
  }
}
