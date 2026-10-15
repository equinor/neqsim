package neqsim.thermo.util.gerg;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * Wrapper for calling the EOS-CG multi-fluid model using the GERG-2008 API.
 *
 * <p>
 * This class provides a wrapper around the EOS-CG (Equation of State for Combustion Gases) multi-fluid model.
 * Initialized native models are reused per thread. Their coefficient tables and mutable temperature/composition
 * workspaces are never shared across threads; repeated calls on one thread avoid repeating parameter initialization.
 * Configuring a utility or its phase concurrently with evaluation is unsupported.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class NeqSimEOSCG {
  /** Generation invalidating initialized working models on every thread after {@link #clearCache()}. */
  private static final AtomicLong CACHE_GENERATION = new AtomicLong();

  /** One native working model per thread; native composition/temperature caches are mutable. */
  private static final ThreadLocal<WorkingModel> MODEL_CACHE = ThreadLocal.withInitial(WorkingModel::new);

  /**
   * Thread-owned model tables and calculation scratch, reused until the cache generation changes.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  private static final class WorkingModel {
    /** Last global invalidation observed by this thread. */
    private long generation = -1;
    /** Initialized native model owned by this thread. */
    private EOSCG model;
  }

  double[] normalizedComposition = new double[28 + 1];
  double[] notNormalizedComposition = new double[28 + 1];
  PhaseInterface phase = null;

  /**
   * Default constructor.
   */
  public NeqSimEOSCG() {
    getCachedModel();
  }

  /**
   * Constructor with phase.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public NeqSimEOSCG(PhaseInterface phase) {
    getCachedModel();
    this.setPhase(phase);
  }

  /**
   * Obtains this thread's initialized native model, including its owned mutable calculation scratch. Resolving on each
   * call avoids retaining a constructor-thread model when a utility is subsequently used on another thread.
   *
   * @return thread-confined working model
   */
  private static EOSCG getCachedModel() {
    WorkingModel cache = MODEL_CACHE.get();
    long generation = CACHE_GENERATION.get();
    if (cache.generation != generation) {
      EOSCG model = new EOSCG();
      model.setup();
      cache.model = model;
      cache.generation = generation;
    }
    return cache.model;
  }

  /**
   * Resolves working state on the calling thread rather than retaining a constructor-thread model.
   *
   * @return this thread's working model
   */
  EOSCG model() {
    return getCachedModel();
  }

  /**
   * Invalidates all threads' EOS-CG working models on their next evaluation. An evaluation already in progress finishes
   * using its owned model. This is useful for tests and parameter reloads.
   */
  public static void clearCache() {
    CACHE_GENERATION.incrementAndGet();
    MODEL_CACHE.remove();
  }

  public double getMolarDensity(PhaseInterface phase) {
    this.setPhase(phase);
    return getMolarDensity();
  }

  public double getDensity(PhaseInterface phase) {
    this.setPhase(phase);
    return getMolarDensity() * phase.getMolarMass() * 1000.0;
  }

  public double getDensity() {
    return getMolarDensity() * phase.getMolarMass() * 1000.0;
  }

  public double getPressure() {
    return getPressure(getMolarDensity());
  }

  /**
   * Calculate pressure at a specified molar density.
   *
   * @param molarDensityMolPerL molar density in mol/L
   * @return pressure in kPa
   * @throws IllegalArgumentException if the density is not finite and positive
   */
  public double getPressure(double molarDensityMolPerL) {
    if (!Double.isFinite(molarDensityMolPerL) || molarDensityMolPerL <= 0.0) {
      throw new IllegalArgumentException("Molar density must be finite and positive");
    }
    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    model().pressure(phase.getTemperature(), molarDensityMolPerL, normalizedComposition, P, Z);
    return P.val;
  }

  public double getMolarMass() {
    doubleW mm = new doubleW(0.0);
    model().molarMass(normalizedComposition, mm);
    return mm.val / 1.0e3;
  }

  public double getMolarDensity() {
    return getMolarDensity(phase.getType());
  }

  /**
   * Get the molar-density root associated with a requested phase type.
   *
   * @param phaseType phase type selecting the vapor or liquid density root
   * @return molar density in mol/L
   * @throws IllegalStateException if the requested density root cannot be found
   */
  public double getMolarDensity(PhaseType phaseType) {
    EOSCG eosCG = model();
    boolean liquidRoot = phaseType == PhaseType.LIQUID || phaseType == PhaseType.OIL || phaseType == PhaseType.AQUEOUS;
    int flag = liquidRoot ? 2 : 0;
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    doubleW D = new doubleW(0.0);
    double pressure = phase.getPressure() * 1.0e2;
    if (liquidRoot) {
      double liquidDensity = ReferenceEosLiquidDensitySolver.solve(pressure, density -> {
        doubleW calculatedPressure = new doubleW(0.0);
        doubleW compressibility = new doubleW(0.0);
        eosCG.pressure(phase.getTemperature(), density, normalizedComposition, calculatedPressure, compressibility);
        return calculatedPressure.val;
      });
      if (Double.isFinite(liquidDensity)) {
        return liquidDensity;
      }
    }
    eosCG.density(flag, phase.getTemperature(), pressure, normalizedComposition, D, ierr, herr);
    if (ierr.val != 0 || !Double.isFinite(D.val) || D.val <= 0.0) {
      throw new IllegalStateException("EOS-CG density calculation failed for phase type " + phaseType + " at "
          + phase.getTemperature() + " K and " + phase.getPressure() + " bar: " + herr.val);
    }
    return D.val;
  }

  public double[] getProperties(PhaseInterface phase, String[] properties) {
    double[] allProperties = propertiesEOSCG();
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

  public double[] propertiesEOSCG() {
    return propertiesEOSCG(getMolarDensity());
  }

  /**
   * Calculate EOS-CG properties at a specified molar density without solving a pressure-density root.
   *
   * @param molarDensityMolPerL molar density in mol/L
   * @return property vector in the native EOS-CG units
   * @throws IllegalArgumentException if the density is not finite and positive
   */
  public double[] propertiesEOSCG(double molarDensityMolPerL) {
    if (!Double.isFinite(molarDensityMolPerL) || molarDensityMolPerL <= 0.0) {
      throw new IllegalArgumentException("Molar density must be finite and positive");
    }
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
    model().properties(phase.getTemperature(), molarDensityMolPerL, normalizedComposition, p, z, dpdd, d2pdd2, d2pdtd,
        dpdt, u, h, s, cv, cp, w, g, jt, kappa, A);
    return new double[] {p.val, z.val, dpdd.val, d2pdd2.val, d2pdtd.val, dpdt.val, u.val, h.val, s.val, cv.val, cp.val,
        w.val, g.val, jt.val, kappa.val};
  }

  public void setPhase(PhaseInterface phase) {
    this.phase = phase;
    Arrays.fill(notNormalizedComposition, 0.0);
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      String componentName = phase.getComponent(i).getComponentName();

      switch (componentName) {
      case "methane":
        notNormalizedComposition[1] = phase.getComponent(i).getx();
        break;
      case "nitrogen":
        notNormalizedComposition[2] = phase.getComponent(i).getx();
        break;
      case "CO2":
        notNormalizedComposition[3] = phase.getComponent(i).getx();
        break;
      case "ethane":
        notNormalizedComposition[4] = phase.getComponent(i).getx();
        break;
      case "propane":
        notNormalizedComposition[5] = phase.getComponent(i).getx();
        break;
      case "i-butane":
        notNormalizedComposition[6] = phase.getComponent(i).getx();
        break;
      case "n-butane":
        notNormalizedComposition[7] = phase.getComponent(i).getx();
        break;
      case "i-pentane":
        notNormalizedComposition[8] = phase.getComponent(i).getx();
        break;
      case "n-pentane":
        notNormalizedComposition[9] = phase.getComponent(i).getx();
        break;
      case "n-hexane":
        notNormalizedComposition[10] = phase.getComponent(i).getx();
        break;
      case "n-heptane":
        notNormalizedComposition[11] = phase.getComponent(i).getx();
        break;
      case "n-octane":
        notNormalizedComposition[12] = phase.getComponent(i).getx();
        break;
      case "n-nonane":
        notNormalizedComposition[13] = phase.getComponent(i).getx();
        break;
      case "nC10":
        notNormalizedComposition[14] = phase.getComponent(i).getx();
        break;
      case "hydrogen":
        notNormalizedComposition[15] = phase.getComponent(i).getx();
        break;
      case "oxygen":
        notNormalizedComposition[16] = phase.getComponent(i).getx();
        break;
      case "CO":
        notNormalizedComposition[17] = phase.getComponent(i).getx();
        break;
      case "water":
        notNormalizedComposition[18] = phase.getComponent(i).getx();
        break;
      case "H2S":
        notNormalizedComposition[19] = phase.getComponent(i).getx();
        break;
      case "helium":
        notNormalizedComposition[20] = phase.getComponent(i).getx();
        break;
      case "argon":
        notNormalizedComposition[21] = phase.getComponent(i).getx();
        break;
      case "SO2":
        notNormalizedComposition[22] = phase.getComponent(i).getx();
        break;
      case "ammonia":
        notNormalizedComposition[23] = phase.getComponent(i).getx();
        break;
      case "chlorine":
        notNormalizedComposition[24] = phase.getComponent(i).getx();
        break;
      case "HCl":
      case "hydrochloric acid":
        notNormalizedComposition[25] = phase.getComponent(i).getx();
        break;
      case "MEA":
        notNormalizedComposition[26] = phase.getComponent(i).getx();
        break;
      case "DEA":
        notNormalizedComposition[27] = phase.getComponent(i).getx();
        break;
      case "MDEA":
      case "methyldiethanolamine":
      case "methyl diethanolamine":
        notNormalizedComposition[28] = phase.getComponent(i).getx();
        break;
      default:
        double molarMass = phase.getComponent(i).getMolarMass();
        if (molarMass > 44.096759796142 / 1000.0 && molarMass < 58.1236991882324 / 1000.0) {
          notNormalizedComposition[7] += phase.getComponent(i).getx();
        }
        if (molarMass > 58.1236991882324 / 1000.0 && molarMass < 72.15064 / 1000.0) {
          notNormalizedComposition[8] += phase.getComponent(i).getx();
        }
        if (molarMass > 72.15064 / 1000.0 && molarMass < 86.2 / 1000.0) {
          notNormalizedComposition[10] += phase.getComponent(i).getx();
        }
        if (molarMass > 86.2 / 1000.0 && molarMass < 100.204498291016 / 1000.0) {
          notNormalizedComposition[11] += phase.getComponent(i).getx();
        }
        if (molarMass > 100.204498291016 / 1000.0 && molarMass < 107.0 / 1000.0) {
          notNormalizedComposition[12] += phase.getComponent(i).getx();
        }
        if (molarMass > 107.0 / 1000.0 && molarMass < 121.0 / 1000.0) {
          notNormalizedComposition[13] += phase.getComponent(i).getx();
        }
        if (molarMass > 121.0 / 1000.0) {
          notNormalizedComposition[14] += phase.getComponent(i).getx();
        }
        break;
      }
    }
    normalizeComposition();
  }

  public void normalizeComposition() {
    double result = 0;
    for (double value : notNormalizedComposition) {
      result += value;
    }
    if (result < 1.0e-30) {
      Arrays.fill(normalizedComposition, 0.0);
      return;
    }
    for (int k = 0; k < normalizedComposition.length; k++) {
      normalizedComposition[k] = notNormalizedComposition[k] / result;
    }
  }

  public doubleW[] getAlpha0_EOSCG() {
    return getAlpha0_EOSCG(getMolarDensity());
  }

  /**
   * Calculate ideal Helmholtz-energy derivatives at a specified molar density.
   *
   * @param molarDensityMolPerL molar density in mol/L
   * @return ideal Helmholtz-energy derivative vector
   */
  public doubleW[] getAlpha0_EOSCG(double molarDensityMolPerL) {
    double temperature = phase.getTemperature();
    int rows = 4;
    doubleW[] a0 = new doubleW[rows];
    for (int i = 0; i < rows; i++) {
      a0[i] = new doubleW(0.0);
    }
    model().alpha0(temperature, molarDensityMolPerL, normalizedComposition, a0);
    return a0;
  }

  public doubleW[][] getAlphares_EOSCG() {
    return getAlphares_EOSCG(getMolarDensity());
  }

  /**
   * Calculate residual Helmholtz-energy derivatives at a specified molar density.
   *
   * @param molarDensityMolPerL molar density in mol/L
   * @return residual Helmholtz-energy derivative matrix
   */
  public doubleW[][] getAlphares_EOSCG(double molarDensityMolPerL) {
    double temperature = phase.getTemperature();

    int rows = 4;
    int cols = 4;
    doubleW[][] ar = new doubleW[rows][cols];
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        ar[i][j] = new doubleW(0.0);
      }
    }

    model().alphar(2, 3, temperature, molarDensityMolPerL, normalizedComposition, ar);
    return ar;
  }

  /**
   * Calculate the dimensionless residual Helmholtz energy at a prescribed molar density.
   *
   * <p>
   * This method is used for constant-volume composition derivatives. The composition is the normalized EOS-CG
   * composition captured from the phase supplied to this wrapper.
   * </p>
   *
   * @param temperature temperature in K
   * @param molarDensity molar density in mol/L
   * @return dimensionless residual Helmholtz energy, {@code alphaR = Ares/(nRT)}
   */
  public double getResidualHelmholtzEnergy(double temperature, double molarDensity) {
    doubleW[][] residualHelmholtz = new doubleW[4][4];
    for (int i = 0; i < residualHelmholtz.length; i++) {
      for (int j = 0; j < residualHelmholtz[i].length; j++) {
        residualHelmholtz[i][j] = new doubleW(0.0);
      }
    }
    model().alphar(1, 0, temperature, molarDensity, normalizedComposition, residualHelmholtz);
    return residualHelmholtz[0][0].val;
  }
}
