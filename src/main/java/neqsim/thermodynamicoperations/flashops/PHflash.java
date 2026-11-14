/*
 * PHflash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * PHflash class.
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PHflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Hspec = 0;
  Flash tpFlash;
  int type = 0;

  /**
   * Relative enthalpy residual below which a stalled Newton iteration is accepted as converged.
   */
  static final double STALL_RELATIVE_ENTHALPY_TOLERANCE = 1.0e-6;

  /**
   * Number of consecutive iterations without improvement of the best residual that defines a stall.
   */
  static final int STALL_ITERATIONS_WITHOUT_IMPROVEMENT = 15;

  /**
   * Relative enthalpy residual that ends the iteration without a second confirming evaluation; zero keeps the nominal
   * two-evaluation criterion.
   */
  private final double singleResidualTolerance;

  /**
   * Iteration count after which an available temperature bracket takes over from the damped Newton step.
   */
  static final int BRACKET_TAKEOVER_ITERATIONS = 20;

  /** Relative temperature bracket width below which the enthalpy root is resolved to machine precision. */
  static final double BRACKET_COLLAPSE_RELATIVE_WIDTH = 1.0e-13;

  /** Number of Newton iterations used by the latest {@link #solveQ()} call. */
  private int lastIterationCount = 0;

  /**
   * Constructor for PHflash operation.
   *
   * <p>
   * Uses a specialized single-component PH flash if the system contains only one component and the specified enthalpy
   * is within the valid range.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Hspec a double
   * @param type a int, flash type (0 = standard) or (1 = direct PH flash if not pure component)
   */
  public PHflash(SystemInterface system, double Hspec, int type) {
    this(system, Hspec, type, 0.0);
  }

  /**
   * Constructor for a PH flash that may stop on one relative enthalpy residual below a tolerance.
   *
   * <p>
   * By default the iteration stops when two consecutive residuals together are below 1e-8, so the accepted state is
   * confirmed by one more equilibrium flash. An outer iteration that repeats this flash many times, such as a
   * distillation tray sweep, does not need that confirmation.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Hspec specified total enthalpy in J
   * @param type flash type (0 = standard) or (1 = direct PH flash if not pure component)
   * @param singleResidualTolerance relative enthalpy residual that ends the iteration on its own; zero or negative
   * keeps the two-evaluation criterion
   */
  public PHflash(SystemInterface system, double Hspec, int type, double singleResidualTolerance) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Hspec = Hspec;
    this.type = type;
    this.singleResidualTolerance = singleResidualTolerance;
  }

  /**
   * calcdQdTT.
   *
   * @return a double
   */
  public double calcdQdTT() {
    double dQdTT = -system.getTemperature() * system.getTemperature() * system.getCp();
    double enthalpyScale = Hspec == 0.0 ? 1.0 : Math.abs(Hspec);
    return dQdTT / enthalpyScale;
  }

  /**
   * calcdQdT.
   *
   * @return a double
   */
  public double calcdQdT() {
    double enthalpyScale = Hspec == 0.0 ? 1.0 : Math.abs(Hspec);
    double dQ = (system.getEnthalpy() - Hspec) / enthalpyScale;
    return dQ;
  }

  /**
   * solveQ.
   *
   * <p>
   * Newton iteration on 1/T for the specified enthalpy. The nominal stop criterion limits the sum of magnitudes of two
   * consecutive relative enthalpy residuals to 1e-8. For association models (CPA) with glycol/water/aromatic mixtures
   * the enthalpy returned by the inner TP flash carries numerical noise above that level, so the iteration would
   * otherwise run to the iteration cap with the temperature frozen. A stall detector therefore accepts the solution
   * only when the current residual is finite and its magnitude is below {@link #STALL_RELATIVE_ENTHALPY_TOLERANCE}, and
   * the best residual has not improved for {@link #STALL_ITERATIONS_WITHOUT_IMPROVEMENT} consecutive iterations. The
   * relaxed tolerance is checked against the returned state; improving iterations retain the nominal stop criterion.
   * </p>
   *
   * @return a double
   */
  public double solveQ() {
    double oldTemp = 1.0 / system.getTemperature();
    double nyTemp = 1.0 / system.getTemperature();
    int iterations = 1;
    double error = 1.0;
    double errorOld = 1.0e10;
    double factor = 0.8;
    double newCorr = 1.0;
    system.init(2);
    boolean correctFactor = true;
    double maxTemperature = 1e10;
    double minTemperature = 0.0;
    double previousInverseTemperature = Double.NaN;
    double previousResidual = Double.NaN;
    double bestError = Double.MAX_VALUE;
    int iterationsWithoutImprovement = 0;
    boolean stalled = false;
    // Enthalpy increases monotonically with temperature at fixed pressure, so one state on each
    // side of the specification brackets the root. [0]=low T, [1]=low residual, [2]=high T,
    // [3]=high residual, [4]=side of the latest update (-1 low, +1 high, 0 none).
    double[] bracket = {Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.0};
    boolean bracketCollapsed = false;
    boolean tightlyConverged = false;

    do {
      if (Math.abs(error) > Math.abs(errorOld) && factor > 0.1 && correctFactor) {
        factor *= 0.5;
      } else if (Math.abs(error) < Math.abs(errorOld) && correctFactor) {
        factor = iterations / (iterations + 1.0) * 1.0;
      }
      iterations++;
      oldTemp = nyTemp;
      double residual = calcdQdT();
      double derivative = calcdQdTT();
      newCorr = factor * residual / derivative;
      // Reuse the two completed equilibrium flashes to estimate the caloric slope.
      // The fixed-composition Cp derivative misses phase-fraction/composition changes.
      // A decreasing finite residual and a physically signed, bounded slope admit
      // an undamped secant step; otherwise retain the original damped Newton step.
      double inverseTemperatureStep = oldTemp - previousInverseTemperature;
      if (Double.isFinite(previousResidual) && inverseTemperatureStep != 0.0
          && Math.abs(residual) < Math.abs(previousResidual)) {
        double secantDerivative = (residual - previousResidual) / inverseTemperatureStep;
        double derivativeRatio = secantDerivative / derivative;
        if (Double.isFinite(secantDerivative) && secantDerivative < 0.0 && Double.isFinite(derivativeRatio)
            && derivativeRatio >= 0.1 && derivativeRatio <= 10.0) {
          newCorr = residual / secantDerivative;
        }
      }
      previousInverseTemperature = oldTemp;
      previousResidual = residual;
      nyTemp = oldTemp - newCorr;
      updateTemperatureBracket(bracket, system.getTemperature(), residual);

      if (iterations > 150 && Math.abs(error) < 10.0) {
        nyTemp = 1.0
            / (system.getTemperature() - Math.signum(system.getTemperature() - 1.0 / nyTemp) * Math.abs(error));
        correctFactor = false;
      } else if (Math.abs(system.getTemperature() - 1.0 / nyTemp) > 10.0) {
        nyTemp = 1.0 / (system.getTemperature() - Math.signum(system.getTemperature() - 1.0 / nyTemp) * 10.0);
        correctFactor = false;
      } else if (nyTemp < 0) {
        nyTemp = Math.abs(1.0 / (system.getTemperature() + 10.0));
        correctFactor = false;
      } else if (Double.isNaN(nyTemp)) {
        nyTemp = oldTemp + 0.1;
        correctFactor = false;
      } else {
        correctFactor = true;
      }
      // The fixed-composition heat capacity ignores latent heat. In a narrow-boiling two-phase
      // region it underestimates dH/dT by orders of magnitude, so the Newton step overshoots and
      // oscillates across the root. Once both sides are known, a step that leaves the bracket (or
      // a Newton sequence that has not converged) is replaced by an Illinois regula-falsi step,
      // which contracts the bracket monotonically and converges superlinearly.
      if (hasTemperatureBracket(bracket)) {
        double proposedTemperature = 1.0 / nyTemp;
        boolean insideBracket = proposedTemperature > bracket[0] && proposedTemperature < bracket[2];
        if (!insideBracket || iterations > BRACKET_TAKEOVER_ITERATIONS) {
          nyTemp = 1.0 / bracketedTemperature(bracket);
          correctFactor = false;
        }
      }
      system.setTemperature(1.0 / nyTemp);
      if (system.getTemperature() > maxTemperature) {
        system.setTemperature(maxTemperature - 0.1);
      } else if (system.getTemperature() < minTemperature) {
        system.setTemperature(minTemperature + 0.1);
      }
      // A trial temperature can land in a region where the cubic EOS has no valid root
      // (NaN compressibility). Instead of aborting the whole PH flash, back the temperature
      // off toward the previous (successful) value until the inner TP flash converges.
      int flashBackoff = 0;
      while (true) {
        try {
          tpFlash.run();
          break;
        } catch (RuntimeException ex) {
          flashBackoff++;
          if (flashBackoff > 15) {
            throw ex;
          }
          nyTemp = 0.5 * (nyTemp + oldTemp);
          system.setTemperature(1.0 / nyTemp);
        }
      }
      system.init(2);
      errorOld = error;
      error = calcdQdT();
      updateTemperatureBracket(bracket, system.getTemperature(), error);
      bracketCollapsed = hasTemperatureBracket(bracket)
          && bracket[2] - bracket[0] <= BRACKET_COLLAPSE_RELATIVE_WIDTH * bracket[2];

      if (error > 0 && system.getTemperature() > maxTemperature) {
        maxTemperature = system.getTemperature();
      } else if (error < 0 && system.getTemperature() < minTemperature) {
        minTemperature = system.getTemperature();
      }
      if (Math.abs(error) < bestError) {
        bestError = Math.abs(error);
        iterationsWithoutImprovement = 0;
      } else {
        iterationsWithoutImprovement++;
      }
      stalled = iterationsWithoutImprovement >= STALL_ITERATIONS_WITHOUT_IMPROVEMENT && Double.isFinite(error)
          && Math.abs(error) < STALL_RELATIVE_ENTHALPY_TOLERANCE;
      // The nominal criterion needs two consecutive small residuals, which costs one extra
      // equilibrium flash after the root is already resolved. A caller that repeats the flash
      // inside its own iteration can accept a single sufficiently small residual instead.
      tightlyConverged = singleResidualTolerance > 0.0 && iterations >= 3 && Math.abs(error) < singleResidualTolerance;
    } while (((Math.abs(error) + Math.abs(errorOld)) > 1e-8 || iterations < 3) && iterations < 200 && !stalled
        && !bracketCollapsed && !tightlyConverged);
    lastIterationCount = iterations;
    return 1.0 / nyTemp;
  }

  /**
   * Check whether both sides of the enthalpy root have been located.
   *
   * @param bracket bracket state {lowT, lowResidual, highT, highResidual, lastSide}
   * @return {@code true} when a finite low and high temperature bound are available
   */
  private static boolean hasTemperatureBracket(double[] bracket) {
    return !Double.isNaN(bracket[0]) && !Double.isNaN(bracket[2]) && bracket[2] > bracket[0];
  }

  /**
   * Tighten the temperature bracket with a newly evaluated equilibrium state.
   *
   * <p>
   * A negative residual means the enthalpy is below the specification, so the temperature is a lower bound; a positive
   * residual gives an upper bound. When the same side is updated twice in a row the residual stored for the opposite
   * side is halved (Illinois modification), which prevents the one-sided stagnation of plain regula falsi on the kinked
   * enthalpy curve of a phase boundary.
   * </p>
   *
   * @param bracket bracket state {lowT, lowResidual, highT, highResidual, lastSide}, updated in place
   * @param temperature temperature of the evaluated state in Kelvin
   * @param residual relative enthalpy residual of the evaluated state
   */
  private static void updateTemperatureBracket(double[] bracket, double temperature, double residual) {
    if (Double.isNaN(residual) || Double.isInfinite(residual) || Double.isNaN(temperature) || residual == 0.0) {
      return;
    }
    if (residual < 0.0) {
      if (Double.isNaN(bracket[0]) || temperature > bracket[0]) {
        if (!Double.isNaN(bracket[2]) && temperature >= bracket[2]) {
          return;
        }
        bracket[0] = temperature;
        bracket[1] = residual;
        if (bracket[4] < 0.0 && !Double.isNaN(bracket[3])) {
          bracket[3] *= 0.5;
        }
        bracket[4] = -1.0;
      }
    } else {
      if (Double.isNaN(bracket[2]) || temperature < bracket[2]) {
        if (!Double.isNaN(bracket[0]) && temperature <= bracket[0]) {
          return;
        }
        bracket[2] = temperature;
        bracket[3] = residual;
        if (bracket[4] > 0.0 && !Double.isNaN(bracket[1])) {
          bracket[1] *= 0.5;
        }
        bracket[4] = 1.0;
      }
    }
  }

  /**
   * Calculate the next trial temperature inside an established bracket.
   *
   * @param bracket bracket state {lowT, lowResidual, highT, highResidual, lastSide}
   * @return regula-falsi temperature in Kelvin, or the bracket midpoint when the interpolation is not strictly interior
   */
  private static double bracketedTemperature(double[] bracket) {
    double width = bracket[2] - bracket[0];
    double temperature = bracket[0] - bracket[1] * width / (bracket[3] - bracket[1]);
    if (Double.isNaN(temperature) || temperature <= bracket[0] || temperature >= bracket[2]) {
      temperature = bracket[0] + 0.5 * width;
    }
    return temperature;
  }

  /**
   * Number of Newton iterations used by the latest {@link #solveQ()} call.
   *
   * @return iteration count, 0 if {@link #solveQ()} has not been run
   */
  public int getLastIterationCount() {
    return lastIterationCount;
  }

  /**
   * solveQ2.
   *
   * @return a double
   */
  public double solveQ2() {
    double oldTemp = 1.0 / system.getTemperature();
    double nyTemp = 1.0 / system.getTemperature();
    int iterations = 1;
    double error = 1.0;
    double errorOld = 1.0e10;
    double factor = 0.8;
    double newCorr = 1.0;
    system.init(2);
    boolean correctFactor = true;
    // System.out.println("temp start " + system.getTemperature());
    do {
      if (error > errorOld && factor > 0.1 && correctFactor) {
        factor *= 0.5;
      } else if (error < errorOld && correctFactor) {
        factor = iterations / (iterations + 1.0) * 1.0;
      }
      iterations++;
      oldTemp = nyTemp;
      newCorr = factor * calcdQdT() / calcdQdTT();
      nyTemp = oldTemp - newCorr;
      if (Math.abs(system.getTemperature() - 1.0 / nyTemp) > 10.0) {
        nyTemp = 1.0 / (system.getTemperature() - Math.signum(system.getTemperature() - 1.0 / nyTemp) * 10.0);
        correctFactor = false;
      } else if (nyTemp < 0) {
        nyTemp = Math.abs(1.0 / (system.getTemperature() + 10.0));
        correctFactor = false;
      } else if (Double.isNaN(nyTemp)) {
        nyTemp = oldTemp + 0.1;
        correctFactor = false;
      } else {
        correctFactor = true;
      }
      system.setTemperature(1.0 / nyTemp);
      tpFlash.run();
      system.init(2);
      errorOld = error;
      error = Math.abs(calcdQdT());
      // error = Math.abs((1.0 / nyTemp - 1.0 / oldTemp) / (1.0 / oldTemp));
      // if(iterations>100) System.out.println("temp " + system.getTemperature() + "
      // iter "+ iterations + " error "+ error + " correction " + newCorr + " factor
      // "+ factor);
    } while (((error + errorOld) > 1e-8 || iterations < 3) && iterations < 200);
    // System.out.println("temp " + system.getTemperature() + " iter "+ iterations +
    // " error "+ error );
    return 1.0 / nyTemp;
  }

  /**
   * Check whether K-value warm starts are suitable for the inner TP flashes.
   *
   * <p>
   * Delegates to {@link neqsim.thermo.ThermodynamicModelSettings#isInnerFlashWarmStartSafe} so that every iterative
   * flash shares one policy. CPA association-site fractions can change strongly with temperature, so reusing K-values
   * while the PH solver moves through temperature space may increase TP-flash work or bias the iteration path. Cubic
   * EOS models retain the established warm-start acceleration.
   * </p>
   *
   * @return {@code true} when inner TP flashes may reuse K-values
   */
  protected boolean isInnerTpFlashWarmStartSafe() {
    return neqsim.thermo.ThermodynamicModelSettings.isInnerFlashWarmStartSafe(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // No caloric temperature constraint exists without material. Keep the stream's
    // temperature template instead of dividing zero enthalpy and heat capacity.
    if (system.getTotalNumberOfMoles() == 0.0) {
      if (Hspec != 0.0) {
        throw new IllegalArgumentException("An empty system cannot carry nonzero enthalpy");
      }
      return;
    }
    // First TPflash runs COLD (Wilson initial K) so that stale K from a
    // previous unrelated flash (at different P/T) does not bias the solution.
    // Then enable K-value warm-start for subsequent TPflash iterations only when
    // the thermodynamic model is suitable. CPA association terms can make K-values
    // stale as temperature changes, increasing work in glycol/water columns.
    boolean prevWarm = neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues();
    boolean useInnerWarmStart = isInnerTpFlashWarmStartSafe();
    try {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(false);
      tpFlash.run();
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(useInnerWarmStart);
      // System.out.println("enthalpy start: " + system.getEnthalpy());
      if (type == 0) {
        solveQ();
      } else {
        SysNewtonRhapsonPHflash secondOrderSolver = new SysNewtonRhapsonPHflash(system, 2,
            system.getPhases()[0].getNumberOfComponents(), 0);
        secondOrderSolver.setSpec(Hspec);
        secondOrderSolver.solve(1);
      }
    } finally {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(prevWarm);
    }
    // System.out.println("enthalpy: " + system.getEnthalpy());
    // System.out.println("Temperature: " + system.getTemperature());
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
