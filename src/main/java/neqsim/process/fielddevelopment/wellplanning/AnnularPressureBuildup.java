package neqsim.process.fielddevelopment.wellplanning;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.mechanicaldesign.subsea.WellIntegrityScreening;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Annular pressure build-up (APB) in a trapped, sealed well annulus.
 *
 * <p>
 * The pressure increase follows from volume compatibility of the trapped fluid and the annulus:
 * </p>
 * <p>
 * V0 alpha dT - V0 kappa dP - V_bled = C dP + D + V_gas dP / (P0 + dP)
 * </p>
 * <ul>
 * <li>alpha, kappa: fluid thermal expansivity (1/K) and isothermal compressibility (1/bar), constants or from a NeqSim
 * fluid by finite differences</li>
 * <li>C: annulus compliance from the Lame thick-wall radial displacement of the outer string (internal pressure dP) and
 * the inner string (external pressure dP)</li>
 * <li>D: volume change that does not depend on the annular pressure: thermal expansion of the steel strings and the
 * inner string ballooning from a tubing pressure change</li>
 * <li>V_gas: optional compressible cushion (ideal gas, isothermal) at initial pressure P0</li>
 * </ul>
 * <p>
 * The residual is monotone in dP and solved by bisection. The result can be exported as a
 * {@link WellIntegrityScreening.AnnulusReading} for MAASP comparison. Limitations: uniform temperature change per
 * annulus, no cement bond or formation compliance (outer string treated as free standing unless rigid boundaries are
 * selected), no fluid leak-off, no gas dissolution. Validation is by closed-form hand calculations (rigid-annulus limit
 * dP = alpha dT / kappa and the Lame compliance formula) only.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class AnnularPressureBuildup implements Serializable {
  private static final long serialVersionUID = 1000L;

  private double lengthM = 1000.0;
  private double innerStringOuterRadiusM = 0.1397 / 2.0;
  private double innerStringInnerRadiusM = 0.1215 / 2.0;
  private double outerStringInnerRadiusM = 0.2204 / 2.0;
  private double outerStringOuterRadiusM = 0.2445 / 2.0;
  private double youngModulusPa = 207.0e9;
  private double poisson = 0.3;
  private double steelExpansionPerK = 1.2e-5;
  private double alphaPerK = 3.0e-4;
  private double kappaPerBar = 4.5e-5;
  private double deltaTFluidK = 0.0;
  private double deltaTInnerK = Double.NaN;
  private double deltaTOuterK = Double.NaN;
  private double initialPressureBara = 1.0;
  private double bledVolumeM3 = 0.0;
  private double gasCushionM3 = 0.0;
  private double innerStringInternalPressureChangeBar = 0.0;
  private boolean rigidBoundaries = false;

  /**
   * Creates a calculator with generic 9 5/8 in by 5 1/2 in annulus defaults and water-like fluid constants.
   */
  public AnnularPressureBuildup() {
  }

  /**
   * Sets the annulus length (the section of constant geometry).
   *
   * @param lengthM length in m
   * @return this calculator
   */
  public AnnularPressureBuildup setAnnulusLength(double lengthM) {
    this.lengthM = lengthM;
    return this;
  }

  /**
   * Sets the radial geometry.
   *
   * @param innerStringOuterDiameterM outer diameter of the inner string (tubing or inner casing) in m
   * @param innerStringInnerDiameterM inner diameter of the inner string in m
   * @param outerStringInnerDiameterM inner diameter of the outer casing in m
   * @param outerStringOuterDiameterM outer diameter of the outer casing in m
   * @return this calculator
   * @throws IllegalArgumentException if the diameters are not nested
   */
  public AnnularPressureBuildup setGeometry(double innerStringOuterDiameterM, double innerStringInnerDiameterM,
      double outerStringInnerDiameterM, double outerStringOuterDiameterM) {
    if (innerStringInnerDiameterM >= innerStringOuterDiameterM || innerStringOuterDiameterM >= outerStringInnerDiameterM
        || outerStringInnerDiameterM >= outerStringOuterDiameterM) {
      throw new IllegalArgumentException("diameters must be nested: inner ID < inner OD < outer ID < outer OD");
    }
    this.innerStringOuterRadiusM = innerStringOuterDiameterM / 2.0;
    this.innerStringInnerRadiusM = innerStringInnerDiameterM / 2.0;
    this.outerStringInnerRadiusM = outerStringInnerDiameterM / 2.0;
    this.outerStringOuterRadiusM = outerStringOuterDiameterM / 2.0;
    return this;
  }

  /**
   * Sets steel properties.
   *
   * @param youngModulusGPa Young modulus in GPa
   * @param poissonRatio Poisson ratio
   * @param expansionPerK linear thermal expansion coefficient in 1/K
   * @return this calculator
   */
  public AnnularPressureBuildup setSteel(double youngModulusGPa, double poissonRatio, double expansionPerK) {
    this.youngModulusPa = youngModulusGPa * 1.0e9;
    this.poisson = poissonRatio;
    this.steelExpansionPerK = expansionPerK;
    return this;
  }

  /**
   * Sets constant fluid properties.
   *
   * @param thermalExpansivityPerK volumetric thermal expansivity in 1/K
   * @param compressibilityPerBar isothermal compressibility in 1/bar
   * @return this calculator
   */
  public AnnularPressureBuildup setFluidProperties(double thermalExpansivityPerK, double compressibilityPerBar) {
    this.alphaPerK = thermalExpansivityPerK;
    this.kappaPerBar = compressibilityPerBar;
    return this;
  }

  /**
   * Derives expansivity and compressibility from a NeqSim liquid fluid by central finite differences of the TP-flash
   * density at the mean temperature and initial pressure, and stores them.
   *
   * @param fluid trapped fluid (for example water or water and MEG with CPA), liquid at the conditions
   * @param initialPressureBara initial annulus pressure in bara
   * @param initialTemperatureK initial temperature in K
   * @return array of expansivity (1/K) and compressibility (1/bar)
   */
  public double[] setFluidFromThermoSystem(SystemInterface fluid, double initialPressureBara,
      double initialTemperatureK) {
    double tMean = initialTemperatureK + 0.5 * deltaTFluidK;
    double pMean = initialPressureBara;
    double dT = 1.0;
    double dP = 5.0;
    double rho0 = density(fluid, pMean, tMean);
    double rhoTp = density(fluid, pMean, tMean + dT);
    double rhoTm = density(fluid, pMean, tMean - dT);
    double rhoPp = density(fluid, pMean + dP, tMean);
    double rhoPm = density(fluid, Math.max(pMean - dP, 0.5), tMean);
    this.alphaPerK = -(rhoTp - rhoTm) / (2.0 * dT * rho0);
    this.kappaPerBar = (rhoPp - rhoPm) / ((pMean + dP - Math.max(pMean - dP, 0.5)) * rho0);
    this.initialPressureBara = initialPressureBara;
    return new double[] {alphaPerK, kappaPerBar};
  }

  /**
   * Flashes a clone and returns its mixture density.
   *
   * @param fluid fluid
   * @param pBar pressure in bara
   * @param tK temperature in K
   * @return density in kg/m3
   */
  private static double density(SystemInterface fluid, double pBar, double tK) {
    SystemInterface f = fluid.clone();
    f.setPressure(pBar);
    f.setTemperature(tK);
    new ThermodynamicOperations(f).TPflash();
    f.initProperties();
    return f.getDensity("kg/m3");
  }

  /**
   * Sets the average annulus fluid temperature change; steel strings follow the fluid unless set separately.
   *
   * @param deltaTK temperature increase in K
   * @return this calculator
   */
  public AnnularPressureBuildup setTemperatureChange(double deltaTK) {
    this.deltaTFluidK = deltaTK;
    return this;
  }

  /**
   * Sets separate temperature changes of the inner and outer strings.
   *
   * @param innerStringDeltaTK inner string temperature change in K
   * @param outerStringDeltaTK outer string temperature change in K
   * @return this calculator
   */
  public AnnularPressureBuildup setStringTemperatureChanges(double innerStringDeltaTK, double outerStringDeltaTK) {
    this.deltaTInnerK = innerStringDeltaTK;
    this.deltaTOuterK = outerStringDeltaTK;
    return this;
  }

  /**
   * Sets the initial annulus pressure.
   *
   * @param bara initial pressure in bara
   * @return this calculator
   */
  public AnnularPressureBuildup setInitialPressure(double bara) {
    this.initialPressureBara = bara;
    return this;
  }

  /**
   * Sets the fluid volume bled from the annulus (at the final state).
   *
   * @param bledVolumeM3 bled volume in m3
   * @return this calculator
   */
  public AnnularPressureBuildup setBledVolume(double bledVolumeM3) {
    this.bledVolumeM3 = bledVolumeM3;
    return this;
  }

  /**
   * Sets a compressible gas cushion (ideal gas, isothermal) at the initial annulus pressure.
   *
   * @param volumeM3 cushion volume at the initial pressure in m3
   * @return this calculator
   */
  public AnnularPressureBuildup setGasCushionVolume(double volumeM3) {
    this.gasCushionM3 = volumeM3;
    return this;
  }

  /**
   * Sets the pressure change inside the inner string (for ballooning of the inner string).
   *
   * @param deltaPBar pressure change in bar
   * @return this calculator
   */
  public AnnularPressureBuildup setInnerStringInternalPressureChange(double deltaPBar) {
    this.innerStringInternalPressureChangeBar = deltaPBar;
    return this;
  }

  /**
   * Selects rigid boundaries (no wall deformation or steel expansion), the classical upper bound.
   *
   * @param rigid true for rigid boundaries
   * @return this calculator
   */
  public AnnularPressureBuildup setRigidBoundaries(boolean rigid) {
    this.rigidBoundaries = rigid;
    return this;
  }

  /**
   * Returns the annulus volume.
   *
   * @return volume in m3
   */
  public double getAnnulusVolumeM3() {
    return Math.PI
        * (outerStringInnerRadiusM * outerStringInnerRadiusM - innerStringOuterRadiusM * innerStringOuterRadiusM)
        * lengthM;
  }

  /**
   * Radial displacement of a thick-walled cylinder (plane stress Lame solution).
   *
   * @param a inner radius in m
   * @param b outer radius in m
   * @param pi internal pressure in Pa
   * @param po external pressure in Pa
   * @param r radius of evaluation in m
   * @return displacement in m (positive outward)
   */
  private double lameDisplacement(double a, double b, double pi, double po, double r) {
    double b2 = b * b;
    double a2 = a * a;
    double e = youngModulusPa;
    return ((1.0 - poisson) * (pi * a2 - po * b2) / (b2 - a2) * r
        + (1.0 + poisson) * (pi - po) * a2 * b2 / ((b2 - a2) * r)) / e;
  }

  /**
   * Returns the annulus compliance, the volume increase per unit annular pressure from wall deformation.
   *
   * @return compliance in m3/bar
   */
  public double getComplianceM3PerBar() {
    if (rigidBoundaries) {
      return 0.0;
    }
    double unit = 1.0e5;
    double uOuter = lameDisplacement(outerStringInnerRadiusM, outerStringOuterRadiusM, unit, 0.0,
        outerStringInnerRadiusM);
    double uInner = lameDisplacement(innerStringInnerRadiusM, innerStringOuterRadiusM, 0.0, unit,
        innerStringOuterRadiusM);
    return 2.0 * Math.PI * lengthM * (outerStringInnerRadiusM * uOuter - innerStringOuterRadiusM * uInner);
  }

  /**
   * Returns the pressure-independent volume change of the annulus (steel thermal expansion and inner-string ballooning
   * from the tubing pressure change).
   *
   * @return volume change in m3
   */
  public double getFixedVolumeChangeM3() {
    if (rigidBoundaries) {
      return 0.0;
    }
    double dTi = Double.isNaN(deltaTInnerK) ? deltaTFluidK : deltaTInnerK;
    double dTo = Double.isNaN(deltaTOuterK) ? deltaTFluidK : deltaTOuterK;
    double thermal = 2.0 * Math.PI * lengthM * steelExpansionPerK
        * (outerStringInnerRadiusM * outerStringInnerRadiusM * dTo
            - innerStringOuterRadiusM * innerStringOuterRadiusM * dTi);
    double uBalloon = lameDisplacement(innerStringInnerRadiusM, innerStringOuterRadiusM,
        innerStringInternalPressureChangeBar * 1.0e5, 0.0, innerStringOuterRadiusM);
    double balloon = -2.0 * Math.PI * lengthM * innerStringOuterRadiusM * uBalloon;
    return thermal + balloon;
  }

  /**
   * Solves for the annular pressure change.
   *
   * @return result with pressure change, final pressure and volume breakdown
   */
  public Result calculate() {
    double v0 = getAnnulusVolumeM3();
    double c = getComplianceM3PerBar();
    double d = getFixedVolumeChangeM3();
    double p0 = initialPressureBara;
    double lo = -(p0 - 0.1);
    double hi = 5000.0;
    for (int i = 0; i < 200; i++) {
      double mid = 0.5 * (lo + hi);
      if (residual(mid, v0, c, d, p0) > 0.0) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    double dp = 0.5 * (lo + hi);
    Result r = new Result();
    r.deltaPressureBar = dp;
    r.finalPressureBara = p0 + dp;
    r.annulusVolumeM3 = v0;
    r.complianceM3PerBar = c;
    r.fixedVolumeChangeM3 = d;
    r.freeFluidExpansionM3 = v0 * alphaPerK * deltaTFluidK;
    r.fluidCompressionM3 = v0 * kappaPerBar * dp;
    r.rigidLimitBar = kappaPerBar > 0.0 ? alphaPerK * deltaTFluidK / kappaPerBar : Double.NaN;
    return r;
  }

  /**
   * Residual of the volume balance, positive when the pressure is too low.
   *
   * @param dp annular pressure change in bar
   * @param v0 annulus volume in m3
   * @param c compliance in m3/bar
   * @param d fixed volume change in m3
   * @param p0 initial pressure in bara
   * @return fluid volume minus available volume in m3
   */
  private double residual(double dp, double v0, double c, double d, double p0) {
    double fluid = v0 * alphaPerK * deltaTFluidK - v0 * kappaPerBar * dp - bledVolumeM3;
    double gas = gasCushionM3 > 0.0 ? gasCushionM3 * dp / (p0 + dp) : 0.0;
    double room = c * dp + d + gas;
    return fluid - room;
  }

  /** Result of an APB calculation. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private double deltaPressureBar;
    private double finalPressureBara;
    private double annulusVolumeM3;
    private double complianceM3PerBar;
    private double fixedVolumeChangeM3;
    private double freeFluidExpansionM3;
    private double fluidCompressionM3;
    private double rigidLimitBar;

    /**
     * Gets the pressure change.
     *
     * @return pressure change in bar
     */
    public double getDeltaPressureBar() {
      return deltaPressureBar;
    }

    /**
     * Gets the final annular pressure.
     *
     * @return pressure in bara
     */
    public double getFinalPressureBara() {
      return finalPressureBara;
    }

    /**
     * Gets the annulus volume.
     *
     * @return volume in m3
     */
    public double getAnnulusVolumeM3() {
      return annulusVolumeM3;
    }

    /**
     * Gets the wall-deformation compliance.
     *
     * @return compliance in m3/bar
     */
    public double getComplianceM3PerBar() {
      return complianceM3PerBar;
    }

    /**
     * Gets the pressure-independent annulus volume change.
     *
     * @return volume change in m3
     */
    public double getFixedVolumeChangeM3() {
      return fixedVolumeChangeM3;
    }

    /**
     * Gets the unrestrained thermal expansion of the fluid.
     *
     * @return volume in m3
     */
    public double getFreeFluidExpansionM3() {
      return freeFluidExpansionM3;
    }

    /**
     * Gets the fluid volume lost to compression.
     *
     * @return volume in m3
     */
    public double getFluidCompressionM3() {
      return fluidCompressionM3;
    }

    /**
     * Gets the rigid-annulus upper bound alpha dT / kappa.
     *
     * @return pressure change in bar
     */
    public double getRigidLimitBar() {
      return rigidLimitBar;
    }

    /**
     * Converts the predicted annulus pressure into a reading for {@link WellIntegrityScreening}.
     *
     * @param annulusId annulus identifier, for example A
     * @param maaspBara maximum allowable annulus surface pressure in bara
     * @return annulus reading with the predicted pressure
     */
    public WellIntegrityScreening.AnnulusReading toAnnulusReading(String annulusId, double maaspBara) {
      return new WellIntegrityScreening.AnnulusReading(annulusId, finalPressureBara, maaspBara)
          .setThermalEffectsExcluded(false);
    }

    /**
     * Returns the results as a map.
     *
     * @return ordered map of result names to values
     */
    public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<String, Object>();
      m.put("deltaPressureBar", deltaPressureBar);
      m.put("finalPressureBara", finalPressureBara);
      m.put("annulusVolumeM3", annulusVolumeM3);
      m.put("complianceM3PerBar", complianceM3PerBar);
      m.put("fixedVolumeChangeM3", fixedVolumeChangeM3);
      m.put("rigidLimitBar", rigidLimitBar);
      return m;
    }
  }
}
