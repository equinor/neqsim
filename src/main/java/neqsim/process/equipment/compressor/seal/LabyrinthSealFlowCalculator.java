package neqsim.process.equipment.compressor.seal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.unit.PressureUnit;
import neqsim.util.unit.TemperatureUnit;

/**
 * Gas leakage through a straight-through labyrinth seal (teeth on stator).
 *
 * <p>
 * The calculator takes any NeqSim fluid, the upstream pressure and temperature, the downstream pressure (or the
 * pressure drop) and a {@link LabyrinthSealGeometry}, and returns the leakage mass flow with the selected
 * {@link LabyrinthFlowMethod}. Upstream gas properties (density, molar mass and the real-gas isentropic exponent
 * {@code kappa = -(Cp/Cv)(v/p)(dp/dv)}) come from a TP flash of a clone of the fluid; the caller's fluid is not
 * changed.
 * </p>
 *
 * <p>
 * With pressures p0 (upstream) and pn (downstream) in kPa, density rho in kg/m3, clearance area A, number of teeth n,
 * radial clearance c, tooth pitch t and tip width b, the methods are:
 * </p>
 * <ul>
 * <li>Stodola: {@code m = 0.71 A sqrt(1000 rho (p0^2 - pn^2) / (p0 n))}</li>
 * <li>Egli (Aungier coefficients): {@code m = A Cr Ct Cc rho sqrt(1000 R T / M)} with contraction ratio
 * {@code Cr = 1 - 1/(3 + (54.3/(1 + 100 c/b))^3.45)}, throttling coefficient
 * {@code Ct = 2.143 (ln n - 1.464) (1 - pn/p0)^(0.375 pn/p0) / (n - 4.322)} and a carry-over coefficient Cc from
 * c/t</li>
 * <li>Vermes: {@code m = u A sqrt(1000 rho p0) sqrt((1 - r^2)/(n + ln r))}, {@code r = pn/p0},
 * {@code u = sqrt(1/(1 - alpha))}, {@code alpha = 8.52/((t - b)/c + 7.23)}</li>
 * <li>Kearton: {@code m = Ck 0.71 A sqrt(1000 p0 rho) sqrt(F (1 - r^2)/n)} with
 * {@code Ck = 1 + 3.27 (1 - exp(-0.4 n)) (c/t)^(2/3)} and F the mean tooth pressure ratio of the profile
 * {@code p_i = sqrt(p0^2 - (i/n)(p0^2 - pn^2))}. The equation peaks at a pressure ratio r*(n) that depends only on the
 * number of teeth (0.43 for two teeth, 0.26 for five); below r* the last tooth is choked and the leakage is held at the
 * peak instead of falling with the backpressure</li>
 * <li>Neumann (modified, teeth on stator): tooth-by-tooth solution of {@code p_{i+1}^2 = p_i^2 - (m/(Cf_i u_i A))^2
 * p_i/rho_i} with a flow coefficient that depends on the tooth pressure ratio, a kinetic carry-over factor after the
 * first tooth and an exact PH flash at the inlet enthalpy after every tooth; see {@link #getToothPressures(String)}. A
 * tooth chokes when its pressure ratio reaches the critical ratio, and the choked leakage is the largest flow at which
 * no tooth chokes.</li>
 * </ul>
 *
 * <p>
 * Each closed-form result is limited to the critical flow of the clearance, {@code m_crit = A sqrt(1000 rho p0 k phi)}
 * with {@code phi = (2/(k+1))^((k+1)/(k-1))} (Egli uses the equivalent form
 * {@code A rho sqrt(1000 R T/M) sqrt(k phi)}); for Kearton the limit is the lower of this and the Kearton peak.
 * {@link #isChoked()} reports when a limit is active.
 * </p>
 *
 * <p>
 * Range problems follow the convention of the differential-pressure flow meters: they never throw. When a correlation
 * is mathematically undefined for the geometry (a non-positive Egli coefficient, or a Vermes residual-energy factor of
 * one or more), that method returns NaN and the reason is listed by {@link #getValidityViolations()};
 * {@link #isWithinValidityRange()} is then false. Invalid inputs (missing values, outlet pressure not below inlet
 * pressure) still throw.
 * </p>
 *
 * <p>
 * Typical use:
 * </p>
 *
 * <pre>
 * LabyrinthSealFlowCalculator calc = new LabyrinthSealFlowCalculator(fluid, geometry);
 * calc.setInletPressure(2.765, "barg");
 * calc.setInletTemperature(86.12, "C");
 * calc.setOutletPressure(1.5417, "barg");
 * calc.setMethod(LabyrinthFlowMethod.KEARTON);
 * calc.calculate();
 * double leakage = calc.getMassFlowRate("kg/hr");
 * </pre>
 *
 * @author neqsim
 * @version 1.0
 */
public class LabyrinthSealFlowCalculator implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LogManager.getLogger(LabyrinthSealFlowCalculator.class);

  /** Carry-over coefficient used by Stodola and Kearton. */
  private static final double CARRY_OVER_COEFFICIENT = 0.71;

  private final SystemInterface fluid;
  private LabyrinthSealGeometry geometry;
  private LabyrinthFlowMethod method = LabyrinthFlowMethod.KEARTON;

  private double inletPressureBara = Double.NaN;
  private double inletTemperatureK = Double.NaN;
  private double outletPressureBara = Double.NaN;
  private double pressureDropBar = Double.NaN;
  private double standardTemperatureK = ThermodynamicConstantsInterface.standardStateTemperature;
  private double standardPressureBara = ThermodynamicConstantsInterface.referencePressure;

  private boolean calculated = false;
  private double massFlowKgPerSec = Double.NaN;
  private double criticalMassFlowKgPerSec = Double.NaN;
  private boolean choked = false;
  private double inletDensity = Double.NaN;
  private double inletMolarMass = Double.NaN;
  private double inletKappa = Double.NaN;
  private double inletCompressibility = Double.NaN;
  private boolean liquidAtInlet = false;
  private final List<String> validityViolations = new ArrayList<String>();
  private boolean inletEvaluated = false;
  private SystemInterface inletGasState;
  private transient NeumannLabyrinthSolver neumannSolver;
  private double[] toothPressuresBara = new double[0];
  private double[] toothTemperaturesK = new double[0];
  private double[] toothDensities = new double[0];

  /**
   * Creates a calculator.
   *
   * @param fluid fluid defining the gas composition and the equation of state; it is cloned and not modified
   * @param geometry seal geometry
   * @throws IllegalArgumentException if the fluid or the geometry is null
   */
  public LabyrinthSealFlowCalculator(SystemInterface fluid, LabyrinthSealGeometry geometry) {
    if (fluid == null) {
      throw new IllegalArgumentException("A fluid is required: pass a SystemInterface with the seal gas composition");
    }
    if (geometry == null) {
      throw new IllegalArgumentException("A LabyrinthSealGeometry is required");
    }
    this.fluid = fluid.clone();
    this.geometry = new LabyrinthSealGeometry(geometry);
  }

  /**
   * Replaces the seal geometry. The geometry is copied, so later changes to the argument do not affect the calculator.
   *
   * @param geometry seal geometry
   * @throws IllegalArgumentException if the geometry is null
   */
  public void setGeometry(LabyrinthSealGeometry geometry) {
    if (geometry == null) {
      throw new IllegalArgumentException("A LabyrinthSealGeometry is required");
    }
    this.geometry = new LabyrinthSealGeometry(geometry);
    neumannSolver = null;
    calculated = false;
  }

  /**
   * Returns a copy of the seal geometry. Change the geometry with {@link #setGeometry(LabyrinthSealGeometry)}.
   *
   * @return copy of the seal geometry
   */
  public LabyrinthSealGeometry getGeometry() {
    return new LabyrinthSealGeometry(geometry);
  }

  /**
   * Selects the leakage correlation.
   *
   * @param method flow method
   * @throws IllegalArgumentException if the method is null
   */
  public void setMethod(LabyrinthFlowMethod method) {
    if (method == null) {
      throw new IllegalArgumentException("A LabyrinthFlowMethod is required");
    }
    this.method = method;
    calculated = false;
  }

  /**
   * Returns the selected leakage correlation.
   *
   * @return flow method
   */
  public LabyrinthFlowMethod getMethod() {
    return method;
  }

  /**
   * Sets the pressure upstream of the first tooth.
   *
   * @param pressure pressure value
   * @param unit pressure unit supported by {@link PressureUnit}, for example "bara", "barg" or "kPa"
   */
  public void setInletPressure(double pressure, String unit) {
    inletPressureBara = new PressureUnit(pressure, unit).getValue("bara");
    invalidateInlet();
  }

  /**
   * Sets the gas temperature upstream of the first tooth.
   *
   * @param temperature temperature value
   * @param unit temperature unit, for example "C" or "K"
   */
  public void setInletTemperature(double temperature, String unit) {
    inletTemperatureK = new TemperatureUnit(temperature, unit).getValue("K");
    invalidateInlet();
  }

  /**
   * Discards the cached inlet state and results after an inlet change.
   */
  private void invalidateInlet() {
    inletEvaluated = false;
    neumannSolver = null;
    calculated = false;
  }

  /**
   * Sets the pressure downstream of the last tooth. Clears a previously set pressure drop.
   *
   * @param pressure pressure value
   * @param unit pressure unit supported by {@link PressureUnit}
   */
  public void setOutletPressure(double pressure, String unit) {
    outletPressureBara = new PressureUnit(pressure, unit).getValue("bara");
    pressureDropBar = Double.NaN;
    calculated = false;
  }

  /**
   * Sets the pressure drop across the seal instead of the outlet pressure. Clears a previously set outlet pressure.
   *
   * @param pressureDrop pressure difference
   * @param unit one of "bar", "mbar", "Pa", "kPa", "MPa", "psi"
   */
  public void setPressureDrop(double pressureDrop, String unit) {
    pressureDropBar = pressureDifferenceToBar(pressureDrop, unit);
    outletPressureBara = Double.NaN;
    calculated = false;
  }

  /**
   * Sets the standard conditions used by {@link #getStandardVolumeFlowRate(String)}. The default is 15 C and 1.01325
   * bara. Both values are validated before either is stored, so a rejected call leaves the previous conditions
   * unchanged.
   *
   * @param temperature standard temperature
   * @param temperatureUnit temperature unit
   * @param pressure standard pressure
   * @param pressureUnit pressure unit
   * @throws IllegalArgumentException for an unsupported unit, or a temperature or absolute pressure that is not finite
   * and positive
   */
  public void setStandardConditions(double temperature, String temperatureUnit, double pressure, String pressureUnit) {
    double temperatureK = new TemperatureUnit(temperature, temperatureUnit).getValue("K");
    double pressureBara = new PressureUnit(pressure, pressureUnit).getValue("bara");
    if (!isPositiveFinite(temperatureK)) {
      throw new IllegalArgumentException(
          "Standard temperature must be finite and above 0 K, got " + temperature + " " + temperatureUnit);
    }
    if (!isPositiveFinite(pressureBara)) {
      throw new IllegalArgumentException(
          "Standard pressure must be finite and above 0 bara, got " + pressure + " " + pressureUnit);
    }
    standardTemperatureK = temperatureK;
    standardPressureBara = pressureBara;
  }

  /**
   * Converts a pressure difference to bar.
   *
   * @param value pressure difference
   * @param unit difference unit
   * @return pressure difference in bar
   * @throws IllegalArgumentException for an unsupported unit
   */
  private static double pressureDifferenceToBar(double value, String unit) {
    if ("bar".equals(unit)) {
      return value;
    } else if ("mbar".equals(unit)) {
      return value * 1.0e-3;
    } else if ("Pa".equals(unit)) {
      return value * 1.0e-5;
    } else if ("kPa".equals(unit)) {
      return value * 1.0e-2;
    } else if ("MPa".equals(unit)) {
      return value * 10.0;
    } else if ("psi".equals(unit)) {
      return value * 0.0689475729;
    }
    throw new IllegalArgumentException(
        "Unsupported pressure difference unit '" + unit + "'; use bar, mbar, Pa, kPa, MPa or psi");
  }

  /**
   * Returns the outlet pressure resolved from either the outlet pressure or the pressure drop.
   *
   * @return outlet pressure in bara, NaN if neither is set
   */
  private double resolvedOutletPressureBara() {
    if (!Double.isNaN(pressureDropBar)) {
      return inletPressureBara - pressureDropBar;
    }
    return outletPressureBara;
  }

  /**
   * Checks that all inputs are present and consistent.
   *
   * @throws IllegalStateException with a remediation hint when an input is missing or inconsistent
   */
  private void validateInputs() {
    geometry.validate();
    if (Double.isNaN(inletPressureBara) || Double.isNaN(inletTemperatureK)) {
      throw new IllegalStateException(
          "Set the inlet conditions with setInletPressure(...) and setInletTemperature(...) before calculate()");
    }
    double outlet = resolvedOutletPressureBara();
    if (Double.isNaN(outlet)) {
      throw new IllegalStateException(
          "Set the outlet pressure with setOutletPressure(...) or the pressure drop with setPressureDrop(...)");
    }
    if (outlet <= 0.0) {
      throw new IllegalStateException("The outlet pressure must be above zero absolute, got " + outlet + " bara");
    }
    if (outlet >= inletPressureBara) {
      throw new IllegalStateException("The outlet pressure (" + outlet + " bara) must be below the inlet pressure ("
          + inletPressureBara + " bara); check the pressure units (bara or barg)");
    }
    if (inletTemperatureK <= 0.0) {
      throw new IllegalStateException("The inlet temperature must be above 0 K, got " + inletTemperatureK + " K");
    }
  }

  /**
   * Flashes a clone of the fluid at the inlet conditions and stores the gas-phase properties. The result is cached
   * until the inlet pressure or temperature changes.
   *
   * @throws IllegalStateException if there is no gas phase at the inlet
   */
  private void evaluateInletProperties() {
    if (inletEvaluated) {
      return;
    }
    SystemInterface inlet = fluid.clone();
    inlet.setTemperature(inletTemperatureK);
    inlet.setPressure(inletPressureBara);
    new ThermodynamicOperations(inlet).TPflash();
    inlet.initProperties();
    if (!inlet.hasPhaseType("gas")) {
      throw new IllegalStateException("No gas phase at the seal inlet (" + inletPressureBara + " bara, "
          + inletTemperatureK + " K); labyrinth leakage correlations are for gas only");
    }
    liquidAtInlet = inlet.getNumberOfPhases() > 1;
    if (liquidAtInlet) {
      logger.warn("Liquid present at the labyrinth inlet ({} phases); gas-phase properties are used",
          inlet.getNumberOfPhases());
    }
    PhaseInterface gas = inlet.getPhase("gas");
    inletDensity = gas.getDensity("kg/m3");
    inletMolarMass = gas.getMolarMass() * 1000.0;
    inletKappa = gas.getKappa();
    inletCompressibility = gas.getZ();
    if (liquidAtInlet) {
      inletGasState = inlet.phaseToSystem("gas");
      inletGasState.setTemperature(inletTemperatureK);
      inletGasState.setPressure(inletPressureBara);
      new ThermodynamicOperations(inletGasState).TPflash();
      inletGasState.initProperties();
    } else {
      inletGasState = inlet;
    }
    neumannSolver = null;
    inletEvaluated = true;
  }

  /**
   * Returns the Neumann solver for the current inlet state and geometry, creating it when needed.
   *
   * @return Neumann solver
   */
  private NeumannLabyrinthSolver neumannSolver() {
    if (neumannSolver == null) {
      evaluateInletProperties();
      neumannSolver = new NeumannLabyrinthSolver(inletGasState, inletDensity, inletKappa, geometry);
    }
    return neumannSolver;
  }

  /**
   * Replaces the Neumann solver for the current inlet state and geometry. Package-private seam for tests that inject an
   * isenthalpic-state function; it stays in use until the inlet state or the geometry changes.
   *
   * @param solver Neumann solver to use
   */
  void useNeumannSolver(NeumannLabyrinthSolver solver) {
    neumannSolver = solver;
    calculated = false;
  }

  /**
   * Computes the uncapped mass flow and the critical mass flow for a method from the stored inlet properties.
   *
   * <p>
   * When the correlation is mathematically undefined for the geometry (a non-positive Egli coefficient or a Vermes
   * residual-energy factor of one or more), the uncapped flow is NaN and a message is added to {@code violations}.
   * </p>
   *
   * @param flowMethod flow method
   * @param violations list receiving validity violations, each prefixed with the method name
   * @return array {uncapped mass flow, critical mass flow} in kg/s, with an optional third element above zero when the
   * method itself has detected choking (Kearton below its peak pressure ratio)
   */
  private double[] computeFlows(LabyrinthFlowMethod flowMethod, List<String> violations) {
    double p0 = inletPressureBara * 100.0;
    double pn = resolvedOutletPressureBara() * 100.0;
    double ratio = pn / p0;
    double area = geometry.getClearanceArea();
    int n = geometry.getNumberOfTeeth();
    double clearance = geometry.getRadialClearance();
    double pitch = geometry.getToothPitch();
    double tip = geometry.getTipWidth();
    double k = inletKappa;
    double criticalFactor = Math.sqrt(k * Math.pow(2.0 / (k + 1.0), (k + 1.0) / (k - 1.0)));
    double orificeCriticalFlow = area * Math.sqrt(1000.0 * inletDensity * p0) * criticalFactor;

    switch (flowMethod) {
    case STODOLA: {
      double m = CARRY_OVER_COEFFICIENT * area * Math.sqrt(1000.0 * inletDensity * (p0 * p0 - pn * pn) / (p0 * n));
      return new double[] {m, orificeCriticalFlow};
    }
    case EGLI: {
      double contraction = 1.0 - 1.0 / (3.0 + Math.pow(54.3 / (1.0 + 100.0 * clearance / tip), 3.45));
      double throttling = 2.143 * (Math.log(n) - 1.464) * Math.pow(1.0 - ratio, 0.375 * ratio) / (n - 4.322);
      double x1;
      double x2;
      if (n <= 12) {
        x1 = 15.1 - 0.05255 * Math.exp(0.507 * (12 - n));
        x2 = 1.058 + 0.0218 * n;
      } else {
        x1 = 13.15 + 0.1625 * n;
        x2 = 1.32;
      }
      double carryOver = 1.0 + x1 * (clearance / pitch - x2 * Math.log(1.0 + clearance / pitch)) / (1.0 - x2);
      double velocityScale = Math.sqrt(1000.0 * ThermodynamicConstantsInterface.R * inletTemperatureK / inletMolarMass);
      double critical = area * inletDensity * velocityScale * criticalFactor;
      if (!isPositiveFinite(contraction) || !isPositiveFinite(throttling) || !isPositiveFinite(carryOver)) {
        violations.add(String.format(Locale.ROOT,
            "EGLI: coefficients outside correlation range (contraction %.4g, throttling %.4g, carry-over %.4g; "
                + "c/t = %.4g, c/b = %.4g, n = %d); no leakage reported",
            contraction, throttling, carryOver, clearance / pitch, clearance / tip, n));
        return new double[] {Double.NaN, critical};
      }
      double m = area * contraction * throttling * carryOver * inletDensity * velocityScale;
      return new double[] {m, critical};
    }
    case VERMES: {
      double alpha = 8.52 / ((pitch - tip) / clearance + 7.23);
      if (!(alpha > 0.0 && alpha < 1.0)) {
        violations.add(String.format(Locale.ROOT,
            "VERMES: residual-energy factor alpha = %.4g outside 0 to 1 ((t - b)/c = %.4g); no leakage reported", alpha,
            (pitch - tip) / clearance));
        return new double[] {Double.NaN, orificeCriticalFlow};
      }
      double carryOver = Math.sqrt(1.0 / (1.0 - alpha));
      double denominator = n + Math.log(ratio);
      if (denominator <= 0.0) {
        logger.debug("Vermes term n + ln(pn/p0) = {} is not positive; flow is choked", denominator);
        return new double[] {Double.POSITIVE_INFINITY, orificeCriticalFlow};
      }
      double m = carryOver * area * Math.sqrt(inletDensity * p0 * 1000.0)
          * Math.sqrt((1.0 - ratio * ratio) / denominator);
      return new double[] {m, orificeCriticalFlow};
    }
    case KEARTON: {
      double prefix = CARRY_OVER_COEFFICIENT * area * Math.sqrt(1000.0 * p0 * inletDensity)
          * (1.0 + 3.27 * (1.0 - Math.exp(-0.4 * n)) * Math.pow(clearance / pitch, 2.0 / 3.0));
      double peakRatio = keartonCriticalPressureRatio(n);
      double peakFlow = prefix * keartonPressureFactor(peakRatio, n);
      double critical = Math.min(orificeCriticalFlow, peakFlow);
      if (ratio < peakRatio) {
        return new double[] {peakFlow, critical, 1.0};
      }
      return new double[] {prefix * keartonPressureFactor(ratio, n), critical};
    }
    default:
      throw new IllegalStateException("Unsupported labyrinth flow method " + flowMethod);
    }
  }

  /**
   * Runs the selected method.
   *
   * <p>
   * Range problems do not throw: when the method is undefined for the geometry the mass flow is NaN and the reason is
   * available from {@link #getValidityViolations()}.
   * </p>
   *
   * @throws IllegalStateException if inputs are missing or inconsistent, or there is no gas at the inlet
   */
  public void calculate() {
    validateInputs();
    evaluateInletProperties();
    validityViolations.clear();
    applyMethod(method, validityViolations);
  }

  /**
   * Returns true when the value is finite and above zero.
   *
   * @param value value to check
   * @return true for a finite positive value
   */
  private static boolean isPositiveFinite(double value) {
    return value > 0.0 && !Double.isInfinite(value);
  }

  /**
   * Kearton pressure factor {@code sqrt(F (1 - r^2) / n)}, where F is the mean tooth pressure ratio of the profile
   * {@code p_i/p_0 = sqrt(1 - (i/n)(1 - r^2))}.
   *
   * @param ratio outlet to inlet absolute pressure ratio
   * @param n number of teeth
   * @return pressure factor
   */
  private static double keartonPressureFactor(double ratio, int n) {
    double previous = 1.0;
    double sumRatio = 0.0;
    for (int i = 1; i <= n; i++) {
      double current = Math.sqrt(1.0 - ((double) i / n) * (1.0 - ratio * ratio));
      sumRatio += current / previous;
      previous = current;
    }
    return Math.sqrt(sumRatio / n * (1.0 - ratio * ratio) / n);
  }

  /**
   * Returns the pressure ratio at which the Kearton equation has its maximum leakage. Below this ratio the equation
   * would predict less leakage as the backpressure falls; physically the last tooth is choked and the leakage stays at
   * the peak, which is how {@link LabyrinthFlowMethod#KEARTON} is evaluated. The ratio depends only on the number of
   * teeth (for one tooth it is 1/sqrt(3)).
   *
   * @param n number of teeth, at least 1
   * @return peak outlet to inlet absolute pressure ratio
   */
  static double keartonCriticalPressureRatio(int n) {
    double lower = 0.0;
    double upper = 1.0;
    double inverseGolden = (Math.sqrt(5.0) - 1.0) / 2.0;
    while (upper - lower > 1.0e-12) {
      double left = upper - inverseGolden * (upper - lower);
      double right = lower + inverseGolden * (upper - lower);
      if (keartonPressureFactor(left, n) < keartonPressureFactor(right, n)) {
        lower = left;
      } else {
        upper = right;
      }
    }
    return 0.5 * (lower + upper);
  }

  /**
   * Stores the capped result of a method. A negative or NaN uncapped flow is never published.
   *
   * @param flowMethod flow method
   * @param violations list receiving validity violations
   */
  private void applyMethod(LabyrinthFlowMethod flowMethod, List<String> violations) {
    if (flowMethod == LabyrinthFlowMethod.NEUMANN) {
      applyNeumann(violations);
      return;
    }
    int before = violations.size();
    double[] flows = computeFlows(flowMethod, violations);
    double uncapped = flows[0];
    if (!(uncapped >= 0.0)) {
      if (violations.size() == before) {
        violations.add(flowMethod.name() + ": correlation returned " + uncapped + "; no leakage reported");
      }
      uncapped = Double.NaN;
    }
    boolean chokedByMethod = flows.length > 2 && flows[2] > 0.0;
    criticalMassFlowKgPerSec = flows[1];
    choked = chokedByMethod || uncapped > flows[1];
    massFlowKgPerSec = choked ? Math.min(uncapped, flows[1]) : uncapped;
    toothPressuresBara = new double[0];
    toothTemperaturesK = new double[0];
    toothDensities = new double[0];
    calculated = true;
  }

  /**
   * Runs the Neumann solver, seeded with the Kearton flow, and stores its result and tooth profile.
   *
   * @param violations list receiving validity violations
   */
  private void applyNeumann(List<String> violations) {
    double seed = computeFlows(LabyrinthFlowMethod.KEARTON, new ArrayList<String>())[0];
    NeumannLabyrinthSolver.Result result = neumannSolver().solve(resolvedOutletPressureBara() * 1.0e5, seed);
    violations.addAll(result.violations);
    massFlowKgPerSec = result.massFlow;
    choked = result.choked;
    criticalMassFlowKgPerSec = result.choked ? result.massFlow : Double.NaN;
    if (result.profile != null) {
      int points = result.profile.pressurePa.length;
      toothPressuresBara = new double[points];
      for (int i = 0; i < points; i++) {
        toothPressuresBara[i] = result.profile.pressurePa[i] * 1.0e-5;
      }
      toothTemperaturesK = result.profile.temperatureK.clone();
      toothDensities = result.profile.density.clone();
    } else {
      toothPressuresBara = new double[0];
      toothTemperaturesK = new double[0];
      toothDensities = new double[0];
    }
    calculated = true;
  }

  /**
   * Runs every method on the same inlet state. The calculator keeps the results of the selected method afterwards.
   *
   * <p>
   * Every method has a key in the map. A method that is undefined for the geometry maps to NaN and its reason is listed
   * by {@link #getValidityViolations()}, which after this call covers all methods.
   * </p>
   *
   * @return mass flow in kg/s for each method
   * @throws IllegalStateException if inputs are missing or inconsistent, or there is no gas at the inlet
   */
  public Map<LabyrinthFlowMethod, Double> calculateAllMethods() {
    validateInputs();
    evaluateInletProperties();
    validityViolations.clear();
    Map<LabyrinthFlowMethod, Double> flows = new EnumMap<LabyrinthFlowMethod, Double>(LabyrinthFlowMethod.class);
    for (LabyrinthFlowMethod candidate : LabyrinthFlowMethod.values()) {
      applyMethod(candidate, validityViolations);
      flows.put(candidate, massFlowKgPerSec);
    }
    applyMethod(method, new ArrayList<String>());
    return flows;
  }

  /**
   * Lists why methods evaluated by the last {@link #calculate()} or {@link #calculateAllMethods()} call are outside
   * their correlation range. Each entry starts with the method name.
   *
   * @return copy of the list of violations, empty when every evaluated method is within range
   */
  public List<String> getValidityViolations() {
    requireResult();
    return new ArrayList<String>(validityViolations);
  }

  /**
   * Returns whether every method evaluated by the last calculation is within its correlation range.
   *
   * @return true when {@link #getValidityViolations()} is empty
   */
  public boolean isWithinValidityRange() {
    return getValidityViolations().isEmpty();
  }

  /**
   * Throws if no result is available.
   *
   * @throws IllegalStateException if calculate() has not been run since the last input change
   */
  private void requireResult() {
    if (!calculated) {
      throw new IllegalStateException("No result: call calculate() after setting all inputs");
    }
  }

  /**
   * Converts a mass flow from kg/s.
   *
   * @param kgPerSec mass flow in kg/s
   * @param unit target unit: "kg/sec", "kg/s", "kg/min", "kg/hr", "kg/h" or "kg/day"
   * @return mass flow in the requested unit
   * @throws IllegalArgumentException for an unsupported unit
   */
  private static double convertMassFlow(double kgPerSec, String unit) {
    if ("kg/sec".equals(unit) || "kg/s".equals(unit)) {
      return kgPerSec;
    } else if ("kg/min".equals(unit)) {
      return kgPerSec * 60.0;
    } else if ("kg/hr".equals(unit) || "kg/h".equals(unit)) {
      return kgPerSec * 3600.0;
    } else if ("kg/day".equals(unit)) {
      return kgPerSec * 86400.0;
    }
    throw new IllegalArgumentException(
        "Unsupported mass flow unit '" + unit + "'; use kg/sec, kg/min, kg/hr or kg/day");
  }

  /**
   * Converts a volume flow from m3/s, keeping the unit prefix (for example "Sm3").
   *
   * @param perSec volume flow per second
   * @param unit target unit, e.g. "m3/hr"
   * @param volume expected volume token, "m3" or "Sm3"
   * @return volume flow in the requested unit
   * @throws IllegalArgumentException for an unsupported unit
   */
  private static double convertVolumeFlow(double perSec, String unit, String volume) {
    if ((volume + "/sec").equals(unit) || (volume + "/s").equals(unit)) {
      return perSec;
    } else if ((volume + "/min").equals(unit)) {
      return perSec * 60.0;
    } else if ((volume + "/hr").equals(unit) || (volume + "/h").equals(unit)) {
      return perSec * 3600.0;
    } else if ((volume + "/day").equals(unit)) {
      return perSec * 86400.0;
    } else if (("M" + volume + "/day").equals(unit)) {
      return perSec * 86400.0 / 1.0e6;
    }
    throw new IllegalArgumentException("Unsupported volume flow unit '" + unit + "'; use " + volume + "/sec, " + volume
        + "/min, " + volume + "/hr, " + volume + "/day or M" + volume + "/day");
  }

  /**
   * Returns the leakage mass flow.
   *
   * @param unit "kg/sec", "kg/s", "kg/min", "kg/hr", "kg/h" or "kg/day"
   * @return mass flow
   */
  public double getMassFlowRate(String unit) {
    requireResult();
    return convertMassFlow(massFlowKgPerSec, unit);
  }

  /**
   * Returns the critical (choked) mass flow of the clearance for the selected method.
   *
   * <p>
   * For {@link LabyrinthFlowMethod#NEUMANN} this is the largest flow at which no tooth chokes. When the leakage is not
   * choked it is found by a separate search on the first request, which costs additional PH flashes.
   * </p>
   *
   * @param unit mass flow unit, see {@link #getMassFlowRate(String)}
   * @return critical mass flow, NaN when it cannot be determined
   */
  public double getCriticalMassFlowRate(String unit) {
    requireResult();
    if (method == LabyrinthFlowMethod.NEUMANN && Double.isNaN(criticalMassFlowKgPerSec) && massFlowKgPerSec > 0.0) {
      criticalMassFlowKgPerSec = neumannSolver().chokedFlow(massFlowKgPerSec);
    }
    return convertMassFlow(criticalMassFlowKgPerSec, unit);
  }

  /**
   * Returns the pressure at the inlet and after each tooth from the last Neumann calculation.
   *
   * @param unit pressure unit supported by {@link PressureUnit}, for example "bara" or "barg"
   * @return n + 1 pressures, empty when the selected method is not NEUMANN or the calculation failed
   */
  public double[] getToothPressures(String unit) {
    requireResult();
    double[] values = new double[toothPressuresBara.length];
    for (int i = 0; i < values.length; i++) {
      values[i] = new PressureUnit(toothPressuresBara[i], "bara").getValue(unit);
    }
    return values;
  }

  /**
   * Returns the gas temperature at the inlet and after each tooth from the last Neumann calculation.
   *
   * @param unit temperature unit, for example "C" or "K"
   * @return n + 1 temperatures, empty when the selected method is not NEUMANN or the calculation failed
   */
  public double[] getToothTemperatures(String unit) {
    requireResult();
    double[] values = new double[toothTemperaturesK.length];
    for (int i = 0; i < values.length; i++) {
      values[i] = new TemperatureUnit(toothTemperaturesK[i], "K").getValue(unit);
    }
    return values;
  }

  /**
   * Returns the gas density at the inlet and after each tooth from the last Neumann calculation.
   *
   * @return n + 1 densities in kg/m3, empty when the selected method is not NEUMANN or the calculation failed
   */
  public double[] getToothDensities() {
    requireResult();
    return toothDensities.clone();
  }

  /**
   * Returns whether the selected method is limited by the critical flow.
   *
   * @return true when the leakage equals the critical flow
   */
  public boolean isChoked() {
    requireResult();
    return choked;
  }

  /**
   * Returns the leakage volume flow at inlet conditions.
   *
   * @param unit "m3/sec", "m3/min", "m3/hr", "m3/day" or "Mm3/day"
   * @return actual volume flow
   */
  public double getActualVolumeFlowRate(String unit) {
    requireResult();
    return convertVolumeFlow(massFlowKgPerSec / inletDensity, unit, "m3");
  }

  /**
   * Returns the leakage as ideal-gas standard volume flow at the standard conditions (default 15 C, 1.01325 bara).
   *
   * @param unit "Sm3/sec", "Sm3/min", "Sm3/hr", "Sm3/day" or "MSm3/day"
   * @return standard volume flow
   */
  public double getStandardVolumeFlowRate(String unit) {
    requireResult();
    double molarFlow = massFlowKgPerSec / (inletMolarMass / 1000.0);
    double molarVolume = ThermodynamicConstantsInterface.R * standardTemperatureK / (standardPressureBara * 1.0e5);
    return convertVolumeFlow(molarFlow * molarVolume, unit, "Sm3");
  }

  /**
   * Returns the mean gas velocity in the clearance based on inlet density.
   *
   * @param unit "m/s", "m/sec" or "ft/s"
   * @return clearance velocity
   * @throws IllegalArgumentException for an unsupported unit
   */
  public double getClearanceVelocity(String unit) {
    requireResult();
    double velocity = massFlowKgPerSec / inletDensity / geometry.getClearanceArea();
    if ("m/s".equals(unit) || "m/sec".equals(unit)) {
      return velocity;
    } else if ("ft/s".equals(unit)) {
      return velocity / 0.3048;
    }
    throw new IllegalArgumentException("Unsupported velocity unit '" + unit + "'; use m/s or ft/s");
  }

  /**
   * Returns the pressure drop across the seal.
   *
   * @param unit one of "bar", "mbar", "Pa", "kPa", "MPa", "psi"
   * @return pressure drop
   */
  public double getPressureDrop(String unit) {
    double dropBar = inletPressureBara - resolvedOutletPressureBara();
    return dropBar / pressureDifferenceToBar(1.0, unit);
  }

  /**
   * Returns the gas density at the inlet.
   *
   * @return density in kg/m3
   */
  public double getInletDensity() {
    requireResult();
    return inletDensity;
  }

  /**
   * Returns the gas molar mass at the inlet.
   *
   * @return molar mass in kg/kmol
   */
  public double getInletMolarMass() {
    requireResult();
    return inletMolarMass;
  }

  /**
   * Returns the real-gas isentropic exponent of the gas at the inlet.
   *
   * @return isentropic exponent kappa
   */
  public double getInletIsentropicExponent() {
    requireResult();
    return inletKappa;
  }

  /**
   * Returns the gas compressibility factor at the inlet.
   *
   * @return compressibility factor Z
   */
  public double getInletCompressibility() {
    requireResult();
    return inletCompressibility;
  }

  /**
   * Returns whether the inlet flash found a liquid phase next to the gas.
   *
   * @return true if liquid is present at the inlet
   */
  public boolean hasLiquidAtInlet() {
    requireResult();
    return liquidAtInlet;
  }

  /**
   * Returns the inputs and results as a strict JSON object. Undefined (NaN or infinite) numbers are written as null.
   *
   * @return JSON string
   */
  public String toJson() {
    requireResult();
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("method", method.name());
    map.put("massFlowRate_kg_s", finiteOrNull(massFlowKgPerSec));
    map.put("massFlowRate_kg_hr", finiteOrNull(getMassFlowRate("kg/hr")));
    map.put("criticalMassFlowRate_kg_s", finiteOrNull(getCriticalMassFlowRate("kg/sec")));
    map.put("choked", choked);
    map.put("withinValidityRange", validityViolations.isEmpty());
    map.put("validityViolations", new ArrayList<String>(validityViolations));
    map.put("actualVolumeFlowRate_m3_hr", finiteOrNull(getActualVolumeFlowRate("m3/hr")));
    map.put("standardVolumeFlowRate_Sm3_hr", finiteOrNull(getStandardVolumeFlowRate("Sm3/hr")));
    map.put("clearanceVelocity_m_s", finiteOrNull(getClearanceVelocity("m/s")));
    map.put("inletPressure_bara", finiteOrNull(inletPressureBara));
    map.put("inletTemperature_C", finiteOrNull(inletTemperatureK - 273.15));
    map.put("outletPressure_bara", finiteOrNull(resolvedOutletPressureBara()));
    map.put("pressureDrop_bar", finiteOrNull(getPressureDrop("bar")));
    map.put("inletDensity_kg_m3", finiteOrNull(inletDensity));
    map.put("inletMolarMass_kg_kmol", finiteOrNull(inletMolarMass));
    map.put("inletIsentropicExponent", finiteOrNull(inletKappa));
    map.put("inletCompressibility", finiteOrNull(inletCompressibility));
    map.put("liquidAtInlet", liquidAtInlet);
    map.put("eos", fluid.getModelName());
    map.put("geometry", geometry.toMap());
    if (toothPressuresBara.length > 0) {
      map.put("toothPressures_bara", finiteList(toothPressuresBara, 0.0));
      map.put("toothTemperatures_C", finiteList(toothTemperaturesK, -273.15));
      map.put("toothDensities_kg_m3", finiteList(toothDensities, 0.0));
    }
    return new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(map);
  }

  /**
   * Converts an array to a list of boxed values with an offset, writing null for non-finite entries.
   *
   * @param values values to convert
   * @param offset offset added to each value
   * @return list of boxed values
   */
  private static List<Double> finiteList(double[] values, double offset) {
    List<Double> list = new ArrayList<Double>(values.length);
    for (double value : values) {
      list.add(finiteOrNull(value + offset));
    }
    return list;
  }

  /**
   * Boxes a finite value, or returns null for NaN and infinity so the JSON stays strict.
   *
   * @param value value to box
   * @return boxed value, or null when not finite
   */
  private static Double finiteOrNull(double value) {
    return Double.isNaN(value) || Double.isInfinite(value) ? null : Double.valueOf(value);
  }
}
