package neqsim.physicalproperties.methods.gasphysicalproperties.diffusivity;

import java.util.HashMap;
import java.util.Map;
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
 * Uses Chapman-Enskog kinetic theory with Lennard-Jones parameters. The LJ parameters used here are
 * the standard values from Poling, Prausnitz, O'Connell (2001) Table E-1 and Bird, Stewart,
 * Lightfoot (2002) Table E.1, which are validated for gas diffusion and viscosity calculations.
 * These may differ from the general-purpose LJ parameters in the NeqSim component database.
 * </p>
 *
 * <p>
 * Valid temperature range is approximately 200-2000 K for most gas systems.
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

  /**
   * Standard Lennard-Jones parameters for gas diffusion calculations. Values from Poling, Prausnitz
   * and O'Connell (2001) Table E-1 and Bird, Stewart and Lightfoot (2002) Table E.1. Format: {sigma
   * [Angstrom], epsilon/k [K]}.
   */
  private static final Map<String, double[]> DIFFUSION_LJ_PARAMS = createDiffusionLJParams();

  double[][] binaryDiffusionCoefficients;
  double[][] binaryLennardJonesOmega;
  double[] effectiveDiffusionCoefficient;

  /**
   * Create lookup table of standard Lennard-Jones parameters for gas diffusion.
   *
   * @return map of component name (lowercase) to [sigma, epsilon/k] array
   */
  private static Map<String, double[]> createDiffusionLJParams() {
    Map<String, double[]> lj = new HashMap<String, double[]>();
    // Noble gases
    lj.put("helium", new double[] {2.551, 10.22});
    lj.put("neon", new double[] {2.820, 32.8});
    lj.put("argon", new double[] {3.542, 93.3});
    lj.put("krypton", new double[] {3.655, 178.9});
    lj.put("xenon", new double[] {4.047, 231.0});
    // Diatomic and simple gases
    lj.put("hydrogen", new double[] {2.827, 59.7});
    lj.put("nitrogen", new double[] {3.798, 71.4});
    lj.put("oxygen", new double[] {3.467, 106.7});
    lj.put("co", new double[] {3.690, 91.7});
    lj.put("no", new double[] {3.492, 116.7});
    // Common molecules (all keys lowercase for case-insensitive lookup)
    lj.put("co2", new double[] {3.941, 195.2});
    lj.put("n2o", new double[] {3.828, 232.4});
    lj.put("so2", new double[] {4.112, 335.4});
    lj.put("h2s", new double[] {3.623, 301.1});
    lj.put("nh3", new double[] {2.900, 558.3});
    lj.put("water", new double[] {2.641, 809.1});
    // Light hydrocarbons
    lj.put("methane", new double[] {3.758, 148.6});
    lj.put("ethane", new double[] {4.443, 215.7});
    lj.put("propane", new double[] {5.118, 237.1});
    lj.put("n-butane", new double[] {4.687, 531.4});
    lj.put("i-butane", new double[] {5.278, 330.1});
    lj.put("n-pentane", new double[] {5.784, 341.1});
    lj.put("i-pentane", new double[] {5.464, 381.0});
    lj.put("n-hexane", new double[] {5.949, 399.3});
    lj.put("n-heptane", new double[] {7.451, 205.78});
    lj.put("n-octane", new double[] {7.451, 320.0});
    lj.put("cyclohexane", new double[] {6.182, 297.1});
    // Aromatic hydrocarbons
    lj.put("benzene", new double[] {5.349, 412.3});
    lj.put("toluene", new double[] {5.926, 412.3});
    // Oxygenated compounds
    lj.put("methanol", new double[] {3.626, 481.8});
    lj.put("ethanol", new double[] {4.530, 362.6});
    lj.put("acetone", new double[] {4.600, 560.2});
    // Halogenated
    lj.put("ccl4", new double[] {5.947, 322.7});
    lj.put("chcl3", new double[] {5.389, 340.2});
    lj.put("ch2cl2", new double[] {4.898, 356.3});
    lj.put("sf6", new double[] {5.128, 222.1});
    return lj;
  }

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

    // Override binary LJ parameters with standard diffusion values where available
    initDiffusionLJParameters();
  }

  /**
   * Override the inherited binary Lennard-Jones parameters with standard diffusion-specific values
   * from Poling/BSL tables. The general-purpose LJ parameters in the NeqSim database may be
   * parameterized for other purposes (EOS, viscosity) and give poor diffusion predictions.
   */
  private void initDiffusionLJParameters() {
    int nComps = gasPhase.getPhase().getNumberOfComponents();
    // First, get per-component LJ params (override DB values with diffusion-specific ones)
    double[] sigma = new double[nComps];
    double[] epsOverK = new double[nComps];
    for (int i = 0; i < nComps; i++) {
      String name = gasPhase.getPhase().getComponent(i).getComponentName().toLowerCase();
      double[] ljParams = DIFFUSION_LJ_PARAMS.get(name);
      if (ljParams != null) {
        sigma[i] = ljParams[0];
        epsOverK[i] = ljParams[1];
      } else {
        // Keep original DB values as fallback
        sigma[i] = gasPhase.getPhase().getComponent(i).getLennardJonesMolecularDiameter();
        epsOverK[i] = gasPhase.getPhase().getComponent(i).getLennardJonesEnergyParameter();
      }
    }
    // Recompute binary combining rules
    for (int i = 0; i < nComps; i++) {
      for (int j = 0; j < nComps; j++) {
        binaryMolecularDiameter[i][j] = (sigma[i] + sigma[j]) / 2.0;
        binaryEnergyParameter[i][j] = Math.sqrt(epsOverK[i] * epsOverK[j]);
      }
    }
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
    // Convert from cm²/s to m²/s
    binaryDiffusionCoefficients[i][j] *= 1e-4;
    return binaryDiffusionCoefficients[i][j];
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
    } else if (multicomponentDiffusionMethod == 1) {
      calcEffectiveDiffusionCoefficients();
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
