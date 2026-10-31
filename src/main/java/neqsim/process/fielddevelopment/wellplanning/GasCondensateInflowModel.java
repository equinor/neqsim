package neqsim.process.fielddevelopment.wellplanning;

import java.io.Serializable;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Steady-state radial inflow of a gas-condensate well with condensate banking and non-Darcy flow.
 *
 * <p>
 * The model follows the steady-state (Fevang and Whitson 1996 style) approach using the constant-composition relation
 * of the wellstream. In molar terms the wellstream rate n_w obeys
 * </p>
 * <p>
 * Psi(p_e) - Psi(p_wf) = a n_w + b n_w^2, Psi(p) = integral of F(p) dp
 * </p>
 * <ul>
 * <li>a = (ln(r_e/r_w) - 0.75 + s) / (2 pi k h) (Darcy plus skin, pseudo-steady drainage)</li>
 * <li>b = k_rg beta M_g V (1/r_w - 1/r_e) / (4 pi^2 h^2 mu_g) evaluated at the well (Forchheimer term)</li>
 * <li>F(p) = k_rg rho_g / (mu_g V) with molar gas density rho_g, gas mole fraction V of the constant-composition flash
 * and gas viscosity mu_g</li>
 * </ul>
 * <p>
 * Above the dew point k_rg is the endpoint k_rg,max, which for a dry gas reduces to the classical real-gas
 * pseudo-pressure solution. Below the dew point the oil saturation is found from k_ro/k_rg = (L/V) rho_g mu_o / (rho_o
 * mu_g) with Corey relative permeabilities (region 2); where the constant-composition liquid saturation is below the
 * critical value the oil is immobile and the gas flows with k_rg at that saturation (region 3, CCE approximation).
 * </p>
 * <p>
 * Limitations: screening only. Fluid densities are NeqSim EOS densities (with the system volume correction, so the
 * real-gas factor implied by density can differ from the raw EOS Z by about 0.5 percent). Velocity-dependent
 * (capillary-number) relative permeability, composition change by depletion (CVD), water flow, layered or horizontal
 * wells and transient banking build-up are not modelled. Validation is by the dry-gas pseudo-pressure analytic limit
 * and by internal consistency (Forchheimer quadratic residual, monotonic banking impairment); no field data is used.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class GasCondensateInflowModel implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double R_GAS = 8.314462618;
  private static final double STD_MOLAR_VOLUME = R_GAS * 288.15 / 101325.0;

  private final SystemInterface wellstream;
  private double reservoirTemperatureK;
  private double reservoirPressureBara = 300.0;
  private double permeabilityM2 = 10.0 * 9.869233e-16;
  private double thicknessM = 50.0;
  private double drainageRadiusM = 400.0;
  private double wellboreRadiusM = 0.1;
  private double skin = 0.0;
  private double swc = 0.2;
  private double soc = 0.1;
  private double krgMax = 1.0;
  private double kroMax = 1.0;
  private double ng = 3.0;
  private double no = 3.0;
  private double betaPerM = 0.0;
  private int stepsPerRegion = 20;
  private double dewPointBara = Double.NaN;

  /**
   * Creates a gas-condensate inflow model.
   *
   * @param wellstream reservoir fluid (components and mixing rule set); it is cloned for each flash
   * @param reservoirTemperatureC reservoir temperature in degrees Celsius
   */
  public GasCondensateInflowModel(SystemInterface wellstream, double reservoirTemperatureC) {
    this.wellstream = wellstream.clone();
    this.reservoirTemperatureK = reservoirTemperatureC + 273.15;
  }

  /**
   * Sets the drainage-boundary pressure.
   *
   * @param bara reservoir pressure in bara
   * @return this model
   */
  public GasCondensateInflowModel setReservoirPressure(double bara) {
    this.reservoirPressureBara = bara;
    this.dewPointBara = Double.NaN;
    return this;
  }

  /**
   * Sets formation properties.
   *
   * @param permeabilityMd absolute permeability in mD
   * @param thicknessM net pay thickness in m
   * @return this model
   */
  public GasCondensateInflowModel setFormation(double permeabilityMd, double thicknessM) {
    this.permeabilityM2 = permeabilityMd * 9.869233e-16;
    this.thicknessM = thicknessM;
    return this;
  }

  /**
   * Sets geometry and skin.
   *
   * @param drainageRadiusM drainage radius in m
   * @param wellboreRadiusM wellbore radius in m
   * @param skin mechanical skin factor
   * @return this model
   */
  public GasCondensateInflowModel setGeometry(double drainageRadiusM, double wellboreRadiusM, double skin) {
    this.drainageRadiusM = drainageRadiusM;
    this.wellboreRadiusM = wellboreRadiusM;
    this.skin = skin;
    return this;
  }

  /**
   * Sets Corey gas-oil relative permeability parameters at connate water.
   *
   * @param connateWater connate water saturation
   * @param criticalOil critical (immobile) oil saturation
   * @param krgEndpoint gas relative permeability at connate water and zero oil
   * @param kroEndpoint oil relative permeability at connate water and maximum oil
   * @param gasExponent Corey exponent for gas
   * @param oilExponent Corey exponent for oil
   * @return this model
   */
  public GasCondensateInflowModel setRelativePermeability(double connateWater, double criticalOil, double krgEndpoint,
      double kroEndpoint, double gasExponent, double oilExponent) {
    this.swc = connateWater;
    this.soc = criticalOil;
    this.krgMax = krgEndpoint;
    this.kroMax = kroEndpoint;
    this.ng = gasExponent;
    this.no = oilExponent;
    return this;
  }

  /**
   * Sets the Forchheimer inertial coefficient.
   *
   * @param betaPerM beta in 1/m (zero disables non-Darcy flow)
   * @return this model
   */
  public GasCondensateInflowModel setForchheimerBeta(double betaPerM) {
    this.betaPerM = betaPerM;
    return this;
  }

  /**
   * Estimates the Forchheimer coefficient with the Geertsma (1974) correlation beta = 0.005 / (phi^5.5 sqrt(k)) and
   * stores it.
   *
   * @param porosity porosity fraction
   * @return beta in 1/m
   */
  public double useGeertsmaBeta(double porosity) {
    this.betaPerM = 0.005 / (Math.pow(porosity, 5.5) * Math.sqrt(permeabilityM2));
    return betaPerM;
  }

  /**
   * Sets the number of trapezoid steps in each pressure region.
   *
   * @param steps steps per region (at least 4)
   * @return this model
   */
  public GasCondensateInflowModel setIntegrationSteps(int steps) {
    this.stepsPerRegion = Math.max(4, steps);
    return this;
  }

  /**
   * Returns the upper dew-point pressure of the wellstream at reservoir temperature, found by scanning downward from
   * the reservoir pressure with TP flashes and bisecting the first appearance of liquid.
   *
   * @return dew-point pressure in bara, or 0 if no liquid appears above 2 bara
   */
  public double getDewPointPressureBara() {
    if (Double.isNaN(dewPointBara)) {
      double hi = reservoirPressureBara;
      double step = 0.03 * reservoirPressureBara;
      double lo = hi;
      boolean found = hasLiquid(hi);
      if (found) {
        dewPointBara = hi;
        return dewPointBara;
      }
      while (!found && lo - step > 2.0) {
        hi = lo;
        lo = lo - step;
        found = hasLiquid(lo);
      }
      if (!found) {
        dewPointBara = 0.0;
        return dewPointBara;
      }
      for (int i = 0; i < 25; i++) {
        double mid = 0.5 * (lo + hi);
        if (hasLiquid(mid)) {
          lo = mid;
        } else {
          hi = mid;
        }
      }
      dewPointBara = 0.5 * (lo + hi);
    }
    return dewPointBara;
  }

  /**
   * Checks whether a liquid phase exists at a pressure and the reservoir temperature.
   *
   * @param pBar pressure in bara
   * @return true if a hydrocarbon liquid phase is present
   */
  private boolean hasLiquid(double pBar) {
    SystemInterface f = wellstream.clone();
    f.setTemperature(reservoirTemperatureK);
    f.setPressure(pBar);
    new ThermodynamicOperations(f).TPflash();
    for (int i = 0; i < f.getNumberOfPhases(); i++) {
      PhaseType t = f.getPhase(i).getType();
      if ((t == PhaseType.OIL || t == PhaseType.LIQUID) && f.getBeta(i) > 1.0e-10) {
        return true;
      }
    }
    return false;
  }

  /** Local flowing state at one pressure. */
  private static final class State {
    double integrand;
    double krg = Double.NaN;
    double muGas;
    double gasMoleFraction = 1.0;
    double gasMolarMass;
    double oilSaturation;
  }

  /**
   * Evaluates the integrand F at a pressure.
   *
   * @param pBar pressure in bara
   * @return local state
   */
  private State evaluate(double pBar) {
    SystemInterface f = wellstream.clone();
    f.setTemperature(reservoirTemperatureK);
    f.setPressure(pBar);
    new ThermodynamicOperations(f).TPflash();
    f.initProperties();
    PhaseInterface gas = null;
    PhaseInterface oil = null;
    double vFrac = 1.0;
    double lFrac = 0.0;
    for (int i = 0; i < f.getNumberOfPhases(); i++) {
      PhaseInterface ph = f.getPhase(i);
      if (ph.getType() == PhaseType.GAS) {
        gas = ph;
        vFrac = f.getBeta(i);
      } else if (ph.getType() == PhaseType.OIL || ph.getType() == PhaseType.LIQUID) {
        oil = ph;
        lFrac = f.getBeta(i);
      }
    }
    State s = new State();
    if (gas == null) {
      gas = f.getPhase(0);
      vFrac = 1.0;
      oil = null;
    }
    double rhoG = gas.getDensity("kg/m3") / gas.getMolarMass();
    s.muGas = gas.getViscosity("kg/msec");
    s.gasMolarMass = gas.getMolarMass();
    if (oil == null || lFrac <= 1.0e-12) {
      s.krg = krgMax;
      s.gasMoleFraction = 1.0;
      s.integrand = krgMax * rhoG / s.muGas;
      return s;
    }
    s.gasMoleFraction = vFrac / (vFrac + lFrac);
    double rhoO = oil.getDensity("kg/m3") / oil.getMolarMass();
    double muO = oil.getViscosity("kg/msec");
    double volO = oil.getVolume("m3");
    double volG = gas.getVolume("m3");
    double soCce = (1.0 - swc) * volO / (volO + volG);
    if (soCce < soc) {
      s.oilSaturation = soCce;
      s.krg = krgAt(soCce);
      s.gasMoleFraction = 1.0;
      s.integrand = s.krg * rhoG / s.muGas;
      return s;
    }
    double target = (lFrac / vFrac) * (rhoG * muO) / (rhoO * s.muGas);
    double lo = soc;
    double hi = 1.0 - swc;
    for (int i = 0; i < 80; i++) {
      double mid = 0.5 * (lo + hi);
      double ratio = kroAt(mid) / Math.max(krgAt(mid), 1.0e-300);
      if (ratio < target) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    s.oilSaturation = 0.5 * (lo + hi);
    s.krg = krgAt(s.oilSaturation);
    s.integrand = s.krg * rhoG / (s.muGas * s.gasMoleFraction);
    return s;
  }

  /**
   * Gas relative permeability at an oil saturation.
   *
   * @param so oil saturation
   * @return krg
   */
  private double krgAt(double so) {
    double sg = Math.max(0.0, (1.0 - swc - so) / (1.0 - swc));
    return krgMax * Math.pow(sg, ng);
  }

  /**
   * Oil relative permeability at an oil saturation.
   *
   * @param so oil saturation
   * @return kro
   */
  private double kroAt(double so) {
    double x = Math.max(0.0, (so - soc) / (1.0 - swc - soc));
    return kroMax * Math.pow(x, no);
  }

  /**
   * Integrates F over pressure with the trapezoid rule split at the dew point.
   *
   * @param pLowBara lower pressure in bara
   * @param pHighBara upper pressure in bara
   * @param useBanking true for the banked integrand, false for the unimpaired gas-only integrand
   * @return integral in mol/(m3 s)
   */
  private double integrate(double pLowBara, double pHighBara, boolean useBanking) {
    double pDew = getDewPointPressureBara();
    double[] edges;
    if (pDew > pLowBara && pDew < pHighBara) {
      edges = new double[] {pLowBara, pDew, pHighBara};
    } else {
      edges = new double[] {pLowBara, pHighBara};
    }
    double total = 0.0;
    for (int seg = 0; seg < edges.length - 1; seg++) {
      int n = stepsPerRegion;
      double dp = (edges[seg + 1] - edges[seg]) / n;
      double prev = valueF(edges[seg], useBanking);
      for (int i = 1; i <= n; i++) {
        double cur = valueF(edges[seg] + i * dp, useBanking);
        total += 0.5 * (prev + cur) * dp * 1.0e5;
        prev = cur;
      }
    }
    return total;
  }

  /**
   * Evaluates F for the banked or unimpaired case.
   *
   * @param pBar pressure in bara
   * @param useBanking true for banked
   * @return F in mol/(m3 Pa s)
   */
  private double valueF(double pBar, boolean useBanking) {
    State s = evaluate(pBar);
    if (useBanking) {
      return s.integrand;
    }
    SystemInterface f = wellstream.clone();
    f.setTemperature(reservoirTemperatureK);
    f.setPressure(pBar);
    new ThermodynamicOperations(f).TPflash();
    f.initProperties();
    PhaseInterface gas = f.hasPhaseType("gas") ? f.getPhaseOfType("gas") : f.getPhase(0);
    return krgMax * gas.getDensity("kg/m3") / gas.getMolarMass() / gas.getViscosity("kg/msec");
  }

  /**
   * Calculates inflow at a flowing bottom-hole pressure.
   *
   * @param flowingPressureBara flowing bottom-hole pressure in bara (below reservoir pressure)
   * @return inflow result
   * @throws IllegalArgumentException if the flowing pressure is not below the reservoir pressure
   */
  public Result calculate(double flowingPressureBara) {
    if (flowingPressureBara >= reservoirPressureBara) {
      throw new IllegalArgumentException("flowing pressure must be below reservoir pressure");
    }
    double a = (Math.log(drainageRadiusM / wellboreRadiusM) - 0.75 + skin)
        / (2.0 * Math.PI * permeabilityM2 * thicknessM);
    double dPsi = integrate(flowingPressureBara, reservoirPressureBara, true);
    State w = evaluate(flowingPressureBara);
    double b = w.krg * betaPerM * w.gasMolarMass * w.gasMoleFraction * (1.0 / wellboreRadiusM - 1.0 / drainageRadiusM)
        / (4.0 * Math.PI * Math.PI * thicknessM * thicknessM * w.muGas);
    double nDarcy = dPsi / a;
    double nW = b > 0.0 ? (-a + Math.sqrt(a * a + 4.0 * b * dPsi)) / (2.0 * b) : nDarcy;
    double dPsiFree = integrate(flowingPressureBara, reservoirPressureBara, false);
    Result r = new Result();
    r.flowingPressureBara = flowingPressureBara;
    r.dewPointBara = getDewPointPressureBara();
    r.wellstreamMolePerS = nW;
    r.gasRateSm3PerDay = nW * STD_MOLAR_VOLUME * 86400.0;
    r.darcyRateSm3PerDay = nDarcy * STD_MOLAR_VOLUME * 86400.0;
    r.nonDarcyFactor = nDarcy > 0.0 ? nW / nDarcy : 1.0;
    r.bankingImpairment = dPsiFree > 0.0 ? Math.min(1.0, dPsi / dPsiFree) : 1.0;
    double sTotal = Math.log(drainageRadiusM / wellboreRadiusM) - 0.75 + skin;
    r.bankingSkin = sTotal * (1.0 / r.bankingImpairment - 1.0);
    r.wellOilSaturation = w.oilSaturation;
    r.wellKrg = w.krg;
    double[] split = standardSplit();
    r.condensateRateSm3PerDay = nW * split[0] * 86400.0;
    r.separatorGasRateSm3PerDay = nW * split[1] * STD_MOLAR_VOLUME * 86400.0;
    r.condensateYieldSm3PerSm3 = r.condensateRateSm3PerDay / Math.max(r.separatorGasRateSm3PerDay, 1.0e-30);
    r.nonDarcyResidual = (a * nW + b * nW * nW - dPsi) / dPsi;
    r.bankingActive = r.dewPointBara > 0.0 && flowingPressureBara < r.dewPointBara;
    return r;
  }

  /**
   * Flashes the wellstream at standard conditions (15 degrees Celsius, 1.01325 bara).
   *
   * @return array of standard liquid volume per mole of wellstream (m3/mol) and gas mole fraction
   */
  private double[] standardSplit() {
    SystemInterface f = wellstream.clone();
    f.setTemperature(288.15);
    f.setPressure(1.01325);
    new ThermodynamicOperations(f).TPflash();
    f.initProperties();
    double liquidVolume = 0.0;
    double gasMoles = 0.0;
    for (int i = 0; i < f.getNumberOfPhases(); i++) {
      PhaseInterface ph = f.getPhase(i);
      if (ph.getType() == PhaseType.GAS) {
        gasMoles += ph.getNumberOfMolesInPhase();
      } else {
        liquidVolume += ph.getVolume("m3");
      }
    }
    double total = f.getTotalNumberOfMoles();
    return new double[] {liquidVolume / total, gasMoles / total};
  }

  /**
   * Generates an inflow performance curve.
   *
   * @param points number of pressure points (at least 2)
   * @return array with rows {flowing pressure in bara, gas rate in Sm3/day}
   */
  public double[][] generateIpr(int points) {
    int n = Math.max(2, points);
    double[][] ipr = new double[n][2];
    for (int i = 0; i < n; i++) {
      double pwf = reservoirPressureBara * (1.0 - (i + 1.0) / (n + 1.0) * 0.98);
      ipr[i][0] = pwf;
      ipr[i][1] = calculate(pwf).getGasRateSm3PerDay();
    }
    return ipr;
  }

  /**
   * Finds the flowing pressure that delivers a target gas rate.
   *
   * @param gasRateSm3PerDay target gas-equivalent rate in Sm3/day
   * @return flowing bottom-hole pressure in bara, or the lowest evaluated pressure if the rate is unattainable
   */
  public double flowingPressureForRate(double gasRateSm3PerDay) {
    double lo = 1.5;
    double hi = reservoirPressureBara - 1.0e-3;
    if (calculate(lo).getGasRateSm3PerDay() < gasRateSm3PerDay) {
      return lo;
    }
    for (int i = 0; i < 40; i++) {
      double mid = 0.5 * (lo + hi);
      if (calculate(mid).getGasRateSm3PerDay() > gasRateSm3PerDay) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }

  /** Result of a gas-condensate inflow calculation. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private double flowingPressureBara;
    private double dewPointBara;
    private double wellstreamMolePerS;
    private double gasRateSm3PerDay;
    private double darcyRateSm3PerDay;
    private double nonDarcyFactor;
    private double bankingImpairment;
    private double bankingSkin;
    private double wellOilSaturation;
    private double wellKrg;
    private double condensateYieldSm3PerSm3;
    private double condensateRateSm3PerDay;
    private double separatorGasRateSm3PerDay;
    private double nonDarcyResidual;
    private boolean bankingActive;

    /**
     * Gets the flowing bottom-hole pressure.
     *
     * @return pressure in bara
     */
    public double getFlowingPressureBara() {
      return flowingPressureBara;
    }

    /**
     * Gets the dew-point pressure.
     *
     * @return pressure in bara (0 if none)
     */
    public double getDewPointBara() {
      return dewPointBara;
    }

    /**
     * Gets the wellstream molar rate.
     *
     * @return rate in mol/s
     */
    public double getWellstreamMolePerS() {
      return wellstreamMolePerS;
    }

    /**
     * Gets the gas-equivalent wellstream rate at standard conditions (ideal gas, 15 degrees Celsius).
     *
     * @return rate in Sm3/day
     */
    public double getGasRateSm3PerDay() {
      return gasRateSm3PerDay;
    }

    /**
     * Gets the rate without the Forchheimer term.
     *
     * @return rate in Sm3/day
     */
    public double getDarcyRateSm3PerDay() {
      return darcyRateSm3PerDay;
    }

    /**
     * Gets the ratio of the non-Darcy to the Darcy rate.
     *
     * @return factor between 0 and 1
     */
    public double getNonDarcyFactor() {
      return nonDarcyFactor;
    }

    /**
     * Gets the banking impairment ratio, the integral with banking over the integral for unimpaired gas flow.
     *
     * @return ratio between 0 and 1 (1 means no impairment)
     */
    public double getBankingImpairment() {
      return bankingImpairment;
    }

    /**
     * Gets the equivalent condensate-banking skin.
     *
     * @return skin contribution
     */
    public double getBankingSkin() {
      return bankingSkin;
    }

    /**
     * Gets the oil saturation at the wellbore.
     *
     * @return saturation fraction
     */
    public double getWellOilSaturation() {
      return wellOilSaturation;
    }

    /**
     * Gets the gas relative permeability at the wellbore.
     *
     * @return krg
     */
    public double getWellKrg() {
      return wellKrg;
    }

    /**
     * Gets the separator (standard flash) gas rate.
     *
     * @return rate in Sm3/day
     */
    public double getSeparatorGasRateSm3PerDay() {
      return separatorGasRateSm3PerDay;
    }

    /**
     * Gets the standard-condition condensate yield.
     *
     * @return Sm3 liquid per Sm3 gas-equivalent
     */
    public double getCondensateYieldSm3PerSm3() {
      return condensateYieldSm3PerSm3;
    }

    /**
     * Gets the condensate rate at standard conditions.
     *
     * @return rate in Sm3/day
     */
    public double getCondensateRateSm3PerDay() {
      return condensateRateSm3PerDay;
    }

    /**
     * Gets the relative residual of the inflow quadratic at the solution.
     *
     * @return residual divided by the pseudo-pressure difference (dimensionless)
     */
    public double getNonDarcyResidual() {
      return nonDarcyResidual;
    }

    /**
     * Checks whether the flowing pressure is below the dew point.
     *
     * @return true if banking can occur
     */
    public boolean isBankingActive() {
      return bankingActive;
    }
  }
}
