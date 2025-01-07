package neqsim.physicalproperties.methods.solidphysicalproperties.diffusivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.methodinterface.DiffusivityInterface;
import neqsim.physicalproperties.methods.solidphysicalproperties.SolidPhysicalPropertyMethod;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * Diffusivity class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Diffusivity extends SolidPhysicalPropertyMethod implements DiffusivityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Diffusivity.class);

  double[][] binaryDiffusionCoefficients;
  double[] effectiveDiffusionCoefficient;

  /**
   * <p>
   * Constructor for Diffusivity.
   * </p>
   *
   * @param solidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Diffusivity(PhysicalProperties solidPhase) {
    super(solidPhase);
    binaryDiffusionCoefficients = new double[solidPhase.getPhase()
        .getNumberOfComponents()][solidPhase.getPhase().getNumberOfComponents()];
    effectiveDiffusionCoefficient = new double[solidPhase.getPhase().getNumberOfComponents()];
  }

  /** {@inheritDoc} */
  @Override
  public Diffusivity clone() {
    Diffusivity properties = null;

    try {
      properties = (Diffusivity) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    properties.binaryDiffusionCoefficients = this.binaryDiffusionCoefficients.clone();
    for (int i = 0; i < solidPhase.getPhase().getNumberOfComponents(); i++) {
      // TODO: fails with indexerror if components has been added after construction of object
      // getNumberOfComponents() > len(this.binaryDiffusionCoefficients)
      System.arraycopy(this.binaryDiffusionCoefficients[i], 0,
          properties.binaryDiffusionCoefficients[i], 0,
          solidPhase.getPhase().getNumberOfComponents());
    }
    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    return binaryDiffusionCoefficients;
  }

  /** {@inheritDoc} */
  @Override
  public void calcEffectiveDiffusionCoefficients() {}

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
    double nonIdealCorrection = 1.0;
    // shuld be divided by non ideality factor
    return binaryDiffusionCoefficients[i][j] * nonIdealCorrection;
  }

  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    throw new UnsupportedOperationException("Unimplemented method 'calcBinaryDiffusionCoefficient");
  }
}
