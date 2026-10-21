package neqsim.process.equipment.compressor.seal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tooth-by-tooth (modified Neumann) labyrinth leakage solver used by {@link LabyrinthSealFlowCalculator}.
 *
 * <p>
 * For a trial mass flow {@code m} the solver marches through the teeth. At tooth {@code i}, with upstream pressure
 * {@code p_i} (Pa), gas density {@code rho_i} and real-gas isentropic exponent {@code k_i}, the downstream pressure
 * solves
 * </p>
 *
 * <pre>
 * p_{i+1}^2 = p_i^2 - (m / (Cf_i u_i A))^2 p_i / rho_i
 * Cf_i = pi / (pi + 2 - 5 beta_i + 2 beta_i^2),  beta_i = max((p_i / p_{i+1})^((k_i - 1) / k_i) - 1, 0)
 * u_0 = 1,  u_i = sqrt(n / (n (1 - alpha) + alpha)),  alpha = 1 - 1 / (1 + 16.6 c / t)^2
 * </pre>
 *
 * <p>
 * The flow coefficient depends on {@code p_{i+1}}, so each tooth is solved for its pressure ratio with a bracketed
 * Brent search between the critical ratio and 1, where the residual increases monotonically. The state after each tooth
 * is an exact PH flash of the inlet gas at the inlet enthalpy. A tooth chokes when {@code p_{i+1}} would fall below
 * {@code p_i (2/(k_i + 1))^(k_i/(k_i - 1))}. The leakage is the flow for which the pressure after the last tooth equals
 * the outlet pressure (Brent root search). When that pressure cannot be reached without a tooth choking, the leakage is
 * the largest flow at which no tooth chokes (the choked flow).
 * </p>
 *
 * <p>
 * The march does not depend on the outlet pressure, so marches are cached per trial flow and reused across outlet
 * pressures. GERG-2008 fluids with a single-phase gas inlet first try the dedicated {@code PHflashGERG2008} and fall
 * back to the general {@code PHflash} when the expanded state is multiphase or misses the inlet enthalpy; every other
 * fluid uses the general {@code PHflash}. Every state is checked against the inlet enthalpy.
 * </p>
 *
 * @author neqsim
 * @version 1.0
 */
final class NeumannLabyrinthSolver {
  private static final Logger logger = LogManager.getLogger(NeumannLabyrinthSolver.class);

  /** Relative accuracy of the converged leakage flow. */
  private static final double FLOW_RELATIVE_TOLERANCE = 1.0e-10;
  /** Relative width of the bracket that locates the choked flow. */
  private static final double CHOKE_RELATIVE_TOLERANCE = 1.0e-8;
  /** Absolute accuracy of the dimensionless tooth pressure ratio. */
  private static final double TOOTH_RATIO_ACCURACY = 1.0e-14;
  private static final int MAX_TOOTH_EVALUATIONS = 200;
  /** Largest accepted enthalpy error of an isenthalpic state, expressed as a temperature error in K. */
  private static final double ENTHALPY_TOLERANCE_K = 1.0e-3;
  private static final int MAX_BRACKET_STEPS = 200;
  private static final int MAX_ROOT_EVALUATIONS = 200;
  private static final int MAX_CACHED_MARCHES = 2000;

  private final double inletPressurePa;
  private final double inletTemperatureK;
  private final double inletDensity;
  private final double inletKappa;
  private final double area;
  private final int teeth;
  private final double carryOverRest;
  private final IsenthalpicFlash flash;
  private final Map<Double, March> cache = new LinkedHashMap<Double, March>(64, 0.75f, true) {
    private static final long serialVersionUID = 1L;

    @Override
    protected boolean removeEldestEntry(Map.Entry<Double, March> eldest) {
      return size() > MAX_CACHED_MARCHES;
    }
  };
  private int marchCount = 0;

  /**
   * Creates a solver for one inlet state and geometry, with exact PH flashes of the inlet gas.
   *
   * @param inletGas single-phase gas system flashed at the inlet conditions; it is not modified
   * @param inletDensity inlet gas density in kg/m3
   * @param inletKappa inlet real-gas isentropic exponent
   * @param geometry seal geometry (validated)
   */
  NeumannLabyrinthSolver(SystemInterface inletGas, double inletDensity, double inletKappa,
      LabyrinthSealGeometry geometry) {
    this(inletGas, inletDensity, inletKappa, geometry, defaultIsenthalpicFlash(inletGas));
  }

  /**
   * Creates a solver for one inlet state and geometry with a given isenthalpic-state function.
   *
   * @param inletGas single-phase gas system flashed at the inlet conditions; it is not modified
   * @param inletDensity inlet gas density in kg/m3
   * @param inletKappa inlet real-gas isentropic exponent
   * @param geometry seal geometry (validated)
   * @param flash isenthalpic-state function at the inlet enthalpy
   */
  NeumannLabyrinthSolver(SystemInterface inletGas, double inletDensity, double inletKappa,
      LabyrinthSealGeometry geometry, IsenthalpicFlash flash) {
    this.flash = flash;
    this.inletPressurePa = inletGas.getPressure() * 1.0e5;
    this.inletTemperatureK = inletGas.getTemperature();
    this.inletDensity = inletDensity;
    this.inletKappa = inletKappa;
    this.area = geometry.getClearanceArea();
    this.teeth = geometry.getNumberOfTeeth();
    double ratio = 1.0 + 16.6 * geometry.getRadialClearance() / geometry.getToothPitch();
    double alpha = 1.0 - 1.0 / (ratio * ratio);
    this.carryOverRest = Math.sqrt(teeth / (teeth * (1.0 - alpha) + alpha));
  }

  /** State of the gas after an isenthalpic expansion from the inlet. */
  interface IsenthalpicFlash {
    /**
     * Returns the gas state at a pressure and the inlet enthalpy.
     *
     * @param pressurePa pressure in Pa
     * @param warmStartK initial temperature guess in K
     * @return {temperature K, gas density kg/m3, gas real-gas isentropic exponent}
     * @throws RuntimeException when the state cannot be determined
     */
    double[] state(double pressurePa, double warmStartK);
  }

  /**
   * Returns the isenthalpic-state function used by default: an exact PH flash of the inlet gas at the inlet enthalpy.
   *
   * @param inletGas single-phase gas system flashed at the inlet conditions; it is not modified
   * @return isenthalpic-state function
   */
  static IsenthalpicFlash defaultIsenthalpicFlash(SystemInterface inletGas) {
    final SystemInterface gasTemplate = inletGas;
    final double enthalpy = inletGas.getEnthalpy();
    final boolean useGergFlash = inletGas instanceof SystemGERG2008Eos && inletGas.getNumberOfPhases() == 1;
    return new IsenthalpicFlash() {
      @Override
      public double[] state(double pressurePa, double warmStartK) {
        return isenthalpicState(gasTemplate, enthalpy, useGergFlash, pressurePa, warmStartK);
      }
    };
  }

  /** Pressure, temperature and property profile of one march at a trial flow. */
  static final class March {
    /** Pressure at the inlet and after each tooth, Pa. */
    final double[] pressurePa;
    /** Temperature at the inlet and after each tooth, K. */
    final double[] temperatureK;
    /** Gas density at the inlet and after each tooth, kg/m3. */
    final double[] density;
    /** Real-gas isentropic exponent at the inlet and after each tooth. */
    final double[] kappa;
    /** True when a tooth chokes at this flow; the profile then stops at that tooth. */
    boolean choked;
    /** Failure message when a tooth solve or a flash failed, null otherwise. */
    String failure;

    /**
     * Creates an empty march for n teeth.
     *
     * @param teeth number of teeth
     */
    March(int teeth) {
      pressurePa = new double[teeth + 1];
      temperatureK = new double[teeth + 1];
      density = new double[teeth + 1];
      kappa = new double[teeth + 1];
    }

    /**
     * Returns the pressure after the last tooth.
     *
     * @return pressure in Pa
     */
    double outletPressurePa() {
      return pressurePa[pressurePa.length - 1];
    }
  }

  /** Result of a Neumann solve. */
  static final class Result {
    /** Leakage mass flow in kg/s, NaN when the solve failed. */
    double massFlow = Double.NaN;
    /** True when the leakage is the choked flow. */
    boolean choked;
    /** Profile at the solution, null when the solve failed. */
    March profile;
    /** Validity violations, each prefixed with NEUMANN. */
    final List<String> violations = new ArrayList<String>();
  }

  /**
   * Returns the number of marches evaluated (cache misses) by this solver.
   *
   * @return number of marches
   */
  int getMarchCount() {
    return marchCount;
  }

  /**
   * Marches through the teeth at a trial flow, using the cache.
   *
   * @param massFlow trial mass flow in kg/s, not negative
   * @return the march
   */
  March march(double massFlow) {
    Double key = Double.valueOf(massFlow);
    March cached = cache.get(key);
    if (cached != null) {
      return cached;
    }
    March result = computeMarch(massFlow);
    cache.put(key, result);
    return result;
  }

  /**
   * Marches through the teeth at a trial flow.
   *
   * @param massFlow trial mass flow in kg/s
   * @return the march
   */
  private March computeMarch(double massFlow) {
    March m = new March(teeth);
    m.pressurePa[0] = inletPressurePa;
    m.temperatureK[0] = inletTemperatureK;
    m.density[0] = inletDensity;
    m.kappa[0] = inletKappa;
    if (massFlow == 0.0) {
      for (int i = 1; i <= teeth; i++) {
        m.pressurePa[i] = inletPressurePa;
        m.temperatureK[i] = inletTemperatureK;
        m.density[i] = inletDensity;
        m.kappa[i] = inletKappa;
      }
      return m;
    }
    marchCount++;
    for (int i = 0; i < teeth; i++) {
      double carryOver = i == 0 ? 1.0 : carryOverRest;
      double upstream = m.pressurePa[i];
      double k = m.kappa[i];
      double flux = massFlow / (carryOver * area);
      double ratio;
      try {
        ratio = toothPressureRatio(k, flux * flux / (upstream * m.density[i]));
      } catch (RuntimeException ex) {
        logger.debug("Tooth pressure solve failed at tooth {}", i + 1, ex);
        m.failure = String.format(Locale.ROOT, "NEUMANN: tooth pressure solve failed at tooth %d (%s)", i + 1,
            ex.getMessage());
        return m;
      }
      if (Double.isNaN(ratio)) {
        m.pressurePa[i + 1] = upstream * criticalPressureRatio(k);
        m.choked = true;
        return m;
      }
      double downstream = ratio * upstream;
      m.pressurePa[i + 1] = downstream;
      try {
        double[] state = flash.state(downstream, m.temperatureK[i]);
        m.temperatureK[i + 1] = state[0];
        m.density[i + 1] = state[1];
        m.kappa[i + 1] = state[2];
      } catch (RuntimeException ex) {
        logger.debug("Isenthalpic flash failed after tooth {}", i + 1, ex);
        m.failure = String.format(Locale.ROOT, "NEUMANN: isenthalpic flash failed after tooth %d at %.6g bara (%s)",
            i + 1, downstream * 1.0e-5, ex.getMessage());
        return m;
      }
    }
    return m;
  }

  /**
   * Returns the critical pressure ratio of a tooth, (2/(k+1))^(k/(k-1)).
   *
   * @param k real-gas isentropic exponent upstream of the tooth
   * @return critical (choking) pressure ratio
   */
  private static double criticalPressureRatio(double k) {
    return Math.pow(2.0 / (k + 1.0), k / (k - 1.0));
  }

  /**
   * Solves the dimensionless tooth equation y^2 = 1 - d / Cf(y)^2 for the pressure ratio y = p_{i+1}/p_i, with Cf(y) =
   * pi/(pi + 2 - 5 beta + 2 beta^2) and beta = max(y^(-(k-1)/k) - 1, 0).
   *
   * <p>
   * The residual y^2 - 1 + d/Cf(y)^2 increases monotonically between the critical ratio and 1 and is positive at 1, so
   * the tooth chokes exactly when the residual at the critical ratio is not negative; otherwise the single root is
   * found with a bracketed Brent search. A plain fixed-point iteration on this equation can oscillate for high k.
   * </p>
   *
   * @param k real-gas isentropic exponent upstream of the tooth
   * @param d dimensionless drop flux^2 / (p_i rho_i), with flux = m / (u_i A)
   * @return pressure ratio y, or NaN when the tooth chokes
   */
  private static double toothPressureRatio(final double k, final double d) {
    final double exponent = (k - 1.0) / k;
    UnivariateFunction residual = new UnivariateFunction() {
      @Override
      public double value(double y) {
        double beta = Math.max(Math.pow(y, -exponent) - 1.0, 0.0);
        double flowCoefficient = Math.PI / (Math.PI + 2.0 - 5.0 * beta + 2.0 * beta * beta);
        return y * y - 1.0 + d / (flowCoefficient * flowCoefficient);
      }
    };
    double critical = criticalPressureRatio(k);
    if (!(residual.value(critical) < 0.0)) {
      return Double.NaN;
    }
    return new BrentSolver(TOOTH_RATIO_ACCURACY).solve(MAX_TOOTH_EVALUATIONS, residual, critical, 1.0);
  }

  /**
   * Flashes the inlet gas to a pressure at the inlet enthalpy.
   *
   * <p>
   * The dedicated GERG-2008 PH flash only handles single-phase gas and returns after its TP flash otherwise, so when it
   * finds more than one phase or misses the inlet enthalpy the general PH flash is used instead. Every state is checked
   * against the inlet enthalpy.
   * </p>
   *
   * @param inletGas inlet gas template; it is cloned
   * @param inletEnthalpy inlet enthalpy in J (total for the system)
   * @param useGergFlash true to try the dedicated GERG-2008 PH flash first
   * @param pressurePa pressure in Pa
   * @param warmStartK initial temperature guess in K
   * @return {temperature K, gas density kg/m3, gas real-gas isentropic exponent}
   * @throws IllegalStateException if the inlet enthalpy is not reached or no gas phase remains
   */
  private static double[] isenthalpicState(SystemInterface inletGas, double inletEnthalpy, boolean useGergFlash,
      double pressurePa, double warmStartK) {
    SystemInterface state = inletGas.clone();
    state.setPressure(pressurePa * 1.0e-5);
    state.setTemperature(warmStartK);
    ThermodynamicOperations ops = new ThermodynamicOperations(state);
    boolean solved = false;
    if (useGergFlash) {
      ops.PHflashGERG2008(inletEnthalpy);
      // The dedicated flash only checks phase stability at the warm-start temperature; confirm at the result.
      ops.TPflash();
      state.initProperties();
      solved = state.getNumberOfPhases() == 1 && enthalpyReached(state, inletEnthalpy);
      if (!solved) {
        state.setTemperature(warmStartK);
      }
    }
    if (!solved) {
      ops.PHflash(inletEnthalpy);
      state.initProperties();
      if (!enthalpyReached(state, inletEnthalpy)) {
        throw new IllegalStateException(
            String.format(Locale.ROOT, "PH flash missed the inlet enthalpy by %.3g J at %.6g bara",
                state.getEnthalpy() - inletEnthalpy, pressurePa * 1.0e-5));
      }
    }
    if (!state.hasPhaseType("gas")) {
      throw new IllegalStateException("no gas phase after isenthalpic expansion");
    }
    PhaseInterface gas = state.getPhase("gas");
    double[] result = {state.getTemperature(), gas.getDensity("kg/m3"), gas.getKappa()};
    for (double value : result) {
      if (!(value > 0.0) || Double.isInfinite(value)) {
        throw new IllegalStateException("non-physical state " + Arrays.toString(result));
      }
    }
    return result;
  }

  /**
   * Returns whether a flashed state has the inlet enthalpy within {@link #ENTHALPY_TOLERANCE_K} times its heat
   * capacity.
   *
   * @param state flashed state with initialized properties
   * @param inletEnthalpy inlet enthalpy in J
   * @return true when the enthalpy matches
   */
  private static boolean enthalpyReached(SystemInterface state, double inletEnthalpy) {
    double tolerance = ENTHALPY_TOLERANCE_K * Math.abs(state.getCp());
    return Math.abs(state.getEnthalpy() - inletEnthalpy) <= tolerance;
  }

  /**
   * Finds the leakage for an outlet pressure.
   *
   * @param outletPressurePa outlet pressure in Pa, below the inlet pressure
   * @param initialGuess positive initial flow estimate in kg/s (for example the Kearton result)
   * @return the result
   */
  Result solve(double outletPressurePa, double initialGuess) {
    Result result = new Result();
    double lower = 0.0;
    double upper = initialGuess > 0.0 && !Double.isInfinite(initialGuess) ? initialGuess
        : area * Math.sqrt(inletDensity * inletPressurePa);
    boolean bracketed = false;
    for (int step = 0; step < MAX_BRACKET_STEPS; step++) {
      March trial = march(upper);
      if (trial.failure != null) {
        result.violations.add(trial.failure);
        return result;
      }
      if (trial.choked) {
        break;
      }
      if (trial.outletPressurePa() <= outletPressurePa) {
        bracketed = true;
        break;
      }
      lower = upper;
      upper *= 1.5;
    }
    if (!bracketed) {
      // [lower, upper]: no choking at lower (pressure still above outlet), choking at upper.
      while (upper - lower > CHOKE_RELATIVE_TOLERANCE * upper) {
        double middle = 0.5 * (lower + upper);
        March trial = march(middle);
        if (trial.failure != null) {
          result.violations.add(trial.failure);
          return result;
        }
        if (trial.choked) {
          upper = middle;
        } else if (trial.outletPressurePa() <= outletPressurePa) {
          upper = middle;
          bracketed = true;
          break;
        } else {
          lower = middle;
        }
      }
    }
    if (!bracketed) {
      if (!(lower > 0.0)) {
        result.violations.add("NEUMANN: the first tooth chokes at every trial flow; no leakage reported");
        return result;
      }
      result.massFlow = lower;
      result.choked = true;
      result.profile = march(lower);
      return result;
    }
    final double target = outletPressurePa;
    UnivariateFunction residual = new UnivariateFunction() {
      @Override
      public double value(double flow) {
        March trial = march(flow);
        if (trial.failure != null || trial.choked) {
          throw new IllegalStateException(trial.failure != null ? trial.failure : "choked inside bracket");
        }
        return trial.outletPressurePa() - target;
      }
    };
    try {
      double flow = new BrentSolver(FLOW_RELATIVE_TOLERANCE, FLOW_RELATIVE_TOLERANCE * upper * 1.0e-3)
          .solve(MAX_ROOT_EVALUATIONS, residual, lower, upper);
      result.massFlow = flow;
      result.profile = march(flow);
    } catch (RuntimeException ex) {
      // Brent throws MathIllegalStateException/MathIllegalArgumentException; the residual throws IllegalStateException.
      logger.debug("Neumann flow search failed", ex);
      result.violations.add("NEUMANN: flow search did not converge (" + ex.getMessage() + "); no leakage reported");
    }
    return result;
  }

  /**
   * Finds the choked (largest non-choking) flow, starting from a flow at which no tooth chokes.
   *
   * @param start non-choking flow in kg/s
   * @return result with the choked flow and its profile, or NaN flow and a NEUMANN violation when a march failed
   */
  Result chokedFlow(double start) {
    Result result = new Result();
    result.choked = true;
    double lower = start;
    double upper = 1.5 * start;
    for (int step = 0; step < MAX_BRACKET_STEPS; step++) {
      March trial = march(upper);
      if (trial.failure != null) {
        result.violations.add(criticalSearchFailure(trial.failure));
        return result;
      }
      if (trial.choked) {
        break;
      }
      lower = upper;
      upper *= 1.5;
    }
    while (upper - lower > CHOKE_RELATIVE_TOLERANCE * upper) {
      double middle = 0.5 * (lower + upper);
      March trial = march(middle);
      if (trial.failure != null) {
        result.violations.add(criticalSearchFailure(trial.failure));
        return result;
      }
      if (trial.choked) {
        upper = middle;
      } else {
        lower = middle;
      }
    }
    result.massFlow = lower;
    result.profile = march(lower);
    return result;
  }

  /**
   * Builds the violation for a failed critical-flow search from a march failure.
   *
   * @param marchFailure failure message of the march, prefixed with NEUMANN
   * @return violation message
   */
  private static String criticalSearchFailure(String marchFailure) {
    String detail = marchFailure.startsWith("NEUMANN: ") ? marchFailure.substring("NEUMANN: ".length()) : marchFailure;
    return "NEUMANN: critical-flow search failed (" + detail + "); no critical flow reported";
  }
}
