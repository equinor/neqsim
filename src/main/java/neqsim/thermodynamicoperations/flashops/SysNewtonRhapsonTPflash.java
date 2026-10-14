package neqsim.thermodynamicoperations.flashops;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import neqsim.thermo.system.SystemInterface;

/**
 * Newton-Raphson solver for two-phase TP flash using Michelsen's u-variable formulation.
 *
 * <p>
 * Variables: u[i] = beta * y[i] (Michelsen 1982b). Residual: g[i] = ln(f_gas_i) - ln(f_liq_i). Jacobian: Hessian of the
 * reduced Gibbs energy Q(u). Uses a bounded Armijo backtracking line search on Q (Michelsen &amp; Mollerup 2007, Ch.
 * 12). Rejected steps leave the last accepted split available for the caller's fallback; this local solver does not
 * certify global phase stability.
 * </p>
 *
 * <p>
 * Performance optimizations vs standard implementation:
 * </p>
 * <ul>
 * <li>EJML dense solver instead of JAMA (~2x faster linear solve for n&gt;=10)</li>
 * <li>Pre-allocated solver and work buffers (zero allocation in solve loop)</li>
 * </ul>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SysNewtonRhapsonTPflash implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Number of equations (= number of components). */
  int neq = 0;

  /** Newton iteration counter. */
  int iter = 0;

  /** Reference to the thermodynamic system being solved. */
  SystemInterface system;

  /** Number of components in the system. */
  int numberOfComponents;

  /** Cached feed compositions (constant during flash). */
  private double[] z;

  // Pre-allocated EJML matrices for zero-allocation Newton steps
  /** Jacobian matrix (Hessian of Q). */
  private DMatrixRMaj jacMatrix;

  /** Residual vector (gradient of Q). */
  private DMatrixRMaj fvecVector;

  /** Newton step vector. */
  private DMatrixRMaj dxVector;

  /** Work copy of Jacobian for LU decomposition. */
  private DMatrixRMaj jacWork;

  /** u-variable array: u[i] = beta * y[i]. */
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
    neq = numberOfComponents;

    // Pre-allocate EJML matrices
    jacMatrix = new DMatrixRMaj(neq, neq);
    fvecVector = new DMatrixRMaj(neq, 1);
    dxVector = new DMatrixRMaj(neq, 1);
    jacWork = new DMatrixRMaj(neq, neq);
    uVector = new double[neq];
    uTrialVector = new double[neq];
    linearSolver = LinearSolverFactory_DDRM.lu(neq);

    setu();
    z = new double[numberOfComponents];
    for (int i = 0; i < numberOfComponents; i++) {
      z[i] = system.getPhase(0).getComponent(i).getz();
    }
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
   * Re-bind this solver to a (possibly updated) system reference and refresh the cached feed composition, without
   * re-allocating the EJML matrices. The component count must match — call {@link #getNumberOfComponents()} first.
   *
   * @param system system to solve
   */
  public void setSystem(SystemInterface system) {
    this.system = system;
    if (z == null || z.length != numberOfComponents) {
      z = new double[numberOfComponents];
    }
    for (int i = 0; i < numberOfComponents; i++) {
      z[i] = system.getPhase(0).getComponent(i).getz();
    }
    setu();
  }

  /**
   * Setter for the field <code>fvec</code>.
   */
  public void setfvec() {
    for (int i = 0; i < numberOfComponents; i++) {
      fvecVector.set(i, 0, Math
          .log(system.getPhase(0).getComponent(i).getFugacityCoefficient() * system.getPhase(0).getComponent(i).getx())
          - Math.log(
              system.getPhase(1).getComponent(i).getFugacityCoefficient() * system.getPhase(1).getComponent(i).getx()));
    }
  }

  /**
   * setJac.
   */
  public void setJac() {
    double dij;
    double tempJ;
    double invBeta = 1.0 / system.getBeta(0);
    double invOneMinusBeta = 1.0 / (1.0 - system.getBeta(0));
    for (int i = 0; i < numberOfComponents; i++) {
      double invYi = 1.0 / system.getPhase(0).getComponent(i).getx();
      double invXi = 1.0 / system.getPhase(1).getComponent(i).getx();
      for (int j = 0; j < numberOfComponents; j++) {
        dij = i == j ? 1.0 : 0.0;
        tempJ = invBeta * (dij * invYi - 1.0 + system.getPhase(0).getComponent(i).getdfugdx(j))
            + invOneMinusBeta * (dij * invXi - 1.0 + system.getPhase(1).getComponent(i).getdfugdx(j));
        jacMatrix.set(i, j, tempJ);
      }
    }
  }

  /**
   * Setter for the field <code>u</code>.
   */
  public void setu() {
    for (int i = 0; i < numberOfComponents; i++) {
      uVector[i] = system.getBeta(0) * system.getPhase(0).getComponent(i).getx();
    }
  }

  /**
   * init.
   */
  public void init() {
    double betaSum = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      betaSum += uVector[i];
    }
    system.setBeta(0, betaSum);
    system.setBeta(1, 1.0 - betaSum);

    for (int i = 0; i < numberOfComponents; i++) {
      system.getPhase(0).getComponent(i).setx(uVector[i] / betaSum);
      system.getPhase(1).getComponent(i).setx((z[i] - uVector[i]) / (1.0 - betaSum));
      system.getPhase(0).getComponent(i)
          .setK(system.getPhase(0).getComponent(i).getx() / system.getPhase(1).getComponent(i).getx());
      system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
    }

    // Full system init: computes fugacities, T/P/composition derivatives
    system.init(3);
  }

  /**
   * Compute Michelsen's reduced Gibbs energy Q(u). Q = sum[u_i * ln(y_i * phi_gas_i) + (z_i - u_i) * ln(x_i *
   * phi_liq_i)]. Gradient of Q equals fvec; Hessian of Q equals Jac.
   *
   * @param amounts first-phase component amounts at the evaluated state
   * @return Q value
   */
  private double computeQ(double[] amounts) {
    double q = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      double yi = system.getPhase(0).getComponent(i).getx();
      double xi = system.getPhase(1).getComponent(i).getx();
      double lnFugGas = Math.log(yi * system.getPhase(0).getComponent(i).getFugacityCoefficient());
      double lnFugLiq = Math.log(xi * system.getPhase(1).getComponent(i).getFugacityCoefficient());
      q += amounts[i] * lnFugGas + (z[i] - amounts[i]) * lnFugLiq;
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
    double betaTrial = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (!Double.isFinite(uTrial[i]) || uTrial[i] < 1e-15 || uTrial[i] > z[i] - 1e-15) {
        return false;
      }
      betaTrial += uTrial[i];
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
    double betaTrial = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      betaTrial += uTrial[i];
    }
    system.setBeta(0, betaTrial);
    system.setBeta(1, 1.0 - betaTrial);
    for (int i = 0; i < numberOfComponents; i++) {
      system.getPhase(0).getComponent(i).setx(uTrial[i] / betaTrial);
      system.getPhase(1).getComponent(i).setx((z[i] - uTrial[i]) / (1.0 - betaTrial));
      double ratio = system.getPhase(0).getComponent(i).getx() / system.getPhase(1).getComponent(i).getx();
      system.getPhase(0).getComponent(i).setK(ratio);
      system.getPhase(1).getComponent(i).setK(ratio);
    }
    system.init(1, 0);
    system.init(1, 1);
  }

  /**
   * Lazily initializes the EJML solver after deserialization.
   */
  private void ensureSolverInitialized() {
    if (linearSolver == null) {
      linearSolver = LinearSolverFactory_DDRM.lu(neq);
    }
    if (uTrialVector == null || uTrialVector.length != numberOfComponents) {
      uTrialVector = new double[numberOfComponents];
    }
  }

  /**
   * Takes one full Newton correction on an already converged, fixed two-phase active set.
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
    setfvec();
    setJac();
    jacWork.setTo(jacMatrix);
    if (!linearSolver.setA(jacWork)) {
      throw new IllegalStateException("Singular equilibrium-polishing Jacobian");
    }
    linearSolver.solve(fvecVector, dxVector);
    for (int component = 0; component < numberOfComponents; component++) {
      uTrialVector[component] = uVector[component] - dxVector.get(component, 0);
      if (!Double.isFinite(uTrialVector[component])) {
        throw new IllegalStateException("Non-finite equilibrium-polishing correction");
      }
    }
    if (!isFeasible(uTrialVector)) {
      throw new IllegalStateException("Infeasible equilibrium-polishing correction");
    }
    System.arraycopy(uTrialVector, 0, uVector, 0, numberOfComponents);
    init();
  }

  /**
   * Takes one finite, feasible, descent-qualified Newton step.
   *
   * @return relative norm of the accepted correction
   * @throws IllegalStateException if the direction or every bounded trial is rejected
   */
  public double solve() {
    ensureSolverInitialized();
    iter++;
    init();

    double qCurrent = computeQ(uVector);
    if (!Double.isFinite(qCurrent)) {
      throw new IllegalStateException("Non-finite current Newton Gibbs objective");
    }
    setfvec();
    double maximumResidual = 0.0;
    for (int component = 0; component < numberOfComponents; component++) {
      double residual = fvecVector.get(component, 0);
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
      for (int i = 0; i < numberOfComponents; i++) {
        uTrialVector[i] = uVector[i] - alpha * dxVector.get(i, 0);
      }

      if (isFeasible(uTrialVector)) {
        try {
          setTrialAndComputeFugacities(uTrialVector);
          double qTrial = computeQ(uTrialVector);
          // Armijo condition: Q_trial <= Q_current - c1 * alpha * slope
          if (Double.isFinite(qTrial) && qTrial <= qCurrent - c1 * alpha * slope) {
            accepted = true;
            break;
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
    for (int i = 0; i < numberOfComponents; i++) {
      double step = alpha * dxVector.get(i, 0);
      uVector[i] -= step;
      stepNormSq += step * step;
      uNormSq += uVector[i] * uVector[i];
    }
    return Math.sqrt(stepNormSq) / Math.max(Math.sqrt(uNormSq), 1e-10);
  }
}
