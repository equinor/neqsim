package neqsim.pvtsimulation.flowassurance;

import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Calibrated solid asphaltene precipitation model (Nghiem and Coombe type) on a cubic equation of state.
 *
 * <p>
 * Precipitated asphaltene is a pure solid whose fugacity depends only on temperature and pressure. It is anchored at a
 * measured onset point (T*, P*) of a reference oil:
 * </p>
 *
 * <pre>
 * ln f_s(T, P) = ln f_L(T*, P*) + v_s (P - P*) / (R T) - (h_s / R) (1/T - 1/T*)
 * </pre>
 *
 * <p>
 * where f_L is the asphaltene fugacity in the equilibrated fluid (gas and oil, no solid). Asphaltene precipitates when
 * f_L exceeds f_s. Because the solid reference is independent of the fluid, one calibration on the host oil predicts
 * the onset of the same oil at other pressures and of the same asphaltene in blends with other fluids (gas injection,
 * condensate commingling).
 * </p>
 *
 * <p>
 * The composition response comes from the asphaltene interaction parameters. Unlike the default (zero), they follow a
 * size-asymmetry correlation of Chueh-Prausnitz form on the equation-of-state co-volume (b proportional to Tc/Pc):
 * </p>
 *
 * <pre>
 * k_ij = 1 - [2 (b_i b_j)^(1/6) / (b_i^(1/3) + b_j^(1/3))]^e
 * </pre>
 *
 * <p>
 * so small molecules (N2, CO2, methane, ethane) get large values and heavy fractions near zero. This makes light
 * diluents raise the onset pressure, as titration and field data show. With zero interaction parameters dilution of the
 * asphaltene dominates and the model wrongly predicts that light diluents stabilise the oil.
 * </p>
 *
 * <p>
 * Calibration levels: {@link #calibrate(double, double)} (one onset, solid volume from the asphaltene density),
 * {@link #calibrateLowerOnset(double)} (adds the lower onset below the bubble point and fits the solid volume),
 * {@link #calibrateTemperature(double, double)} (adds an onset at a second temperature and fits h_s), and
 * {@link #fitKijExponent(SystemInterface, double, double, double, double)} (fits e to a measured onset of a blend).
 * </p>
 *
 * <p>
 * The fluid must contain exactly one component whose name contains "asphalten" (for example the database component
 * "asphaltene" or a TBP fraction added by {@link neqsim.thermo.characterization.PedersenAsphalteneCharacterization}),
 * use a cubic equation of state with the classic mixing rule set, and be water-free. If the equation of state splits
 * the asphaltene into a separate liquid (characterization too immiscible), {@link #isAsphalteneLiquidSplitDetected()}
 * returns true; lower the asphaltene acentric factor or interaction exponent.
 * </p>
 *
 * <p>
 * References: Nghiem, L.X., Coombe, D.A. (1997). Modeling asphaltene precipitation during primary depletion. SPE
 * Journal 2(2), 170-176. Nghiem, L.X., Hassam, M.S., Nutakki, R., George, A.E.D. (1993). Efficient modelling of
 * asphaltene precipitation. SPE 26642.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class AsphalteneSolidModel implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AsphalteneSolidModel.class);

  /** Gas constant, J/(mol K). */
  private static final double R = 8.314462618;

  /** Default solid asphaltene density when the component has none in range, kg/m3. */
  public static final double DEFAULT_SOLID_DENSITY = 1150.0;

  /** Bisection tolerance for onset pressures, bar. */
  private static final double PRESSURE_TOLERANCE = 0.05;

  /** Reference (host) fluid; never flashed directly. */
  private final SystemInterface referenceFluid;

  /** Asphaltene component name in the reference fluid. */
  private final String asphalteneName;

  /** Exponent of the size-asymmetry interaction correlation. */
  private double kijExponent = 1.0;

  /** Solid molar volume, m3/mol. */
  private double solidMolarVolume;

  /** Effective solid enthalpy for the temperature dependence, J/mol. */
  private double solidEnthalpy = 0.0;

  /** Calibration temperature, K. */
  private double referenceTemperature = Double.NaN;

  /** Calibration (upper onset) pressure, bara. */
  private double referencePressure = Double.NaN;

  /** Natural logarithm of the solid fugacity at the calibration point (fugacity in bar). */
  private double lnReferenceFugacity = Double.NaN;

  /** Lower onset used in calibration, bara (NaN if not used). */
  private double calibrationLowerPressure = Double.NaN;

  /** Second temperature used in calibration, K (NaN if not used). */
  private double calibrationTemperature2 = Double.NaN;

  /** Onset pressure at the second calibration temperature, bara. */
  private double calibrationPressure2 = Double.NaN;

  /** True if any evaluation found an asphaltene-rich liquid split. */
  private boolean liquidSplitDetected = false;

  /**
   * Result of an onset pressure search.
   */
  public static class OnsetResult implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Upper onset pressure, bara (NaN if none in range). */
    private final double upperOnsetPressure;
    /** Lower onset pressure, bara (NaN if none in range). */
    private final double lowerOnsetPressure;
    /** True if the fluid is already unstable at the maximum search pressure. */
    private final boolean unstableAtMaximumPressure;

    /**
     * Constructor.
     *
     * @param upper upper onset pressure in bara
     * @param lower lower onset pressure in bara
     * @param unstableAtMax true if unstable at the maximum search pressure
     */
    OnsetResult(double upper, double lower, boolean unstableAtMax) {
      this.upperOnsetPressure = upper;
      this.lowerOnsetPressure = lower;
      this.unstableAtMaximumPressure = unstableAtMax;
    }

    /**
     * Get the upper onset pressure: the highest pressure where asphaltene starts to precipitate.
     *
     * @return pressure in bara, NaN if not found
     */
    public double getUpperOnsetPressure() {
      return upperOnsetPressure;
    }

    /**
     * Get the lower onset pressure: below it (below the bubble point) the asphaltene redissolves.
     *
     * @return pressure in bara, NaN if not found
     */
    public double getLowerOnsetPressure() {
      return lowerOnsetPressure;
    }

    /**
     * Check whether the fluid was already unstable at the maximum search pressure.
     *
     * @return true if unstable at the top of the range
     */
    public boolean isUnstableAtMaximumPressure() {
      return unstableAtMaximumPressure;
    }
  }

  /**
   * Create a model for a reference fluid (usually the host oil with its measured onset).
   *
   * @param fluid fluid with one asphaltene component and the classic mixing rule set; it is cloned
   * @throws IllegalArgumentException if the fluid has no or several asphaltene components, no positive asphaltene
   * inventory, or no cubic mixing rule
   */
  public AsphalteneSolidModel(SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("Fluid must not be null");
    }
    this.referenceFluid = fluid.clone();
    this.asphalteneName = findAsphaltene(referenceFluid);
    requireEosMixingRule(referenceFluid);
    double molarMass = referenceFluid.getPhase(0).getComponent(asphalteneName).getMolarMass();
    double density = referenceFluid.getPhase(0).getComponent(asphalteneName).getNormalLiquidDensity() * 1000.0;
    if (!(density >= 900.0 && density <= 1400.0)) {
      density = DEFAULT_SOLID_DENSITY;
    }
    this.solidMolarVolume = molarMass / density;
  }

  /**
   * Find the single asphaltene component.
   *
   * @param fluid fluid to search
   * @return the asphaltene component name
   */
  static String findAsphaltene(SystemInterface fluid) {
    String found = null;
    for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
      String name = fluid.getPhase(0).getComponent(i).getComponentName();
      if (name.toLowerCase(Locale.ROOT).contains("asphalten")) {
        if (found != null) {
          throw new IllegalArgumentException(
              "AsphalteneSolidModel supports one asphaltene component; found " + found + " and " + name);
        }
        found = name;
      }
    }
    if (found == null) {
      throw new IllegalArgumentException("Fluid has no asphaltene component (name containing 'asphalten')");
    }
    double moles = fluid.getPhase(0).getComponent(found).getNumberOfmoles();
    if (!(moles > 0.0)) {
      throw new IllegalArgumentException("Asphaltene component " + found + " has no positive inventory");
    }
    return found;
  }

  /**
   * Require a cubic mixing rule on the fluid.
   *
   * @param fluid fluid to check
   */
  private static void requireEosMixingRule(SystemInterface fluid) {
    PhaseInterface phase = fluid.getPhase(0);
    if (!(phase instanceof PhaseEosInterface) || ((PhaseEosInterface) phase).getEosMixingRule() == null) {
      throw new IllegalArgumentException("AsphalteneSolidModel needs a cubic equation of state with the mixing rule "
          + "set (for example setMixingRule(\"classic\"))");
    }
  }

  /**
   * Size-asymmetry interaction parameter between the asphaltene and a component.
   *
   * @param asphalteneTcOverPc asphaltene Tc/Pc in K/bar
   * @param componentTcOverPc component Tc/Pc in K/bar
   * @param exponent correlation exponent
   * @return interaction parameter (0 for equal size)
   */
  public static double sizeAsymmetryKij(double asphalteneTcOverPc, double componentTcOverPc, double exponent) {
    if (!(asphalteneTcOverPc > 0.0) || !(componentTcOverPc > 0.0)) {
      return 0.0;
    }
    double a = Math.cbrt(asphalteneTcOverPc);
    double c = Math.cbrt(componentTcOverPc);
    double ratio = 2.0 * Math.sqrt(a * c) / (a + c);
    return 1.0 - Math.pow(ratio, exponent);
  }

  /**
   * Set the size-asymmetry interaction parameters between the asphaltene and every other component of a fluid. Water
   * keeps its existing value.
   *
   * @param fluid fluid with the mixing rule set; its asphaltene must have the same name as the reference fluid's
   */
  public void applyInteractionParameters(SystemInterface fluid) {
    requireEosMixingRule(fluid);
    int nc = fluid.getPhase(0).getNumberOfComponents();
    int a = fluid.getPhase(0).getComponent(asphalteneName).getComponentNumber();
    double asphSize = fluid.getPhase(0).getComponent(a).getTC() / fluid.getPhase(0).getComponent(a).getPC();
    double[] kij = new double[nc];
    for (int k = 0; k < nc; k++) {
      if (k == a) {
        continue;
      }
      double size = fluid.getPhase(0).getComponent(k).getTC() / fluid.getPhase(0).getComponent(k).getPC();
      kij[k] = sizeAsymmetryKij(asphSize, size, kijExponent);
    }
    for (int p = 0; p < fluid.getMaxNumberOfPhases(); p++) {
      PhaseInterface phase = fluid.getPhase(p);
      if (!(phase instanceof PhaseEosInterface)) {
        continue;
      }
      EosMixingRulesInterface rule = ((PhaseEosInterface) phase).getEosMixingRule();
      if (rule == null) {
        continue;
      }
      for (int k = 0; k < nc; k++) {
        if (k == a || "water".equalsIgnoreCase(phase.getComponent(k).getComponentName())) {
          continue;
        }
        rule.setBinaryInteractionParameter(a, k, kij[k]);
        rule.setBinaryInteractionParameter(k, a, kij[k]);
      }
    }
  }

  /**
   * Natural logarithm of the asphaltene fugacity (bar) in a fluid at its current temperature and pressure, after an
   * equilibrium flash without a solid phase.
   *
   * @param fluid fluid to evaluate; it is cloned, given the model interaction parameters and flashed
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return ln of the asphaltene fugacity in bar
   */
  public double lnAsphalteneFugacity(SystemInterface fluid, double temperature, double pressure) {
    requirePositive(temperature, "Temperature");
    requirePositive(pressure, "Pressure");
    SystemInterface work = fluid.clone();
    applyInteractionParameters(work);
    work.setTemperature(temperature);
    work.setPressure(pressure);
    try {
      new ThermodynamicOperations(work).TPflash();
    } catch (RuntimeException failure) {
      throw new IllegalStateException("Flash failed at " + temperature + " K and " + pressure + " bara", failure);
    }
    work.init(1);
    int a = work.getPhase(0).getComponent(asphalteneName).getComponentNumber();
    double bestX = -1.0;
    double lnF = Double.NaN;
    for (int p = 0; p < work.getNumberOfPhases(); p++) {
      PhaseInterface phase = work.getPhase(p);
      double x = phase.getComponent(a).getx();
      if (phase.isAsphalteneRich() && work.getNumberOfPhases() > 1) {
        liquidSplitDetected = true;
      }
      double phi = phase.getComponent(a).getFugacityCoefficient();
      if (x > bestX && x > 0.0 && phi > 0.0 && Double.isFinite(phi)) {
        bestX = x;
        lnF = Math.log(x) + Math.log(phi) + Math.log(pressure);
      }
    }
    if (!Double.isFinite(lnF)) {
      throw new IllegalStateException(
          "No finite asphaltene fugacity at " + temperature + " K and " + pressure + " bara");
    }
    return lnF;
  }

  /**
   * Natural logarithm of the solid asphaltene fugacity (bar).
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return ln of the solid fugacity in bar
   */
  public double lnSolidFugacity(double temperature, double pressure) {
    requireCalibrated();
    return lnReferenceFugacity + solidMolarVolume * (pressure - referencePressure) * 1.0e5 / (R * temperature)
        - solidEnthalpy / R * (1.0 / temperature - 1.0 / referenceTemperature);
  }

  /**
   * Supersaturation ln(f_L / f_s) of the asphaltene in a fluid; positive means asphaltene precipitates.
   *
   * @param fluid fluid to evaluate (cloned)
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return ln(f_L / f_s)
   */
  public double supersaturation(SystemInterface fluid, double temperature, double pressure) {
    requireCalibrated();
    return lnAsphalteneFugacity(fluid, temperature, pressure) - lnSolidFugacity(temperature, pressure);
  }

  /**
   * Supersaturation of the reference fluid.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return ln(f_L / f_s)
   */
  public double supersaturation(double temperature, double pressure) {
    return supersaturation(referenceFluid, temperature, pressure);
  }

  /**
   * Calibrate the solid reference to a measured (upper) onset pressure of the reference fluid. The solid molar volume
   * stays at its current value (default: asphaltene molar mass over density).
   *
   * @param temperature onset temperature in K
   * @param onsetPressure measured onset pressure in bara (above the bubble point)
   */
  public void calibrate(double temperature, double onsetPressure) {
    calibrationLowerPressure = Double.NaN;
    calibrationTemperature2 = Double.NaN;
    calibrationPressure2 = Double.NaN;
    solidEnthalpy = 0.0;
    anchor(temperature, onsetPressure);
  }

  /**
   * Set the anchor point.
   *
   * @param temperature temperature in K
   * @param onsetPressure onset pressure in bara
   */
  private void anchor(double temperature, double onsetPressure) {
    requirePositive(temperature, "Temperature");
    requirePositive(onsetPressure, "Onset pressure");
    lnReferenceFugacity = lnAsphalteneFugacity(referenceFluid, temperature, onsetPressure);
    referenceTemperature = temperature;
    referencePressure = onsetPressure;
  }

  /**
   * Add a measured lower onset pressure (below the bubble point, same temperature as {@link #calibrate}) and fit the
   * solid molar volume so both onsets are reproduced.
   *
   * @param lowerOnsetPressure lower onset in bara, below the calibrated upper onset
   * @throws IllegalStateException if the fitted solid volume is not positive (data inconsistent with the model)
   */
  public void calibrateLowerOnset(double lowerOnsetPressure) {
    requireCalibrated();
    if (!(lowerOnsetPressure > 0.0) || lowerOnsetPressure >= referencePressure) {
      throw new IllegalArgumentException("Lower onset must be positive and below the upper onset");
    }
    double lnF = lnAsphalteneFugacity(referenceFluid, referenceTemperature, lowerOnsetPressure);
    double volume = (lnF - lnReferenceFugacity) * R * referenceTemperature
        / ((lowerOnsetPressure - referencePressure) * 1.0e5);
    if (!(volume > 0.0) || !Double.isFinite(volume)) {
      throw new IllegalStateException("Lower onset gives a non-positive solid molar volume (" + volume + " m3/mol); "
          + "the fluid model cannot reproduce both onsets");
    }
    solidMolarVolume = volume;
    calibrationLowerPressure = lowerOnsetPressure;
  }

  /**
   * Add a measured onset at a second temperature and fit the effective solid enthalpy.
   *
   * @param temperature second temperature in K, different from the calibration temperature
   * @param onsetPressure onset pressure at that temperature in bara
   */
  public void calibrateTemperature(double temperature, double onsetPressure) {
    requireCalibrated();
    requirePositive(temperature, "Temperature");
    requirePositive(onsetPressure, "Onset pressure");
    if (Math.abs(temperature - referenceTemperature) < 0.5) {
      throw new IllegalArgumentException("Second temperature must differ from the calibration temperature");
    }
    double lnF = lnAsphalteneFugacity(referenceFluid, temperature, onsetPressure);
    double residual = lnF - lnReferenceFugacity
        - solidMolarVolume * (onsetPressure - referencePressure) * 1.0e5 / (R * temperature);
    solidEnthalpy = -R * residual / (1.0 / temperature - 1.0 / referenceTemperature);
    calibrationTemperature2 = temperature;
    calibrationPressure2 = onsetPressure;
  }

  /**
   * Replay the stored calibration (after changing the interaction exponent).
   */
  private void recalibrate() {
    double lower = calibrationLowerPressure;
    double t2 = calibrationTemperature2;
    double p2 = calibrationPressure2;
    anchor(referenceTemperature, referencePressure);
    if (!Double.isNaN(lower)) {
      calibrateLowerOnset(lower);
    }
    if (!Double.isNaN(t2)) {
      calibrateTemperature(t2, p2);
    }
  }

  /**
   * Find the upper and lower onset pressures of a fluid by scanning down from a maximum pressure and bisecting each
   * sign change of the supersaturation.
   *
   * @param fluid fluid to evaluate (cloned); for blends, a fluid with the same asphaltene component
   * @param temperature temperature in K
   * @param maximumPressure top of the search range in bara
   * @param minimumPressure bottom of the search range in bara
   * @param step scan step in bar
   * @return onset result
   */
  public OnsetResult onsetPressures(SystemInterface fluid, double temperature, double maximumPressure,
      double minimumPressure, double step) {
    requireCalibrated();
    requirePositive(step, "Pressure step");
    requirePositive(minimumPressure, "Minimum pressure");
    if (!(maximumPressure > minimumPressure)) {
      throw new IllegalArgumentException("Maximum pressure must exceed the minimum pressure");
    }
    double previousPressure = maximumPressure;
    double previous = supersaturation(fluid, temperature, previousPressure);
    boolean unstableAtMax = previous > 0.0;
    double upper = Double.NaN;
    double lower = Double.NaN;
    while (previousPressure > minimumPressure) {
      double pressure = Math.max(minimumPressure, previousPressure - step);
      double value = supersaturation(fluid, temperature, pressure);
      if (previous <= 0.0 && value > 0.0 && Double.isNaN(upper)) {
        upper = bisect(fluid, temperature, pressure, previousPressure);
      } else if (previous > 0.0 && value <= 0.0 && (!Double.isNaN(upper) || unstableAtMax)) {
        lower = bisect(fluid, temperature, pressure, previousPressure);
        break;
      }
      previous = value;
      previousPressure = pressure;
    }
    return new OnsetResult(upper, lower, unstableAtMax);
  }

  /**
   * Onset pressures of the reference fluid.
   *
   * @param temperature temperature in K
   * @param maximumPressure top of the search range in bara
   * @param minimumPressure bottom of the search range in bara
   * @param step scan step in bar
   * @return onset result
   */
  public OnsetResult onsetPressures(double temperature, double maximumPressure, double minimumPressure, double step) {
    return onsetPressures(referenceFluid, temperature, maximumPressure, minimumPressure, step);
  }

  /**
   * Bisect the supersaturation sign change between two pressures.
   *
   * @param fluid fluid
   * @param temperature temperature in K
   * @param low lower pressure in bara
   * @param high higher pressure in bara
   * @return pressure of the sign change in bara
   */
  private double bisect(SystemInterface fluid, double temperature, double low, double high) {
    boolean lowPositive = supersaturation(fluid, temperature, low) > 0.0;
    for (int i = 0; i < 60 && high - low > PRESSURE_TOLERANCE; i++) {
      double mid = 0.5 * (low + high);
      if ((supersaturation(fluid, temperature, mid) > 0.0) == lowPositive) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return 0.5 * (low + high);
  }

  /**
   * Fraction of the asphaltene that precipitates as solid at equilibrium.
   *
   * @param fluid fluid to evaluate (cloned)
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return precipitated fraction of the asphaltene (0-1)
   */
  public double precipitatedFraction(SystemInterface fluid, double temperature, double pressure) {
    if (supersaturation(fluid, temperature, pressure) <= 0.0) {
      return 0.0;
    }
    double total = fluid.getPhase(0).getComponent(asphalteneName).getNumberOfmoles();
    double lnSolid = lnSolidFugacity(temperature, pressure);
    double low = 0.0;
    double high = total;
    for (int i = 0; i < 50 && high - low > 1e-6 * total; i++) {
      double mid = 0.5 * (low + high);
      SystemInterface work = fluid.clone();
      work.addComponent(asphalteneName, -mid);
      work.init(0);
      if (lnAsphalteneFugacity(work, temperature, pressure) > lnSolid) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return 0.5 * (low + high) / total;
  }

  /**
   * Fit the interaction exponent to a measured upper onset of another fluid (for example a host-condensate blend),
   * keeping the reference-fluid calibration. Golden-section search over [0.3, 3.5].
   *
   * @param fluid fluid with the measured onset (cloned)
   * @param temperature temperature in K
   * @param measuredOnsetPressure measured upper onset in bara
   * @param maximumPressure top of the search range in bara
   * @param minimumPressure bottom of the search range in bara
   * @return the fitted exponent (also set on the model)
   */
  public double fitKijExponent(SystemInterface fluid, double temperature, double measuredOnsetPressure,
      double maximumPressure, double minimumPressure) {
    requireCalibrated();
    final double ratio = 0.5 * (Math.sqrt(5.0) - 1.0);
    double a = 0.3;
    double b = 3.5;
    double c = b - ratio * (b - a);
    double d = a + ratio * (b - a);
    double fc = onsetError(fluid, temperature, measuredOnsetPressure, maximumPressure, minimumPressure, c);
    double fd = onsetError(fluid, temperature, measuredOnsetPressure, maximumPressure, minimumPressure, d);
    for (int i = 0; i < 25 && b - a > 0.01; i++) {
      if (fc < fd) {
        b = d;
        d = c;
        fd = fc;
        c = b - ratio * (b - a);
        fc = onsetError(fluid, temperature, measuredOnsetPressure, maximumPressure, minimumPressure, c);
      } else {
        a = c;
        c = d;
        fc = fd;
        d = a + ratio * (b - a);
        fd = onsetError(fluid, temperature, measuredOnsetPressure, maximumPressure, minimumPressure, d);
      }
    }
    setKijExponent(0.5 * (a + b));
    return kijExponent;
  }

  /**
   * Onset error for a trial exponent.
   *
   * @param fluid fluid with the measured onset
   * @param temperature temperature in K
   * @param measured measured onset in bara
   * @param maximumPressure top of range in bara
   * @param minimumPressure bottom of range in bara
   * @param exponent trial exponent
   * @return absolute onset error in bar (a large value if no onset is found)
   */
  private double onsetError(SystemInterface fluid, double temperature, double measured, double maximumPressure,
      double minimumPressure, double exponent) {
    setKijExponent(exponent);
    OnsetResult result = onsetPressures(fluid, temperature, maximumPressure, minimumPressure,
        Math.max(1.0, (maximumPressure - minimumPressure) / 60.0));
    if (result.isUnstableAtMaximumPressure()) {
      return 1.0e3 + (maximumPressure - measured);
    }
    if (Double.isNaN(result.getUpperOnsetPressure())) {
      return 1.0e3 + (measured - minimumPressure);
    }
    return Math.abs(result.getUpperOnsetPressure() - measured);
  }

  /**
   * Validate a positive finite value.
   *
   * @param value value
   * @param name name for the message
   */
  private static void requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /** Throw if the model is not calibrated. */
  private void requireCalibrated() {
    if (Double.isNaN(lnReferenceFugacity)) {
      throw new IllegalStateException("Calibrate the model to a measured onset first (calibrate(T, P))");
    }
  }

  /**
   * Check whether the model has been calibrated.
   *
   * @return true after {@link #calibrate(double, double)}
   */
  public boolean isCalibrated() {
    return !Double.isNaN(lnReferenceFugacity);
  }

  /**
   * Set the exponent of the size-asymmetry interaction correlation and replay the calibration, if any.
   *
   * @param exponent exponent (typical 0.5-2.5; larger gives a stronger light-component effect)
   */
  public void setKijExponent(double exponent) {
    requirePositive(exponent, "Interaction exponent");
    this.kijExponent = exponent;
    if (isCalibrated()) {
      recalibrate();
    }
  }

  /**
   * Get the exponent of the size-asymmetry interaction correlation.
   *
   * @return exponent
   */
  public double getKijExponent() {
    return kijExponent;
  }

  /**
   * Interaction parameter the model applies between the asphaltene and a component of the reference fluid.
   *
   * @param componentName component name
   * @return interaction parameter
   */
  public double getInteractionParameter(String componentName) {
    PhaseInterface phase = referenceFluid.getPhase(0);
    double asph = phase.getComponent(asphalteneName).getTC() / phase.getComponent(asphalteneName).getPC();
    double other = phase.getComponent(componentName).getTC() / phase.getComponent(componentName).getPC();
    return sizeAsymmetryKij(asph, other, kijExponent);
  }

  /**
   * Set the solid molar volume. The anchor point is kept; a fitted lower onset is discarded.
   *
   * @param volume solid molar volume in m3/mol
   */
  public void setSolidMolarVolume(double volume) {
    requirePositive(volume, "Solid molar volume");
    this.solidMolarVolume = volume;
    this.calibrationLowerPressure = Double.NaN;
  }

  /**
   * Get the solid molar volume.
   *
   * @return volume in m3/mol
   */
  public double getSolidMolarVolume() {
    return solidMolarVolume;
  }

  /**
   * Get the effective solid enthalpy used for the temperature dependence.
   *
   * @return enthalpy in J/mol (0 unless calibrated at two temperatures or set)
   */
  public double getSolidEnthalpy() {
    return solidEnthalpy;
  }

  /**
   * Set the effective solid enthalpy for the temperature dependence.
   *
   * @param enthalpy enthalpy in J/mol
   */
  public void setSolidEnthalpy(double enthalpy) {
    if (!Double.isFinite(enthalpy)) {
      throw new IllegalArgumentException("Solid enthalpy must be finite");
    }
    this.solidEnthalpy = enthalpy;
    this.calibrationTemperature2 = Double.NaN;
    this.calibrationPressure2 = Double.NaN;
  }

  /**
   * Get the calibration temperature.
   *
   * @return temperature in K (NaN before calibration)
   */
  public double getReferenceTemperature() {
    return referenceTemperature;
  }

  /**
   * Get the calibration (upper onset) pressure.
   *
   * @return pressure in bara (NaN before calibration)
   */
  public double getReferencePressure() {
    return referencePressure;
  }

  /**
   * Get the asphaltene component name.
   *
   * @return component name
   */
  public String getAsphalteneName() {
    return asphalteneName;
  }

  /**
   * Check whether any evaluation found an asphaltene-rich liquid phase split by the equation of state. If true, the
   * asphaltene characterization is too immiscible for the solid model; lower its acentric factor or the interaction
   * exponent.
   *
   * @return true if a split was found
   */
  public boolean isAsphalteneLiquidSplitDetected() {
    return liquidSplitDetected;
  }
}
