package neqsim.physicalproperties.methods.commonphasephysicalproperties.diffusivity;

import neqsim.physicalproperties.methods.commonphasephysicalproperties.CommonPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.DiffusivityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Diffusivity class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Diffusivity extends CommonPhysicalPropertyMethod implements DiffusivityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double[][] binaryDiffusionCoefficients;
  double[] effectiveDiffusionCoefficient;

  /**
   * Constructor for Diffusivity.
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Diffusivity(PhysicalProperties phase) {
    super(phase);
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
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    return 1.0e-6;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod, int multicomponentDiffusionMethod) {
    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      for (int j = 0; j < phase.getPhase().getNumberOfComponents(); j++) {
        binaryDiffusionCoefficients[i][j] = calcBinaryDiffusionCoefficient(i, j, binaryDiffusionCoefficientMethod);
        // System.out.println("diff gas " + binaryDiffusionCoefficients[i][j]);
      }
    }

    if (multicomponentDiffusionMethod == 0) {
      // ok use full matrix
    } else if (multicomponentDiffusionMethod == 0) {
      // calcEffectiveDiffusionCoefficients();
    }
    return binaryDiffusionCoefficients;
  }

  /** {@inheritDoc} */
  @Override
  public void calcEffectiveDiffusionCoefficients() {
    double sum = 0;

    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      sum = 0;
      for (int j = 0; j < phase.getPhase().getNumberOfComponents(); j++) {
        if (i == j) {
          continue;
        } else {
          sum += phase.getPhase().getComponent(j).getx() / binaryDiffusionCoefficients[i][j];
        }
      }
      effectiveDiffusionCoefficient[i] = (1.0 - phase.getPhase().getComponent(i).getx()) / sum;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getFickBinaryDiffusionCoefficient(int i, int j) {
    return binaryDiffusionCoefficients[i][j];
  }

  /** {@inheritDoc} */
  @Override
  public double getEffectiveDiffusionCoefficient(int i) {
    return effectiveDiffusionCoefficient[i];
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxwellStefanBinaryDiffusionCoefficient(int i, int j) {
    return binaryDiffusionCoefficients[i][j];
  }
}
