package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.Serializable;

/**
 * Solves one isothermal two-fluid time level with phase transport, momentum and pressure-dependent volume closure in a
 * common nonlinear system.
 *
 * <p>
 * Each finite-volume cell has seven nonlinear unknowns: gas, oil and water mass per length; gas, oil and water momentum
 * per length; and cell pressure. The six conservation residuals use an implicit-midpoint evaluation. The seventh
 * residual is pressure-dependent volume closure. Total energy remains unchanged by this isothermal kernel and must be
 * advanced by a separately qualified thermal model.
 * </p>
 *
 * <p>
 * A fixed outlet pressure is supplied to {@link Model#evaluate} as a boundary-face condition. It is deliberately not
 * imposed on the final cell pressure, so the final cell retains its volume-closure equation and boundary transport can
 * remain conservative.
 * </p>
 *
 * <p>
 * The model callback must be transactional: repeated evaluations with identical arguments must return identical results
 * and must not advance accepted ledgers or retain trial state. Donor, regime and complementarity selections may be
 * frozen during a Jacobian through {@link Model#beginLinearization} and refreshed after a Newton update through
 * {@link Model#updateActiveSet}. The base residual and all colored probes use the same frozen choices. Every reported
 * active-set change triggers a fresh residual before the next refresh, with a bounded refresh budget per iterate.
 * </p>
 *
 * @author Even Solbraa
 */
public final class UnsplitTransientSolver implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Number of phase mass and momentum conservation variables per cell. */
  public static final int CONSERVATIVE_VARIABLE_COUNT = 6;

  /** Number of nonlinear unknowns and residuals per cell. */
  public static final int BLOCK_SIZE = 7;

  private static final int PHASE_COUNT = 3;
  private static final double MINIMUM_SCALE = 1.0;
  private static final double MINIMUM_PRESSURE_SCALE = 1.0e5;
  private static final int DEFAULT_MAXIMUM_ACTIVE_SET_UPDATES = 20;

  private int maximumIterations = 20;
  private int maximumLineSearchSteps = 12;
  private int maximumActiveSetUpdates = DEFAULT_MAXIMUM_ACTIVE_SET_UPDATES;
  private int cellStencilHalfWidth = 2;
  private double relativeTolerance = 1.0e-7;
  private double finiteDifferenceStep = 1.0e-6;
  private double minimumPressure = 1.0;
  private double fractionToBoundary = 0.99;

  /**
   * Transactional spatial model evaluated by the nonlinear solver.
   */
  public interface Model {
    /**
     * Evaluate six conservative rates and three phase densities at one trial midpoint.
     *
     * @param state seven-column conservative state; column six is unchanged isothermal energy
     * @param pressure midpoint cell-centre pressure in Pa used by conservative rates
     * @param closureState end-of-step conservative state used by volume closure
     * @param closurePressure end-of-step cell pressure in Pa used by phase densities
     * @param time evaluation time in s
     * @param outletPressure fixed outlet-face pressure in Pa, or NaN for a free outlet
     * @param outletPressureFixed true when the outlet face has a pressure boundary condition
     * @return transactional model evaluation
     */
    Evaluation evaluate(double[][] state, double[] pressure, double[][] closureState, double[] closurePressure,
        double time, double outletPressure, boolean outletPressureFixed);

    /**
     * Freeze nonsmooth donor, flow-regime and complementarity choices for one Jacobian, including its unperturbed base
     * residual. The solver releases these choices before the line search, even when initialization or a probe fails.
     *
     * @param state midpoint conservative state
     * @param pressure midpoint pressure in Pa
     */
    default void beginLinearization(double[][] state, double[] pressure) {
      // Optional for smooth models.
    }

    /**
     * Release any choices frozen by {@link #beginLinearization}, including partially initialized choices if that call
     * failed. Implementations must permit cleanup after a failed initialization.
     */
    default void endLinearization() {
      // Optional for smooth models.
    }

    /**
     * Refresh nonsmooth choices at the initial iterate or after an accepted Newton update. After a reported change the
     * solver reevaluates the residual before calling this method again at the same iterate. Calls receive defensive
     * state copies and occur outside a frozen linearization.
     *
     * @param state updated midpoint conservative state
     * @param pressure updated midpoint pressure in Pa
     * @return true when an active choice changed and the residual must be reevaluated
     */
    default boolean updateActiveSet(double[][] state, double[] pressure) {
      return false;
    }
  }

  /** Immutable result of one transactional model evaluation. */
  public static final class Evaluation {
    private final double[][] conservativeRates;
    private final double[][] phaseDensities;

    /**
     * Create an evaluation.
     *
     * @param conservativeRates six or more rates per cell in conservative-variable order
     * @param phaseDensities gas, oil and water densities with shape {@code [3][cellCount]}
     */
    public Evaluation(double[][] conservativeRates, double[][] phaseDensities) {
      if (conservativeRates == null || phaseDensities == null) {
        throw new IllegalArgumentException("Model evaluation arrays cannot be null");
      }
      this.conservativeRates = copy(conservativeRates);
      this.phaseDensities = copy(phaseDensities);
    }

    private void validate(int cellCount) {
      if (conservativeRates.length != cellCount || phaseDensities.length != PHASE_COUNT) {
        throw new IllegalArgumentException("Model evaluation dimensions do not match the cell count");
      }
      for (int cell = 0; cell < cellCount; cell++) {
        if (conservativeRates[cell] == null || conservativeRates[cell].length < CONSERVATIVE_VARIABLE_COUNT) {
          throw new IllegalArgumentException("Each cell requires six conservative rates");
        }
        for (int variable = 0; variable < CONSERVATIVE_VARIABLE_COUNT; variable++) {
          if (!Double.isFinite(conservativeRates[cell][variable])) {
            throw new IllegalArgumentException("Conservative rates must be finite");
          }
        }
      }
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        if (phaseDensities[phase] == null || phaseDensities[phase].length != cellCount) {
          throw new IllegalArgumentException("Each phase requires one density per cell");
        }
        for (int cell = 0; cell < cellCount; cell++) {
          if (!(phaseDensities[phase][cell] > 0.0) || !Double.isFinite(phaseDensities[phase][cell])) {
            throw new IllegalArgumentException("Phase densities must be positive and finite");
          }
        }
      }
    }
  }

  /**
   * Reason a bounded nonlinear solve stopped. Model callback exceptions are propagated instead of returning a result.
   */
  public enum TerminationReason {
    /** Residual and active-set convergence gates passed. */
    CONVERGED,
    /** The nonlinear iteration budget was exhausted. */
    MAXIMUM_ITERATIONS,
    /** Active-set refreshes did not stabilize within the per-iterate budget. */
    ACTIVE_SET_UPDATE_LIMIT,
    /** The linearized system was singular or produced a nonfinite update. */
    SINGULAR_JACOBIAN,
    /** No positive Newton step satisfied the phase-mass and pressure bounds. */
    NO_ADMISSIBLE_STEP,
    /** No admissible trial passed the bounded line search. */
    LINE_SEARCH_FAILED
  }

  /** Immutable, serializable nonlinear solve result. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double[][] state;
    private final double[] pressure;
    private final int iterations;
    private final int modelEvaluations;
    private final double maximumScaledResidual;
    private final double minimumAcceptedStepLength;
    private final boolean activeSetStable;
    private final boolean converged;
    private final TerminationReason terminationReason;

    private Result(double[][] state, double[] pressure, int iterations, int modelEvaluations,
        double maximumScaledResidual, double minimumAcceptedStepLength, boolean activeSetStable, boolean converged,
        TerminationReason terminationReason) {
      this.state = copy(state);
      this.pressure = pressure.clone();
      this.iterations = iterations;
      this.modelEvaluations = modelEvaluations;
      this.maximumScaledResidual = maximumScaledResidual;
      this.minimumAcceptedStepLength = minimumAcceptedStepLength;
      this.activeSetStable = activeSetStable;
      this.converged = converged;
      this.terminationReason = terminationReason;
    }

    /** @return defensive copy of the conservative state */
    public double[][] getState() {
      return copy(state);
    }

    /** @return defensive copy of cell pressure in Pa */
    public double[] getPressure() {
      return pressure.clone();
    }

    /** @return nonlinear iterations used */
    public int getIterations() {
      return iterations;
    }

    /**
     * @return actual calls made by the solver to {@link Model#evaluate}, including frozen bases, colored probes,
     * active-set refresh residuals and rejected line-search trials; excludes work internal to model callbacks
     */
    public int getModelEvaluations() {
      return modelEvaluations;
    }

    /** @return maximum absolute scaled residual */
    public double getMaximumScaledResidual() {
      return maximumScaledResidual;
    }

    /** @return smallest accepted Newton line-search length */
    public double getMinimumAcceptedStepLength() {
      return minimumAcceptedStepLength;
    }

    /** @return whether donor/regime/complementarity choices were stable at exit */
    public boolean isActiveSetStable() {
      return activeSetStable;
    }

    /** @return true when residual and active-set gates both passed */
    public boolean isConverged() {
      return converged;
    }

    /** @return reason the nonlinear solve stopped */
    public TerminationReason getTerminationReason() {
      return terminationReason;
    }
  }

  private static final class CountingModel implements Model {
    private final Model delegate;
    private int evaluations;

    private CountingModel(Model delegate) {
      this.delegate = delegate;
    }

    @Override
    public Evaluation evaluate(double[][] state, double[] pressure, double[][] closureState, double[] closurePressure,
        double time, double outletPressure, boolean outletPressureFixed) {
      evaluations++;
      return delegate.evaluate(state, pressure, closureState, closurePressure, time, outletPressure,
          outletPressureFixed);
    }

    @Override
    public void beginLinearization(double[][] state, double[] pressure) {
      delegate.beginLinearization(state, pressure);
    }

    @Override
    public void endLinearization() {
      delegate.endLinearization();
    }

    @Override
    public boolean updateActiveSet(double[][] state, double[] pressure) {
      return delegate.updateActiveSet(state, pressure);
    }
  }

  private static final class ResidualEvaluation {
    private final double[] residual;
    private final double[][] midpointState;
    private final double[] midpointPressure;

    private ResidualEvaluation(double[] residual, double[][] midpointState, double[] midpointPressure) {
      this.residual = residual;
      this.midpointState = midpointState;
      this.midpointPressure = midpointPressure;
    }
  }

  private static final class ActiveSetEvaluation {
    private final ResidualEvaluation evaluation;
    private final boolean stable;

    private ActiveSetEvaluation(ResidualEvaluation evaluation, boolean stable) {
      this.evaluation = evaluation;
      this.stable = stable;
    }
  }

  private static final class Linearization implements AutoCloseable {
    private final Model model;

    private Linearization(Model model, ResidualEvaluation base) {
      this.model = model;
      try {
        model.beginLinearization(copy(base.midpointState), base.midpointPressure.clone());
      } catch (RuntimeException | Error failure) {
        try {
          model.endLinearization();
        } catch (RuntimeException | Error cleanupFailure) {
          if (cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
          }
        }
        throw failure;
      }
    }

    @Override
    public void close() {
      model.endLinearization();
    }
  }

  /**
   * Solve one common implicit-midpoint time level.
   *
   * @param previousState accepted seven-column state
   * @param previousPressure accepted cell pressure in Pa
   * @param cellAreas pipe cross-sectional area per cell in m2
   * @param timeStep positive step duration in s
   * @param startTime accepted start time in s
   * @param outletPressure fixed outlet-face pressure in Pa, or NaN for a free outlet
   * @param outletPressureFixed true when the outlet face pressure is prescribed
   * @param model transactional spatial model
   * @return nonlinear result with a termination reason; a nonconverged result retains its final admissible iterate
   */
  public Result solve(double[][] previousState, double[] previousPressure, double[] cellAreas, double timeStep,
      double startTime, double outletPressure, boolean outletPressureFixed, Model model) {
    validateInputs(previousState, previousPressure, cellAreas, timeStep, startTime, outletPressure, outletPressureFixed,
        model);
    double[][] state = copy(previousState);
    double[] pressure = previousPressure.clone();
    double[] variableScale = createVariableScale(previousState, previousPressure);
    CountingModel countedModel = new CountingModel(model);
    int iterations = 0;
    double minimumAcceptedStep = 1.0;
    ResidualEvaluation current = evaluateResidual(previousState, previousPressure, state, pressure, cellAreas, timeStep,
        startTime, outletPressure, outletPressureFixed, countedModel);
    ActiveSetEvaluation refreshed = refreshActiveSet(previousState, previousPressure, state, pressure, cellAreas,
        timeStep, startTime, outletPressure, outletPressureFixed, countedModel, current);
    current = refreshed.evaluation;
    boolean activeSetStable = refreshed.stable;
    double residualNorm = maximumAbsolute(current.residual);
    TerminationReason terminationReason = activeSetStable ? TerminationReason.MAXIMUM_ITERATIONS
        : TerminationReason.ACTIVE_SET_UPDATE_LIMIT;

    while (activeSetStable && iterations < maximumIterations && residualNorm > relativeTolerance) {
      iterations++;
      double[][] jacobian;
      try (Linearization ignored = new Linearization(countedModel, current)) {
        current = evaluateResidual(previousState, previousPressure, state, pressure, cellAreas, timeStep, startTime,
            outletPressure, outletPressureFixed, countedModel);
        residualNorm = maximumAbsolute(current.residual);
        jacobian = approximateJacobian(previousState, previousPressure, state, pressure, cellAreas, variableScale,
            current.residual, timeStep, startTime, outletPressure, outletPressureFixed, countedModel);
      }

      double[] rightHandSide = new double[current.residual.length];
      for (int row = 0; row < rightHandSide.length; row++) {
        rightHandSide[row] = -current.residual[row];
      }
      double[] normalizedUpdate;
      try {
        normalizedUpdate = solveDense(jacobian, rightHandSide);
      } catch (IllegalStateException singular) {
        terminationReason = TerminationReason.SINGULAR_JACOBIAN;
        break;
      }

      double admissibleStep = admissibleStepLength(state, pressure, variableScale, normalizedUpdate);
      double stepLength = Math.min(1.0, admissibleStep);
      if (!(stepLength > 0.0)) {
        terminationReason = TerminationReason.NO_ADMISSIBLE_STEP;
        break;
      }
      boolean accepted = false;
      ResidualEvaluation trial = current;
      double[][] trialState = state;
      double[] trialPressure = pressure;
      for (int lineSearch = 0; lineSearch < maximumLineSearchSteps && stepLength > 0.0; lineSearch++) {
        trialState = copy(state);
        trialPressure = pressure.clone();
        applyUpdate(trialState, trialPressure, variableScale, normalizedUpdate, stepLength);
        trial = evaluateResidual(previousState, previousPressure, trialState, trialPressure, cellAreas, timeStep,
            startTime, outletPressure, outletPressureFixed, countedModel);
        double trialNorm = maximumAbsolute(trial.residual);
        if (trialNorm <= residualNorm * (1.0 - 1.0e-4 * stepLength) || trialNorm < relativeTolerance) {
          accepted = true;
          break;
        }
        stepLength *= 0.5;
      }
      if (!accepted) {
        terminationReason = TerminationReason.LINE_SEARCH_FAILED;
        break;
      }

      state = trialState;
      pressure = trialPressure;
      current = trial;
      minimumAcceptedStep = Math.min(minimumAcceptedStep, stepLength);
      refreshed = refreshActiveSet(previousState, previousPressure, state, pressure, cellAreas, timeStep, startTime,
          outletPressure, outletPressureFixed, countedModel, current);
      current = refreshed.evaluation;
      activeSetStable = refreshed.stable;
      residualNorm = maximumAbsolute(current.residual);
      if (!activeSetStable) {
        terminationReason = TerminationReason.ACTIVE_SET_UPDATE_LIMIT;
      }
    }

    boolean converged = terminationReason == TerminationReason.MAXIMUM_ITERATIONS && residualNorm <= relativeTolerance
        && activeSetStable;
    if (converged) {
      terminationReason = TerminationReason.CONVERGED;
    }
    return new Result(state, pressure, iterations, countedModel.evaluations, residualNorm, minimumAcceptedStep,
        activeSetStable, converged, terminationReason);
  }

  /**
   * Evaluate the scaled residual for verification and directional-derivative tests.
   *
   * @param previousState accepted state
   * @param previousPressure accepted pressure in Pa
   * @param candidateState candidate state
   * @param candidatePressure candidate pressure in Pa
   * @param cellAreas cell areas in m2
   * @param timeStep step duration in s
   * @param startTime accepted start time in s
   * @param outletPressure outlet-face pressure in Pa
   * @param outletPressureFixed whether the outlet-face pressure is prescribed
   * @param model transactional model
   * @return scaled residual ordered by seven-variable cell blocks
   */
  public double[] residual(double[][] previousState, double[] previousPressure, double[][] candidateState,
      double[] candidatePressure, double[] cellAreas, double timeStep, double startTime, double outletPressure,
      boolean outletPressureFixed, Model model) {
    validateInputs(previousState, previousPressure, cellAreas, timeStep, startTime, outletPressure, outletPressureFixed,
        model);
    validateCandidate(candidateState, candidatePressure, previousState.length);
    return evaluateResidual(previousState, previousPressure, candidateState, candidatePressure, cellAreas, timeStep,
        startTime, outletPressure, outletPressureFixed, model).residual;
  }

  /**
   * Build the colored finite-difference Jacobian of the scaled residual with respect to scaled unknowns. This
   * diagnostic supports independent directional-derivative qualification without exposing or mutating a nonlinear solve
   * in progress.
   *
   * @param previousState accepted state
   * @param previousPressure accepted pressure in Pa
   * @param candidateState candidate state
   * @param candidatePressure candidate pressure in Pa
   * @param cellAreas cell areas in m2
   * @param timeStep step duration in s
   * @param startTime accepted start time in s
   * @param outletPressure outlet-face pressure in Pa
   * @param outletPressureFixed whether the outlet-face pressure is prescribed
   * @param model transactional model
   * @return square Jacobian ordered by seven-variable cell blocks
   * @throws IllegalStateException if the active set does not stabilize within the configured refresh budget
   */
  public double[][] scaledJacobian(double[][] previousState, double[] previousPressure, double[][] candidateState,
      double[] candidatePressure, double[] cellAreas, double timeStep, double startTime, double outletPressure,
      boolean outletPressureFixed, Model model) {
    validateInputs(previousState, previousPressure, cellAreas, timeStep, startTime, outletPressure, outletPressureFixed,
        model);
    validateCandidate(candidateState, candidatePressure, previousState.length);
    ResidualEvaluation base = evaluateResidual(previousState, previousPressure, candidateState, candidatePressure,
        cellAreas, timeStep, startTime, outletPressure, outletPressureFixed, model);
    ActiveSetEvaluation refreshed = refreshActiveSet(previousState, previousPressure, candidateState, candidatePressure,
        cellAreas, timeStep, startTime, outletPressure, outletPressureFixed, model, base);
    if (!refreshed.stable) {
      throw new IllegalStateException(
          "Active set did not stabilize after " + getMaximumActiveSetUpdates() + " refresh attempts");
    }
    base = refreshed.evaluation;
    try (Linearization ignored = new Linearization(model, base)) {
      // Use the same frozen choices for the base and every perturbed column.
      base = evaluateResidual(previousState, previousPressure, candidateState, candidatePressure, cellAreas, timeStep,
          startTime, outletPressure, outletPressureFixed, model);
      return approximateJacobian(previousState, previousPressure, candidateState, candidatePressure, cellAreas,
          createVariableScale(previousState, previousPressure), base.residual, timeStep, startTime, outletPressure,
          outletPressureFixed, model);
    }
  }

  private ActiveSetEvaluation refreshActiveSet(double[][] previousState, double[] previousPressure,
      double[][] candidateState, double[] candidatePressure, double[] cellAreas, double timeStep, double startTime,
      double outletPressure, boolean outletPressureFixed, Model model, ResidualEvaluation current) {
    for (int update = 0; update < getMaximumActiveSetUpdates(); update++) {
      if (!model.updateActiveSet(copy(current.midpointState), current.midpointPressure.clone())) {
        return new ActiveSetEvaluation(current, true);
      }
      // Even the last permitted switch invalidates the old residual. Keep the
      // returned diagnostics consistent with the final active choices on failure.
      current = evaluateResidual(previousState, previousPressure, candidateState, candidatePressure, cellAreas,
          timeStep, startTime, outletPressure, outletPressureFixed, model);
    }
    return new ActiveSetEvaluation(current, false);
  }

  private ResidualEvaluation evaluateResidual(double[][] previousState, double[] previousPressure,
      double[][] candidateState, double[] candidatePressure, double[] cellAreas, double timeStep, double startTime,
      double outletPressure, boolean outletPressureFixed, Model model) {
    int cellCount = previousState.length;
    double[][] midpointState = copy(previousState);
    double[] midpointPressure = new double[cellCount];
    for (int cell = 0; cell < cellCount; cell++) {
      for (int variable = 0; variable < CONSERVATIVE_VARIABLE_COUNT; variable++) {
        midpointState[cell][variable] = 0.5 * (previousState[cell][variable] + candidateState[cell][variable]);
      }
      midpointPressure[cell] = 0.5 * (previousPressure[cell] + candidatePressure[cell]);
    }
    Evaluation evaluation = model.evaluate(copy(midpointState), midpointPressure.clone(), copy(candidateState),
        candidatePressure.clone(), startTime + 0.5 * timeStep, outletPressure, outletPressureFixed);
    if (evaluation == null) {
      throw new IllegalArgumentException("Model evaluation cannot be null");
    }
    evaluation.validate(cellCount);

    double[] residual = new double[cellCount * BLOCK_SIZE];
    for (int cell = 0; cell < cellCount; cell++) {
      int offset = cell * BLOCK_SIZE;
      for (int variable = 0; variable < CONSERVATIVE_VARIABLE_COUNT; variable++) {
        double scale = Math.max(MINIMUM_SCALE, Math.abs(previousState[cell][variable]));
        residual[offset + variable] = (candidateState[cell][variable] - previousState[cell][variable]
            - timeStep * evaluation.conservativeRates[cell][variable]) / scale;
      }
      double occupiedArea = 0.0;
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        occupiedArea += candidateState[cell][phase] / evaluation.phaseDensities[phase][cell];
      }
      residual[offset + CONSERVATIVE_VARIABLE_COUNT] = (occupiedArea - cellAreas[cell]) / cellAreas[cell];
    }
    return new ResidualEvaluation(residual, midpointState, midpointPressure);
  }

  private double[][] approximateJacobian(double[][] previousState, double[] previousPressure, double[][] candidateState,
      double[] candidatePressure, double[] cellAreas, double[] variableScale, double[] baseResidual, double timeStep,
      double startTime, double outletPressure, boolean outletPressureFixed, Model model) {
    int cellCount = previousState.length;
    int dimension = cellCount * BLOCK_SIZE;
    int colorCount = 2 * cellStencilHalfWidth + 1;
    int activeColorCount = Math.min(colorCount, cellCount);
    double[][] jacobian = new double[dimension][dimension];

    for (int variable = 0; variable < BLOCK_SIZE; variable++) {
      for (int color = 0; color < activeColorCount; color++) {
        double[][] perturbedState = copy(candidateState);
        double[] perturbedPressure = candidatePressure.clone();
        for (int cell = color; cell < cellCount; cell += colorCount) {
          int column = cell * BLOCK_SIZE + variable;
          double increment = finiteDifferenceStep * variableScale[column];
          if (variable < CONSERVATIVE_VARIABLE_COUNT) {
            perturbedState[cell][variable] += increment;
          } else {
            perturbedPressure[cell] += increment;
          }
        }
        double[] perturbedResidual = evaluateResidual(previousState, previousPressure, perturbedState,
            perturbedPressure, cellAreas, timeStep, startTime, outletPressure, outletPressureFixed, model).residual;
        for (int cell = color; cell < cellCount; cell += colorCount) {
          int column = cell * BLOCK_SIZE + variable;
          int firstRowCell = Math.max(0, cell - cellStencilHalfWidth);
          int lastRowCell = Math.min(cellCount - 1, cell + cellStencilHalfWidth);
          for (int rowCell = firstRowCell; rowCell <= lastRowCell; rowCell++) {
            for (int rowVariable = 0; rowVariable < BLOCK_SIZE; rowVariable++) {
              int row = rowCell * BLOCK_SIZE + rowVariable;
              jacobian[row][column] = (perturbedResidual[row] - baseResidual[row]) / finiteDifferenceStep;
            }
          }
        }
      }
    }
    return jacobian;
  }

  private double admissibleStepLength(double[][] state, double[] pressure, double[] variableScale,
      double[] normalizedUpdate) {
    double step = 1.0;
    for (int cell = 0; cell < state.length; cell++) {
      int offset = cell * BLOCK_SIZE;
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double change = variableScale[offset + phase] * normalizedUpdate[offset + phase];
        if (change < 0.0) {
          step = Math.min(step, fractionToBoundary * state[cell][phase] / -change);
        }
      }
      double pressureChange = variableScale[offset + CONSERVATIVE_VARIABLE_COUNT]
          * normalizedUpdate[offset + CONSERVATIVE_VARIABLE_COUNT];
      if (pressureChange < 0.0) {
        step = Math.min(step, fractionToBoundary * (pressure[cell] - minimumPressure) / -pressureChange);
      }
    }
    return Math.max(0.0, step);
  }

  private static void applyUpdate(double[][] state, double[] pressure, double[] variableScale,
      double[] normalizedUpdate, double stepLength) {
    for (int cell = 0; cell < state.length; cell++) {
      int offset = cell * BLOCK_SIZE;
      for (int variable = 0; variable < CONSERVATIVE_VARIABLE_COUNT; variable++) {
        state[cell][variable] += stepLength * variableScale[offset + variable] * normalizedUpdate[offset + variable];
      }
      pressure[cell] += stepLength * variableScale[offset + CONSERVATIVE_VARIABLE_COUNT]
          * normalizedUpdate[offset + CONSERVATIVE_VARIABLE_COUNT];
    }
  }

  private static double[] createVariableScale(double[][] state, double[] pressure) {
    double[] scale = new double[state.length * BLOCK_SIZE];
    for (int cell = 0; cell < state.length; cell++) {
      int offset = cell * BLOCK_SIZE;
      for (int variable = 0; variable < CONSERVATIVE_VARIABLE_COUNT; variable++) {
        scale[offset + variable] = Math.max(MINIMUM_SCALE, Math.abs(state[cell][variable]));
      }
      scale[offset + CONSERVATIVE_VARIABLE_COUNT] = Math.max(MINIMUM_PRESSURE_SCALE, Math.abs(pressure[cell]));
    }
    return scale;
  }

  private static double[] solveDense(double[][] matrix, double[] rightHandSide) {
    int dimension = rightHandSide.length;
    double[][] coefficients = copy(matrix);
    double[] result = rightHandSide.clone();
    for (int pivot = 0; pivot < dimension; pivot++) {
      int pivotRow = pivot;
      double pivotMagnitude = Math.abs(coefficients[pivot][pivot]);
      for (int row = pivot + 1; row < dimension; row++) {
        double magnitude = Math.abs(coefficients[row][pivot]);
        if (magnitude > pivotMagnitude) {
          pivotMagnitude = magnitude;
          pivotRow = row;
        }
      }
      if (!(pivotMagnitude > 1.0e-14) || !Double.isFinite(pivotMagnitude)) {
        throw new IllegalStateException("Unsplit transient Jacobian is singular");
      }
      if (pivotRow != pivot) {
        double[] row = coefficients[pivot];
        coefficients[pivot] = coefficients[pivotRow];
        coefficients[pivotRow] = row;
        double value = result[pivot];
        result[pivot] = result[pivotRow];
        result[pivotRow] = value;
      }
      for (int row = pivot + 1; row < dimension; row++) {
        double factor = coefficients[row][pivot] / coefficients[pivot][pivot];
        if (factor == 0.0) {
          continue;
        }
        coefficients[row][pivot] = 0.0;
        for (int column = pivot + 1; column < dimension; column++) {
          coefficients[row][column] -= factor * coefficients[pivot][column];
        }
        result[row] -= factor * result[pivot];
      }
    }
    for (int row = dimension - 1; row >= 0; row--) {
      for (int column = row + 1; column < dimension; column++) {
        result[row] -= coefficients[row][column] * result[column];
      }
      result[row] /= coefficients[row][row];
      if (!Double.isFinite(result[row])) {
        throw new IllegalStateException("Unsplit transient Jacobian produced a nonfinite update");
      }
    }
    return result;
  }

  private void validateInputs(double[][] state, double[] pressure, double[] areas, double timeStep, double startTime,
      double outletPressure, boolean outletPressureFixed, Model model) {
    if (state == null || pressure == null || areas == null || model == null || state.length == 0
        || state.length != pressure.length || state.length != areas.length) {
      throw new IllegalArgumentException("State, pressure, area and model dimensions must agree");
    }
    if (!(timeStep > 0.0) || !Double.isFinite(timeStep) || !Double.isFinite(startTime)) {
      throw new IllegalArgumentException("Time step must be positive and times finite");
    }
    if (outletPressureFixed && (!(outletPressure > 0.0) || !Double.isFinite(outletPressure))) {
      throw new IllegalArgumentException("A fixed outlet pressure must be positive and finite");
    }
    validateCandidate(state, pressure, state.length);
    for (int cell = 0; cell < state.length; cell++) {
      if (!(areas[cell] > 0.0) || !Double.isFinite(areas[cell])) {
        throw new IllegalArgumentException("Cell areas must be positive and finite");
      }
      if (pressure[cell] < minimumPressure) {
        throw new IllegalArgumentException("Accepted pressure is below the configured minimum");
      }
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        if (state[cell][phase] < 0.0) {
          throw new IllegalArgumentException("Accepted phase masses must be nonnegative");
        }
      }
    }
  }

  private static void validateCandidate(double[][] state, double[] pressure, int cellCount) {
    if (state == null || pressure == null || state.length != cellCount || pressure.length != cellCount) {
      throw new IllegalArgumentException("Candidate dimensions must agree with the accepted state");
    }
    for (int cell = 0; cell < cellCount; cell++) {
      if (state[cell] == null || state[cell].length < BLOCK_SIZE || !Double.isFinite(pressure[cell])) {
        throw new IllegalArgumentException("Each candidate cell requires seven finite state values");
      }
      for (int variable = 0; variable < BLOCK_SIZE; variable++) {
        if (!Double.isFinite(state[cell][variable])) {
          throw new IllegalArgumentException("Candidate state values must be finite");
        }
      }
    }
  }

  private static double maximumAbsolute(double[] values) {
    double maximum = 0.0;
    for (double value : values) {
      maximum = Math.max(maximum, Math.abs(value));
    }
    return maximum;
  }

  private static double[][] copy(double[][] values) {
    if (values == null) {
      return null;
    }
    double[][] result = new double[values.length][];
    for (int row = 0; row < values.length; row++) {
      result[row] = values[row] == null ? null : values[row].clone();
    }
    return result;
  }

  /** @param maximumIterations positive nonlinear iteration budget */
  public void setMaximumIterations(int maximumIterations) {
    if (maximumIterations <= 0) {
      throw new IllegalArgumentException("Maximum iterations must be positive");
    }
    this.maximumIterations = maximumIterations;
  }

  /** @return nonlinear iteration budget */
  public int getMaximumIterations() {
    return maximumIterations;
  }

  /**
   * Set the maximum active-set refresh attempts at the initial iterate and after each accepted Newton update. A final
   * attempt returning false is required to establish stability; every attempt returning true causes a fresh residual,
   * including the last permitted attempt.
   *
   * @param maximumActiveSetUpdates positive refresh-attempt budget, default 20
   */
  public void setMaximumActiveSetUpdates(int maximumActiveSetUpdates) {
    if (maximumActiveSetUpdates <= 0) {
      throw new IllegalArgumentException("Maximum active-set updates must be positive");
    }
    this.maximumActiveSetUpdates = maximumActiveSetUpdates;
  }

  /** @return active-set refresh-attempt budget per iterate */
  public int getMaximumActiveSetUpdates() {
    // A solver serialized before this setting was introduced has a zero field.
    return maximumActiveSetUpdates == 0 ? DEFAULT_MAXIMUM_ACTIVE_SET_UPDATES : maximumActiveSetUpdates;
  }

  /** @param relativeTolerance positive finite scaled-residual tolerance */
  public void setRelativeTolerance(double relativeTolerance) {
    if (!(relativeTolerance > 0.0) || !Double.isFinite(relativeTolerance)) {
      throw new IllegalArgumentException("Relative tolerance must be positive and finite");
    }
    this.relativeTolerance = relativeTolerance;
  }

  /** @return scaled-residual tolerance */
  public double getRelativeTolerance() {
    return relativeTolerance;
  }

  /** @param minimumPressure positive finite lower pressure bound in Pa */
  public void setMinimumPressure(double minimumPressure) {
    if (!(minimumPressure > 0.0) || !Double.isFinite(minimumPressure)) {
      throw new IllegalArgumentException("Minimum pressure must be positive and finite");
    }
    this.minimumPressure = minimumPressure;
  }

  /** @return lower pressure bound in Pa */
  public double getMinimumPressure() {
    return minimumPressure;
  }

  /**
   * @param cellStencilHalfWidth maximum number of neighboring cells affected on either side
   */
  public void setCellStencilHalfWidth(int cellStencilHalfWidth) {
    if (cellStencilHalfWidth < 0) {
      throw new IllegalArgumentException("Cell stencil half-width cannot be negative");
    }
    this.cellStencilHalfWidth = cellStencilHalfWidth;
  }

  /** @return configured cell stencil half-width */
  public int getCellStencilHalfWidth() {
    return cellStencilHalfWidth;
  }
}
