package neqsim.thermodynamicoperations.flashops;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import Jama.CholeskyDecomposition;
import Jama.Matrix;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optim.linear.NonNegativeConstraint;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.linear.SimplexSolver;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReaction;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionConcentrationBasis;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.EosGeFlashModel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.flashops.reactiveflash.FormulaMatrix;

/**
 * Conservative neutral endpoints qualified against reaction-aware aqueous tangent-plane trials.
 *
 * <p>
 * Reaction extents first remove ionic inventory only when the registered network permits complete neutral
 * recombination. The aqueous grand potential is minimized in the span of those reactions and neutral transfers,
 * normalized by a positive conserved atom amount. This keeps chemical affinities stationary even when a reaction
 * changes the total number of species moles. A separate complete frozen-species flash qualifies the neutral phases. The
 * search is conservative: every aqueous start must converge to a local minimum before aqueous absence can be accepted.
 * Exact-zero ionic products are seeded only by feasible registered reaction extents on an owned trial. A negative or
 * unqualified search returns no endpoint; this helper does not construct the new reactive phase split. It is a
 * multistart numerical qualification, not a proof of global convexity. The thermodynamic formulation follows
 * reaction-aware Gibbs minimization and electrochemical phase stability (Tsanas, de Hemptinne and Mougin, Chemical
 * Engineering Science 248 (2022), 117174, doi:10.1016/j.ces.2021.117174). The particular conserved normalization,
 * registered-network basis and safeguarded minimizer are used here to preserve NeqSim's configured reaction invariants.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
final class ReactiveAqueousBoundary {
  /**
   * Attempts a qualified neutral endpoint without modifying the supplied fluid.
   *
   * @param fluid reactive fluid at a possible last-aqueous boundary
   * @return qualified endpoint and stability evidence, or null when it cannot be established
   */
  static Result trySolve(SystemInterface fluid) {
    checkInterrupted();
    try {
      if (fluid instanceof EosGeFlashModel || fluid.getChemicalReactionOperations() == null || !fluid.hasIons()
          || fluid.doSolidPhaseCheck() || fluid.isForcePhaseTypes() || fluid.isMultiphaseWaxCheck()
          || fluid.getChemicalReactionConcentrationBasis() != ChemicalReactionConcentrationBasis.MOLE_FRACTION) {
        return null;
      }
      ReactiveAqueousBoundary boundary = new ReactiveAqueousBoundary(fluid);
      SystemInterface endpoint = boundary.endpoint(boundary.recombine());
      double[] distances = boundary.trialDistances(endpoint);
      for (double distance : distances) {
        if (!Double.isFinite(distance) || distance < -1.0e-8) {
          return null;
        }
      }
      endpoint.isChemicalSystem(true);
      endpoint.getChemicalReactionOperations().reinitialize();
      return new Result(endpoint, distances, boundary.minimumNeutralTrialDistance);
    } catch (CancellationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      return null;
    }
  }

  /** Reject cancellation before building or committing a trial. */
  private static void checkInterrupted() {
    if (Thread.currentThread().isInterrupted()) {
      throw new CancellationException("Reactive aqueous boundary interrupted");
    }
  }

  /**
   * Qualified owned endpoint and its independent neutral/reactive phase-stability evidence.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  static final class Result {
    private final SystemInterface fluid;
    private final double[] distances;
    private final double neutralDistance;

    /**
     * Stores a qualified endpoint.
     *
     * @param fluid owned neutral fluid
     * @param distances reactive aqueous trial distances per trial mole
     * @param neutralDistance minimum frozen-species fluid trial distance
     */
    private Result(SystemInterface fluid, double[] distances, double neutralDistance) {
      this.fluid = fluid;
      this.distances = distances.clone();
      this.neutralDistance = neutralDistance;
    }

    /** @return qualified owned neutral endpoint */
    SystemInterface getFluid() {
      return fluid;
    }

    /** @return defensive copy of the aqueous reactive stability distances */
    double[] getTrialDistances() {
      return distances.clone();
    }

    /** @return minimum distance from the complete neutral fluid flash */
    double getNeutralDistance() {
      return neutralDistance;
    }
  }

  /** Owned source, preserving its configured reaction network. */
  private final SystemInterface current;
  /** Independent frozen-species phase-stability evidence. */
  private double minimumNeutralTrialDistance;
  /** Registered stoichiometry, with species rows and reaction columns. */
  private double[][] nu;
  /** Positive conserved atom-count normalization. */
  private double[] q;
  /** Reference offsets mapping reaction activities to fugacity chemical potentials. */
  private double[] h;
  /** Reaction-consistent reservoir chemical potentials. */
  private double[] p;
  /** Number of registered components. */
  private int nc;
  /** Number of registered reactions. */
  private int nr;
  /** Ionic species, including zero-amount spectators. */
  private List<Integer> ions = new ArrayList<>();
  /** Molecular species present in the neutral reservoir. */
  private List<Integer> neutrals = new ArrayList<>();
  /** Owned aqueous model used for all derivative evaluations. */
  private SystemInterface phaseTemplate;
  /** Independent feasible directions at fixed conserved normalization. */
  private double[][] basis;

  /**
   * Builds the registered reaction span and a conserved positive normalization.
   *
   * @param fluid reactive source
   */
  private ReactiveAqueousBoundary(SystemInterface fluid) {
    current = fluid.clone();
    nc = fluid.getNumberOfComponents();
    ArrayList<ChemicalReaction> reactions = fluid.getChemicalReactionOperations().getReactionList()
        .getChemicalReactionList();
    nr = reactions.size();
    if (nr == 0) {
      throw new IllegalStateException("No registered reactions for an aqueous boundary");
    }
    nu = new double[nc][nr];
    for (int r = 0; r < nr; r++) {
      for (int k = 0; k < reactions.get(r).getNames().length; k++) {
        nu[fluid.getComponent(reactions.get(r).getNames()[k]).getComponentNumber()][r] = reactions.get(r)
            .getStocCoefs()[k];
      }
    }
    for (int i = 0; i < nc; i++) {
      if (FlashPhaseConstraints.isIon(fluid.getComponent(i))) {
        ions.add(i);
      } else {
        neutrals.add(i);
      }
    }
    FormulaMatrix formula = new FormulaMatrix(fluid);
    q = new double[nc];
    for (int i = 0; i < nc; i++) {
      double[] unit = new double[nc];
      unit[i] = 1;
      double[] atoms = formula.computeElementVector(unit);
      for (int e = 0; e < atoms.length; e++) {
        if (!formula.getElementNames()[e].equalsIgnoreCase("charge")) {
          q[i] += Math.abs(atoms[e]);
        }
      }
      if (!(q[i] > 0)) {
        throw new IllegalStateException("No positive conserved normalization for " + fluid.getComponent(i).getName());
      }
    }

    for (int r = 0; r < nr; r++) {
      double[] reaction = new double[nc];
      for (int i = 0; i < nc; i++) {
        reaction[i] = nu[i][r];
      }
      for (double invariant : formula.computeElementVector(reaction)) {
        if (!Double.isFinite(invariant) || Math.abs(invariant) > 1.0e-10) {
          throw new IllegalStateException("Registered reaction violates an elemental or charge invariant");
        }
      }
    }
  }

  /**
   * Finds feasible registered reaction extents that eliminate all ions without deleting spectators.
   *
   * @return conservative neutral inventory
   */
  private double[] recombine() {
    double[] n = FluidEquilibriumChecks.inventory(current);
    double scale = 0;
    for (int i : ions) {
      scale = Math.max(scale, n[i]);
    }
    if (scale == 0) {
      return n;
    }
    List<LinearConstraint> constraints = new ArrayList<>();
    for (int i = 0; i < nc; i++) {
      double rownorm = 0;
      for (int r = 0; r < nr; r++) {
        rownorm += Math.abs(nu[i][r]);
      }
      if (rownorm == 0) {
        if (ions.contains(i) && n[i] > 0) {
          throw new IllegalStateException("spectator ion cannot recombine");
        }
        continue;
      }
      double normalizer = Math.max(n[i], scale);
      double[] row = new double[nr];
      for (int r = 0; r < nr; r++) {
        row[r] = nu[i][r] * scale / normalizer;
      }
      constraints
          .add(new LinearConstraint(row, ions.contains(i) ? Relationship.EQ : Relationship.GEQ, -n[i] / normalizer));
    }
    double[] xi = new SimplexSolver(1e-12, 10, 1e-16)
        .optimize(new MaxIter(1000), new LinearObjectiveFunction(new double[nr], 0),
            new LinearConstraintSet(constraints), GoalType.MINIMIZE, new NonNegativeConstraint(false))
        .getPoint();
    double[] neutral = n.clone();
    for (int i = 0; i < nc; i++) {
      double magnitude = Math.abs(n[i]);
      for (int r = 0; r < nr; r++) {
        double increment = nu[i][r] * scale * xi[r];
        neutral[i] += increment;
        magnitude += Math.abs(increment);
      }
      if (ions.contains(i)) {
        if (Math.abs(neutral[i]) > 128 * Math.ulp(magnitude)) {
          throw new IllegalStateException("inexact neutralization " + neutral[i]);
        }
        neutral[i] = 0;
      } else if (neutral[i] < 0) {
        throw new IllegalStateException("negative neutral trial");
      }
    }
    return neutral;
  }

  /**
   * Qualifies all molecular phases at the recombined inventory.
   *
   * @param n neutral inventory
   * @return owned, physically checked neutral endpoint
   */
  private SystemInterface endpoint(double[] n) {
    SystemInterface result = current.clone();
    result.setMolarFlowRates(n);
    result.isChemicalSystem(false);
    TPflash flash = new TPflash(result);
    flash.run();
    FluidEquilibriumChecks.validate(result, n);
    minimumNeutralTrialDistance = Double.POSITIVE_INFINITY;
    for (double distance : flash.getLastTangentPlaneDistances()) {
      if (Double.isFinite(distance)) {
        minimumNeutralTrialDistance = Math.min(minimumNeutralTrialDistance, distance);
      }
    }
    if (!Double.isFinite(minimumNeutralTrialDistance) || minimumNeutralTrialDistance < -1.0e-8) {
      throw new IllegalStateException("Neutral endpoint lacks nonnegative phase-stability evidence");
    }
    if (result.hasPhaseType("aqueous")) {
      throw new IllegalStateException("Neutral endpoint has aqueous phase");
    }
    return result;
  }

  /**
   * Extends molecular reservoir potentials through reaction equilibrium and builds feasible trial directions.
   *
   * @param endpoint qualified molecular equilibrium
   */
  private void potentials(SystemInterface endpoint) {
    phaseTemplate = current.hasPhaseType("aqueous") ? current.phaseToSystem("aqueous") : current.clone();
    phaseTemplate.setNumberOfPhases(1);
    phaseTemplate.setPhaseType(0, PhaseType.AQUEOUS);
    phaseTemplate.setBeta(0, 1);
    phaseTemplate.isChemicalSystem(false);
    double[] templateInventory = FluidEquilibriumChecks.inventory(phaseTemplate);
    double templateTotal = 0.0;
    for (double amount : templateInventory) {
      templateTotal += amount;
    }
    for (int i = 0; i < nc; i++) {
      phaseTemplate.getPhase(0).getComponent(i).setx(templateInventory[i] / templateTotal);
      phaseTemplate.getPhase(0).getComponent(i).setNumberOfMolesInPhase(templateInventory[i]);
    }
    phaseTemplate.init(1);
    phaseTemplate.getChemicalReactionOperations().reinitialize();
    PhaseInterface phase = phaseTemplate.getPhase(0);
    int water = phase.getComponent("water").getComponentNumber();
    h = new double[nc];
    p = new double[nc];
    for (int i = 0; i < nc; i++) {
      boolean reactive = false;
      for (int r = 0; r < nr; r++) {
        reactive |= nu[i][r] != 0;
      }
      if (reactive) {
        if (!phase.getComponent(i).calcActivity()) {
          throw new IllegalStateException("non-fugacity reaction activity " + phase.getComponent(i).getName());
        }
        double refPhi = phase.getComponent(i).getLogFugacityCoefficient() - phase.getLogActivityCoefficient(i, water);
        h[i] = refPhi - phase.getComponent(i).getReferencePotential()
            / (neqsim.thermo.ThermodynamicConstantsInterface.R * phase.getTemperature());
      }
      if (!ions.contains(i)) {
        if (endpoint.getComponent(i).getNumberOfmoles() <= 0) {
          if (reactive) {
            throw new IllegalStateException("Cannot qualify an absent reactive molecular reservoir");
          }
          neutrals.remove(Integer.valueOf(i));
          continue;
        }
        int reservoir = 0;
        for (int candidate = 1; candidate < endpoint.getNumberOfPhases(); candidate++) {
          if (endpoint.getPhase(candidate).getComponent(i).getx() > endpoint.getPhase(reservoir).getComponent(i)
              .getx()) {
            reservoir = candidate;
          }
        }
        p[i] = Math.log(endpoint.getPhase(reservoir).getComponent(i).getx())
            + endpoint.getPhase(reservoir).getComponent(i).getLogFugacityCoefficient() - h[i];
        if (!Double.isFinite(p[i]) || !Double.isFinite(h[i])) {
          throw new IllegalStateException("Nonfinite molecular reservoir potential");
        }
      }
    }
    double[][] a = new double[nr][ions.size()];
    double[] rhs = new double[nr];
    for (int r = 0; r < nr; r++) {
      for (int k = 0; k < ions.size(); k++) {
        a[r][k] = nu[ions.get(k)][r];
      }
      for (int i : neutrals) {
        rhs[r] -= nu[i][r] * p[i];
      }
    }
    double[] ionic = new SingularValueDecomposition(new Array2DRowRealMatrix(a)).getSolver()
        .solve(new ArrayRealVector(rhs)).toArray();
    for (int k = 0; k < ions.size(); k++) {
      p[ions.get(k)] = ionic[k];
    }
    for (int r = 0; r < nr; r++) {
      double error = 0;
      for (int i = 0; i < nc; i++) {
        error += nu[i][r] * p[i];
      }
      if (Math.abs(error) > 1e-9) {
        throw new IllegalStateException("inconsistent reservoir dual " + error);
      }
    }
    List<double[]> columns = new ArrayList<>();
    int pivot = water;
    if (!neutrals.contains(pivot)) {
      throw new IllegalStateException("No water reservoir for aqueous stability");
    }
    for (int i : neutrals) {
      if (i != pivot) {
        double[] col = new double[nc];
        col[i] = 1;
        col[pivot] = -q[i] / q[pivot];
        addIndependent(columns, col);
      }
    }
    for (int r = 0; r < nr; r++) {
      double[] col = new double[nc];
      for (int i = 0; i < nc; i++) {
        col[i] = nu[i][r];
      }
      addIndependent(columns, col);
    }
    basis = new double[nc][columns.size()];
    for (int k = 0; k < columns.size(); k++) {
      for (int i = 0; i < nc; i++) {
        basis[i][k] = columns.get(k)[i];
      }
    }
  }

  /**
   * Appends an independent orthonormal direction, keeping molecular transfers before trace-ion reactions.
   *
   * @param columns existing orthonormal columns
   * @param col candidate direction, modified in place
   */
  private void addIndependent(List<double[]> columns, double[] col) {
    for (double[] existing : columns) {
      double d = dot(existing, col);
      for (int i = 0; i < nc; i++) {
        col[i] -= d * existing[i];
      }
    }
    double norm = Math.sqrt(dot(col, col));
    if (norm < 1e-10) {
      return;
    }
    for (int i = 0; i < nc; i++) {
      col[i] /= norm;
    }
    columns.add(col);
  }

  /**
   * Computes a vector inner product.
   *
   * @param x first vector
   * @param y second vector
   * @return inner product
   */
  private static double dot(double[] x, double[] y) {
    double s = 0;
    for (int i = 0; i < x.length; i++) {
      s += x[i] * y[i];
    }
    return s;
  }

  /**
   * Evaluates one owned aqueous-root trial with a consistent amount and composition basis.
   *
   * <p>
   * Molecular-rich starts may converge to a gas-like root. Retaining such virtual trials enlarges the search domain:
   * they can reject a boundary but are never committed as ionic material phases. The accepted endpoint is separately
   * qualified and has exactly zero ionic inventory. Every trial still has to solve the actual pressure equation on a
   * mechanically stable root.
   * </p>
   *
   * @param n species amounts
   * @param init thermodynamic derivative level
   * @return evaluated trial
   */
  private SystemInterface phase(double[] n, int init) {
    // The evaluator owns this one-phase workspace; no trial phase is returned or committed to the caller.
    // Reusing it avoids rebuilding configured reaction helpers for each line-search evaluation.
    SystemInterface trial = phaseTemplate;
    trial.setMolarFlowRates(n);
    trial.isChemicalSystem(false);
    trial.setNumberOfPhases(1);
    trial.setPhaseType(0, PhaseType.AQUEOUS);
    trial.setBeta(0, 1);
    double total = 0;
    for (double amount : n) {
      if (!Double.isFinite(amount) || amount < 0.0) {
        throw new IllegalStateException("Invalid aqueous trial amount");
      }
      total += amount;
    }
    if (!(total > 0.0) || !Double.isFinite(total)) {
      throw new IllegalStateException("Invalid aqueous trial total");
    }
    for (int i = 0; i < nc; i++) {
      trial.getPhase(0).getComponent(i).setx(n[i] / total);
      trial.getPhase(0).getComponent(i).setNumberOfMolesInPhase(n[i]);
    }
    trial.init(init);
    FluidEquilibriumChecks.validatePressureRoot(trial.getPhase(0));
    return trial;
  }

  /**
   * Evaluates chemical-potential distances in a common reaction activity convention.
   *
   * @param trial evaluated aqueous trial
   * @param n trial species amounts
   * @return full grand-potential gradient
   */
  private double[] gradient(SystemInterface trial, double[] n) {
    double[] g = new double[nc];
    double total = 0;
    for (double amount : n) {
      total += amount;
    }
    for (int i = 0; i < nc; i++) {
      if (n[i] > 0) {
        g[i] = Math.log(n[i] / total) + trial.getPhase(0).getComponent(i).getLogFugacityCoefficient() - h[i] - p[i];
      }
    }
    return g;
  }

  /**
   * Provides a conservative interior trial when ionic products are exactly absent from the molecular reservoir.
   *
   * <p>
   * Only registered reaction extents may introduce species. The small common lower bound is an initialization target,
   * not a physical inventory floor; the subsequent minimizer freely reduces every amount towards its boundary.
   * </p>
   *
   * @param seed nonnegative trial support
   * @return trial with positive active reaction support, or the unchanged seed when already interior
   */
  private double[] interiorSeed(double[] seed) {
    boolean required = false;
    for (int i = 0; i < nc; i++) {
      for (int r = 0; r < nr; r++) {
        required |= nu[i][r] != 0.0 && seed[i] == 0.0;
      }
    }
    if (!required) {
      return seed.clone();
    }
    double target = Double.POSITIVE_INFINITY;
    for (int i : neutrals) {
      if (seed[i] > 0.0) {
        target = Math.min(target, seed[i] * 1.0e-8);
      }
    }
    if (!(target > 0.0) || !Double.isFinite(target)) {
      throw new IllegalStateException("No feasible positive molecular support for a reactive aqueous trial");
    }
    List<LinearConstraint> constraints = new ArrayList<>();
    for (int i = 0; i < nc; i++) {
      double norm = 0.0;
      for (int r = 0; r < nr; r++) {
        norm += Math.abs(nu[i][r]);
      }
      if (norm > 0.0) {
        constraints.add(new LinearConstraint(nu[i], Relationship.GEQ, (target - seed[i]) / target));
      }
    }
    double[] extent = new SimplexSolver(1.0e-12, 10, 1.0e-16)
        .optimize(new MaxIter(1000), new LinearObjectiveFunction(new double[nr], 0.0),
            new LinearConstraintSet(constraints), GoalType.MINIMIZE, new NonNegativeConstraint(false))
        .getPoint();
    double[] result = seed.clone();
    for (int i = 0; i < nc; i++) {
      for (int r = 0; r < nr; r++) {
        result[i] += nu[i][r] * target * extent[r];
      }
      if (!Double.isFinite(result[i]) || result[i] < 0.0) {
        throw new IllegalStateException("Invalid conservative reactive interior seed");
      }
    }
    return result;
  }

  /**
   * Minimizes aqueous grand potential in the complete registered reaction and molecular-transfer span.
   *
   * @param seed feasible positive trial support
   * @return stationary grand-potential distance per trial mole
   */
  private double minimize(double[] seed) {
    double[] n = interiorSeed(seed);
    double[][] ionBasis = new double[ions.size()][nr];
    double[] seedIons = new double[ions.size()];
    for (int k = 0; k < ions.size(); k++) {
      seedIons[k] = n[ions.get(k)];
      for (int r = 0; r < nr; r++) {
        ionBasis[k][r] = nu[ions.get(k)][r];
      }
    }
    RealMatrix ni = new Array2DRowRealMatrix(ionBasis);
    double[] closed = ni.operate(new SingularValueDecomposition(ni).getSolver().solve(new ArrayRealVector(seedIons)))
        .toArray();
    for (int k = 0; k < ions.size(); k++) {
      n[ions.get(k)] = closed[k];
    }
    for (int i = 0; i < nc; i++) {
      boolean active = neutrals.contains(i);
      for (int r = 0; r < nr; r++) {
        active |= nu[i][r] != 0.0;
      }
      if (!Double.isFinite(n[i]) || n[i] < 0.0 || active && n[i] == 0.0) {
        throw new IllegalStateException("Aqueous trial lacks feasible positive reaction support");
      }
    }
    double normalizer = dot(q, n);
    if (!(normalizer > 0.0) || !Double.isFinite(normalizer)) {
      throw new IllegalStateException("Invalid conserved trial normalization");
    }
    for (int i = 0; i < nc; i++) {
      n[i] /= normalizer;
    }
    int nv = basis[0].length;
    Matrix coordinates = new Matrix(basis);
    for (int iteration = 0; iteration < 100; iteration++) {
      checkInterrupted();
      SystemInterface trial = phase(n, 3);
      double[] g = gradient(trial, n);
      double merit = dot(n, g);
      double[] reduced = coordinates.transpose().times(new Matrix(g, nc)).getColumnPackedCopy();
      double error = 0;
      for (double v : reduced) {
        error = Math.max(error, Math.abs(v));
      }
      if (!Double.isFinite(error) || !Double.isFinite(merit)) {
        throw new IllegalStateException("Nonfinite aqueous stability residual");
      }
      double total = 0;
      for (double amount : n) {
        total += amount;
      }
      double[][] hessian = new double[nc][nc];
      for (int i = 0; i < nc; i++) {
        if (n[i] > 0) {
          for (int j = 0; j < nc; j++) {
            if (n[j] > 0) {
              hessian[i][j] = (i == j ? 1 / n[i] : 0) - 1 / total + trial.getPhase(0).getComponent(i).getdfugdn(j);
            }
          }
        }
      }
      Matrix projected = coordinates.transpose().times(new Matrix(hessian)).times(coordinates);
      if (error < 1e-8) {
        double[][] curvature = new double[nv][nv];
        for (int i = 0; i < nv; i++) {
          for (int j = 0; j < nv; j++) {
            double scaling = Math.sqrt(Math.abs(projected.get(i, i))) * Math.sqrt(Math.abs(projected.get(j, j)));
            curvature[i][j] = 0.5 * (projected.get(i, j) + projected.get(j, i)) / scaling;
          }
        }
        if (!new Matrix(curvature).chol().isSPD()) {
          throw new IllegalStateException("Aqueous stationary trial is not a qualified local minimum");
        }
        // Minimize on the conserved basis, then compare with the existing molar stability tolerance.
        return merit / total;
      }
      double[] delta = null;
      for (int damping = 0; damping < 18; damping++) {
        double lambda = damping == 0 ? 0 : Math.pow(10, damping - 11);
        double[][] regular = new double[nv][nv];
        for (int i = 0; i < nv; i++) {
          for (int j = 0; j < nv; j++) {
            regular[i][j] = .5 * (projected.get(i, j) + projected.get(j, i));
          }
        }
        for (int i = 0; i < nv; i++) {
          regular[i][i] += lambda * Math.max(Math.abs(projected.get(i, i)), 1e-12);
        }
        CholeskyDecomposition chol = new Matrix(regular).chol();
        if (!chol.isSPD()) {
          continue;
        }
        delta = coordinates.times(chol.solve(new Matrix(reduced, nv))).times(-1).getColumnPackedCopy();
        if (dot(g, delta) < 0) {
          break;
        }
        delta = null;
      }
      if (delta == null) {
        throw new IllegalStateException("No descent direction");
      }
      double alpha = 1;
      for (int i = 0; i < nc; i++) {
        if (delta[i] < 0) {
          alpha = Math.min(alpha, -.95 * n[i] / delta[i]);
        }
      }
      double evaluationRoundoff = 0;
      for (int i = 0; i < nc; i++) {
        if (n[i] > 0) {
          evaluationRoundoff += n[i] * (1 + Math.abs(Math.log(n[i] / total))
              + Math.abs(trial.getPhase(0).getComponent(i).getLogFugacityCoefficient()) + Math.abs(h[i])
              + Math.abs(p[i]));
        }
      }
      evaluationRoundoff *= 64 * Math.ulp(1.0);
      boolean accepted = false;
      for (int ls = 0; ls < 30; ls++) {
        checkInterrupted();
        double[] candidate = n.clone();
        for (int i = 0; i < nc; i++) {
          candidate[i] += alpha * delta[i];
        }
        SystemInterface candidatePhase = phase(candidate, 1);
        double next = dot(candidate, gradient(candidatePhase, candidate));
        boolean armijo = Double.isFinite(next) && next <= merit + 1e-4 * alpha * dot(g, delta);
        if (!armijo && Double.isFinite(next) && next <= merit + evaluationRoundoff
            && Math.abs(alpha * dot(g, delta)) <= evaluationRoundoff) {
          double[] nextReduced = coordinates.transpose().times(new Matrix(gradient(candidatePhase, candidate), nc))
              .getColumnPackedCopy();
          double nextError = 0;
          for (double v : nextReduced) {
            nextError = Math.max(nextError, Math.abs(v));
          }
          armijo = nextError < error * (1 - 1e-4 * alpha);
        }
        if (armijo) {
          n = candidate;
          accepted = true;
          break;
        }
        alpha *= .5;
      }
      if (!accepted) {
        throw new IllegalStateException("TPD line search failed");
      }
    }
    throw new IllegalStateException("TPD iteration limit");
  }

  /**
   * Requires independent converged aqueous minima from the old composition and each present molecular-rich start.
   *
   * @param endpoint qualified neutral equilibrium
   * @return minimum distances for every start
   */
  private double[] trialDistances(SystemInterface endpoint) {
    potentials(endpoint);
    double[] oldAqueous = FluidEquilibriumChecks
        .inventory(current.hasPhaseType("aqueous") ? current.phaseToSystem("aqueous") : current);
    double normalizer = dot(q, oldAqueous);
    for (int i = 0; i < nc; i++) {
      oldAqueous[i] /= normalizer;
    }
    List<Double> distances = new ArrayList<>();
    distances.add(minimize(oldAqueous));
    for (int pure : neutrals) {
      double[] seed = oldAqueous.clone();
      for (int i = 0; i < nc; i++) {
        seed[i] *= .01;
      }
      seed[pure] += .99 / q[pure];
      distances.add(minimize(seed));
    }
    double[] result = new double[distances.size()];
    for (int index = 0; index < result.length; index++) {
      result[index] = distances.get(index);
    }
    return result;
  }
}
