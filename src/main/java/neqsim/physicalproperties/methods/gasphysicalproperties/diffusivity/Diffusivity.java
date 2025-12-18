package neqsim.physicalproperties.methods.gasphysicalproperties.diffusivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.gasphysicalproperties.GasPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.DiffusivityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * Diffusivity class for gas phase diffusion coefficient calculations.
 * </p>
 *
 * <p>
 * Uses Chapman-Enskog kinetic theory with Lennard-Jones parameters. Valid temperature range is
 * approximately 200-2000 K for most gas systems.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Diffusivity extends GasPhysicalPropertyMethod implements DiffusivityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Diffusivity.class);

  /** Minimum validated temperature [K]. */
  protected static final double T_MIN = 200.0;

  /** Maximum validated temperature [K]. */
  protected static final double T_MAX = 2000.0;

  /** Flag to enable/disable temperature range warnings. */
  protected boolean enableTemperatureWarnings = true;

  double[][] binaryDiffusionCoefficients;
  double[][] binaryLennardJonesOmega;
  double[] effectiveDiffusionCoefficient;

  /**
   * <p>
   * Constructor for Diffusivity.
   * </p>
   *
   * @param gasPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Diffusivity(PhysicalProperties gasPhase) {
    super(gasPhase);
    binaryDiffusionCoefficients = new double[gasPhase.getPhase().getNumberOfComponents()][gasPhase
        .getPhase().getNumberOfComponents()];
    binaryLennardJonesOmega = new double[gasPhase.getPhase().getNumberOfComponents()][gasPhase
        .getPhase().getNumberOfComponents()];
    effectiveDiffusionCoefficient = new double[gasPhase.getPhase().getNumberOfComponents()];
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
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    // method - estimation method
    // if(method==? then)
    double T = gasPhase.getPhase().getTemperature();

    // Temperature range validation
    if (enableTemperatureWarnings && (T < T_MIN || T > T_MAX)) {
      logger.warn(
          "Temperature {} K is outside validated range [{}-{}] for gas diffusivity calculation", T,
          T_MIN, T_MAX);
    }

    double A2 = 1.06036;
    double B2 = 0.15610;
    double C2 = 0.19300;
    double D2 = 0.47635;
    double E2 = 1.03587;
    double F2 = 1.52996;
    double G2 = 1.76474;
    double H2 = 3.89411;
    double tempVar2 = T / binaryEnergyParameter[i][j];
    binaryLennardJonesOmega[i][j] = A2 / Math.pow(tempVar2, B2) + C2 / Math.exp(D2 * tempVar2)
        + E2 / Math.exp(F2 * tempVar2) + G2 / Math.exp(H2 * tempVar2);
    binaryDiffusionCoefficients[i][j] = 0.00266 * Math.pow(T, 1.5)
        / (gasPhase.getPhase().getPressure() * Math.sqrt(binaryMolecularMass[i][j])
            * Math.pow(binaryMolecularDiameter[i][j], 2) * binaryLennardJonesOmega[i][j]);
    return binaryDiffusionCoefficients[i][j] *= 1e-4;
  }

  /**
   * Enable or disable temperature range warnings.
   *
   * @param enable true to enable warnings, false to disable
   */
  public void setEnableTemperatureWarnings(boolean enable) {
    this.enableTemperatureWarnings = enable;
  }

  /**
   * Check if temperature is within the validated range.
   *
   * @return true if temperature is within valid range
   */
  public boolean isTemperatureInValidRange() {
    double T = gasPhase.getPhase().getTemperature();
    return T >= T_MIN && T <= T_MAX;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
      for (int j = i; j < gasPhase.getPhase().getNumberOfComponents(); j++) {
        binaryDiffusionCoefficients[i][j] =
            calcBinaryDiffusionCoefficient(i, j, binaryDiffusionCoefficientMethod);
        binaryDiffusionCoefficients[j][i] = binaryDiffusionCoefficients[i][j];
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

    for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
      sum = 0;
      for (int j = 0; j < gasPhase.getPhase().getNumberOfComponents(); j++) {
        if (i == j) {
        } else {
          sum += gasPhase.getPhase().getComponent(j).getx() / binaryDiffusionCoefficients[i][j];
        }
      }
      effectiveDiffusionCoefficient[i] = (1.0 - gasPhase.getPhase().getComponent(i).getx()) / sum;
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
    /*
     * double temp = (i==j)? 1.0: 0.0; double nonIdealCorrection = temp +
     * gasPhase.getPhase().getComponent(i).getx() * gasPhase.getPhase().getComponent(i).getdfugdn(j)
     * * gasPhase.getPhase().getNumberOfMolesInPhase(); if (Double.isNaN(nonIdealCorrection))
     * nonIdealCorrection=1.0; return binaryDiffusionCoefficients[i][j]/nonIdealCorrection; // shuld
     * be divided by non ideality factor
     */
    return binaryDiffusionCoefficients[i][j];
  }
}
