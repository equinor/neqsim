package neqsim.thermodynamicoperations.flashops;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * Newton-Raphson solver for a fixed fluid-phase TP flash using Michelsen's amount-variable formulation.
 *
 * <p>
 * For more than two phases, independent amounts occupy every phase except the last logical phase, whose amounts follow
 * from complete species conservation. The reduced Hessian contains each independent phase's curvature and the common
 * reference-phase curvature. The last phase must retain a representable positive amount of every free species; this
 * local solver rejects a disappearing phase and leaves active-set changes to the outer flash.
 * </p>
 *
 * <p>
 * In the two-phase path, each variable stores the initially smaller allowed-phase amount (Michelsen 1982b). Residuals
 * equate chemical potentials only for species allowed in both phases. Species restricted to one phase retain their
 * complete feed inventory there, but remain in phase properties and the Gibbs objective. Jacobian: Hessian of the
 * reduced Gibbs energy Q(u). Uses a bounded Armijo backtracking line search on Q (Michelsen &amp; Mollerup 2007, Ch.
 * 12). Rejected steps leave the last accepted split available for the caller's fallback; this local solver does not
 * certify global phase stability.
 * </p>
 *
 * <p>
 * Both allowed-phase amounts must be positive in floating-point arithmetic. No absolute composition floor excludes a
 * representable trace species. Storing the smaller amount avoids subtractive loss of a trace from its much larger feed.
 * Coordinate signs multiply the corresponding residual and both Hessian indices; this is an exact linear change of
 * variables in the same reduced Gibbs objective. The orientation stays fixed during each local Newton solve.
 * </p>
 *
 * <p>
 * Numerical work storage:
 * </p>
 * <ul>
 * <li>EJML dense linear solver for the reduced Gibbs Hessian</li>
 * <li>Reused numerical work matrices and amount buffers while the active dimension is unchanged</li>
 * </ul>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SysNewtonRhapsonTPflash implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Number of independent allowed-phase component amounts. */
  int neq = 0;

  /** Newton iteration counter. */
  int iter = 0;

  /** Reference to the thermodynamic system being solved. */
  SystemInterface system;

  /** Number of components in the system. */
  int numberOfComponents;

  /** Number of phases in the current fixed active set. */
  private int activePhases;

  /** Logical phase of each independent amount in a multiphase active set. */
  private int[] variablePhases;

  /** Cached feed compositions (constant during flash). */
  private double[] z;

  /** Component index belonging to each freely partitioning Newton variable. */
  private int[] variableComponents;

  /** Whether each component can partition between the active phases. */
  private boolean[] freeComponent;

  /** Whether a two-phase free component uses its second-phase amount as the independent coordinate. */
  private boolean[] secondPhaseCoordinate;

  /** Phase admissibility captured before compositions are perturbed. */
  private FlashPhaseConstraints phaseConstraints;

  // Reused EJML matrices for Newton corrections
  /** Jacobian matrix (Hessian of Q). */
  private DMatrixRMaj jacMatrix;

  /** Residual vector (gradient of Q). */
  private DMatrixRMaj fvecVector;

  /** Newton step vector. */
  private DMatrixRMaj dxVector;

  /** Work copy of Jacobian for LU decomposition. */
  private DMatrixRMaj jacWork;

  /** Independent smaller-phase amounts for two phases; full phase-major amounts for more phases. */
  private double[] uVector;

  /** Trial u-vector used by the Armijo line search. */
  private double[] uTrialVector;

  /** Pre-allocated EJML LU solver. */
  private transient LinearSolverDense<DMatrixRMaj> linearSolver;

  /**
   * Constructor for sysNewtonRhapsonTPflash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param numberOfPhases a int
   * @param numberOfComponents a int
   */
  public SysNewtonRhapsonTPflash(SystemInterface system, int numberOfPhases, int numberOfComponents) {
    this.system = system;
    this.numberOfComponents = numberOfComponents;
    z = new double[numberOfComponents];
    variableComponents = new int[numberOfComponents];
    freeComponent = new boolean[numberOfComponents];
    secondPhaseCoordinate = new boolean[numberOfComponents];
    uVector = new double[numberOfComponents];
    uTrialVector = new double[numberOfComponents];
    refreshActiveVariables();
    setu();
  }

  /**
   * Number of components this solver was sized for. Used by the caller to detect when the cached instance is stale
   * (e.g. after solid precipitation removed a component).
   *
   * @return number of components
   */
  public int getNumberOfComponents() {
    return numberOfComponents;
  }

  /**
   * Re-binds this solver to an updated system reference and refreshes the feed and allowed phase participation.
   * Numerical work arrays are retained when the number of freely partitioning species is unchanged. The component count
   * must match; call {@link #getNumberOfComponents()} first.
   *
   * @param system system to solve
   */
  public void setSystem(SystemInterface system) {
    this.system = system;
    refreshActiveVariables();
    setu();
  }

  /**
   * Refreshes the feed and phase constraints, resizing numerical work arrays only if the active dimension changes.
   *
   * @throws IllegalStateException if an occupied species has no admissible active phase
   */
  private void refreshActiveVariables() {
    activePhases = system.getNumberOfPhases();
    phaseConstraints = FlashPhaseConstraints.capture(system);
    if (activePhases > 2) {
      refreshMultiphaseVariables();
      return;
    }
    if (uVector.length != numberOfComponents) {
      uVector = new double[numberOfComponents];
      uTrialVector = new double[numberOfComponents];
    }
    if (!phaseConstraints.supportsTwoPhaseSplit()) {
      throw new IllegalStateException("Newton active set requires two phases and an unambiguous ionic host");
    }
    if (variableComponents == null || variableComponents.length != numberOfComponents) {
      variableComponents = new int[numberOfComponents];
      freeComponent = new boolean[numberOfComponents];
    }
    if (secondPhaseCoordinate == null || secondPhaseCoordinate.length != numberOfComponents) {
      secondPhaseCoordinate = new boolean[numberOfComponents];
    }
    int activeCount = 0;
    for (int component = 0; component < numberOfComponents; component++) {
      z[component] = system.getPhase(0).getComponent(component).getz();
      boolean firstAllowed = phaseConstraints.isAllowed(0, component);
      boolean secondAllowed = phaseConstraints.isAllowed(1, component);
      if (z[component] > 0.0 && !firstAllowed && !secondAllowed) {
        throw new IllegalStateException("Occupied component has no admissible phase in the Newton active set");
      }
      freeComponent[component] = z[component] > 0.0 && firstAllowed && secondAllowed;
      secondPhaseCoordinate[component] = freeComponent[component]
          && system.getBeta(1) * system.getPhase(1).getComponent(component).getx() < system.getBeta(0)
              * system.getPhase(0).getComponent(component).getx();
      if (freeComponent[component]) {
        variableComponents[activeCount++] = component;
      }
    }
    if (jacMatrix == null || activeCount != neq) {
      neq = activeCount;
      jacMatrix = new DMatrixRMaj(neq, neq);
      fvecVector = new DMatrixRMaj(neq, 1);
      dxVector = new DMatrixRMaj(neq, 1);
      jacWork = new DMatrixRMaj(neq, neq);
      linearSolver = neq == 0 ? null : LinearSolverFactory_DDRM.lu(neq);
    }
  }

  /**
   * Setter for the field <code>fvec</code>.
   */
  public void setfvec() {
    if (activePhases > 2) {
      for (int row = 0; row < neq; row++) {
        int component = variableComponents[row];
        fvecVector.set(row, 0, logFugacity(variablePhases[row], component) - logFugacity(activePhases - 1, component));
      }
      return;
    }
    for (int row = 0; row < neq; row++) {
      int i = variableComponents[row];
      double residual = Math
          .log(system.getPhase(0).getComponent(i).getFugacityCoefficient() * system.getPhase(0).getComponent(i).getx())
          - Math.log(
              system.getPhase(1).getComponent(i).getFugacityCoefficient() * system.getPhase(1).getComponent(i).getx());
      fvecVector.set(row, 0, secondPhaseCoordinate[i] ? -residual : residual);
    }
  }

  /**
   * setJac.
   */
  public void setJac() {
    if (activePhases > 2) {
      for (int row = 0; row < neq; row++) {
        for (int column = 0; column < neq; column++) {
          int i = variableComponents[row];
          int j = variableComponents[column];
          double value = phaseCurvature(activePhases - 1, i, j);
          if (variablePhases[row] == variablePhases[column]) {
            value += phaseCurvature(variablePhases[row], i, j);
          }
          jacMatrix.set(row, column, value);
        }
      }
      return;
    }
    double dij;
    double tempJ;
    double invBeta = 1.0 / system.getBeta(0);
    double invOneMinusBeta = 1.0 / (1.0 - system.getBeta(0));
    for (int row = 0; row < neq; row++) {
      int i = variableComponents[row];
      double invYi = 1.0 / system.getPhase(0).getComponent(i).getx();
      double invXi = 1.0 / system.getPhase(1).getComponent(i).getx();
      for (int column = 0; column < neq; column++) {
        int j = variableComponents[column];
        dij = i == j ? 1.0 : 0.0;
        tempJ = invBeta * (dij * invYi - 1.0 + system.getPhase(0).getComponent(i).getdfugdx(j))
            + invOneMinusBeta * (dij * invXi - 1.0 + system.getPhase(1).getComponent(i).getdfugdx(j));
        jacMatrix.set(row, column, secondPhaseCoordinate[i] == secondPhaseCoordinate[j] ? tempJ : -tempJ);
      }
    }
  }

  /**
   * Setter for the field <code>u</code>.
   */
  public void setu() {
    if (activePhases == 2 && secondPhaseCoordinate == null) {
      refreshActiveVariables();
    }
    if (activePhases > 2) {
      for (int phase = 0; phase < activePhases; phase++) {
        for (int component = 0; component < numberOfComponents; component++) {
          uVector[phase * numberOfComponents + component] = freeComponent[component]
              ? system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx()
              : phaseConstraints.isAllowed(phase, component) ? z[component] : 0.0;
        }
      }
      reconstructReferenceAmounts(uVector);
      if (!isFeasible(uVector)) {
        throw new IllegalStateException("Multiphase Newton requires positive conserved phase amounts");
      }
      return;
    }
    for (int i = 0; i < numberOfComponents; i++) {
      int phase = secondPhaseCoordinate[i] ? 1 : 0;
      uVector[i] = freeComponent[i] ? system.getBeta(phase) * system.getPhase(phase).getComponent(i).getx()
          : phaseConstraints.isAllowed(0, i) ? z[i] : 0.0;
    }
  }

  /**
   * init.
   */
  public void init() {
    if (activePhases == 2 && secondPhaseCoordinate == null) {
      // Older serialized solvers stored first-phase coordinates without an orientation map.
      refreshActiveVariables();
      setu();
    }
    setCompositions(uVector);
    // Full system init: computes fugacities, T/P/composition derivatives
    system.init(3);
  }

  /**
   * Computes Michelsen's reduced Gibbs energy from complete phase amounts. Each two-phase coordinate represents the
   * initially smaller amount; its complement contains the remainder. The signed gradient and Hessian follow this
   * coordinate orientation.
   *
   * @param amounts complete component amount coordinates at the evaluated state
   * @return Q value
   */
  private double computeQ(double[] amounts) {
    if (activePhases > 2) {
      double objective = 0.0;
      for (int phase = 0; phase < activePhases; phase++) {
        for (int component = 0; component < numberOfComponents; component++) {
          double amount = amounts[phase * numberOfComponents + component];
          if (amount > 0.0) {
            objective += amount * logFugacity(phase, component);
          }
        }
      }
      return objective;
    }
    double q = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      // A forbidden or absent species contributes lim(n * log(n)) = 0. Never evaluate log(0) for it.
      double firstAmount = secondPhaseCoordinate[i] ? z[i] - amounts[i] : amounts[i];
      if (firstAmount > 0.0) {
        q += firstAmount * (Math.log(system.getPhase(0).getComponent(i).getx())
            + Math.log(system.getPhase(0).getComponent(i).getFugacityCoefficient()));
      }
      double secondAmount = secondPhaseCoordinate[i] ? amounts[i] : z[i] - amounts[i];
      if (secondAmount > 0.0) {
        q += secondAmount * (Math.log(system.getPhase(1).getComponent(i).getx())
            + Math.log(system.getPhase(1).getComponent(i).getFugacityCoefficient()));
      }
    }
    return q;
  }

  /**
   * Check physical feasibility of trial u values.
   *
   * @param uTrial trial u vector
   * @return true if all compositions and beta are in valid range
   */
  private boolean isFeasible(double[] uTrial) {
    if (activePhases > 2) {
      for (int phase = 0; phase < activePhases; phase++) {
        double beta = 0.0;
        for (int component = 0; component < numberOfComponents; component++) {
          double amount = uTrial[phase * numberOfComponents + component];
          if (!Double.isFinite(amount) || (freeComponent[component] && !(amount > 0.0))) {
            return false;
          }
          if (!freeComponent[component]
              && amount != (phaseConstraints.isAllowed(phase, component) ? z[component] : 0.0)) {
            return false;
          }
          beta += amount;
        }
        if (!(beta > 1e-12 && beta < 1.0 - 1e-12)) {
          return false;
        }
      }
      return true;
    }
    double betaTrial = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (!Double.isFinite(uTrial[i])) {
        return false;
      }
      if (freeComponent[i]) {
        if (!(uTrial[i] > 0.0 && z[i] - uTrial[i] > 0.0)) {
          return false;
        }
      } else if (uTrial[i] != (phaseConstraints.isAllowed(0, i) ? z[i] : 0.0)) {
        return false;
      }
      betaTrial += secondPhaseCoordinate[i] ? z[i] - uTrial[i] : uTrial[i];
    }
    return betaTrial > 1e-12 && betaTrial < 1.0 - 1e-12;
  }

  /**
   * Set compositions from trial u vector and compute fugacities only (init level 1). Used for line search Q evaluation
   * at trial points.
   *
   * @param uTrial trial u vector
   */
  private void setTrialAndComputeFugacities(double[] uTrial) {
    setCompositions(uTrial);
    if (activePhases > 2) {
      for (int phase = 0; phase < activePhases; phase++) {
        system.init(1, phase);
      }
      return;
    }
    system.init(1, 0);
    system.init(1, 1);
  }

  /**
   * Reconstructs normalized phase compositions from complete component amounts.
   *
   * @param amounts component amount coordinates on the total-feed mole basis
   */
  private void setCompositions(double[] amounts) {
    if (activePhases > 2) {
      for (int phase = 0; phase < activePhases; phase++) {
        double beta = 0.0;
        for (int component = 0; component < numberOfComponents; component++) {
          beta += amounts[phase * numberOfComponents + component];
        }
        system.setBeta(phase, beta);
        for (int component = 0; component < numberOfComponents; component++) {
          system.getPhase(phase).getComponent(component).setx(amounts[phase * numberOfComponents + component] / beta);
        }
      }
      return;
    }
    double beta = 0.0;
    for (int component = 0; component < numberOfComponents; component++) {
      beta += secondPhaseCoordinate[component] ? z[component] - amounts[component] : amounts[component];
    }
    if (!(beta > 0.0 && beta < 1.0)) {
      throw new IllegalStateException("Newton active set does not contain two finite phase amounts");
    }
    system.setBeta(0, beta);
    system.setBeta(1, 1.0 - beta);
    for (int component = 0; component < numberOfComponents; component++) {
      double firstAmount = secondPhaseCoordinate[component] ? z[component] - amounts[component] : amounts[component];
      double secondAmount = secondPhaseCoordinate[component] ? amounts[component] : z[component] - amounts[component];
      double firstComposition = firstAmount / beta;
      double secondComposition = secondAmount / (1.0 - beta);
      system.getPhase(0).getComponent(component).setx(firstComposition);
      system.getPhase(1).getComponent(component).setx(secondComposition);
      // Fixed-species K is only a finite compatibility value for callers retaining log-K history.
      // The exact phase restriction is imposed on amounts and compositions, independently of this value.
      double constrainedRatio = phaseConstraints.constrainedK(component);
      double ratio = !Double.isNaN(constrainedRatio) ? constrainedRatio
          : secondComposition > 0.0 ? firstComposition / secondComposition : 1.0;
      system.getPhase(0).getComponent(component).setK(ratio);
      system.getPhase(1).getComponent(component).setK(ratio);
    }
  }

  /**
   * Forms a trial by changing only freely partitioning component amounts.
   *
   * @param scale fraction of the Newton correction
   */
  private void formTrial(double scale) {
    System.arraycopy(uVector, 0, uTrialVector, 0, uVector.length);
    if (activePhases > 2) {
      for (int row = 0; row < neq; row++) {
        int index = variablePhases[row] * numberOfComponents + variableComponents[row];
        uTrialVector[index] = uVector[index] - scale * dxVector.get(row, 0);
      }
      reconstructReferenceAmounts(uTrialVector);
      return;
    }
    for (int row = 0; row < neq; row++) {
      int component = variableComponents[row];
      uTrialVector[component] = uVector[component] - scale * dxVector.get(row, 0);
    }
  }

  /**
   * Lazily initializes the EJML solver after deserialization.
   */
  private void ensureSolverInitialized() {
    if (phaseConstraints == null || activePhases == 0 || (activePhases == 2 && secondPhaseCoordinate == null)) {
      // Streams serialized before phase constraints were introduced do not contain an active-variable map.
      refreshActiveVariables();
      setu();
    }
    if (activePhases != system.getNumberOfPhases()) {
      throw new IllegalStateException("Newton active topology changed; rebind the solver before reuse");
    }
    if (linearSolver == null && neq > 0) {
      linearSolver = LinearSolverFactory_DDRM.lu(neq);
    }
    if (uTrialVector == null || uTrialVector.length != uVector.length) {
      uTrialVector = new double[uVector.length];
    }
  }

  /**
   * Takes one full Newton correction on an already converged, fixed fluid-phase active set.
   *
   * <p>
   * This local polishing step avoids a Gibbs line search when the predicted decrease is below floating-point
   * resolution. The caller must verify feasibility, fugacity convergence, active roots and Gibbs energy and restore its
   * snapshot if the correction fails. It must not be used as an unconstrained global flash iteration.
   * </p>
   *
   * @throws IllegalStateException if the Newton correction is not finite or feasible
   */
  void polishConvergedEquilibrium() {
    ensureSolverInitialized();
    init();
    if (neq == 0) {
      return;
    }
    setfvec();
    setJac();
    jacWork.setTo(jacMatrix);
    if (!linearSolver.setA(jacWork)) {
      throw new IllegalStateException("Singular equilibrium-polishing Jacobian");
    }
    linearSolver.solve(fvecVector, dxVector);
    formTrial(1.0);
    if (!isFeasible(uTrialVector)) {
      throw new IllegalStateException("Infeasible equilibrium-polishing correction");
    }
    System.arraycopy(uTrialVector, 0, uVector, 0, uVector.length);
    init();
  }

  /**
   * Takes one finite, feasible, descent-qualified Newton step.
   *
   * @return maximum of the relative accepted correction and remaining log-fugacity residual
   * @throws IllegalStateException if the direction or every bounded trial is rejected
   */
  public double solve() {
    ensureSolverInitialized();
    iter++;
    init();
    if (neq == 0) {
      return 0.0;
    }

    double qCurrent = computeQ(uVector);
    if (!Double.isFinite(qCurrent)) {
      throw new IllegalStateException("Non-finite current Newton Gibbs objective");
    }
    setfvec();
    double maximumResidual = 0.0;
    for (int row = 0; row < neq; row++) {
      double residual = fvecVector.get(row, 0);
      if (!Double.isFinite(residual)) {
        throw new IllegalStateException("Non-finite Newton flash residual");
      }
      maximumResidual = Math.max(maximumResidual, Math.abs(residual));
    }
    // At roundoff-level equilibrium the Gibbs decrease is not numerically resolvable.
    if (maximumResidual <= 32.0 * Math.ulp(1.0)) {
      return 0.0;
    }
    setJac();

    // Levenberg-Marquardt regularization for stability
    double jacTrace = 0.0;
    for (int i = 0; i < neq; i++) {
      jacTrace += Math.abs(jacMatrix.get(i, i));
    }
    double lambda = 1e-8 * jacTrace / neq;
    for (int i = 0; i < neq; i++) {
      jacMatrix.set(i, i, jacMatrix.get(i, i) + lambda);
    }

    // Solve J * dx = fvec using EJML (pre-allocated work buffer)
    jacWork.setTo(jacMatrix);
    if (!linearSolver.setA(jacWork)) {
      throw new IllegalStateException("Singular Newton flash Jacobian");
    }
    linearSolver.solve(fvecVector, dxVector);

    // Directional derivative: slope = fvec^T * dx = grad(Q)^T * (Jac^-1 * grad(Q)) > 0
    double slope = 0.0;
    for (int i = 0; i < neq; i++) {
      slope += fvecVector.get(i, 0) * dxVector.get(i, 0);
    }

    if (!Double.isFinite(slope) || slope <= 0.0) {
      throw new IllegalStateException("Newton flash direction is not finite descent");
    }

    // Armijo backtracking line search on Michelsen Q function
    double alpha = 1.0;
    double c1 = 1e-4;
    int maxBacktrack = 8;
    boolean accepted = false;
    for (int bt = 0; bt < maxBacktrack; bt++) {
      formTrial(alpha);

      if (isFeasible(uTrialVector)) {
        try {
          setTrialAndComputeFugacities(uTrialVector);
          double qTrial = computeQ(uTrialVector);
          // Armijo condition: Q_trial <= Q_current - c1 * alpha * slope
          if (Double.isFinite(qTrial) && qTrial <= qCurrent - c1 * alpha * slope) {
            accepted = true;
            break;
          }
          // Below objective resolution, require a substantial decrease of the actual equilibrium residual.
          // This admits a local correction only when both its predicted decrease and apparent energy increase
          // are bounded by roundoff; the finite descent and feasibility checks above still apply.
          double objectiveRoundoff = 32.0 * Math.ulp(Math.abs(qCurrent));
          if (Double.isFinite(qTrial) && alpha * slope <= objectiveRoundoff && qTrial <= qCurrent + objectiveRoundoff) {
            setfvec();
            double trialResidual = 0.0;
            for (int row = 0; row < neq; row++) {
              trialResidual = Math.max(trialResidual, Math.abs(fvecVector.get(row, 0)));
            }
            if (Double.isFinite(trialResidual) && trialResidual < 0.5 * maximumResidual) {
              accepted = true;
              break;
            }
          }
        } catch (Exception ex) {
          // Cubic solver failed at trial point — try shorter step
        }
      }

      alpha *= 0.5;
    }

    if (!accepted) {
      // Trials may have changed phase compositions and K values; restore before SSI fallback.
      init();
      throw new IllegalStateException("No acceptable Newton flash step after bounded backtracking");
    }

    // Update u only with the exact evaluated and accepted step
    double stepNormSq = 0.0;
    double uNormSq = 0.0;
    for (int row = 0; row < neq; row++) {
      int component = variableComponents[row];
      double step = alpha * dxVector.get(row, 0);
      stepNormSq += step * step;
      int index = activePhases > 2 ? variablePhases[row] * numberOfComponents + component : component;
      uNormSq += uTrialVector[index] * uTrialVector[index];
    }
    // A bound-limited correction can be tiny while the phase equilibrium equations remain unsolved.
    // Report the actual residual as well, so the caller continues to a feasible equilibrium or SSI boundary step.
    setfvec();
    double remainingResidual = 0.0;
    for (int row = 0; row < neq; row++) {
      double residual = fvecVector.get(row, 0);
      if (!Double.isFinite(residual)) {
        init();
        throw new IllegalStateException("Non-finite accepted Newton flash residual");
      }
      remainingResidual = Math.max(remainingResidual, Math.abs(residual));
    }
    System.arraycopy(uTrialVector, 0, uVector, 0, uVector.length);
    return Math.max(remainingResidual, Math.sqrt(stepNormSq) / Math.max(Math.sqrt(uNormSq), 1e-10));
  }

  /** Refreshes the variable map for more than two fixed fluid phases. */
  private void refreshMultiphaseVariables() {
    for (int phase = 0; phase < activePhases; phase++) {
      PhaseType type = system.getPhaseType(phase);
      if (type != PhaseType.GAS && type != PhaseType.LIQUID && type != PhaseType.OIL && type != PhaseType.AQUEOUS
          && type != PhaseType.LIQUID_ASPHALTENE) {
        throw new IllegalStateException("Multiphase Newton supports fluid phases only");
      }
    }
    int maximumVariables = (activePhases - 1) * numberOfComponents;
    variableComponents = new int[maximumVariables];
    variablePhases = new int[maximumVariables];
    freeComponent = new boolean[numberOfComponents];
    int activeCount = 0;
    for (int component = 0; component < numberOfComponents; component++) {
      z[component] = system.getPhase(0).getComponent(component).getz();
      int allowed = 0;
      for (int phase = 0; phase < activePhases; phase++) {
        allowed += phaseConstraints.isAllowed(phase, component) ? 1 : 0;
      }
      if (z[component] > 0.0 && allowed != 1 && allowed != activePhases) {
        throw new IllegalStateException("Multiphase Newton requires an unambiguous allowed-phase set");
      }
      freeComponent[component] = z[component] > 0.0 && allowed == activePhases;
      if (freeComponent[component]) {
        for (int phase = 0; phase < activePhases - 1; phase++) {
          variableComponents[activeCount] = component;
          variablePhases[activeCount++] = phase;
        }
      }
    }
    if (uVector.length != activePhases * numberOfComponents) {
      uVector = new double[activePhases * numberOfComponents];
      uTrialVector = new double[uVector.length];
    }
    if (jacMatrix == null || activeCount != neq) {
      neq = activeCount;
      jacMatrix = new DMatrixRMaj(neq, neq);
      fvecVector = new DMatrixRMaj(neq, 1);
      dxVector = new DMatrixRMaj(neq, 1);
      jacWork = new DMatrixRMaj(neq, neq);
      linearSolver = neq == 0 ? null : LinearSolverFactory_DDRM.lu(neq);
    }
  }

  /**
   * Computes log fugacity without forming products or exponentiating the model value.
   *
   * @param phase logical phase
   * @param component component index
   * @return log of dimensionless fugacity
   */
  private double logFugacity(int phase, int component) {
    return Math.log(system.getPhase(phase).getComponent(component).getx())
        + system.getPhase(phase).getComponent(component).getLogFugacityCoefficient();
  }

  /**
   * Returns the Gibbs curvature for phase amounts on the total-feed basis.
   *
   * @param phase logical phase
   * @param i differentiated chemical potential
   * @param j differentiated component amount
   * @return derivative of log fugacity with respect to total-feed phase amount
   */
  private double phaseCurvature(int phase, int i, int j) {
    return ((i == j ? 1.0 / system.getPhase(phase).getComponent(i).getx() : 0.0) - 1.0
        + system.getPhase(phase).getComponent(i).getdfugdx(j)) / system.getBeta(phase);
  }

  /**
   * Eliminates the last logical phase by species conservation, leaving fixed ions untouched.
   *
   * @param amounts full phase amounts in phase-major order
   */
  private void reconstructReferenceAmounts(double[] amounts) {
    for (int component = 0; component < numberOfComponents; component++) {
      if (freeComponent[component]) {
        double remaining = z[component];
        for (int phase = 0; phase < activePhases - 1; phase++) {
          remaining -= amounts[phase * numberOfComponents + component];
        }
        amounts[(activePhases - 1) * numberOfComponents + component] = remaining;
      }
    }
  }
}
