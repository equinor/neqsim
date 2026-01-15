package neqsim.thermo.util.gerg;

import java.util.Arrays;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * Wrapper for calling the EOS-CG multi-fluid model using the GERG-2008 API.
 *
 * <p>
 * This class provides a wrapper around the EOS-CG (Equation of State for Combustion Gases)
 * multi-fluid model. The EOS-CG parameters are cached statically to avoid expensive
 * re-initialization on every call, which significantly improves performance for applications that
 * make repeated property calculations.
 * </p>
 */
public class NeqSimEOSCG {
  /**
   * Cached EOS-CG model instance. The setup() method initializes constant parameters, so caching
   * provides significant performance improvement for repeated calculations.
   */
  private static volatile EOSCG cachedModel = null;
  private static final Object CACHE_LOCK = new Object();

  double[] normalizedComposition = new double[27 + 1];
  double[] notNormalizedComposition = new double[27 + 1];
  PhaseInterface phase = null;
  EOSCG eosCG;

  /**
   * Default constructor.
   */
  public NeqSimEOSCG() {
    this.eosCG = getCachedModel();
  }

  /**
   * Constructor with phase.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public NeqSimEOSCG(PhaseInterface phase) {
    this.eosCG = getCachedModel();
    this.setPhase(phase);
  }

  /**
   * Gets a cached and initialized EOS-CG model instance. If no cached instance exists, creates one
   * and initializes it with setup(). This method is thread-safe.
   *
   * @return a cached, initialized EOSCG instance
   */
  private static EOSCG getCachedModel() {
    if (cachedModel == null) {
      synchronized (CACHE_LOCK) {
        if (cachedModel == null) {
          EOSCG model = new EOSCG();
          model.setup();
          cachedModel = model;
        }
      }
    }
    return cachedModel;
  }

  /**
   * Clears the cached EOS-CG model instance. This is primarily useful for testing or when
   * parameters need to be reloaded.
   */
  public static void clearCache() {
    synchronized (CACHE_LOCK) {
      cachedModel = null;
    }
  }

  public double getMolarDensity(PhaseInterface phase) {
    this.setPhase(phase);
    return getMolarDensity();
  }

  public double getDensity(PhaseInterface phase) {
    this.setPhase(phase);
    return getMolarDensity() * phase.getMolarMass();
  }

  public double getDensity() {
    return getMolarDensity() * phase.getMolarMass();
  }

  public double getPressure() {
    double moldens = getMolarDensity();
    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    eosCG.pressure(phase.getTemperature(), moldens, normalizedComposition, P, Z);
    return P.val;
  }

  public double getMolarMass() {
    doubleW mm = new doubleW(0.0);
    eosCG.molarMass(normalizedComposition, mm);
    return mm.val / 1.0e3;
  }

  public double getMolarDensity() {
    int flag = 0;
    if (phase != null) {
      PhaseType type = phase.getType();
      if (type == PhaseType.LIQUID || type == PhaseType.OIL || type == PhaseType.AQUEOUS) {
        flag = 2; // search for high density root
      }
    }
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    doubleW D = new doubleW(0.0);
    double pressure = phase.getPressure() * 1.0e2;
    eosCG.density(flag, phase.getTemperature(), pressure, normalizedComposition, D, ierr, herr);
    if (ierr.val != 0) {
      // System.out.println("NeqSimEOSCG: Density solver failed. ierr=" + ierr.val + ", herr=" +
      // herr.val);
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
    eosCG.properties(phase.getTemperature(), dens, normalizedComposition, p, z, dpdd, d2pdd2,
        d2pdtd, dpdt, u, h, s, cv, cp, w, g, jt, kappa, A);
    return new double[] {p.val, z.val, dpdd.val, d2pdd2.val, d2pdtd.val, dpdt.val, u.val, h.val,
        s.val, cv.val, cp.val, w.val, g.val, jt.val, kappa.val};
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
    for (int k = 0; k < normalizedComposition.length; k++) {
      normalizedComposition[k] = notNormalizedComposition[k] / result;
    }
  }

  public doubleW[] getAlpha0_EOSCG() {
    double temperature = phase.getTemperature();
    double molarDensity = getMolarDensity(phase);
    int rows = 4;
    doubleW[] a0 = new doubleW[rows];
    for (int i = 0; i < rows; i++) {
      a0[i] = new doubleW(0.0);
    }
    eosCG.alpha0(temperature, molarDensity, normalizedComposition, a0);
    return a0;
  }

  public doubleW[][] getAlphares_EOSCG() {
    double temperature = phase.getTemperature();
    double molarDensity = getMolarDensity(phase);

    int rows = 4;
    int cols = 4;
    doubleW[][] ar = new doubleW[rows][cols];
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        ar[i][j] = new doubleW(0.0);
      }
    }

    eosCG.alphar(2, 3, temperature, molarDensity, normalizedComposition, ar);
    return ar;
  }
}
