package neqsim.process.fielddevelopment.wellplanning;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tubing movement and load calculator for a vertical completion string (Lubinski and Mitchell formulation).
 *
 * <p>
 * For a change in internal and external pressure and average temperature the free-packer length change is the sum of
 * </p>
 * <ul>
 * <li>piston effect dL1 = -L F / (E A_s), F = (A_p - A_i) dP_i - (A_p - A_o) dP_o at the packer</li>
 * <li>ballooning dL2 = -2 nu L (dPi_avg - R^2 dPo_avg) / (E (R^2 - 1)), R = OD / ID</li>
 * <li>temperature dL3 = alpha L dT</li>
 * <li>helical buckling dL4 = -r^2 F_f^2 / (8 E I w_e) when the fictitious force F_f (compression) exceeds the helical
 * threshold 2 sqrt(2) * 2 sqrt(E I w_e / r)</li>
 * </ul>
 * <p>
 * For a fixed packer the restrained length change gives an axial force change of -E A_s (dL1 + dL2 + dL3) / L; a
 * travel-limited seal assembly restrains only the part beyond its stroke. A von Mises (triaxial) check with Lame hoop
 * and radial stresses at the inner wall is provided for the combined load.
 * </p>
 * <p>
 * Limitations: vertical well, uniform properties, no friction or bending in deviated sections, no initial landing force
 * history, no thermal gradient along the string beyond the average temperature change. Validation is by closed-form
 * hand calculations only; it is a screening and not a WellCat or StressCheck replacement.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TubingMovementCalculator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Packer or seal-assembly restraint type. */
  public enum PackerMode {
    /** Free to move (seals in a polished bore receptacle). */
    FREE,
    /** Rigidly fixed at the packer. */
    FIXED,
    /** Free within a limited stroke, restrained beyond it. */
    LIMITED_STROKE
  }

  private double lengthM = 3000.0;
  private double outerDiameterM = 0.1143;
  private double innerDiameterM = 0.0985;
  private double packerBoreDiameterM = 0.1200;
  private double casingInnerDiameterM = 0.1594;
  private double youngModulusPa = 207.0e9;
  private double poisson = 0.3;
  private double expansionPerK = 1.2e-5;
  private double steelDensity = 7850.0;
  private double internalFluidDensity = 800.0;
  private double externalFluidDensity = 1000.0;
  private PackerMode mode = PackerMode.FREE;
  private double strokeM = 0.0;
  private double setDownForceN = 0.0;
  private double dPiPackerBar = 0.0;
  private double dPoPackerBar = 0.0;
  private double dPiAvgBar = 0.0;
  private double dPoAvgBar = 0.0;
  private double deltaTK = 0.0;

  /**
   * Creates a calculator with 4 1/2 in tubing defaults.
   */
  public TubingMovementCalculator() {
  }

  /**
   * Sets the string length.
   *
   * @param lengthM free length between hanger and packer in m
   * @return this calculator
   */
  public TubingMovementCalculator setLength(double lengthM) {
    this.lengthM = lengthM;
    return this;
  }

  /**
   * Sets tubing, packer and casing diameters.
   *
   * @param outerDiameterM tubing outer diameter in m
   * @param innerDiameterM tubing inner diameter in m
   * @param packerBoreDiameterM packer seal-bore diameter in m
   * @param casingInnerDiameterM casing inner diameter in m (for buckling clearance)
   * @return this calculator
   * @throws IllegalArgumentException if the diameters are inconsistent
   */
  public TubingMovementCalculator setGeometry(double outerDiameterM, double innerDiameterM, double packerBoreDiameterM,
      double casingInnerDiameterM) {
    if (innerDiameterM >= outerDiameterM || casingInnerDiameterM <= outerDiameterM) {
      throw new IllegalArgumentException("require tubing ID < tubing OD < casing ID");
    }
    this.outerDiameterM = outerDiameterM;
    this.innerDiameterM = innerDiameterM;
    this.packerBoreDiameterM = packerBoreDiameterM;
    this.casingInnerDiameterM = casingInnerDiameterM;
    return this;
  }

  /**
   * Sets steel properties.
   *
   * @param youngModulusGPa Young modulus in GPa
   * @param poissonRatio Poisson ratio
   * @param expansionPerK linear thermal expansion in 1/K
   * @return this calculator
   */
  public TubingMovementCalculator setSteel(double youngModulusGPa, double poissonRatio, double expansionPerK) {
    this.youngModulusPa = youngModulusGPa * 1.0e9;
    this.poisson = poissonRatio;
    this.expansionPerK = expansionPerK;
    return this;
  }

  /**
   * Sets fluid densities inside and outside the tubing (for the buckling weight).
   *
   * @param internalKgPerM3 tubing fluid density in kg/m3
   * @param externalKgPerM3 annulus fluid density in kg/m3
   * @return this calculator
   */
  public TubingMovementCalculator setFluidDensities(double internalKgPerM3, double externalKgPerM3) {
    this.internalFluidDensity = internalKgPerM3;
    this.externalFluidDensity = externalKgPerM3;
    return this;
  }

  /**
   * Sets the restraint type.
   *
   * @param mode restraint type
   * @param strokeM stroke in m for LIMITED_STROKE (ignored otherwise)
   * @return this calculator
   */
  public TubingMovementCalculator setPackerMode(PackerMode mode, double strokeM) {
    this.mode = mode;
    this.strokeM = strokeM;
    return this;
  }

  /**
   * Sets the load change: pressures at the packer, mean pressures along the string and mean temperature.
   *
   * @param dPiPackerBar internal pressure change at the packer in bar
   * @param dPoPackerBar annulus pressure change at the packer in bar
   * @param dPiMeanBar mean internal pressure change along the string in bar
   * @param dPoMeanBar mean annulus pressure change along the string in bar
   * @param deltaTK mean tubing temperature change in K
   * @return this calculator
   */
  public TubingMovementCalculator setLoadChange(double dPiPackerBar, double dPoPackerBar, double dPiMeanBar,
      double dPoMeanBar, double deltaTK) {
    this.dPiPackerBar = dPiPackerBar;
    this.dPoPackerBar = dPoPackerBar;
    this.dPiAvgBar = dPiMeanBar;
    this.dPoAvgBar = dPoMeanBar;
    this.deltaTK = deltaTK;
    return this;
  }

  /**
   * Sets the compressive set-down force applied at the packer on landing.
   *
   * @param newtons compressive force in N (positive compresses the string)
   * @return this calculator
   */
  public TubingMovementCalculator setSetDownForce(double newtons) {
    this.setDownForceN = newtons;
    return this;
  }

  /**
   * Returns the steel cross-section area.
   *
   * @return area in m2
   */
  public double getSteelArea() {
    return Math.PI / 4.0 * (outerDiameterM * outerDiameterM - innerDiameterM * innerDiameterM);
  }

  /**
   * Calculates the movement and forces.
   *
   * @return result
   */
  public Result calculate() {
    double as = getSteelArea();
    double ai = Math.PI / 4.0 * innerDiameterM * innerDiameterM;
    double ao = Math.PI / 4.0 * outerDiameterM * outerDiameterM;
    double ap = Math.PI / 4.0 * packerBoreDiameterM * packerBoreDiameterM;
    double e = youngModulusPa;
    double fPiston = (ap - ai) * dPiPackerBar * 1.0e5 - (ap - ao) * dPoPackerBar * 1.0e5;
    double r2 = outerDiameterM * outerDiameterM / (innerDiameterM * innerDiameterM);
    Result res = new Result();
    res.pistonForceN = fPiston;
    res.pistonDeltaLM = -lengthM * fPiston / (e * as);
    res.balloonDeltaLM = -2.0 * poisson * lengthM * (dPiAvgBar * 1.0e5 - r2 * dPoAvgBar * 1.0e5) / (e * (r2 - 1.0));
    res.temperatureDeltaLM = expansionPerK * lengthM * deltaTK;
    double inertia = Math.PI / 64.0 * (Math.pow(outerDiameterM, 4.0) - Math.pow(innerDiameterM, 4.0));
    double g = 9.80665;
    double we = steelDensity * g * as + internalFluidDensity * g * ai - externalFluidDensity * g * ao;
    double clearance = 0.5 * (casingInnerDiameterM - outerDiameterM);
    double fFict = fPiston + setDownForceN;
    res.fictitiousForceN = fFict;
    res.effectiveWeightNPerM = we;
    if (we > 0.0) {
      res.sinusoidalBucklingForceN = 2.0 * Math.sqrt(e * inertia * we / clearance);
      res.helicalBucklingForceN = 2.0 * Math.sqrt(2.0) * res.sinusoidalBucklingForceN;
      res.sinusoidalBuckled = fFict > res.sinusoidalBucklingForceN;
      res.helicalBuckled = fFict > res.helicalBucklingForceN;
      if (res.helicalBuckled) {
        res.bucklingDeltaLM = -clearance * clearance * fFict * fFict / (8.0 * e * inertia * we);
      }
    }
    double unbuckled = res.pistonDeltaLM + res.balloonDeltaLM + res.temperatureDeltaLM;
    res.freeDeltaLM = unbuckled + res.bucklingDeltaLM;
    double restrained;
    switch (mode) {
    case FIXED:
      restrained = unbuckled;
      break;
    case LIMITED_STROKE:
      restrained = 0.0;
      if (unbuckled > strokeM) {
        restrained = unbuckled - strokeM;
      } else if (unbuckled < -strokeM) {
        restrained = unbuckled + strokeM;
      }
      break;
    default:
      restrained = 0.0;
      break;
    }
    res.axialForceChangeN = -e * as * restrained / lengthM;
    res.axialStressChangePa = res.axialForceChangeN / as;
    res.movementAtPackerM = mode == PackerMode.FIXED ? 0.0 : res.freeDeltaLM - restrained;
    return res;
  }

  /**
   * Performs a von Mises triaxial check at the tubing inner wall.
   *
   * @param internalPressureBara internal pressure in bara
   * @param externalPressureBara external pressure in bara
   * @param effectiveTensionN effective (buoyed) axial tension in N, tension positive
   * @param yieldStrengthMPa minimum yield strength in MPa
   * @return triaxial result with the von Mises stress and design factor
   */
  public Triaxial vonMises(double internalPressureBara, double externalPressureBara, double effectiveTensionN,
      double yieldStrengthMPa) {
    double a = innerDiameterM / 2.0;
    double b = outerDiameterM / 2.0;
    double pi = internalPressureBara * 1.0e5;
    double po = externalPressureBara * 1.0e5;
    double as = getSteelArea();
    double ai = Math.PI * a * a;
    double ao = Math.PI * b * b;
    double sigmaH = (pi * (b * b + a * a) - 2.0 * po * b * b) / (b * b - a * a);
    double sigmaR = -pi;
    double sigmaA = (effectiveTensionN + pi * ai - po * ao) / as;
    double vm = Math.sqrt(
        sigmaA * sigmaA + sigmaH * sigmaH + sigmaR * sigmaR - sigmaA * sigmaH - sigmaH * sigmaR - sigmaR * sigmaA);
    Triaxial t = new Triaxial();
    t.axialStressPa = sigmaA;
    t.hoopStressPa = sigmaH;
    t.radialStressPa = sigmaR;
    t.vonMisesPa = vm;
    t.designFactor = yieldStrengthMPa * 1.0e6 / Math.max(vm, 1.0e-9);
    return t;
  }

  /** Result of a tubing movement calculation. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private double pistonForceN;
    private double pistonDeltaLM;
    private double balloonDeltaLM;
    private double temperatureDeltaLM;
    private double bucklingDeltaLM;
    private double freeDeltaLM;
    private double fictitiousForceN;
    private double effectiveWeightNPerM;
    private double sinusoidalBucklingForceN = Double.NaN;
    private double helicalBucklingForceN = Double.NaN;
    private boolean sinusoidalBuckled;
    private boolean helicalBuckled;
    private double axialForceChangeN;
    private double axialStressChangePa;
    private double movementAtPackerM;

    /**
     * Gets the piston force at the packer.
     *
     * @return force in N (positive pushes the tubing up)
     */
    public double getPistonForceN() {
      return pistonForceN;
    }

    /**
     * Gets the piston-effect length change.
     *
     * @return length change in m (positive is elongation)
     */
    public double getPistonDeltaLM() {
      return pistonDeltaLM;
    }

    /**
     * Gets the ballooning length change.
     *
     * @return length change in m
     */
    public double getBalloonDeltaLM() {
      return balloonDeltaLM;
    }

    /**
     * Gets the temperature length change.
     *
     * @return length change in m
     */
    public double getTemperatureDeltaLM() {
      return temperatureDeltaLM;
    }

    /**
     * Gets the helical buckling length change.
     *
     * @return length change in m (zero if not helically buckled)
     */
    public double getBucklingDeltaLM() {
      return bucklingDeltaLM;
    }

    /**
     * Gets the total free (unrestrained) length change.
     *
     * @return length change in m
     */
    public double getFreeDeltaLM() {
      return freeDeltaLM;
    }

    /**
     * Gets the fictitious (buckling) force including set-down.
     *
     * @return force in N, compression positive
     */
    public double getFictitiousForceN() {
      return fictitiousForceN;
    }

    /**
     * Gets the effective weight per length of tubing used for buckling.
     *
     * @return weight in N/m
     */
    public double getEffectiveWeightNPerM() {
      return effectiveWeightNPerM;
    }

    /**
     * Gets the sinusoidal buckling threshold force.
     *
     * @return force in N, NaN if the effective weight is not positive
     */
    public double getSinusoidalBucklingForceN() {
      return sinusoidalBucklingForceN;
    }

    /**
     * Gets the helical buckling threshold force.
     *
     * @return force in N, NaN if the effective weight is not positive
     */
    public double getHelicalBucklingForceN() {
      return helicalBucklingForceN;
    }

    /**
     * Checks for sinusoidal buckling.
     *
     * @return true if the fictitious force exceeds the sinusoidal threshold
     */
    public boolean isSinusoidalBuckled() {
      return sinusoidalBuckled;
    }

    /**
     * Checks for helical buckling.
     *
     * @return true if the fictitious force exceeds the helical threshold
     */
    public boolean isHelicalBuckled() {
      return helicalBuckled;
    }

    /**
     * Gets the axial force change caused by restraint (tension positive).
     *
     * @return force change in N
     */
    public double getAxialForceChangeN() {
      return axialForceChangeN;
    }

    /**
     * Gets the axial stress change caused by restraint.
     *
     * @return stress change in Pa
     */
    public double getAxialStressChangePa() {
      return axialStressChangePa;
    }

    /**
     * Gets the seal-assembly movement at the packer.
     *
     * @return movement in m (positive is elongation, zero for a fixed packer)
     */
    public double getMovementAtPackerM() {
      return movementAtPackerM;
    }

    /**
     * Returns the results as a map.
     *
     * @return ordered map of result names to values
     */
    public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<String, Object>();
      m.put("pistonDeltaLM", pistonDeltaLM);
      m.put("balloonDeltaLM", balloonDeltaLM);
      m.put("temperatureDeltaLM", temperatureDeltaLM);
      m.put("bucklingDeltaLM", bucklingDeltaLM);
      m.put("freeDeltaLM", freeDeltaLM);
      m.put("axialForceChangeN", axialForceChangeN);
      m.put("helicalBuckled", helicalBuckled);
      return m;
    }
  }

  /** Triaxial stress state at the tubing inner wall. */
  public static final class Triaxial implements Serializable {
    private static final long serialVersionUID = 1000L;
    private double axialStressPa;
    private double hoopStressPa;
    private double radialStressPa;
    private double vonMisesPa;
    private double designFactor;

    /**
     * Gets the axial stress.
     *
     * @return stress in Pa
     */
    public double getAxialStressPa() {
      return axialStressPa;
    }

    /**
     * Gets the hoop stress.
     *
     * @return stress in Pa
     */
    public double getHoopStressPa() {
      return hoopStressPa;
    }

    /**
     * Gets the radial stress.
     *
     * @return stress in Pa
     */
    public double getRadialStressPa() {
      return radialStressPa;
    }

    /**
     * Gets the von Mises equivalent stress.
     *
     * @return stress in Pa
     */
    public double getVonMisesPa() {
      return vonMisesPa;
    }

    /**
     * Gets the triaxial design factor yield / von Mises.
     *
     * @return design factor
     */
    public double getDesignFactor() {
      return designFactor;
    }
  }
}
