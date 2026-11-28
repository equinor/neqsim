package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static neqsim.util.util.ArrayUtils.copyMatrixArray;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker;
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
  private static final double INTERVAL_RESIDUAL_BUDGET_FRACTION = 0.25;
  private static final double MINIMUM_SCALED_NONLINEAR_TOLERANCE = 256.0 * Math.ulp(1.0);
  private final TwoFluidConservationEquations equations;
  private final PhaseDensityModel densityModel;
  private final UnsplitTransientSolver solver;
  private final int maximumHalvings;
  private final int maximumSubsteps;
  private final LagrangianSlugTracker conservativeSlugTracker;
  private boolean pressureInterpolationEnabled;

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
    this(equations, densityModel, solver, maximumHalvings, maximumSubsteps, null);
  }

  /**
   * Create an interval preparer with an optional transactional conservative slug tracker.
   *
   * <p>
   * The tracker is cloned at construction and again for every prepared interval. Its geometry is exposed to the
   * finite-volume face operator for each attempted substep, but it advances only after that substep has passed the
   * nonlinear and independent conservation checks. Rejected attempts therefore leave marker position, age, statistics
   * and probes unchanged. The returned interval owns a defensive copy of the accepted tracker endpoint.
   * </p>
   *
   * @param equations isothermal finite-volume operator
   * @param densityModel side-effect-free pressure-dependent phase density closure
   * @param solver configured nonlinear solver
   * @param maximumHalvings maximum bisection depth for an attempted interval; zero disables retries
   * @param maximumSubsteps maximum locally accepted substeps in one prepared interval
   * @param conservativeSlugTracker conservative Lagrangian marker state, or null for cell-average transport
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The established integrator contract shares one transactional operator and configured solver; only the newly introduced marker state is cloned")
  public TwoFluidUnsplitIntegrator(TwoFluidConservationEquations equations, PhaseDensityModel densityModel,
      UnsplitTransientSolver solver, int maximumHalvings, int maximumSubsteps,
      LagrangianSlugTracker conservativeSlugTracker) {
    if (equations == null || densityModel == null || solver == null || maximumHalvings < 0 || maximumHalvings > 30
        || maximumSubsteps < 1) {
      throw new IllegalArgumentException("Interval preparation requires equations, densities, solver and valid limits");
    }
    if (equations.isConservativeSlugForceIntegrationEnabled() && conservativeSlugTracker == null) {
      throw new IllegalArgumentException("Tracked subcell forces require a conservative Lagrangian marker owner");
    }
    this.equations = equations;
    this.densityModel = densityModel;
    this.solver = solver;
    this.maximumHalvings = maximumHalvings;
    this.maximumSubsteps = maximumSubsteps;
    this.conservativeSlugTracker = conservativeSlugTracker == null ? null
        : SerializationUtils.clone(conservativeSlugTracker);
  }

  /**
   * Select experimental transient pressure interpolation inside each trial's conservative face fluxes.
   *
   * <p>
   * The mobility uses the selected temporal weight times each attempted step, including retries. It vanishes for linear
   * pressure profiles and couples the alternating cell-pressure mode. This is not a retained-face-history scheme or a
   * general hydrostatic well-balancing treatment.
   * </p>
   *
   * @param enabled true to include the interpolation defect; default false
   */
  public synchronized void setPressureInterpolationEnabled(boolean enabled) {
    pressureInterpolationEnabled = enabled;
  }

  /** @return whether transient pressure interpolation is selected */
  public synchronized boolean isPressureInterpolationEnabled() {
    return pressureInterpolationEnabled;
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
   * @return immutable prepared interval with every accepted time-level transport ledger
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
    LagrangianSlugTracker localSlugTracker = conservativeSlugTracker == null ? null
        : SerializationUtils.clone(conservativeSlugTracker);
    synchronizeSlugGeometry(localSlugTracker);
    boolean allowNewtonRestart = localSlugTracker != null && equations.isConservativeSlugForceIntegrationEnabled();
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
            null, null, local, Double.NaN, "");
      }
      double timeWeight = solver
          .getTimeIntegrationMethod() == UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER ? 1.0 : 0.5;
      double interpolationTimeScale = pressureInterpolationEnabled ? timeWeight * dt : 0.0;
      synchronizeSlugGeometry(localSlugTracker);
      TwoFluidUnsplitModelAdapter adapter = new TwoFluidUnsplitModelAdapter(equations, local, spatialStep, densityModel,
          interpolationTimeScale,
          !pressureInterpolationEnabled && !equations.isConservativeSlugForceIntegrationEnabled(), allowNewtonRestart);
      double[][] state = new double[local.length][];
      double[] pressure = new double[local.length];
      double[] area = new double[local.length];
      for (int cell = 0; cell < local.length; cell++) {
        state[cell] = local[cell].getStateVector();
        pressure[cell] = local[cell].getPressure();
        area[cell] = local[cell].getArea();
      }
      double localTolerance = localSolveTolerance(solver.getRelativeTolerance(), relativeTolerance, dt, duration);
      UnsplitTransientSolver.Result candidate = solver.solveWithTolerance(state, pressure, area, dt, time,
          outletPressure, outletPressureFixed, adapter, localTolerance, allowNewtonRestart);
      evaluations += candidate.getModelEvaluations();
      if (!candidate.isConverged() || !candidate.isActiveSetStable()) {
        rejected++;
        double middle = time + 0.5 * dt;
        if (attempt.depth >= maximumHalvings || !(middle > time) || !(middle < attempt.endTime)) {
          throw new IntervalPreparationException("Nonlinear subdivision limit or time resolution reached", time, dt,
              prepared.size(), rejected, candidate.getTerminationReason(), candidate, local, localTolerance,
              adapter.describeRegimeChoices(candidate.getMaximumScaledResidualIndex() < 0 ? -1
                  : candidate.getMaximumScaledResidualIndex() / UnsplitTransientSolver.BLOCK_SIZE));
        }
        pending.push(new Attempt(attempt.endTime, attempt.depth + 1));
        pending.push(new Attempt(middle, attempt.depth + 1));
        continue;
      }
      // Configuration, density and independent verification exceptions fail immediately; they are not retry signals.
      PreparedStep step = adapter.prepareStep(candidate, dt, time, outletPressure, outletPressureFixed, localTolerance);
      prepared.add(step);
      local = step.getEndpointSections();
      if (localSlugTracker != null) {
        localSlugTracker.advanceTimeStep(local, dt);
        synchronizeSlugGeometry(localSlugTracker);
      }
      time = attempt.endTime;
    }
    return new PreparedInterval(prepared, initial, startTime, endTime, rejected, evaluations, relativeTolerance,
        localSlugTracker);
  }

  /**
   * Keep a scaled nonlinear stopping tolerance above double-precision residual noise.
   *
   * <p>
   * The duration-weighted budget remains the preferred tolerance. Its value can fall below the reproducible numerical
   * floor after repeated subdivision of a long interval, where line search cannot distinguish further descent. The
   * configured solver tolerance remains an absolute upper bound, and every accepted endpoint still passes the
   * independent step and complete-interval conservation checks.
   * </p>
   *
   * @param configuredTolerance configured solver tolerance
   * @param verificationTolerance complete-interval verification tolerance
   * @param timeStep attempted substep duration in s
   * @param duration complete interval duration in s
   * @return bounded local nonlinear tolerance
   */
  static double localSolveTolerance(double configuredTolerance, double verificationTolerance, double timeStep,
      double duration) {
    double durationWeighted = INTERVAL_RESIDUAL_BUDGET_FRACTION * verificationTolerance * timeStep / duration;
    return Math.min(configuredTolerance, Math.max(MINIMUM_SCALED_NONLINEAR_TOLERANCE, durationWeighted));
  }

  /** Install the current accepted marker geometry without advancing it. */
  private void synchronizeSlugGeometry(LagrangianSlugTracker tracker) {
    equations.setConservativeSlugs(tracker == null ? null : tracker.getSlugs());
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
        int preparedSubsteps, int rejectedAttempts, UnsplitTransientSolver.TerminationReason terminationReason,
        UnsplitTransientSolver.Result candidate, TwoFluidSection[] acceptedSections, double localTolerance,
        String regimeDetails) {
      super(message + ": time=" + failureTime + ", dt=" + attemptedTimeStep + ", preparedSubsteps=" + preparedSubsteps
          + ", rejectedAttempts=" + rejectedAttempts + ", reason=" + terminationReason
          + (candidate == null ? ""
              : ", iterations=" + candidate.getIterations() + ", evaluations=" + candidate.getModelEvaluations()
                  + ", scaledResidual=" + candidate.getMaximumScaledResidual() + ", localTolerance=" + localTolerance
                  + ", activeSetStable=" + candidate.isActiveSetStable() + ", residualIndex="
                  + candidate.getMaximumScaledResidualIndex() + ", minimumNewtonStep="
                  + candidate.getMinimumAcceptedStepLength() + describeWorstCell(candidate, acceptedSections)
                  + regimeDetails));
      this.failureTime = failureTime;
      this.attemptedTimeStep = attemptedTimeStep;
      this.preparedSubsteps = preparedSubsteps;
      this.rejectedAttempts = rejectedAttempts;
      this.terminationReason = terminationReason;
    }

    /** Describe the conservative state around the largest residual without changing the failed candidate. */
    private static String describeWorstCell(UnsplitTransientSolver.Result candidate,
        TwoFluidSection[] acceptedSections) {
      int index = candidate.getMaximumScaledResidualIndex();
      double[][] state = candidate.getState();
      if (index < 0 || state.length == 0 || index >= state.length * UnsplitTransientSolver.BLOCK_SIZE) {
        return "";
      }
      int cell = index / UnsplitTransientSolver.BLOCK_SIZE;
      int equation = index % UnsplitTransientSolver.BLOCK_SIZE;
      double[] accepted = acceptedSections[cell].getStateVector();
      double[] pressure = candidate.getPressure();
      StringBuilder details = new StringBuilder(", worstCell=").append(cell).append(", equation=").append(equation)
          .append(", inclinationRad=").append(acceptedSections[cell].getInclination()).append(", acceptedRegime=")
          .append(acceptedSections[cell].getFlowRegime()).append(", acceptedOilWaterRegime=")
          .append(acceptedSections[cell].getOilWaterFlowRegime());
      String[] phaseNames = {"Gas", "Oil", "Water"};
      for (int phase = 0; phase < 3; phase++) {
        double acceptedVelocity = accepted[phase] > 0.0 ? accepted[phase + 3] / accepted[phase] : Double.NaN;
        double candidateVelocity = state[cell][phase] > 0.0 ? state[cell][phase + 3] / state[cell][phase] : Double.NaN;
        details.append(", accepted").append(phaseNames[phase]).append("MassPerLength=").append(accepted[phase])
            .append(", candidate").append(phaseNames[phase]).append("MassPerLength=").append(state[cell][phase])
            .append(", accepted").append(phaseNames[phase]).append("Velocity=").append(acceptedVelocity)
            .append(", candidate").append(phaseNames[phase]).append("Velocity=").append(candidateVelocity);
      }
      return details.append(", acceptedPressure=").append(acceptedSections[cell].getPressure())
          .append(", candidatePressure=").append(pressure[cell]).toString();
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
    private final double[][] phaseMassCellTransferKg;
    private final double[][] conservativeFrictionImpulsePerLength;
    private final double[] massResidualKg;
    private final double maximumScaledResidual;
    private final LagrangianSlugTracker acceptedSlugTracker;

    private PreparedInterval(List<PreparedStep> steps, TwoFluidSection[] initial, double startTime, double endTime,
        int rejectedAttempts, long modelEvaluations, double relativeTolerance,
        LagrangianSlugTracker acceptedSlugTracker) {
      this.substeps = Collections.unmodifiableList(new ArrayList<>(steps));
      this.startTime = startTime;
      this.endTime = endTime;
      this.rejectedAttempts = rejectedAttempts;
      this.modelEvaluations = modelEvaluations;
      this.acceptedSlugTracker = acceptedSlugTracker == null ? null : SerializationUtils.clone(acceptedSlugTracker);
      TwoFluidSection[] endpoint = steps.get(steps.size() - 1).getEndpointSections();
      phaseMassFaceTransferKg = new double[endpoint.length + 1][3];
      phaseMassSourceTransferKg = new double[endpoint.length][3];
      phaseMassCellTransferKg = new double[endpoint.length][3];
      conservativeFrictionImpulsePerLength = new double[endpoint.length][3];
      double[][] momentumChanges = new double[endpoint.length][3];
      CompensatedSum[][] faceTransfers = sums(endpoint.length + 1, 3);
      CompensatedSum[][] sourceTransfers = sums(endpoint.length, 3);
      CompensatedSum[][] cellTransfers = sums(endpoint.length, 3);
      CompensatedSum[][] momentumTransfer = sums(endpoint.length, 3);
      CompensatedSum[][] frictionTransfer = sums(endpoint.length, 3);
      CompensatedSum[] domainTransfers = sums(3);
      for (PreparedStep step : steps) {
        double dt = step.getTimeStepSeconds();
        double[][] faces = step.getEvaluation().getPhaseMassFaceFluxes();
        double[][] sources = step.getEvaluation().getPhaseMassSourcesPerLength();
        double[][] rates = step.getEvaluation().getRates();
        double[][] frictionImpulse = step.getConservativeFrictionImpulsePerLength();
        for (int face = 0; face < faces.length; face++) {
          for (int phase = 0; phase < 3; phase++) {
            faceTransfers[face][phase].add(dt * faces[face][phase]);
          }
        }
        for (int cell = 0; cell < endpoint.length; cell++) {
          for (int phase = 0; phase < 3; phase++) {
            double source = dt * sources[cell][phase] * endpoint[cell].getLength();
            sourceTransfers[cell][phase].add(source);
            cellTransfers[cell][phase].add(dt * (faces[cell][phase] - faces[cell + 1][phase]) + source);
            momentumTransfer[cell][phase].add(dt * rates[cell][phase + 3]);
            frictionTransfer[cell][phase].add(frictionImpulse[cell][phase]);
          }
        }
        for (int phase = 0; phase < 3; phase++) {
          double domainTransfer = dt * (faces[0][phase] - faces[faces.length - 1][phase]);
          for (int cell = 0; cell < endpoint.length; cell++) {
            domainTransfer += dt * sources[cell][phase] * endpoint[cell].getLength();
          }
          domainTransfers[phase].add(domainTransfer);
        }
      }
      for (int face = 0; face < phaseMassFaceTransferKg.length; face++) {
        for (int phase = 0; phase < 3; phase++) {
          phaseMassFaceTransferKg[face][phase] = faceTransfers[face][phase].value();
        }
      }
      for (int cell = 0; cell < endpoint.length; cell++) {
        for (int phase = 0; phase < 3; phase++) {
          phaseMassSourceTransferKg[cell][phase] = sourceTransfers[cell][phase].value();
          phaseMassCellTransferKg[cell][phase] = cellTransfers[cell][phase].value();
          momentumChanges[cell][phase] = momentumTransfer[cell][phase].value();
          conservativeFrictionImpulsePerLength[cell][phase] = frictionTransfer[cell][phase].value();
        }
      }
      double maximumResidual = 0.0;
      double maximumCellMassResidual = 0.0;
      double maximumCellMomentumResidual = 0.0;
      double maximumDomainMassResidual = 0.0;
      for (int cell = 0; cell < endpoint.length; cell++) {
        double[] before = initial[cell].getStateVector();
        double[] after = endpoint[cell].getStateVector();
        for (int phase = 0; phase < 3; phase++) {
          double massChange = phaseMassCellTransferKg[cell][phase] / endpoint[cell].getLength();
          double cellMassResidual = Math.abs(after[phase] - before[phase] - massChange)
              / Math.max(1.0, Math.abs(before[phase]));
          double cellMomentumResidual = Math.abs(after[phase + 3] - before[phase + 3] - momentumChanges[cell][phase])
              / Math.max(1.0, Math.abs(before[phase + 3]));
          maximumCellMassResidual = Math.max(maximumCellMassResidual, cellMassResidual);
          maximumCellMomentumResidual = Math.max(maximumCellMomentumResidual, cellMomentumResidual);
          maximumResidual = Math.max(maximumResidual, Math.max(cellMassResidual, cellMomentumResidual));
        }
      }
      double[] initialMass = steps.get(0).getInitialMassKg();
      double[] finalMass = steps.get(steps.size() - 1).getFinalMassKg();
      massResidualKg = new double[3];
      for (int phase = 0; phase < 3; phase++) {
        massResidualKg[phase] = finalMass[phase] - initialMass[phase] - domainTransfers[phase].value();
        double domainMassResidual = Math.abs(massResidualKg[phase]) / Math.max(1.0, initialMass[phase]);
        maximumDomainMassResidual = Math.max(maximumDomainMassResidual, domainMassResidual);
        maximumResidual = Math.max(maximumResidual, domainMassResidual);
      }
      if (!Double.isFinite(maximumResidual) || maximumResidual > relativeTolerance) {
        throw new IllegalStateException("Complete interval conservation failed: scaled residual=" + maximumResidual
            + ", cell mass=" + maximumCellMassResidual + ", cell momentum=" + maximumCellMomentumResidual
            + ", domain mass=" + maximumDomainMassResidual + ", tolerance=" + relativeTolerance);
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
      return copyMatrixArray(phaseMassFaceTransferKg);
    }

    /** @return defensive gas/oil/water source transfers per cell in kg, including actual cell lengths */
    public double[][] getPhaseMassSourceTransferKg() {
      return copyMatrixArray(phaseMassSourceTransferKg);
    }

    /** @return defensive net gas/oil/water transfer into each cell in kg */
    public double[][] getPhaseMassCellTransferKg() {
      return copyMatrixArray(phaseMassCellTransferKg);
    }

    /**
     * Return the accepted passive-friction momentum impulse summed over all prepared substeps.
     *
     * @return defensive gas/oil/water impulse per pipe length in N s/m for every cell
     */
    public double[][] getConservativeFrictionImpulsePerLength() {
      return conservativeFrictionImpulsePerLength == null ? new double[getEndpointSections().length][3]
          : copyMatrixArray(conservativeFrictionImpulsePerLength);
    }

    /** @return gas/oil/water inventory residuals in kg against the exact accepted face and source transfers */
    public double[] getMassResidualKg() {
      return massResidualKg.clone();
    }

    /**
     * Return the accepted conservative marker endpoint.
     *
     * @return defensive tracker copy, or null when the interval used cell-average transport
     */
    public LagrangianSlugTracker getAcceptedSlugTracker() {
      return acceptedSlugTracker == null ? null : SerializationUtils.clone(acceptedSlugTracker);
    }
  }

  private static CompensatedSum[][] sums(int rows, int columns) {
    CompensatedSum[][] result = new CompensatedSum[rows][columns];
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++) {
        result[row][column] = new CompensatedSum();
      }
    }
    return result;
  }

  private static CompensatedSum[] sums(int length) {
    CompensatedSum[] result = new CompensatedSum[length];
    for (int index = 0; index < length; index++) {
      result[index] = new CompensatedSum();
    }
    return result;
  }

  private static TwoFluidSection[] cloneSections(TwoFluidSection[] sections) {
    TwoFluidSection[] copy = new TwoFluidSection[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      copy[cell] = sections[cell].clone();
    }
    return copy;
  }

}
