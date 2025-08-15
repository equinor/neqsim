package neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.liquidphysicalproperties.LiquidPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.DiffusivityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Diffusivity class.
 *
 * @author Even Solbraa
 */
abstract class Diffusivity extends LiquidPhysicalPropertyMethod implements DiffusivityInterface {
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
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Diffusivity(PhysicalProperties liquidPhase) {
    super(liquidPhase);
    binaryDiffusionCoefficients = new double[liquidPhase.getPhase()
        .getNumberOfComponents()][liquidPhase.getPhase().getNumberOfComponents()];
    effectiveDiffusionCoefficient = new double[liquidPhase.getPhase().getNumberOfComponents()];
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
    if (this.binaryDiffusionCoefficients != null && this.binaryDiffusionCoefficients.length > 0) {
      properties.binaryDiffusionCoefficients = this.binaryDiffusionCoefficients.clone();
      for (int i = 0; i < this.binaryDiffusionCoefficients.length; i++) {
        if (this.binaryDiffusionCoefficients[i] != null
            && properties.binaryDiffusionCoefficients[i] != null) {
          System.arraycopy(this.binaryDiffusionCoefficients[i], 0,
              properties.binaryDiffusionCoefficients[i], 0,
              this.binaryDiffusionCoefficients[i].length);
        }
      }
    }
    if (this.effectiveDiffusionCoefficient != null
        && this.effectiveDiffusionCoefficient.length > 0) {
      properties.effectiveDiffusionCoefficient = this.effectiveDiffusionCoefficient.clone();
    }
    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
        binaryDiffusionCoefficients[i][j] =
            calcBinaryDiffusionCoefficient(i, j, binaryDiffusionCoefficientMethod);
      }
    }

    // Vignes correlation
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
        if (i != j) {
          binaryDiffusionCoefficients[i][j] = Math.pow(binaryDiffusionCoefficients[i][j],
              liquidPhase.getPhase().getComponent(j).getx())
              * Math.pow(binaryDiffusionCoefficients[j][i],
                  liquidPhase.getPhase().getComponent(i).getx());
        }
        // System.out.println("diff liq " + binaryDiffusionCoefficients[i][j] );
      }
    }
    return binaryDiffusionCoefficients;
  }

  /** {@inheritDoc} */
  @Override
  public void calcEffectiveDiffusionCoefficients() {
    double sum = 0;

    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      sum = 0;
      for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
        if (i == j) {
        } else {
          sum += liquidPhase.getPhase().getComponent(j).getx() / binaryDiffusionCoefficients[i][j];
        }
      }
      effectiveDiffusionCoefficient[i] =
          (1.0 - liquidPhase.getPhase().getComponent(i).getx()) / sum;
    }
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
    double temp = (i == j) ? 1.0 : 0.0;
    double nonIdealCorrection = temp + liquidPhase.getPhase().getComponent(i).getx()
        * liquidPhase.getPhase().getComponent(i).getdfugdn(j)
        * liquidPhase.getPhase().getNumberOfMolesInPhase();
    if (Double.isNaN(nonIdealCorrection)) {
      nonIdealCorrection = 1.0;
    }

    // shuld be divided by non ideality factor
    return binaryDiffusionCoefficients[i][j] * nonIdealCorrection;
  }
}
