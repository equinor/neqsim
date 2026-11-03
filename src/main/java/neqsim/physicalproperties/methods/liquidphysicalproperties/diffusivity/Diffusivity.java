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
   * Constructor for Diffusivity.
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Diffusivity(PhysicalProperties liquidPhase) {
    super(liquidPhase);
    binaryDiffusionCoefficients = new double[liquidPhase.getPhase().getNumberOfComponents()][liquidPhase.getPhase()
        .getNumberOfComponents()];
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
        if (this.binaryDiffusionCoefficients[i] != null) {
          properties.binaryDiffusionCoefficients[i] = this.binaryDiffusionCoefficients[i].clone();
        }
      }
    }
    if (this.effectiveDiffusionCoefficient != null && this.effectiveDiffusionCoefficient.length > 0) {
      properties.effectiveDiffusionCoefficient = this.effectiveDiffusionCoefficient.clone();
    }
    return properties;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Uses the Wesselingh-Krishna extension of Vignes interpolation. The limiting diffusivity of pair i/j in any third
   * solvent is approximated by the geometric mean of the two binary infinite-dilution coefficients. The resulting
   * weights sum to one, recovering binary Vignes interpolation and dimensional scale covariance. This empirical
   * third-solvent approximation does not itself qualify multicomponent evaporation rates.
   * </p>
   *
   * @throws IllegalStateException if an off-diagonal limiting coefficient is not finite and positive
   */
  @Override
  public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod, int multicomponentDiffusionMethod) {
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
        binaryDiffusionCoefficients[i][j] = calcBinaryDiffusionCoefficient(i, j, binaryDiffusionCoefficientMethod);
      }
    }

    // Each unordered pair reads both original limiting directions before either direction is overwritten.
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      for (int j = i + 1; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
        double forward = binaryDiffusionCoefficients[i][j];
        double reverse = binaryDiffusionCoefficients[j][i];
        if (!(forward > 0.0) || !Double.isFinite(forward) || !(reverse > 0.0) || !Double.isFinite(reverse)) {
          throw new IllegalStateException(
              "Liquid limiting diffusivities must be finite and positive for pair " + i + "/" + j);
        }
        double xi = liquidPhase.getPhase().getComponent(i).getx();
        double xj = liquidPhase.getPhase().getComponent(j).getx();
        double forwardWeight = 0.5 * (1.0 + xj - xi);
        double mixed = Math.exp(forwardWeight * Math.log(forward) + (1.0 - forwardWeight) * Math.log(reverse));
        binaryDiffusionCoefficients[i][j] = mixed;
        binaryDiffusionCoefficients[j][i] = mixed;
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
      effectiveDiffusionCoefficient[i] = (1.0 - liquidPhase.getPhase().getComponent(i).getx()) / sum;
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
        * liquidPhase.getPhase().getComponent(i).getdfugdn(j) * liquidPhase.getPhase().getNumberOfMolesInPhase();
    if (Double.isNaN(nonIdealCorrection)) {
      nonIdealCorrection = 1.0;
    }

    // shuld be divided by non ideality factor
    return binaryDiffusionCoefficients[i][j] * nonIdealCorrection;
  }
}
