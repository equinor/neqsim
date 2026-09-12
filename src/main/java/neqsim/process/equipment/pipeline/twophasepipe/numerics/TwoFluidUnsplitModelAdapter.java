package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.Serializable;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations.TransactionalEvaluation;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;

/**
 * Transactional bridge between {@link UnsplitTransientSolver} and the two-fluid finite-volume operator.
 *
 * <p>
 * Every residual probe starts from cloned accepted section templates. Trial pressure-dependent densities are installed
 * before conservative variables are recovered, and the closure densities are evaluated at the end-state pressure used
 * by the solver's volume equation. A prescribed outlet pressure is applied at the external boundary face only.
 * </p>
 *
 * <p>
 * This adapter is intentionally not selected by {@code TwoFluidPipe.runTransient}. It establishes the evaluator
 * contract needed for subsequent pipe integration and severe-slugging qualification without changing a production
 * default.
 * </p>
 */
public final class TwoFluidUnsplitModelAdapter implements UnsplitTransientSolver.Model, Serializable {
  private static final long serialVersionUID = 1L;
  private static final int PHASE_COUNT = 3;
  private static final int STATE_SIZE = 7;

  /** Pressure-dependent gas, oil, and water density closure. */
  @FunctionalInterface
  public interface PhaseDensityModel extends Serializable {
    /**
     * Evaluate phase densities for one cell.
     *
     * @param cell cell index
     * @param conservativeState seven-column conservative state
     * @param pressure cell pressure in Pa
     * @param time common midpoint coefficient-evaluation time in s, also used at the end-state pressure
     * @return gas, oil, and water densities in kg/m3
     */
    double[] calculate(int cell, double[] conservativeState, double pressure, double time);
  }

  /**
   * Owns nonsmooth choices made by the finite-volume closure while Newton forms and accepts an active set.
   *
   * <p>
   * The controller must not mutate the pipe's accepted sections. Implementations may retain only donor, regime and
   * phase-presence choices derived from the supplied defensive state copies.
   * </p>
   */
  public interface ActiveSetController extends Serializable {
    /**
     * Freeze the choices used by all residual probes in one Jacobian.
     *
     * @param state defensive midpoint conservative state
     * @param pressure defensive midpoint pressure in Pa, including pressure-dependent donor choices
     */
    default void beginLinearization(double[][] state, double[] pressure) {
    }

    /** Release choices frozen for the completed Jacobian. */
    default void endLinearization() {
    }

    /**
     * Refresh choices after an accepted Newton update.
     *
     * @param state defensive midpoint conservative state
     * @param pressure defensive midpoint pressure in Pa
     * @return true when a choice changed and the residual must be relinearized
     */
    default boolean update(double[][] state, double[] pressure) {
      return false;
    }
  }

  private static final ActiveSetController SMOOTH_ACTIVE_SET = new ActiveSetController() {
    private static final long serialVersionUID = 1L;
  };

  private final TwoFluidConservationEquations equations;
  private final TwoFluidSection[] acceptedTemplates;
  private final double spatialStep;
  private final PhaseDensityModel densityModel;
  private final ActiveSetController activeSetController;

  /**
   * Create a transactional adapter.
   *
   * @param equations configured finite-volume operator
   * @param acceptedTemplates accepted section state used as the trial template
   * @param spatialStep representative cell size in m
   * @param densityModel pressure-dependent phase density model
   */
  public TwoFluidUnsplitModelAdapter(TwoFluidConservationEquations equations, TwoFluidSection[] acceptedTemplates,
      double spatialStep, PhaseDensityModel densityModel) {
    this(equations, acceptedTemplates, spatialStep, densityModel, SMOOTH_ACTIVE_SET);
  }

  /**
   * Create a transactional adapter with explicit ownership of nonsmooth closure choices.
   *
   * @param equations configured finite-volume operator
   * @param acceptedTemplates accepted section state used as the trial template
   * @param spatialStep representative cell size in m
   * @param densityModel pressure-dependent phase density model
   * @param activeSetController donor, regime and phase-presence active-set owner
   */
  public TwoFluidUnsplitModelAdapter(TwoFluidConservationEquations equations, TwoFluidSection[] acceptedTemplates,
      double spatialStep, PhaseDensityModel densityModel, ActiveSetController activeSetController) {
    if (equations == null || densityModel == null) {
      throw new IllegalArgumentException("Equations and density model cannot be null");
    }
    if (activeSetController == null) {
      throw new IllegalArgumentException("Active-set controller cannot be null");
    }
    if (acceptedTemplates == null || acceptedTemplates.length == 0) {
      throw new IllegalArgumentException("At least one accepted section template is required");
    }
    if (!(spatialStep > 0.0) || !Double.isFinite(spatialStep)) {
      throw new IllegalArgumentException("Spatial step must be positive and finite");
    }
    this.equations = equations;
    this.acceptedTemplates = cloneSections(acceptedTemplates);
    double[][] acceptedState = new double[this.acceptedTemplates.length][];
    double[] acceptedPressure = new double[this.acceptedTemplates.length];
    for (int cell = 0; cell < this.acceptedTemplates.length; cell++) {
      TwoFluidSection section = this.acceptedTemplates[cell];
      if (!(section.getLength() > 0.0) || !Double.isFinite(section.getLength()) || !(section.getArea() > 0.0)
          || !Double.isFinite(section.getArea())) {
        throw new IllegalArgumentException("Accepted cells must have positive finite length and area");
      }
      acceptedState[cell] = section.getStateVector();
      acceptedPressure[cell] = section.getPressure();
    }
    validateShape(acceptedState, acceptedPressure, "accepted");
    this.spatialStep = spatialStep;
    this.densityModel = densityModel;
    this.activeSetController = activeSetController;
  }

  @Override
  public synchronized void beginLinearization(double[][] state, double[] pressure) {
    validateShape(state, pressure, "linearization");
    activeSetController.beginLinearization(copy(state), pressure.clone());
  }

  @Override
  public synchronized void endLinearization() {
    activeSetController.endLinearization();
  }

  @Override
  public synchronized boolean updateActiveSet(double[][] state, double[] pressure) {
    validateShape(state, pressure, "active-set");
    return activeSetController.update(copy(state), pressure.clone());
  }

  @Override
  public synchronized UnsplitTransientSolver.Evaluation evaluate(double[][] state, double[] pressure,
      double[][] closureState, double[] closurePressure, double time, double outletPressure,
      boolean outletPressureFixed) {
    validateShape(state, pressure, "midpoint");
    validateShape(closureState, closurePressure, "closure");
    if (!Double.isFinite(time)) {
      throw new IllegalArgumentException("Coefficient-evaluation time must be finite");
    }
    if (outletPressureFixed && (!(outletPressure > 0.0) || !Double.isFinite(outletPressure))) {
      throw new IllegalArgumentException("A fixed outlet pressure must be positive and finite");
    }

    TwoFluidSection[] trialSections = createTrialSections(state, pressure, time);

    double[][] closureDensities = new double[PHASE_COUNT][trialSections.length];
    for (int cell = 0; cell < trialSections.length; cell++) {
      double[] densities = densities(cell, closureState[cell], closurePressure[cell], time);
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        closureDensities[phase][cell] = densities[phase];
      }
    }

    double[][] rates;
    synchronized (equations) {
      double savedOutletPressure = equations.getOutletBoundaryPressure();
      try {
        equations.setOutletBoundaryPressure(outletPressureFixed ? outletPressure : Double.NaN);
        rates = equations.calcRHSTransactional(trialSections, spatialStep);
      } finally {
        equations.setOutletBoundaryPressure(savedOutletPressure);
      }
    }
    return new UnsplitTransientSolver.Evaluation(rates, closureDensities);
  }

  /**
   * Independently verify a converged isothermal candidate and prepare its endpoint and exact midpoint ledger.
   *
   * <p>
   * Nothing is committed: accepted section templates, equation diagnostics, clocks and streams remain unchanged. This
   * checks the actual finite-volume residual again instead of trusting a solver result produced with another adapter,
   * boundary, time step or configuration. Callers still own atomic commit/rollback and physical qualification. Legacy
   * trial primitive recovery remains the residual extension away from volume closure; endpoint recovery never repairs
   * the converged state. Density coefficients use the common midpoint time at both midpoint and end pressure, as in
   * {@link #evaluate}.
   * </p>
   *
   * @param candidate nonlinear solve result
   * @param timeStep step duration in s
   * @param startTime accepted start time in s
   * @param outletPressure prescribed external face pressure in Pa
   * @param outletPressureFixed whether the outlet face has a pressure boundary
   * @param relativeTolerance positive finite scaled conservation and relative-volume tolerance
   * @return immutable prepared step with defensive endpoint sections and the exact midpoint transport ledger
   * @throws IllegalArgumentException for invalid inputs or endpoint properties
   * @throws IllegalStateException for an unconverged, inconsistent or unsupported candidate
   */
  public synchronized PreparedStep prepareStep(UnsplitTransientSolver.Result candidate, double timeStep,
      double startTime, double outletPressure, boolean outletPressureFixed, double relativeTolerance) {
    if (candidate == null || !(timeStep > 0.0) || !Double.isFinite(timeStep) || !Double.isFinite(startTime)
        || !Double.isFinite(startTime + timeStep) || startTime + timeStep <= startTime || !(relativeTolerance > 0.0)
        || !Double.isFinite(relativeTolerance)) {
      throw new IllegalArgumentException("Step preparation requires a candidate, finite advancing time and tolerance");
    }
    if (!candidate.isConverged() || !candidate.isActiveSetStable()) {
      throw new IllegalStateException("An unconverged or active-set-unstable result cannot be prepared");
    }
    double[][] endpointState = candidate.getState();
    double[] endpointPressure = candidate.getPressure();
    validateShape(endpointState, endpointPressure, "endpoint");
    if (outletPressureFixed && (!(outletPressure > 0.0) || !Double.isFinite(outletPressure))) {
      throw new IllegalArgumentException("A fixed outlet pressure must be positive and finite");
    }
    double evaluationTime = startTime + 0.5 * timeStep;
    double[][] midpointState = new double[acceptedTemplates.length][STATE_SIZE];
    double[] midpointPressure = new double[acceptedTemplates.length];
    double[][] previousState = new double[acceptedTemplates.length][];
    for (int cell = 0; cell < acceptedTemplates.length; cell++) {
      previousState[cell] = acceptedTemplates[cell].getStateVector();
      if (endpointState[cell][6] != previousState[cell][6]) {
        throw new IllegalStateException("An isothermal candidate cannot change the retained energy variable");
      }
      for (int variable = 0; variable < STATE_SIZE; variable++) {
        midpointState[cell][variable] = 0.5 * previousState[cell][variable] + 0.5 * endpointState[cell][variable];
      }
      midpointPressure[cell] = 0.5 * acceptedTemplates[cell].getPressure() + 0.5 * endpointPressure[cell];
    }
    TwoFluidSection[] midpoint = createTrialSections(midpointState, midpointPressure, evaluationTime);
    TwoFluidSection[] endpoint = cloneSections(acceptedTemplates);
    for (int cell = 0; cell < endpoint.length; cell++) {
      installDensities(endpoint[cell], densities(cell, endpointState[cell], endpointPressure[cell], evaluationTime));
      endpoint[cell].setPressure(endpointPressure[cell]);
      endpoint[cell].setConservativeEndpoint(endpointState[cell], relativeTolerance);
    }

    TransactionalEvaluation evaluation;
    synchronized (equations) {
      if (equations.isIncludeEnergyEquation() || equations.isIncludeMassTransfer() || equations.isHeatTransferEnabled()
          || equations.isImplicitInterfacialPressure() || equations.isStiffBubbleDragEnabled()
          || equations.isConservativeSlugForceIntegrationEnabled()) {
        throw new IllegalStateException("Prepared unsplit steps require an isothermal operator without phase transfer "
            + "or separately split stiff/subcell source integration");
      }
      double savedOutletPressure = equations.getOutletBoundaryPressure();
      try {
        equations.setOutletBoundaryPressure(outletPressureFixed ? outletPressure : Double.NaN);
        evaluation = equations.evaluateTransactional(midpoint, spatialStep);
      } finally {
        equations.setOutletBoundaryPressure(savedOutletPressure);
      }
    }
    double[][] rates = evaluation.getRates();
    double maximumResidual = 0.0;
    for (int cell = 0; cell < endpoint.length; cell++) {
      for (int variable = 0; variable < UnsplitTransientSolver.CONSERVATIVE_VARIABLE_COUNT; variable++) {
        double residual = (endpointState[cell][variable] - previousState[cell][variable]
            - timeStep * rates[cell][variable]) / Math.max(1.0, Math.abs(previousState[cell][variable]));
        if (!Double.isFinite(residual)) {
          throw new IllegalStateException("Prepared-step conservation residual must be finite");
        }
        maximumResidual = Math.max(maximumResidual, Math.abs(residual));
      }
    }
    if (maximumResidual > relativeTolerance) {
      throw new IllegalStateException("Candidate does not satisfy the current midpoint operator: scaled residual="
          + maximumResidual + ", tolerance=" + relativeTolerance);
    }
    return new PreparedStep(endpoint, acceptedTemplates, evaluation, timeStep, startTime, maximumResidual);
  }

  /** Immutable, verified candidate; preparation alone does not advance physical time or commit any pipe state. */
  public static final class PreparedStep implements Serializable {
    private static final long serialVersionUID = 1L;
    private final TwoFluidSection[] endpointSections;
    private final TransactionalEvaluation midpointEvaluation;
    private final double timeStep;
    private final double startTime;
    private final double maximumScaledResidual;
    private final double[] initialMassKg = new double[PHASE_COUNT];
    private final double[] finalMassKg = new double[PHASE_COUNT];

    private PreparedStep(TwoFluidSection[] endpoint, TwoFluidSection[] previous, TransactionalEvaluation evaluation,
        double timeStep, double startTime, double maximumScaledResidual) {
      endpointSections = cloneSections(endpoint);
      midpointEvaluation = evaluation;
      this.timeStep = timeStep;
      this.startTime = startTime;
      this.maximumScaledResidual = maximumScaledResidual;
      for (int cell = 0; cell < endpoint.length; cell++) {
        double[] before = previous[cell].getStateVector();
        double[] after = endpoint[cell].getStateVector();
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          initialMassKg[phase] += before[phase] * previous[cell].getLength();
          finalMassKg[phase] += after[phase] * endpoint[cell].getLength();
        }
      }
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        if (!Double.isFinite(initialMassKg[phase]) || !Double.isFinite(finalMassKg[phase])) {
          throw new IllegalStateException("Prepared inventories must be finite");
        }
      }
    }

    /**
     * @return defensive conserved/primitive endpoint clones with solved cell pressures; closure diagnostics still
     * require a separate endpoint refresh as described by {@link TwoFluidSection#setConservativeEndpoint}
     */
    public TwoFluidSection[] getEndpointSections() {
      return cloneSections(endpointSections);
    }

    /** @return immutable exact midpoint derivatives and transport ledger */
    public TransactionalEvaluation getMidpointEvaluation() {
      return midpointEvaluation;
    }

    /** @return prepared duration in s; no clock is advanced */
    public double getTimeStepSeconds() {
      return timeStep;
    }

    /** @return accepted start time in s */
    public double getStartTimeSeconds() {
      return startTime;
    }

    /** @return independently verified maximum scaled conservative residual */
    public double getMaximumScaledResidual() {
      return maximumScaledResidual;
    }

    /** @return defensive initial gas/oil/water inventories in kg */
    public double[] getInitialMassKg() {
      return initialMassKg.clone();
    }

    /** @return defensive final gas/oil/water inventories in kg */
    public double[] getFinalMassKg() {
      return finalMassKg.clone();
    }

    /** @return gas/oil/water mass-balance residuals in kg, using exact midpoint boundary/source transfers */
    public double[] getMassResidualKg() {
      double[] inlet = midpointEvaluation.getMassBalanceRate().getInletMassFlowKgPerSecond();
      double[] outlet = midpointEvaluation.getMassBalanceRate().getOutletMassFlowKgPerSecond();
      double[] source = midpointEvaluation.getMassBalanceRate().getSourceMassFlowKgPerSecond();
      double[] residual = new double[PHASE_COUNT];
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        residual[phase] = finalMassKg[phase] - initialMassKg[phase]
            - timeStep * (inlet[phase] - outlet[phase] + source[phase]);
      }
      return residual;
    }
  }

  private TwoFluidSection[] createTrialSections(double[][] state, double[] pressure, double time) {
    TwoFluidSection[] trial = cloneSections(acceptedTemplates);
    for (int cell = 0; cell < trial.length; cell++) {
      trial[cell].setPressure(pressure[cell]);
      installDensities(trial[cell], densities(cell, state[cell], pressure[cell], time));
    }
    equations.applyState(trial, state);
    return trial;
  }

  private static void installDensities(TwoFluidSection section, double[] densities) {
    section.setGasDensity(densities[0]);
    section.setOilDensity(densities[1]);
    section.setWaterDensity(densities[2]);
  }

  private double[] densities(int cell, double[] state, double pressure, double time) {
    double[] values = densityModel.calculate(cell, state.clone(), pressure, time);
    if (values == null || values.length != PHASE_COUNT) {
      throw new IllegalArgumentException("Density model must return gas, oil, and water densities");
    }
    for (double value : values) {
      if (!(value > 0.0) || !Double.isFinite(value)) {
        throw new IllegalArgumentException("Phase densities must be positive and finite");
      }
    }
    return values.clone();
  }

  private void validateShape(double[][] state, double[] pressure, String label) {
    if (state == null || pressure == null || state.length != acceptedTemplates.length
        || pressure.length != acceptedTemplates.length) {
      throw new IllegalArgumentException(label + " arrays must match the accepted section count");
    }
    for (int cell = 0; cell < state.length; cell++) {
      if (state[cell] == null || state[cell].length < STATE_SIZE) {
        throw new IllegalArgumentException(label + " state requires seven variables per cell");
      }
      if (!(pressure[cell] > 0.0) || !Double.isFinite(pressure[cell])) {
        throw new IllegalArgumentException(label + " pressures must be positive and finite");
      }
      for (int variable = 0; variable < STATE_SIZE; variable++) {
        if (!Double.isFinite(state[cell][variable]) || variable < PHASE_COUNT && state[cell][variable] < 0.0) {
          throw new IllegalArgumentException(label + " state must be finite with nonnegative phase masses");
        }
      }
    }
  }

  private static TwoFluidSection[] cloneSections(TwoFluidSection[] sections) {
    TwoFluidSection[] copy = new TwoFluidSection[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      if (sections[cell] == null) {
        throw new IllegalArgumentException("Section templates cannot contain null entries");
      }
      copy[cell] = sections[cell].clone();
    }
    return copy;
  }

  private static double[][] copy(double[][] values) {
    double[][] result = new double[values.length][];
    for (int row = 0; row < values.length; row++) {
      result[row] = values[row].clone();
    }
    return result;
  }
}
