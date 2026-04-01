package neqsim.thermodynamicoperations.flashops;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import neqsim.thermo.system.SystemInterface;

/**
 * Newton-Raphson solver for two-phase TP flash using Michelsen's u-variable formulation.
 *
 * <p>
 * Variables: u[i] = beta * y[i] (Michelsen 1982b). Residual: g[i] = ln(f_gas_i) - ln(f_liq_i).
 * Jacobian: Hessian of the reduced Gibbs energy Q(u). Uses Armijo backtracking line search on Q for
 * global convergence (Michelsen &amp; Mollerup 2007, Ch. 12).
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

  /** Pre-allocated EJML LU solver. */
  private transient LinearSolverDense<DMatrixRMaj> linearSolver;

  /**
   * <p>
   * Constructor for sysNewtonRhapsonTPflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param numberOfPhases a int
   * @param numberOfComponents a int
   */
  public SysNewtonRhapsonTPflash(SystemInterface system, int numberOfPhases,
      int numberOfComponents) {
    this.system = system;
    this.numberOfComponents = numberOfComponents;
    neq = numberOfComponents;

    // Pre-allocate EJML matrices
    jacMatrix = new DMatrixRMaj(neq, neq);
    fvecVector = new DMatrixRMaj(neq, 1);
    dxVector = new DMatrixRMaj(neq, 1);
    jacWork = new DMatrixRMaj(neq, neq);
    uVector = new double[neq];
    linearSolver = LinearSolverFactory_DDRM.lu(neq);

    setu();
    z = new double[numberOfComponents];
    for (int i = 0; i < numberOfComponents; i++) {
      z[i] = system.getPhase(0).getComponent(i).getz();
    }
  }

  /**
   * <p>
   * Setter for the field <code>fvec</code>.
   * </p>
   */
  public void setfvec() {
    for (int i = 0; i < numberOfComponents; i++) {
      fvecVector.set(i, 0,
          Math.log(system.getPhase(0).getComponent(i).getFugacityCoefficient()
              * system.getPhase(0).getComponent(i).getx())
              - Math.log(system.getPhase(1).getComponent(i).getFugacityCoefficient()
                  * system.getPhase(1).getComponent(i).getx()));
    }
  }

  /**
   * <p>
   * setJac.
   * </p>
   */
  public void setJac() {
    double dij;
    double tempJ;
    double beta = Math.max(1.0e-12, Math.min(1.0 - 1.0e-12, system.getBeta()));
    double invBeta = 1.0 / beta;
    double invOneMinusBeta = 1.0 / (1.0 - beta);
    for (int i = 0; i < numberOfComponents; i++) {
      double yi = Math.max(1.0e-20, system.getPhase(0).getComponent(i).getx());
      double xi = Math.max(1.0e-20, system.getPhase(1).getComponent(i).getx());
      double invYi = 1.0 / yi;
      double invXi = 1.0 / xi;
      for (int j = 0; j < numberOfComponents; j++) {
        dij = i == j ? 1.0 : 0.0;
        tempJ = invBeta * (dij * invYi - 1.0 + system.getPhase(0).getComponent(i).getdfugdx(j))
            + invOneMinusBeta
                * (dij * invXi - 1.0 + system.getPhase(1).getComponent(i).getdfugdx(j));
        if (!Double.isFinite(tempJ)) {
          tempJ = 0.0;
        }
        jacMatrix.set(i, j, tempJ);
      }
      jacMatrix.add(i, i, 1.0e-12);
    }
  }

  /**
   * <p>
   * Setter for the field <code>u</code>.
   * </p>
   */
  public void setu() {
    for (int i = 0; i < numberOfComponents; i++) {
      uVector[i] = system.getBeta() * system.getPhase(0).getComponent(i).getx();
    }
  }

  /**
   * <p>
   * init.
   * </p>
   */
  public void init() {
    double betaSum = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      betaSum += uVector[i];
    }
    system.setBeta(betaSum);

    for (int i = 0; i < numberOfComponents; i++) {
      system.getPhase(0).getComponent(i).setx(uVector[i] / betaSum);
      system.getPhase(1).getComponent(i).setx((z[i] - uVector[i]) / (1.0 - betaSum));
      system.getPhase(0).getComponent(i).setK(
          system.getPhase(0).getComponent(i).getx() / system.getPhase(1).getComponent(i).getx());
      system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
    }

    // Full system init: computes fugacities, T/P/composition derivatives
    system.init(3);
  }

  /**
   * Compute Michelsen's reduced Gibbs energy Q(u). Q = sum[u_i * ln(y_i * phi_gas_i) + (z_i - u_i)
   * * ln(x_i * phi_liq_i)]. Gradient of Q equals fvec; Hessian of Q equals Jac.
   *
   * @return Q value
   */
  private double computeQ() {
    double q = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      double yi = system.getPhase(0).getComponent(i).getx();
      double xi = system.getPhase(1).getComponent(i).getx();
      double lnFugGas = Math.log(yi * system.getPhase(0).getComponent(i).getFugacityCoefficient());
      double lnFugLiq = Math.log(xi * system.getPhase(1).getComponent(i).getFugacityCoefficient());
      q += uVector[i] * lnFugGas + (z[i] - uVector[i]) * lnFugLiq;
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
      if (uTrial[i] < 1e-15 || uTrial[i] > z[i] - 1e-15) {
        return false;
      }
      betaTrial += uTrial[i];
    }
    return betaTrial > 1e-12 && betaTrial < 1.0 - 1e-12;
  }

  /**
   * Set compositions from trial u vector and compute fugacities only (init level 1). Used for line
   * search Q evaluation at trial points.
   *
   * @param uTrial trial u vector
   */
  private void setTrialAndComputeFugacities(double[] uTrial) {
    double betaTrial = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      betaTrial += uTrial[i];
    }
    system.setBeta(betaTrial);
    for (int i = 0; i < numberOfComponents; i++) {
      system.getPhase(0).getComponent(i).setx(uTrial[i] / betaTrial);
      system.getPhase(1).getComponent(i).setx((z[i] - uTrial[i]) / (1.0 - betaTrial));
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
  }

  /**
   * <p>
   * solve.
   * </p>
   *
   * @return a double
   */
  public double solve() {
    ensureSolverInitialized();
    iter++;
    init();

    double qCurrent = computeQ();
    setfvec();
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
    linearSolver.setA(jacWork);
    linearSolver.solve(fvecVector, dxVector);

    // Directional derivative: slope = fvec^T * dx = grad(Q)^T * (Jac^-1 * grad(Q)) > 0
    double slope = 0.0;
    for (int i = 0; i < neq; i++) {
      slope += fvecVector.get(i, 0) * dxVector.get(i, 0);
    }

    // Armijo backtracking line search on Michelsen Q function
    double alpha = 1.0;
    double c1 = 1e-4;
    int maxBacktrack = 8;
    double[] uTrial = new double[numberOfComponents];

    for (int bt = 0; bt < maxBacktrack; bt++) {
      for (int i = 0; i < numberOfComponents; i++) {
        uTrial[i] = uVector[i] - alpha * dxVector.get(i, 0);
      }

      if (isFeasible(uTrial)) {
        try {
          setTrialAndComputeFugacities(uTrial);
          double qTrial = computeQ();
          // Armijo condition: Q_trial <= Q_current - c1 * alpha * slope
          if (qTrial <= qCurrent - c1 * alpha * slope) {
            break;
          }
        } catch (Exception ex) {
          // Cubic solver failed at trial point — try shorter step
        }
      }

      alpha *= 0.5;
    }

    // Update u with accepted step
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
