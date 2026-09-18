package neqsim.thermo.util;

import java.io.Serializable;
import java.util.Arrays;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Homogeneous-equilibrium acoustic derivative at fixed specific entropy and total composition.
 *
 * <p>
 * Calculates c squared = (dp/drho) at constant s,z using thermodynamic (EOS) total volume, with pressure in Pa and
 * density in kg/m3. Phase amounts and compositions relax to equilibrium; slip, finite-rate transfer, solid formation
 * and reactions are excluded. This is a fluid-frame acoustic speed, not a laboratory-frame decompression
 * characteristic. All flashes use clones. Mixtures use fresh TP flashes in a bracketed temperature root, independently
 * of PSflash. Pure fluids first use PSflash to retain the saturation quality degree of freedom, with a checked TP-root
 * fallback. No unconverged derivative is returned as a usable speed.
 * </p>
 */
public final class EquilibriumSoundSpeed {
  private static final double ENTROPY_TOLERANCE = 1.0e-7;
  private static final double BALANCE_TOLERANCE = 1.0e-8;
  private static final double FUGACITY_TOLERANCE = 1.0e-6;
  private static final double DERIVATIVE_TOLERANCE = 2.0e-3;
  private static final int MAX_REFINEMENTS = 10;

  private EquilibriumSoundSpeed() {
  }

  /** Finite-difference direction; one-sided stencils stay on the centre state's phase branch. */
  public enum Stencil {
    /** Symmetric three-point stencil. */
    CENTRAL,
    /** Second-order stencil towards increasing pressure. */
    FORWARD,
    /** Second-order stencil towards decreasing pressure. */
    BACKWARD,
    /** No accepted stencil. */
    UNAVAILABLE
  }

  /** Outcome of the calculation. */
  public enum Status {
    /** Entropy, inventory, phase equilibrium and step convergence checks passed. */
    CONVERGED,
    /** A checked equilibrium state could not be obtained. */
    FLASH_FAILED,
    /** The requested physics or resulting phase assemblage is unsupported. */
    UNSUPPORTED_STATE,
    /** Positive, step-converged density derivatives could not be obtained. */
    DERIVATIVE_NOT_CONVERGED
  }

  /** Immutable thermodynamic snapshot of one accepted pressure sample. */
  public static final class State implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double pressurePa;
    private final double temperatureK;
    private final double density;
    private final double entropyResidual;
    private final double componentResidual;
    private final double fugacityResidual;
    private final String[] phaseTypes;
    private final double[] phaseFractions;

    private State(SystemInterface fluid, double targetEntropy, double balance, double fugacity) {
      pressurePa = fluid.getPressure() * 1.0e5;
      temperatureK = fluid.getTemperature();
      density = fluid.getMass("kg") / fluid.getVolume("m3");
      entropyResidual = fluid.getEntropy("J/kgK") - targetEntropy;
      componentResidual = balance;
      fugacityResidual = fugacity;
      phaseTypes = new String[fluid.getNumberOfPhases()];
      phaseFractions = new double[phaseTypes.length];
      for (int i = 0; i < phaseTypes.length; i++) {
        phaseTypes[i] = fluid.getPhase(i).getType().name();
        phaseFractions[i] = fluid.getBeta(i);
      }
    }

    /** @return absolute pressure in Pa */
    public double getPressurePa() {
      return pressurePa;
    }

    /** @return temperature in K */
    public double getTemperatureK() {
      return temperatureK;
    }

    /** @return mass divided by EOS total volume, in kg/m3 */
    public double getDensity() {
      return density;
    }

    /** @return signed specific entropy error in J/(kg K) */
    public double getEntropyResidual() {
      return entropyResidual;
    }

    /** @return maximum component inventory error divided by total input moles */
    public double getComponentResidual() {
      return componentResidual;
    }

    /** @return maximum absolute interphase log fugacity ratio (zero for one phase) */
    public double getFugacityResidual() {
      return fugacityResidual;
    }

    /** @return copy of the phase type names, aligned with the phase fractions */
    public String[] getPhaseTypes() {
      return phaseTypes.clone();
    }

    /** @return copy of molar phase fractions */
    public double[] getPhaseFractions() {
      return phaseFractions.clone();
    }

    private boolean samePhases(State other) {
      String[] first = phaseTypes.clone();
      String[] second = other.phaseTypes.clone();
      Arrays.sort(first);
      Arrays.sort(second);
      return Arrays.equals(first, second);
    }
  }

  /** Immutable result with explicit failure status and the final accepted stencil samples. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Status status;
    private final String message;
    private final double soundSpeed;
    private final double pressureStepPa;
    private final double relativeStepError;
    private final double specificEntropy;
    private final Stencil stencil;
    private final boolean phaseBoundaryEncountered;
    private final int flashEvaluations;
    private final State[] samples;

    private Result(Status status, String message, double speed, double step, double error, Stencil stencil,
        State[] samples, Calculation calculation) {
      this.status = status;
      this.message = message;
      soundSpeed = speed;
      pressureStepPa = step;
      relativeStepError = error;
      this.stencil = stencil;
      this.samples = samples.clone();
      specificEntropy = calculation.entropy;
      phaseBoundaryEncountered = calculation.boundary;
      flashEvaluations = calculation.evaluations;
    }

    /** @return true only when all closure and derivative checks passed */
    public boolean isConverged() {
      return status == Status.CONVERGED;
    }

    /** @return convergence or failure category */
    public Status getStatus() {
      return status;
    }

    /** @return human-readable convergence or failure detail */
    public String getMessage() {
      return message;
    }

    /** @return acoustic speed in m/s, or NaN on failure */
    public double getSoundSpeed() {
      return soundSpeed;
    }

    /** @return final pressure increment in Pa, or NaN when no stencil was evaluated */
    public double getPressureStepPa() {
      return pressureStepPa;
    }

    /** @return relative density-derivative difference between successive steps */
    public double getRelativeStepError() {
      return relativeStepError;
    }

    /** @return target specific entropy in J/(kg K) */
    public double getSpecificEntropy() {
      return specificEntropy;
    }

    /** @return final finite-difference direction */
    public Stencil getStencil() {
      return stencil;
    }

    /** @return whether any sampled stencil encountered a different phase assemblage */
    public boolean isPhaseBoundaryEncountered() {
      return phaseBoundaryEncountered;
    }

    /** @return total number of PS and TP flash evaluations, including rejected trials */
    public int getFlashEvaluations() {
      return flashEvaluations;
    }

    /** @return copy of final stencil states; centre is first, then the two sample states */
    public State[] getSamples() {
      return samples.clone();
    }
  }

  /**
   * Calculate using an initial relative pressure step of 0.001.
   *
   * @param fluid fluid with defined pressure, temperature and component inventory
   * @return diagnostic result; failed calculations have a NaN speed
   * @throws IllegalArgumentException for a null fluid or nonphysical input
   */
  public static Result calculate(SystemInterface fluid) {
    return calculate(fluid, 1.0e-3);
  }

  /**
   * Calculate using step halving and central or second-order one-sided differences.
   *
   * <p>
   * The current state's specific entropy is the target. The centre and all neighbours are equilibrated at fixed
   * pressure, entropy and inventory. EOS volume is used regardless of physical-property density corrections. The
   * supplied fluid is neither flashed nor initialized. A phase-boundary flag records sampled changes, not a complete
   * phase-boundary search.
   * </p>
   *
   * @param fluid fluid defining pressure, specific entropy and total composition
   * @param relativePressureStep initial pressure increment divided by centre pressure, [1e-6, 0.05]
   * @return immutable diagnostic result
   * @throws IllegalArgumentException for a null fluid, invalid pressure, temperature, inventory or step
   */
  public static Result calculate(SystemInterface fluid, double relativePressureStep) {
    if (fluid == null || !Double.isFinite(relativePressureStep) || relativePressureStep < 1.0e-6
        || relativePressureStep > 0.05) {
      throw new IllegalArgumentException("Fluid is required; relative pressure step must be in [1e-6, 0.05]");
    }
    if (!(fluid.getPressure() > 0.0) || !Double.isFinite(fluid.getPressure()) || !(fluid.getTemperature() > 0.0)
        || !Double.isFinite(fluid.getTemperature()) || !(fluid.getTotalNumberOfMoles() > 0.0)
        || !Double.isFinite(fluid.getTotalNumberOfMoles()) || fluid.getNumberOfComponents() == 0) {
      throw new IllegalArgumentException("Finite positive pressure, temperature and component inventory are required");
    }
    Calculation calculation = new Calculation(fluid);
    boolean previousWarm = ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      ThermodynamicModelSettings.setUseWarmStartKValues(false);
      return calculation.run(relativePressureStep);
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(previousWarm);
    }
  }

  private static final class UnsupportedStateException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    private UnsupportedStateException(String message) {
      super(message);
    }
  }

  private static final class Estimate {
    private final Stencil stencil;
    private final State[] states;
    private final double derivative;

    private Estimate(Stencil stencil, State[] states, double derivative) {
      this.stencil = stencil;
      this.states = states;
      this.derivative = derivative;
    }
  }

  private static final class Calculation {
    private final SystemInterface source;
    private final double[] inventory;
    private final double totalMoles;
    private double entropy = Double.NaN;
    private int evaluations;
    private boolean boundary;

    private Calculation(SystemInterface fluid) {
      source = fluid.clone();
      totalMoles = source.getTotalNumberOfMoles();
      inventory = new double[source.getNumberOfComponents()];
      for (int i = 0; i < inventory.length; i++) {
        inventory[i] = source.getComponent(i).getNumberOfmoles();
        if (!Double.isFinite(inventory[i]) || inventory[i] < 0.0) {
          throw new IllegalArgumentException("Component amounts must be finite and nonnegative");
        }
      }
    }

    private Result run(double relativeStep) {
      State[] states = new State[0];
      Stencil stencil = Stencil.UNAVAILABLE;
      double step = Double.NaN;
      double error = Double.NaN;
      try {
        if (source.isChemicalSystem() || source.isForcePhaseTypes() || source.doSolidPhaseCheck()
            || source.getHydrateCheck()) {
          throw new UnsupportedStateException(
              "Requires nonreacting fluid equilibrium without forced phases, solids or hydrates");
        }
        validatePhases(source);
        source.init(3);
        entropy = source.getEntropy("J/kgK");
        if (!Double.isFinite(entropy)) {
          throw new IllegalStateException("Input specific entropy is not finite");
        }
        double pressure = source.getPressure();
        State centre = solve(pressure);
        states = new State[] { centre };
        Estimate previous = null;
        step = pressure * relativeStep * 1.0e5;
        for (int refinement = 0; refinement <= MAX_REFINEMENTS; refinement++, step *= 0.5) {
          Estimate current = estimate(centre, step);
          if (current == null) {
            previous = null;
            continue;
          }
          states = current.states;
          stencil = current.stencil;
          if (previous != null && previous.stencil == current.stencil && previous.derivative > 0.0
              && current.derivative > 0.0 && Double.isFinite(previous.derivative)
              && Double.isFinite(current.derivative)) {
            error = Math.abs(current.derivative - previous.derivative) / Math.abs(current.derivative);
            double speed = Math.sqrt(1.0 / current.derivative);
            if (error <= DERIVATIVE_TOLERANCE && Double.isFinite(speed) && speed > 0.0) {
              return new Result(Status.CONVERGED, "Entropy, inventory, fugacity and step convergence checks passed",
                  speed, step, error, stencil, states, this);
            }
          }
          previous = current;
        }
        return new Result(Status.DERIVATIVE_NOT_CONVERGED, "No positive step-converged equilibrium density derivative",
            Double.NaN, step * 2.0, error, stencil, states, this);
      } catch (UnsupportedStateException ex) {
        return new Result(Status.UNSUPPORTED_STATE, ex.getMessage(), Double.NaN, step, error, stencil, states, this);
      } catch (RuntimeException ex) {
        return new Result(Status.FLASH_FAILED, ex.getMessage(), Double.NaN, step, error, stencil, states, this);
      }
    }

    private Estimate estimate(State centre, double step) {
      double pressure = centre.pressurePa;
      State low = solve((pressure - step) / 1.0e5);
      State high = solve((pressure + step) / 1.0e5);
      boolean sameLow = centre.samePhases(low);
      boolean sameHigh = centre.samePhases(high);
      boundary |= !sameLow || !sameHigh;
      if (sameLow && sameHigh) {
        return new Estimate(Stencil.CENTRAL, new State[] { centre, low, high },
            (high.density - low.density) / (2.0 * step));
      }
      if (sameHigh) {
        State far = solve((pressure + 2.0 * step) / 1.0e5);
        boundary |= !centre.samePhases(far);
        return centre.samePhases(far)
            ? new Estimate(Stencil.FORWARD, new State[] { centre, high, far },
                (-3.0 * centre.density + 4.0 * high.density - far.density) / (2.0 * step))
            : null;
      }
      if (sameLow) {
        State far = solve((pressure - 2.0 * step) / 1.0e5);
        boundary |= !centre.samePhases(far);
        return centre.samePhases(far)
            ? new Estimate(Stencil.BACKWARD, new State[] { centre, low, far },
                (3.0 * centre.density - 4.0 * low.density + far.density) / (2.0 * step))
            : null;
      }
      return null;
    }

    private State solve(double pressure) {
      if (inventory.length == 1) {
        SystemInterface trial = source.clone();
        trial.setPressure(pressure);
        try {
          evaluations++;
          new ThermodynamicOperations(trial).PSflash(entropy, "J/kgK");
          trial.init(3);
          return checked(trial, pressure);
        } catch (UnsupportedStateException ex) {
          throw ex;
        } catch (RuntimeException ex) {
          // Pure-fluid PS solves saturation quality; rejected single-phase roots can use TP.
        }
      }
      double guess = source.getTemperature();
      SystemInterface initial = tp(pressure, guess);
      double initialError = residual(initial);
      if (Math.abs(initialError) <= ENTROPY_TOLERANCE) {
        return checked(initial, pressure);
      }
      double lower = guess;
      double upper = guess;
      double lowError = initialError;
      double highError = initialError;
      double span = Math.max(1.0, guess * 0.005);
      for (int i = 0; i < 30 && (lowError > 0.0 || highError < 0.0); i++, span *= 1.6) {
        if (lowError > 0.0) {
          lower = Math.max(1.0, guess - span);
          lowError = residual(tp(pressure, lower));
        }
        if (highError < 0.0) {
          upper = guess + span;
          highError = residual(tp(pressure, upper));
        }
      }
      if (!(lowError <= 0.0 && highError >= 0.0)) {
        throw new IllegalStateException("Could not bracket specific entropy at " + pressure + " bara");
      }
      double lastError = Double.NaN;
      for (int i = 0; i < 100; i++) {
        double temperature = 0.5 * (lower + upper);
        SystemInterface trial = tp(pressure, temperature);
        lastError = residual(trial);
        if (Math.abs(lastError) <= ENTROPY_TOLERANCE) {
          return checked(trial, pressure);
        }
        if (lastError > 0.0) {
          upper = temperature;
        } else {
          lower = temperature;
        }
        if (upper - lower <= 1.0e-12 * Math.max(1.0, temperature)) {
          break;
        }
      }
      throw new IllegalStateException("Entropy root did not close at " + pressure + " bara; residual = " + lastError
          + " J/(kg K). A pure-fluid saturation discontinuity requires a quality solve.");
    }

    private SystemInterface tp(double pressure, double temperature) {
      SystemInterface trial = source.clone();
      trial.setPressure(pressure);
      trial.setTemperature(temperature);
      // Fresh clones and cold starts avoid inheriting iteration history from adjacent root trials.
      ThermodynamicModelSettings.setUseWarmStartKValues(false);
      evaluations++;
      new ThermodynamicOperations(trial).TPflash();
      trial.init(3);
      return trial;
    }

    private double residual(SystemInterface trial) {
      double value = trial.getEntropy("J/kgK") - entropy;
      if (!Double.isFinite(value)) {
        throw new IllegalStateException("Nonfinite specific entropy during TP root");
      }
      return value;
    }

    private void validatePhases(SystemInterface fluid) {
      int phases = fluid.getNumberOfPhases();
      if (phases < 1 || phases > 2) {
        throw new UnsupportedStateException("Only single-fluid and vapor-liquid equilibrium states are supported");
      }
      int gasPhases = 0;
      double phaseSum = 0.0;
      for (int k = 0; k < phases; k++) {
        PhaseType type = fluid.getPhase(k).getType();
        gasPhases += type == PhaseType.GAS ? 1 : 0;
        if (type != PhaseType.GAS && type != PhaseType.OIL && type != PhaseType.LIQUID && type != PhaseType.AQUEOUS) {
          throw new UnsupportedStateException("Unsupported phase: " + type);
        }
        double beta = fluid.getBeta(k);
        if (!Double.isFinite(beta) || beta < 0.0 || beta > 1.0) {
          throw new IllegalStateException("Invalid molar phase fraction");
        }
        phaseSum += beta;
      }
      if (phases == 2 && gasPhases != 1) {
        throw new UnsupportedStateException("Two-phase acoustic states require one gas and one liquid phase");
      }
      if (Math.abs(phaseSum - 1.0) > BALANCE_TOLERANCE) {
        throw new IllegalStateException("Molar phase fractions do not sum to one");
      }
    }

    private State checked(SystemInterface fluid, double pressure) {
      validatePhases(fluid);
      int phases = fluid.getNumberOfPhases();
      double balance = Math.abs(fluid.getTotalNumberOfMoles() - totalMoles) / totalMoles;
      double fugacity = 0.0;
      for (int i = 0; i < inventory.length; i++) {
        double recovered = 0.0;
        for (int k = 0; k < phases; k++) {
          double x = fluid.getPhase(k).getComponent(i).getx();
          if (!Double.isFinite(x) || x < 0.0 || x > 1.0) {
            throw new IllegalStateException("Invalid phase composition");
          }
          recovered += fluid.getPhase(k).getNumberOfMolesInPhase() * x;
        }
        balance = Math.max(balance, Math.abs(recovered - inventory[i]) / totalMoles);
        balance = Math.max(balance, Math.abs(fluid.getComponent(i).getNumberOfmoles() - inventory[i]) / totalMoles);
        if (phases == 2 && inventory[i] / totalMoles > 1.0e-12) {
          double first = fluid.getPhase(0).getComponent(i).getx()
              * fluid.getPhase(0).getComponent(i).getFugacityCoefficient();
          double second = fluid.getPhase(1).getComponent(i).getx()
              * fluid.getPhase(1).getComponent(i).getFugacityCoefficient();
          fugacity = Math.max(fugacity, Math.abs(Math.log(first / second)));
        }
      }
      State state = new State(fluid, entropy, balance, fugacity);
      if (balance > BALANCE_TOLERANCE || !Double.isFinite(balance) || !Double.isFinite(fugacity)
          || fugacity > FUGACITY_TOLERANCE || !Double.isFinite(state.entropyResidual)
          || Math.abs(state.entropyResidual) > ENTROPY_TOLERANCE || !(state.density > 0.0)
          || !Double.isFinite(state.density) || !(state.temperatureK > 0.0) || !Double.isFinite(state.temperatureK)
          || !Double.isFinite(state.pressurePa) || !(state.pressurePa > 0.0)
          || Math.abs(fluid.getPressure() - pressure) > 1.0e-10 * pressure) {
        throw new IllegalStateException(
            "Equilibrium closure failed at " + pressure + " bara: entropy residual = " + state.entropyResidual
                + " J/(kg K), component residual = " + balance + ", log fugacity residual = " + fugacity);
      }
      return state;
    }
  }
}
