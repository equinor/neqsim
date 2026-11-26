package neqsim.thermo.util.gerg;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import org.netlib.util.doubleW;
import neqsim.thermo.phase.PhaseGERG2008Eos;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * NeqSimGERG2008 class.
 *
 * <p>
 * This class provides a wrapper around the GERG-2008 equation of state for calculating thermodynamic properties of
 * natural gas mixtures. Initialised GERG-2008 kernels are cached to avoid expensive re-initialization on every call,
 * which significantly improves performance for applications that make repeated property calculations (such as
 * compressor performance estimation).
 * </p>
 *
 * <p>
 * <b>Thread safety and model ownership.</b> A GERG-2008 kernel keeps mutable temperature and composition caches and
 * numerical work arrays. The cache therefore holds one initialised kernel per thread and per {@link GERG2008Type}, and
 * every calculation resolves the kernel on the thread that performs it. Wrappers owned by different threads can run at
 * the same time, for example in {@code ProcessSystem.runParallel()}, and give the same results as serial runs. Each
 * thread that uses a model type holds its own kernel and coefficient tables for as long as the thread lives, so
 * long-lived pools keep their kernels.
 * </p>
 *
 * <p>
 * The wrapper itself, its phase and its fluid are not thread-safe: do not share one {@code NeqSimGERG2008} or one fluid
 * between threads; {@code clone()} the fluid per worker instead.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class NeqSimGERG2008 {
  /**
   * Each worker owns its GERG kernel. The kernel stores mutable reducing-function and derivative work data.
   */
  private static final ConcurrentHashMap<GERG2008Type, ThreadLocal<GERG2008>> MODEL_CACHE = new ConcurrentHashMap<>();

  double[] normalizedGERGComposition = new double[22 + 1];
  double[] notNormalizedGERGComposition = new double[22 + 1];
  PhaseInterface phase = null;
  private GERG2008Type modelType = GERG2008Type.STANDARD;

  /**
   * Constructor for NeqSimGERG2008.
   */
  public NeqSimGERG2008() {
  }

  /**
   * Constructor for NeqSimGERG2008.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public NeqSimGERG2008(PhaseInterface phase) {
    this(phase, GERG2008Type.STANDARD);
  }

  /**
   * Constructor for NeqSimGERG2008 with specified model type.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param modelType the GERG-2008 model variant to use (STANDARD or HYDROGEN_ENHANCED)
   */
  public NeqSimGERG2008(PhaseInterface phase, GERG2008Type modelType) {
    this.modelType = modelType;
    this.setPhase(phase);
  }

  /**
   * Gets the current thread's initialized GERG kernel for this wrapper's model.
   *
   * @return initialized thread-confined GERG kernel
   */
  private GERG2008 kernel() {
    return MODEL_CACHE.computeIfAbsent(modelType, type -> ThreadLocal.withInitial(() -> {
      GERG2008 model = createNewModel(type);
      model.SetupGERG();
      return model;
    })).get();
  }

  /**
   * Creates a new GERG model instance based on the specified type.
   *
   * @param type the GERG-2008 model variant
   * @return a new GERG2008 instance (not yet initialized)
   */
  private static GERG2008 createNewModel(GERG2008Type type) {
    switch (type) {
    case HYDROGEN_ENHANCED:
      return new GERG2008H2();
    case AMMONIA_EXTENDED:
      return new GERG2008NH3();
    case STANDARD:
    default:
      return new GERG2008();
    }
  }

  /**
   * Clears the cached GERG-2008 model instances. This is primarily useful for testing or when parameters need to be
   * reloaded.
   *
   * <p>
   * Existing wrappers stay usable: the next calculation on each thread builds and initialises a fresh kernel. Kernels
   * already held by running calculations finish on the old instance and are then released.
   * </p>
   */
  public static void clearCache() {
    MODEL_CACHE.clear();
  }

  /**
   * Get the current GERG-2008 model type.
   *
   * @return the model type
   */
  public GERG2008Type getModelType() {
    return modelType;
  }

  /**
   * Set the GERG-2008 model type. Uses cached model instance for the new type.
   *
   * @param modelType the GERG-2008 model variant to use
   */
  public void setModelType(GERG2008Type modelType) {
    this.modelType = modelType;
  }

  /**
   * getMolarDensity.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getMolarDensity(PhaseInterface phase) {
    this.setPhase(phase);
    return getMolarDensity();
  }

  /**
   * getDensity.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getDensity(PhaseInterface phase) {
    this.setPhase(phase);
    // return getMolarDensity() * getMolarMass() * 1000.0;
    return getMolarDensity() * phase.getMolarMass() * 1000.0;
  }

  /**
   * getDensity.
   *
   * @return a double
   */
  public double getDensity() {
    return getMolarDensity() * phase.getMolarMass() * 1000.0;
    // return getMolarDensity() * getMolarMass()* 1000.0;
  }

  /**
   * getPressure.
   *
   * @return a double
   */
  public double getPressure() {
    double moldens = getMolarDensity();
    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    kernel().PressureGERG(phase.getTemperature(), moldens, normalizedGERGComposition, P, Z);
    return P.val;
  }

  /**
   * getMolarMass.
   *
   * @return a double
   */
  public double getMolarMass() {
    doubleW mm = new doubleW(0.0);
    kernel().MolarMassGERG(normalizedGERGComposition, mm);
    return mm.val / 1.0e3;
  }

  /**
   * getMolarDensity.
   *
   * @return a double
   */
  public double getMolarDensity() {
    int flag = 0;
    if (phase != null) {
      PhaseType type = phase.getType();
      if (type == PhaseType.LIQUID || type == PhaseType.OIL || type == PhaseType.AQUEOUS) {
        flag = 2; // search for high density root
      }
    }
    GERG2008 model = kernel();
    boolean envelope = phase instanceof PhaseGERG2008Eos && ((PhaseGERG2008Eos) phase).isPhaseEnvelopeCalculation();
    return model.phaseDensity(flag, phase.getTemperature(), phase.getPressure() * 100.0, normalizedGERGComposition,
        envelope);
  }

  /**
   * Tests whether an initialized gas-labelled phase represents the same subcritical dense root as a liquid request.
   *
   * @return true if the gas-labelled root is on the dense side of the pseudo-critical density and a liquid request
   * reproduces it
   */
  public boolean isSubcriticalDenseGasRoot() {
    if (phase.getType() != PhaseType.GAS) {
      return false;
    }
    GERG2008 model = kernel();
    doubleW criticalTemperature = new doubleW(0.0);
    doubleW criticalDensity = new doubleW(0.0);
    model.PseudoCriticalPointGERG(normalizedGERGComposition, criticalTemperature, criticalDensity);
    if (phase.getTemperature() >= criticalTemperature.val) {
      return false;
    }
    double density = getMolarDensity();
    if (density <= criticalDensity.val) {
      return false;
    }
    double liquidDensity = model.phaseDensity(2, phase.getTemperature(), phase.getPressure() * 100.0,
        normalizedGERGComposition, false);
    return Math.abs(density - liquidDensity) <= 1.0e-7 * liquidDensity;
  }

  /**
   * propertiesGERG.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return an array of type double
   */
  public double[] propertiesGERG(PhaseInterface phase) {
    this.setPhase(phase);
    return propertiesGERG();
  }

  /**
   * getProperties.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param properties an array of {@link java.lang.String} objects
   * @return an array of type double
   */
  public double[] getProperties(PhaseInterface phase, String[] properties) {
    double[] allProperties = propertiesGERG();
    double[] returnProperties = new double[properties.length];

    for (int i = 0; i < properties.length; i++) {
      switch (properties[i]) {
      case "density":
        returnProperties[i] = allProperties[0];
        break;
      case "Cp":
        returnProperties[i] = allProperties[1];
        break;
      case "Cv":
        returnProperties[i] = allProperties[2];
        break;
      case "soundSpeed":
        returnProperties[i] = allProperties[3];
        break;
      default:
        break;
      }
    }
    return returnProperties;
  }

  /**
   * propertiesGERG.
   *
   * @return an array of type double
   */
  public double[] propertiesGERG() {
    doubleW p = new doubleW(0.0);
    doubleW z = new doubleW(0.0);
    doubleW dpdd = new doubleW(0.0);
    doubleW d2pdd2 = new doubleW(0.0);
    doubleW d2pdtd = new doubleW(0.0);
    doubleW dpdt = new doubleW(0.0);
    doubleW u = new doubleW(0.0);
    doubleW h = new doubleW(0.0);
    doubleW s = new doubleW(0.0);
    doubleW cv = new doubleW(0.0);
    doubleW cp = new doubleW(0.0);
    doubleW w = new doubleW(0.0);
    doubleW g = new doubleW(0.0);
    doubleW jt = new doubleW(0.0);
    doubleW kappa = new doubleW(0.0);
    doubleW A = new doubleW(0.0);
    double dens = getMolarDensity();
    // neqsim.thermo.GERG.Densitygerg.densitygerg(0, 0, 0, arg3, 0, arg5, arg6,
    // arg7);
    kernel().PropertiesGERG(phase.getTemperature(), dens, normalizedGERGComposition, p, z, dpdd, d2pdd2, d2pdtd, dpdt,
        u, h, s, cv, cp, w, g, jt, kappa, A);
    double[] properties = new double[] {p.val, z.val, dpdd.val, d2pdd2.val, d2pdtd.val, dpdt.val, u.val, h.val, s.val,
        cv.val, cp.val, w.val, g.val, jt.val, kappa.val};
    return properties;
  }

  /**
   * Setter for the field <code>phase</code>.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setPhase(PhaseInterface phase) {
    this.phase = phase;
    mapComposition(null, notNormalizedGERGComposition);
    normalizeComposition();
  }

  /**
   * Map phase components to GERG slots before normalization.
   *
   * @param composition optional mole fractions in phase component order
   * @param mapped destination in GERG component order
   */
  private void mapComposition(double[] composition, double[] mapped) {
    Arrays.fill(mapped, 0.0);
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      String componentName = phase.getComponent(i).getComponentName();

      double fraction = composition == null ? phase.getComponent(i).getx() : composition[i];
      switch (componentName) {
      case "methane":
        mapped[1] = fraction;
        break;
      case "nitrogen":
        mapped[2] = fraction;
        break;
      case "CO2":
        mapped[3] = fraction;
        break;
      case "ethane":
        mapped[4] = fraction;
        break;
      case "propane":
        mapped[5] = fraction;
        break;
      case "i-butane":
        mapped[6] = fraction;
        break;
      case "n-butane":
        mapped[7] = fraction;
        break;
      case "i-pentane":
        mapped[8] = fraction;
        break;
      case "n-pentane":
        mapped[9] = fraction;
        break;
      case "n-hexane":
        mapped[10] = fraction;
        break;
      case "n-heptane":
        mapped[11] = fraction;
        break;
      case "n-octane":
        mapped[12] = fraction;
        break;
      case "n-nonane":
        mapped[13] = fraction;
        break;
      case "nC10":
        mapped[14] = fraction;
        break;
      case "hydrogen":
        mapped[15] = fraction;
        break;
      case "oxygen":
        mapped[16] = fraction;
        break;
      case "CO":
        mapped[17] = fraction;
        break;
      case "water":
        mapped[18] = fraction;
        break;
      case "H2S":
        mapped[19] = fraction;
        break;
      case "helium":
        mapped[20] = fraction;
        break;
      case "argon":
        mapped[21] = fraction;
        break;
      case "ammonia":
        mapped[22] = fraction;
        break;

      default:
        double molarMass = phase.getComponent(i).getMolarMass();
        if (molarMass > 44.096759796142 / 1000.0 && molarMass < 58.1236991882324 / 1000.0) {
          mapped[7] += fraction;
        }
        if (molarMass > 58.1236991882324 / 1000.0 && molarMass < 72.15064 / 1000.0) {
          mapped[8] += fraction;
        }
        if (molarMass > 72.15064 / 1000.0 && molarMass < 86.2 / 1000.0) {
          mapped[10] += fraction;
        }
        if (molarMass > 86.2 / 1000.0 && molarMass < 100.204498291016 / 1000.0) {
          mapped[11] += fraction;
        }
        if (molarMass > 100.204498291016 / 1000.0 && molarMass < 107.0 / 1000.0) {
          mapped[12] += fraction;
        }
        if (molarMass > 107.0 / 1000.0 && molarMass < 121.0 / 1000.0) {
          mapped[13] += fraction;
        }
        if (molarMass > 121.0 / 1000.0) {
          mapped[14] += fraction;
        }
        break;
      }
    }
  }

  /**
   * normalizeComposition.
   */
  public void normalizeComposition() {
    normalizeComposition(notNormalizedGERGComposition, normalizedGERGComposition);
  }

  /**
   * Normalize GERG mole fractions.
   *
   * @param input mapped GERG fractions before normalization
   * @param output normalized GERG fractions
   */
  private static void normalizeComposition(double[] input, double[] output) {
    double result = 0;
    for (double value : input) {
      result += value;
    }
    // Handle case where sum is zero or near-zero to avoid NaN
    if (result < 1e-30) {
      // Set all normalized fractions to zero to avoid NaN
      for (int k = 0; k < output.length; k++) {
        output[k] = 0.0;
      }
      return;
    }
    for (int k = 0; k < output.length; k++) {
      output[k] = input[k] / result;
    }
  }

  /**
   * getAlpha0_GERG2008.
   *
   * @return an array of {@link org.netlib.util.doubleW} objects
   */
  public doubleW[] getAlpha0_GERG2008() {
    // Get temperature and molar density from the phase object
    double temperature = phase.getTemperature();
    double molarDensity = getMolarDensity(phase);

    // Create and initialize a 4x4 array for the derivatives.
    // Assuming doubleW is a simple wrapper with a public field 'val'.
    int rows = 4;
    int cols = 4;
    doubleW[] a0 = new doubleW[rows];
    for (int i = 0; i < rows; i++) {
      a0[i] = new doubleW(0.0);
    }

    // Call the GERG2008 function to fill in the ar array.
    // The first two parameters (itau and idelta) are set to 0.
    kernel().Alpha0GERG(temperature, molarDensity, normalizedGERGComposition, a0);

    // Return the computed dimensionless residual Helmholtz free energy.
    // This is equivalent to alpha_res = A^r/(RT)
    return a0;
  }

  /**
   * Get reduced residual helmholtz free energy and its derivatives. The returned array has the following structure:
   * <ul>
   * <li>ar(0,0) - Residual Helmholtz energy (dimensionless, =a/RT)</li>
   * <li>ar(0,1) - delta*partial (ar)/partial(delta)</li>
   * <li>ar(0,2) - delta^2*partial^2(ar)/partial(delta)^2</li>
   * <li>ar(0,3) - delta^3*partial^3(ar)/partial(delta)^3</li>
   * <li>ar(1,0) - tau*partial (ar)/partial(tau)</li>
   * <li>ar(1,1) - tau*delta*partial^2(ar)/partial(tau)/partial(delta)</li>
   * <li>ar(2,0) - tau^2*partial^2(ar)/partial(tau)^2</li>
   * </ul>
   *
   * @return a doubleW[][] representing the reduced residual helmholtz free energy
   */
  public doubleW[][] getAlphares_GERG2008() {
    // Get temperature and molar density from the phase object
    double temperature = phase.getTemperature();
    double molarDensity = getMolarDensity(phase);

    // Create and initialize a 4x4 array for the derivatives.
    // Assuming doubleW is a simple wrapper with a public field 'val'.
    int rows = 4;
    int cols = 4;
    doubleW[][] ar = new doubleW[rows][cols];
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        ar[i][j] = new doubleW(0.0);
      }
    }

    // Call the GERG2008 function to fill in the ar array. The first two
    // arguments specify the highest order of \u03c4- and \u03b4-derivatives to
    // return. Thermodynamic consistency requires mixed temperature/volume
    // derivatives as well as up to third-order volume derivatives, so request
    // \u03c4-derivatives up to second order and \u03b4-derivatives up to third
    // order.
    kernel().AlpharGERG(2, 3, temperature, molarDensity, normalizedGERGComposition, ar);

    // Return the computed dimensionless residual Helmholtz free energy.
    // This is equivalent to alpha_res = A^r/(RT)
    return ar;
  }

  /**
   * Calculate the dimensionless residual Helmholtz energy at a prescribed molar density.
   *
   * <p>
   * This method is used for constant-volume composition derivatives. The composition is the normalized GERG composition
   * captured from the phase supplied to this wrapper.
   * </p>
   *
   * @param temperature temperature in K
   * @param molarDensity molar density in mol/L
   * @return dimensionless residual Helmholtz energy, {@code alphaR = Ares/(nRT)}
   */
  public double getResidualHelmholtzEnergy(double temperature, double molarDensity) {
    return kernel().residualEnergy(temperature, molarDensity, normalizedGERGComposition);
  }

  /**
   * Evaluate a trial phase composition at a specified density without changing the phase or this wrapper's reference
   * composition.
   *
   * @param temperature temperature in K
   * @param molarDensity molar density in mol/L
   * @param composition trial mole fractions in phase component order
   * @return dimensionless residual Helmholtz energy
   */
  public double getResidualHelmholtzEnergy(double temperature, double molarDensity, double[] composition) {
    double[] mapped = new double[normalizedGERGComposition.length];
    mapComposition(composition, mapped);
    normalizeComposition(mapped, mapped);
    return kernel().residualEnergy(temperature, molarDensity, mapped);
  }

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // test HitHub
    SystemInterface fluid1 = new SystemSrkEos();
    fluid1.addComponent("methane", 10.0);
    fluid1.addComponent("hydrogen", 90.0);
    // fluid1.addComponent("CO2", 1.0);
    // fluid1.addComponent("ethane", 10.0);
    // fluid1.addComponent("propane", 3.0);
    // fluid1.addComponent("n-butane", 1.0);
    // fluid1.addComponent("oxygen", 1.0);
    /*
     * fluid1.addComponent("n-butane", 0.006304); fluid1.addComponent("i-butane", 0.003364);
     * fluid1.addComponent("n-pentane", 0.001005); fluid1.addComponent("i-pentane", 0.000994);
     * fluid1.addComponent("n-hexane", 0.000369); fluid1.addComponent("n-heptane", 0.000068);
     * fluid1.addComponent("n-octane", 0.000008);
     */
    // fluid//1.addComponent("ethane", 5.0);
    // fluid1.addComponent("propane", 5.0);
    // fluid1.addTBPfraction("C8", 0.01, 211.0/1000.0, 0.82);
    fluid1.setTemperature(273.15 + 20);
    fluid1.setPressure(150.0);
    fluid1.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid1);
    ops.TPflash();
    // fluid1.display();
    System.out.println("density GERG " + fluid1.getPhase(0).getDensity_GERG2008());

    NeqSimGERG2008 test = new NeqSimGERG2008(fluid1.getPhase("gas"));
    // fluid1.getPhase("gas").getProperties_GERG2008();
    System.out.println("density " + test.getDensity());
    System.out.println("pressure " + test.getPressure());
    // System.out.println("properties " + test.propertiesGERG());
    double[] properties = test.propertiesGERG();
    System.out.println("Pressure [kPa]:            " + properties[0]);
    System.out.println("Compressibility factor:            " + properties[1]);
    System.out.println("d(P)/d(rho) [kPa/(mol/l)]            " + properties[2]);
    System.out.println("d^2(P)/d(rho)^2 [kPa/(mol/l)^2]:            " + properties[3]);
    System.out.println("d2(P)/d2(T) [kPa/K]:             " + properties[4]);
    System.out.println("d(P)/d(T) [kPa/K]:             " + properties[5]);
    System.out.println("Energy [J/mol]:             " + properties[6]);
    System.out.println("Enthalpy [J/mol]:             " + properties[7]);
    System.out.println("Entropy [J/mol-K]:             " + properties[8]);
    System.out.println("Isochoric heat capacity [J/mol-K]:             " + properties[9]);
    System.out.println("Isobaric heat capacity [J/mol-K]:            " + properties[10]);
    System.out.println("Speed of sound [m/s]:            " + properties[11]);
    System.out.println("Gibbs energy [J/mol]:            " + properties[12]);
    System.out.println("Joule-Thomson coefficient [K/kPa]:            " + properties[13]);
    System.out.println("Isentropic exponent:           " + properties[14]);
  }
}
