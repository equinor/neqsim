package neqsim.thermo;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Checks thermodynamic identities and the declared derivatives of logarithmic fugacity coefficients.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ThermodynamicModelTest implements ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ThermodynamicModelTest.class);
  /** Relative perturbation for second-order finite differences, balancing truncation and roundoff. */
  private static final double DIFFERENCE_STEP = Math.cbrt(Math.ulp(1.0));
  SystemInterface system;
  private double maxError = 1e-10;

  /** Creates an unbound model checker. */
  public ThermodynamicModelTest() {
  }

  /**
   * Creates a checker for the supplied fluid.
   *
   * @param system fluid whose initialized properties will be checked
   */
  public ThermodynamicModelTest(SystemInterface system) {
    this.system = system;
  }

  /** Initializes the fluid's analytic derivatives and logs each consistency check. */
  public void runTest() {
    system.init(3);
    logger.info("testing fugacity coefficients: {}", checkFugacityCoefficients());
    logger.info("testing composition identities: {}", checkFugacityCoefficientsDn());
    logger.info("testing composition symmetry: {}", checkFugacityCoefficientsDn2());
    logger.info("testing pressure identities: {}", checkFugacityCoefficientsDP());
    logger.info("testing temperature identities: {}", checkFugacityCoefficientsDT());
    logger.info("comparing numerical derivatives: {}", checkNumerically());
  }

  /**
   * Checks the residual Gibbs-energy identity independently in each active logical phase.
   *
   * @return true when every finite phase residual is within the configured absolute tolerance
   */
  public boolean checkFugacityCoefficients() {
    if (!hasPhases()) {
      return false;
    }
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      PhaseInterface state = system.getPhase(phase);
      double residual = -state.getGresTP() / (R * state.getTemperature());
      for (int component = 0; component < state.getNumberOfComponents(); component++) {
        ComponentInterface species = state.getComponent(component);
        double coefficient = species.getFugacityCoefficient();
        double moles = species.getNumberOfMolesInPhase();
        if (!(coefficient > 0.0) || !Double.isFinite(coefficient) || !Double.isFinite(moles) || moles < 0.0) {
          return false;
        }
        residual += moles * Math.log(coefficient);
      }
      if (!withinTolerance(residual)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks the Gibbs-Duhem composition identity for every component in every active phase.
   *
   * @return true when all finite composition-identity residuals meet the configured absolute tolerance
   */
  public boolean checkFugacityCoefficientsDn() {
    if (!hasPhases()) {
      return false;
    }
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      PhaseInterface state = system.getPhase(phase);
      for (int column = 0; column < state.getNumberOfComponents(); column++) {
        double residual = 0.0;
        for (int row = 0; row < state.getNumberOfComponents(); row++) {
          ComponentInterface species = state.getComponent(row);
          double derivative = species.getdfugdn(column);
          double moles = species.getNumberOfMolesInPhase();
          if (!Double.isFinite(derivative) || !Double.isFinite(moles) || moles < 0.0) {
            return false;
          }
          residual += moles * derivative;
        }
        if (!withinTolerance(residual)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Checks each composition-derivative symmetry pair separately, without signed cancellation.
   *
   * @return true when every finite pair residual meets the configured absolute tolerance
   */
  public boolean checkFugacityCoefficientsDn2() {
    if (!hasPhases()) {
      return false;
    }
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      PhaseInterface state = system.getPhase(phase);
      for (int row = 0; row < state.getNumberOfComponents(); row++) {
        for (int column = row; column < state.getNumberOfComponents(); column++) {
          double first = state.getComponent(row).getdfugdn(column);
          double second = state.getComponent(column).getdfugdn(row);
          if (!Double.isFinite(first) || !Double.isFinite(second) || !withinTolerance(first - second)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * Checks the pressure derivative identity independently in each phase.
   *
   * @return true when every finite pressure-identity residual meets the configured absolute tolerance
   */
  public boolean checkFugacityCoefficientsDP() {
    if (!hasPhases()) {
      return false;
    }
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      PhaseInterface state = system.getPhase(phase);
      double residual = -(state.getZ() - 1.0) * state.getNumberOfMolesInPhase() / state.getPressure();
      for (int component = 0; component < state.getNumberOfComponents(); component++) {
        ComponentInterface species = state.getComponent(component);
        double derivative = species.getdfugdp();
        double moles = species.getNumberOfMolesInPhase();
        if (!Double.isFinite(derivative) || !Double.isFinite(moles) || moles < 0.0) {
          return false;
        }
        residual += moles * derivative;
      }
      if (!withinTolerance(residual)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks the temperature derivative identity independently in each phase.
   *
   * @return true when every finite temperature-identity residual meets the configured absolute tolerance
   */
  public boolean checkFugacityCoefficientsDT() {
    if (!hasPhases()) {
      return false;
    }
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      PhaseInterface state = system.getPhase(phase);
      double residual = state.getHresTP() / (R * state.getTemperature() * state.getTemperature());
      for (int component = 0; component < state.getNumberOfComponents(); component++) {
        ComponentInterface species = state.getComponent(component);
        double derivative = species.getdfugdt();
        double moles = species.getNumberOfMolesInPhase();
        if (!Double.isFinite(derivative) || !Double.isFinite(moles) || moles < 0.0) {
          return false;
        }
        residual += moles * derivative;
      }
      if (!withinTolerance(residual)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Compares declared T, P and mole derivatives with independent finite differences on detached snapshots.
   *
   * <p>
   * Each active logical phase is checked on its retained root without flashing or mutating the supplied fluid. Two
   * second-order estimates, at a step and half that step, must agree with each other and with the analytic derivative.
   * Their tolerance is {@code maxError * max(1, abs(analytic), abs(numerical))}; callers must choose an accuracy that
   * their property evaluator and finite differences can resolve. An unresolved or non-finite check returns false. Mole
   * perturbations never remove more than the available amount; boundary components use a one-sided stencil. These are
   * the unconstrained partial derivatives exposed by the component API. Electrolyte model qualification also requires
   * separate charge-preserving directional checks and is not established by this method alone.
   * </p>
   *
   * @return true only when every derivative and its step-size consistency check meets the configured tolerance
   */
  public boolean checkNumerically() {
    if (!hasPhases()) {
      return false;
    }
    try {
      SystemInterface reference = SerializationUtils.clone(system);
      reference.init(3);
      byte[] snapshot = SerializationUtils.serialize(reference);
      for (int phase = 0; phase < reference.getNumberOfPhases(); phase++) {
        PhaseInterface state = reference.getPhase(phase);
        for (int variable = -2; variable < state.getNumberOfComponents(); variable++) {
          double coordinate = variable == -2 ? state.getTemperature()
              : variable == -1 ? state.getPressure() : state.getComponent(variable).getNumberOfMolesInPhase();
          if (!Double.isFinite(coordinate) || coordinate < 0.0 || (variable < 0 && coordinate == 0.0)) {
            return false;
          }
          double scale = variable < 0 ? coordinate : state.getNumberOfMolesInPhase();
          double step = DIFFERENCE_STEP * scale;
          if (!(step > 0.0) || !Double.isFinite(step)) {
            return false;
          }
          boolean central = coordinate > step;
          double[] coarse = differences(snapshot, phase, variable, step, central);
          double[] fine = differences(snapshot, phase, variable, step / 2.0, central);
          for (int component = 0; component < state.getNumberOfComponents(); component++) {
            ComponentInterface species = state.getComponent(component);
            double analytic = variable == -2 ? species.getdfugdt()
                : variable == -1 ? species.getdfugdp() : species.getdfugdn(variable);
            if (!derivativesAgree(analytic, fine[component]) || !derivativesAgree(coarse[component], fine[component])) {
              logger.debug(
                  "Derivative check failed: phase={}, component={}, variable={}, analytic={}, coarse={}, fine={}",
                  phase, component, variable, analytic, coarse[component], fine[component]);
              return false;
            }
          }
        }
      }
      return true;
    } catch (RuntimeException ex) {
      logger.debug("Numerical derivative check could not evaluate an independent trial", ex);
      return false;
    }
  }

  /**
   * Computes all logarithmic fugacity derivatives for one coordinate using an independent second-order stencil.
   *
   * @param snapshot serialized initialized reference state
   * @param phase logical phase index
   * @param variable minus two for T, minus one for P, otherwise component mole index
   * @param step positive finite perturbation
   * @param central whether the negative perturbation remains inside the physical amount domain
   * @return numerical derivatives for all components in the phase
   */
  private double[] differences(byte[] snapshot, int phase, int variable, double step, boolean central) {
    double[] plus = evaluate(snapshot, phase, variable, step);
    double[] other = evaluate(snapshot, phase, variable, central ? -step : 2.0 * step);
    double[] origin = central ? null : evaluate(snapshot, phase, variable, 0.0);
    for (int component = 0; component < plus.length; component++) {
      plus[component] = central ? (plus[component] - other[component]) / (2.0 * step)
          : (-3.0 * origin[component] + 4.0 * plus[component] - other[component]) / (2.0 * step);
    }
    return plus;
  }

  /**
   * Evaluates logarithmic fugacity coefficients on a fully detached trial with unchanged phase topology.
   *
   * @param snapshot serialized initialized reference state
   * @param phase logical phase index
   * @param variable minus two for T, minus one for P, otherwise component mole index
   * @param change coordinate increment
   * @return logarithmic fugacity coefficients in component order
   */
  private double[] evaluate(byte[] snapshot, int phase, int variable, double change) {
    SystemInterface trial = SerializationUtils.deserialize(snapshot);
    PhaseInterface state = trial.getPhase(phase);
    if (variable == -2) {
      state.setTemperature(state.getTemperature() + change);
    } else if (variable == -1) {
      state.setPressure(state.getPressure() + change);
    } else {
      trial.addComponent(variable, change, phase);
      trial.initBeta();
      trial.init_x_y();
    }
    trial.init(1, phase);
    double[] values = new double[state.getNumberOfComponents()];
    for (int component = 0; component < values.length; component++) {
      double coefficient = state.getComponent(component).getFugacityCoefficient();
      values[component] = coefficient > 0.0 && Double.isFinite(coefficient) ? Math.log(coefficient) : Double.NaN;
    }
    return values;
  }

  /**
   * Compares finite derivatives with the explicitly configured mixed absolute/relative tolerance.
   *
   * @param first analytic or coarse derivative
   * @param second fine numerical derivative
   * @return whether both values are finite and agree to the configured accuracy
   */
  private boolean derivativesAgree(double first, double second) {
    return Double.isFinite(first) && Double.isFinite(second)
        && withinTolerance((first - second) / Math.max(1.0, Math.max(Math.abs(first), Math.abs(second))));
  }

  /**
   * Checks an individual finite residual without allowing errors from other equations to cancel it.
   *
   * @param residual individual equation residual
   * @return true when the configured absolute error bound is satisfied
   */
  private boolean withinTolerance(double residual) {
    return Double.isFinite(residual) && Double.isFinite(maxError) && maxError >= 0.0 && Math.abs(residual) <= maxError;
  }

  /**
   * Checks that at least one active phase and component are available.
   *
   * @return whether the checker has a nonempty fluid
   */
  private boolean hasPhases() {
    if (system == null || system.getNumberOfPhases() <= 0) {
      return false;
    }
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase) == null || system.getPhase(phase).getNumberOfComponents() <= 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Sets the absolute tolerance for thermodynamic identities and the scaled tolerance for numerical derivatives.
   *
   * @param maxErr maximum permitted finite error; a negative or non-finite value makes checks fail
   */
  public void setMaxError(double maxErr) {
    this.maxError = maxErr;
  }
}
