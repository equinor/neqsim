package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PhaseDensityModel;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PreparedStep;

/**
 * Prepare an isothermal interval with bounded subdivision of rejected unsplit steps.
 *
 * <p>
 * Every attempt uses a fresh transactional adapter and the last locally verified endpoint. Nonlinear failure bisects
 * the attempted interval; failed iterates never enter accepted state or transport ledgers. All locally prepared
 * substeps are discarded if the complete interval cannot be prepared. No pipe clock, stream or diagnostic is committed.
 * The caller owns any subsequent atomic commit and transport of components with the accepted face ledger.
 * </p>
 *
 * <p>
 * Subdivision controls nonlinear convergence, not temporal truncation error. It neither smooths closure transitions nor
 * certifies their physical validity. The solver's settings and production transient defaults remain unchanged.
 * </p>
 */
public final class TwoFluidUnsplitIntegrator {
  private final TwoFluidConservationEquations equations;
  private final PhaseDensityModel densityModel;
  private final UnsplitTransientSolver solver;
  private final int maximumHalvings;
  private final int maximumSubsteps;

  /**
   * Create an interval preparer allowing at most eight halvings and 256 accepted substeps.
   *
   * @param equations isothermal finite-volume operator with configured inlet and closure options
   * @param densityModel side-effect-free pressure-dependent phase density closure
   * @param solver configured nonlinear solver, whose options are not changed
   */
  public TwoFluidUnsplitIntegrator(TwoFluidConservationEquations equations, PhaseDensityModel densityModel,
      UnsplitTransientSolver solver) {
    this(equations, densityModel, solver, 8, 256);
  }

  /**
   * Create an interval preparer with explicit retry limits.
   *
   * @param equations isothermal finite-volume operator
   * @param densityModel side-effect-free pressure-dependent phase density closure
   * @param solver configured nonlinear solver
   * @param maximumHalvings maximum bisection depth for an attempted interval; zero disables retries
   * @param maximumSubsteps maximum locally accepted substeps in one prepared interval
   */
  public TwoFluidUnsplitIntegrator(TwoFluidConservationEquations equations, PhaseDensityModel densityModel,
      UnsplitTransientSolver solver, int maximumHalvings, int maximumSubsteps) {
    if (equations == null || densityModel == null || solver == null || maximumHalvings < 0 || maximumHalvings > 30
        || maximumSubsteps < 1) {
      throw new IllegalArgumentException("Interval preparation requires equations, densities, solver and valid limits");
    }
    this.equations = equations;
    this.densityModel = densityModel;
    this.solver = solver;
    this.maximumHalvings = maximumHalvings;
    this.maximumSubsteps = maximumSubsteps;
  }

  /**
   * Verify the complete requested interval before exposing a candidate for commit.
   *
   * @param acceptedSections immutable-to-this-call accepted section templates
   * @param spatialStep representative cell size in m; actual cell lengths define inventories
   * @param duration requested interval duration in s; its endpoint is the representable sum startTime + duration
   * @param startTime accepted start time in s
   * @param outletPressure external outlet-face pressure in Pa
   * @param outletPressureFixed whether that outlet-face pressure is prescribed
   * @param relativeTolerance independent endpoint and conservative residual verification tolerance
   * @return immutable prepared interval with every accepted midpoint transport ledger
   * @throws IllegalArgumentException for invalid time, geometry, state or density inputs
   * @throws IllegalStateException for an unsupported operator or failed independent endpoint verification
   * @throws IntervalPreparationException if nonlinear retries, substeps or representable time are exhausted
   */
  public synchronized PreparedInterval prepareInterval(TwoFluidSection[] acceptedSections, double spatialStep,
      double duration, double startTime, double outletPressure, boolean outletPressureFixed, double relativeTolerance) {
    return prepareInterval(acceptedSections, spatialStep, duration, startTime, outletPressure, outletPressureFixed,
        relativeTolerance, duration);
  }

  /**
   * Prepare a complete interval with an independent upper bound on the nominal nonlinear time step.
   *
   * <p>
   * The interval is divided into equal nominal steps no larger than the requested bound, subject to representable clock
   * rounding. Nonlinear rejection may subdivide them further. The complete-interval transaction and cumulative
   * conservation gates are identical to
   * {@link #prepareInterval(TwoFluidSection[], double, double, double, double, boolean, double)}. This controls nominal
   * step size, not temporal truncation error.
   * </p>
   *
   * @param acceptedSections accepted section templates
   * @param spatialStep representative cell size in m
   * @param duration requested complete interval in s
   * @param startTime accepted start time in s
   * @param outletPressure external outlet pressure in Pa
   * @param outletPressureFixed whether that pressure is prescribed
   * @param relativeTolerance independent endpoint and complete-interval conservation tolerance
   * @param maximumTimeStep positive finite nominal step bound in s
   * @return immutable complete interval with exact accepted-substep ledgers
   * @throws IllegalArgumentException for invalid inputs or a nominal step count above the configured budget
   * @throws IllegalStateException for unsupported configurations, failed verification or exhausted nonlinear retries
   */
  public synchronized PreparedInterval prepareInterval(TwoFluidSection[] acceptedSections, double spatialStep,
      double duration, double startTime, double outletPressure, boolean outletPressureFixed, double relativeTolerance,
      double maximumTimeStep) {
    double endTime = startTime + duration;
    if (!(duration > 0.0) || !Double.isFinite(duration) || !Double.isFinite(startTime) || !Double.isFinite(endTime)
        || !(endTime > startTime) || !(relativeTolerance > 0.0) || !Double.isFinite(relativeTolerance)
        || !(maximumTimeStep > 0.0) || !Double.isFinite(maximumTimeStep)
        || outletPressureFixed && (!(outletPressure > 0.0) || !Double.isFinite(outletPressure))) {
      throw new IllegalArgumentException("Interval preparation requires finite advancing time, pressure and tolerance");
    }
    double nominalCount = Math.max(1.0, Math.ceil(duration / maximumTimeStep));
    if (!Double.isFinite(nominalCount) || nominalCount > maximumSubsteps) {
      throw new IllegalArgumentException("Nominal time steps exceed the configured accepted-substep budget");
    }
    TwoFluidUnsplitModelAdapter first = new TwoFluidUnsplitModelAdapter(equations, acceptedSections, spatialStep,
        densityModel);
    first.validatePreparedOperator();
    TwoFluidSection[] local = cloneSections(acceptedSections);
    TwoFluidSection[] initial = local;
    List<PreparedStep> prepared = new ArrayList<>();
    Deque<Attempt> pending = new ArrayDeque<>();
    for (int step = (int) nominalCount; step > 0; step--) {
      double endpoint = step == (int) nominalCount ? endTime
          : startTime + (endTime - startTime) * (step / nominalCount);
      double beginning = startTime + (endTime - startTime) * ((step - 1.0) / nominalCount);
      if (!(endpoint > beginning)) {
        throw new IllegalArgumentException("Nominal time step cannot advance the representable simulation clock");
      }
      pending.push(new Attempt(endpoint, 0));
    }
    double time = startTime;
    int rejected = 0;
    long evaluations = 0;
    while (!pending.isEmpty()) {
      Attempt attempt = pending.pop();
      double dt = attempt.endTime - time;
      if (prepared.size() >= maximumSubsteps) {
        throw new IntervalPreparationException("Accepted substep limit reached", time, dt, prepared.size(), rejected,
            null);
      }
      TwoFluidUnsplitModelAdapter adapter = new TwoFluidUnsplitModelAdapter(equations, local, spatialStep,
          densityModel);
      double[][] state = new double[local.length][];
      double[] pressure = new double[local.length];
      double[] area = new double[local.length];
      for (int cell = 0; cell < local.length; cell++) {
        state[cell] = local[cell].getStateVector();
        pressure[cell] = local[cell].getPressure();
        area[cell] = local[cell].getArea();
      }
      UnsplitTransientSolver.Result candidate = solver.solve(state, pressure, area, dt, time, outletPressure,
          outletPressureFixed, adapter);
      evaluations += candidate.getModelEvaluations();
      if (!candidate.isConverged() || !candidate.isActiveSetStable()) {
        rejected++;
        double middle = time + 0.5 * dt;
        if (attempt.depth >= maximumHalvings || !(middle > time) || !(middle < attempt.endTime)) {
          throw new IntervalPreparationException("Nonlinear subdivision limit or time resolution reached", time, dt,
              prepared.size(), rejected, candidate.getTerminationReason());
        }
        pending.push(new Attempt(attempt.endTime, attempt.depth + 1));
        pending.push(new Attempt(middle, attempt.depth + 1));
        continue;
      }
      // Configuration, density and independent verification exceptions fail immediately; they are not retry signals.
      PreparedStep step = adapter.prepareStep(candidate, dt, time, outletPressure, outletPressureFixed,
          relativeTolerance);
      prepared.add(step);
      local = step.getEndpointSections();
      time = attempt.endTime;
    }
    return new PreparedInterval(prepared, initial, startTime, endTime, rejected, evaluations, relativeTolerance);
  }

  private static final class Attempt {
    private final double endTime;
    private final int depth;

    private Attempt(double endTime, int depth) {
      this.endTime = endTime;
      this.depth = depth;
    }
  }

  /** A failed complete interval; no partially advanced endpoint is published. */
  public static final class IntervalPreparationException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    private final double failureTime;
    private final double attemptedTimeStep;
    private final int preparedSubsteps;
    private final int rejectedAttempts;
    private final UnsplitTransientSolver.TerminationReason terminationReason;

    private IntervalPreparationException(String message, double failureTime, double attemptedTimeStep,
        int preparedSubsteps, int rejectedAttempts, UnsplitTransientSolver.TerminationReason terminationReason) {
      super(message + ": time=" + failureTime + ", dt=" + attemptedTimeStep + ", preparedSubsteps=" + preparedSubsteps
          + ", rejectedAttempts=" + rejectedAttempts + ", reason=" + terminationReason);
      this.failureTime = failureTime;
      this.attemptedTimeStep = attemptedTimeStep;
      this.preparedSubsteps = preparedSubsteps;
      this.rejectedAttempts = rejectedAttempts;
      this.terminationReason = terminationReason;
    }

    /** @return local time of the unsuccessful attempt in s; the caller's clock has not advanced */
    public double getFailureTimeSeconds() {
      return failureTime;
    }

    /** @return duration of the unsuccessful attempt in s */
    public double getAttemptedTimeStepSeconds() {
      return attemptedTimeStep;
    }

    /** @return count of discarded locally prepared substeps */
    public int getPreparedSubsteps() {
      return preparedSubsteps;
    }

    /** @return count of rejected nonlinear attempts */
    public int getRejectedAttempts() {
      return rejectedAttempts;
    }

    /** @return final nonlinear rejection reason, or null for an accepted-substep budget failure */
    public UnsplitTransientSolver.TerminationReason getTerminationReason() {
      return terminationReason;
    }
  }

  /** Immutable complete interval with exact accepted-substep phase transport. */
  public static final class PreparedInterval implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<PreparedStep> substeps;
    private final double startTime;
    private final double endTime;
    private final int rejectedAttempts;
    private final long modelEvaluations;
    private final double[][] phaseMassFaceTransferKg;
    private final double[][] phaseMassSourceTransferKg;
    private final double maximumScaledResidual;

    private PreparedInterval(List<PreparedStep> steps, TwoFluidSection[] initial, double startTime, double endTime,
        int rejectedAttempts, long modelEvaluations, double relativeTolerance) {
      this.substeps = Collections.unmodifiableList(new ArrayList<>(steps));
      this.startTime = startTime;
      this.endTime = endTime;
      this.rejectedAttempts = rejectedAttempts;
      this.modelEvaluations = modelEvaluations;
      TwoFluidSection[] endpoint = steps.get(steps.size() - 1).getEndpointSections();
      phaseMassFaceTransferKg = new double[endpoint.length + 1][3];
      phaseMassSourceTransferKg = new double[endpoint.length][3];
      double[][] momentumChanges = new double[endpoint.length][3];
      for (PreparedStep step : steps) {
        double dt = step.getTimeStepSeconds();
        double[][] faces = step.getMidpointEvaluation().getPhaseMassFaceFluxes();
        double[][] sources = step.getMidpointEvaluation().getPhaseMassSourcesPerLength();
        double[][] rates = step.getMidpointEvaluation().getRates();
        for (int face = 0; face < faces.length; face++) {
          for (int phase = 0; phase < 3; phase++) {
            phaseMassFaceTransferKg[face][phase] += dt * faces[face][phase];
          }
        }
        for (int cell = 0; cell < endpoint.length; cell++) {
          for (int phase = 0; phase < 3; phase++) {
            phaseMassSourceTransferKg[cell][phase] += dt * sources[cell][phase] * endpoint[cell].getLength();
            momentumChanges[cell][phase] += dt * rates[cell][phase + 3];
          }
        }
      }
      double maximumResidual = 0.0;
      for (int cell = 0; cell < endpoint.length; cell++) {
        double[] before = initial[cell].getStateVector();
        double[] after = endpoint[cell].getStateVector();
        for (int phase = 0; phase < 3; phase++) {
          double massChange = (phaseMassFaceTransferKg[cell][phase] - phaseMassFaceTransferKg[cell + 1][phase]
              + phaseMassSourceTransferKg[cell][phase]) / endpoint[cell].getLength();
          maximumResidual = Math.max(maximumResidual,
              Math.abs(after[phase] - before[phase] - massChange) / Math.max(1.0, Math.abs(before[phase])));
          maximumResidual = Math.max(maximumResidual,
              Math.abs(after[phase + 3] - before[phase + 3] - momentumChanges[cell][phase])
                  / Math.max(1.0, Math.abs(before[phase + 3])));
        }
      }
      double[] massResidual = getMassResidualKg();
      double[] initialMass = steps.get(0).getInitialMassKg();
      for (int phase = 0; phase < 3; phase++) {
        maximumResidual = Math.max(maximumResidual, Math.abs(massResidual[phase]) / Math.max(1.0, initialMass[phase]));
      }
      if (!Double.isFinite(maximumResidual) || maximumResidual > relativeTolerance) {
        throw new IllegalStateException("Complete interval conservation failed: scaled residual=" + maximumResidual
            + ", tolerance=" + relativeTolerance);
      }
      maximumScaledResidual = maximumResidual;
    }

    /** @return unmodifiable defensive list of verified chronological substeps, whose elements are immutable */
    public List<PreparedStep> getSubsteps() {
      return Collections.unmodifiableList(new ArrayList<>(substeps));
    }

    /** @return defensive final conserved/primitive sections; closure diagnostics require an endpoint refresh */
    public TwoFluidSection[] getEndpointSections() {
      return substeps.get(substeps.size() - 1).getEndpointSections();
    }

    /** @return interval start time in s */
    public double getStartTimeSeconds() {
      return startTime;
    }

    /** @return exact requested representable interval endpoint in s; no caller clock is advanced */
    public double getEndTimeSeconds() {
      return endTime;
    }

    /** @return nonlinear attempts rejected before complete interval preparation */
    public int getRejectedAttempts() {
      return rejectedAttempts;
    }

    /** @return nonlinear model evaluations, including rejected attempts but excluding independent verification */
    public long getModelEvaluations() {
      return modelEvaluations;
    }

    /** @return independently verified maximum cumulative cell-conservation and domain phase-mass residual */
    public double getMaximumScaledResidual() {
      return maximumScaledResidual;
    }

    /** @return defensive signed gas/oil/water mass transfers at each face in kg, integrated over accepted substeps */
    public double[][] getPhaseMassFaceTransferKg() {
      return copy(phaseMassFaceTransferKg);
    }

    /** @return defensive gas/oil/water source transfers per cell in kg, including actual cell lengths */
    public double[][] getPhaseMassSourceTransferKg() {
      return copy(phaseMassSourceTransferKg);
    }

    /** @return gas/oil/water inventory residuals in kg against the exact accepted face and source transfers */
    public double[] getMassResidualKg() {
      double[] initial = substeps.get(0).getInitialMassKg();
      double[] residual = substeps.get(substeps.size() - 1).getFinalMassKg();
      for (int phase = 0; phase < 3; phase++) {
        residual[phase] -= initial[phase] + phaseMassFaceTransferKg[0][phase]
            - phaseMassFaceTransferKg[phaseMassFaceTransferKg.length - 1][phase];
        for (double[] source : phaseMassSourceTransferKg) {
          residual[phase] -= source[phase];
        }
      }
      return residual;
    }
  }

  private static TwoFluidSection[] cloneSections(TwoFluidSection[] sections) {
    TwoFluidSection[] copy = new TwoFluidSection[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
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
