package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Sequential Quadratic Programming (SQP) optimizer for constrained process optimization.
 *
 * <p>
 * Implements a reduced SQP method for solving nonlinear programming (NLP) problems of the form:
 * </p>
 *
 * <pre>
 * minimize   f(x)
 * subject to g_i(x) = 0     i = 1,...,m_e   (equality constraints)
 *            h_j(x) &gt;= 0   j = 1,...,m_i   (inequality constraints)
 *            x_L &lt;= x &lt;= x_U              (variable bounds)
 * </pre>
 *
 * <p>
 * The algorithm iteratively solves Quadratic Programming (QP) sub-problems using the Lagrangian:
 * </p>
 *
 * <pre>
 * L(x, lambda, mu) = f(x) - lambda ^ T * g(x) - mu ^ T * h(x)
 * </pre>
 *
 * <p>
 * At each iteration, a QP sub-problem is formed with a BFGS approximation of the Hessian of the
 * Lagrangian, and the solution provides a search direction. An Armijo-backtracking line search on a
 * merit function ensures global convergence.
 * </p>
 *
 * <h2>Features</h2>
 * <ul>
 * <li>BFGS quasi-Newton Hessian approximation (damped update for positive definiteness)</li>
 * <li>Active-set QP solver for bound and linear inequality constraints</li>
 * <li>L1 exact penalty merit function for global convergence</li>
 * <li>Finite-difference gradient estimation (user can provide analytical gradients)</li>
 * <li>Variable bounds enforced via projection</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>
 * SQPoptimizer sqp = new SQPoptimizer();
 * sqp.setObjectiveFunction(x -&gt; computeNPV(x, process));
 * sqp.addEqualityConstraint(x -&gt; massBalance(x));
 * sqp.addInequalityConstraint(x -&gt; maxPressure - x[0]);
 * sqp.setVariableBounds(new double[] {50.0, 0.5}, new double[] {200.0, 1.0});
 * sqp.setInitialPoint(new double[] {100.0, 0.8});
 * SQPoptimizer.OptimizationResult result = sqp.solve();
 * </pre>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Nocedal, J. &amp; Wright, S.J., "Numerical Optimization", 2nd ed., Springer (2006), Ch.
 * 18</li>
 * <li>Biegler, L.T., "Nonlinear Programming", SIAM (2010), Ch. 3-4</li>
 * <li>Boggs, P.T. &amp; Tolle, J.W., "Sequential Quadratic Programming", Acta Numerica (1995)</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 * @see ProcessOptimizationEngine
 */
public class SQPoptimizer implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(SQPoptimizer.class);

  /** Objective function: f(x) -> double. */
  private transient ObjectiveFunc objectiveFunction;

  /** Equality constraints: g_i(x) = 0. */
  private transient List<ConstraintFunc> equalityConstraints = new ArrayList<ConstraintFunc>();

  /** Inequality constraints: h_j(x) >= 0. */
  private transient List<ConstraintFunc> inequalityConstraints = new ArrayList<ConstraintFunc>();

  /** Lower bounds on variables. */
  private double[] lowerBounds;

  /** Upper bounds on variables. */
  private double[] upperBounds;

  /** Initial point. */
  private double[] x0;

  /** Number of decision variables. */
  private int n;

  /** Maximum SQP iterations. */
  private int maxIterations = 100;

  /** Convergence tolerance on KKT conditions. */
  private double tolerance = 1e-6;

  /** Step size for finite-difference gradient. */
  private double finiteDifferenceStep = 1e-6;

  /** Penalty parameter for L1 merit function. */
  private double penaltyParameter = 10.0;

  /** Armijo sufficient decrease parameter. */
  private double armijoC1 = 1e-4;

  /** Maximum line search iterations. */
  private int maxLineSearchIterations = 20;

  /** BFGS Hessian approximation (n x n matrix). */
  private double[][] hessian;

  /** Lagrange multipliers for equality constraints. */
  private double[] lambdaEq;

  /** Lagrange multipliers for inequality constraints. */
  private double[] lambdaIneq;

  /**
   * Functional interface for the objective function.
   */
  public interface ObjectiveFunc {
    /**
     * Evaluate the objective function.
     *
     * @param x decision variable vector
     * @return objective function value
     */
    double evaluate(double[] x);
  }

  /**
   * Functional interface for constraint functions.
   */
  public interface ConstraintFunc {
    /**
     * Evaluate the constraint function.
     *
     * @param x decision variable vector
     * @return constraint value (= 0 for equality, &gt;= 0 for inequality)
     */
    double evaluate(double[] x);
  }

  /**
   * Default constructor.
   */
  public SQPoptimizer() {}

  /**
   * Constructor with number of variables.
   *
   * @param numVariables number of decision variables
   */
  public SQPoptimizer(int numVariables) {
    this.n = numVariables;
    this.x0 = new double[n];
    this.lowerBounds = new double[n];
    this.upperBounds = new double[n];
    Arrays.fill(lowerBounds, Double.NEGATIVE_INFINITY);
    Arrays.fill(upperBounds, Double.POSITIVE_INFINITY);
  }

  /**
   * Set the objective function to minimize.
   *
   * @param func objective function
   */
  public void setObjectiveFunction(ObjectiveFunc func) {
    this.objectiveFunction = func;
  }

  /**
   * Add an equality constraint g(x) = 0.
   *
   * @param func constraint function
   */
  public void addEqualityConstraint(ConstraintFunc func) {
    equalityConstraints.add(func);
  }

  /**
   * Add an inequality constraint h(x) &gt;= 0.
   *
   * @param func constraint function
   */
  public void addInequalityConstraint(ConstraintFunc func) {
    inequalityConstraints.add(func);
  }

  /**
   * Set variable bounds.
   *
   * @param lower lower bounds array (length n)
   * @param upper upper bounds array (length n)
   */
  public void setVariableBounds(double[] lower, double[] upper) {
    this.lowerBounds = Arrays.copyOf(lower, lower.length);
    this.upperBounds = Arrays.copyOf(upper, upper.length);
    this.n = lower.length;
  }

  /**
   * Set the initial point.
   *
   * @param x0 initial point array (length n)
   */
  public void setInitialPoint(double[] x0) {
    this.x0 = Arrays.copyOf(x0, x0.length);
    this.n = x0.length;
  }

  /**
   * Set maximum number of SQP iterations.
   *
   * @param maxIter maximum iterations
   */
  public void setMaxIterations(int maxIter) {
    this.maxIterations = maxIter;
  }

  /**
   * Set convergence tolerance.
   *
   * @param tol tolerance
   */
  public void setTolerance(double tol) {
    this.tolerance = tol;
  }

  /**
   * Set the finite-difference step size for gradient estimation.
   *
   * @param step step size
   */
  public void setFiniteDifferenceStep(double step) {
    this.finiteDifferenceStep = step;
  }

  /**
   * Get maximum iterations setting.
   *
   * @return maximum iterations
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Get tolerance setting.
   *
   * @return convergence tolerance
   */
  public double getTolerance() {
    return tolerance;
  }

  /**
   * Solve the NLP problem using SQP.
   *
   * @return optimization result
   */
  public OptimizationResult solve() {
    if (objectiveFunction == null) {
      throw new IllegalStateException("Objective function must be set before solving");
    }
    if (n <= 0) {
      throw new IllegalStateException("Number of variables must be positive");
    }

    int mEq = equalityConstraints.size();
    int mIneq = inequalityConstraints.size();

    // Initialize
    double[] x = Arrays.copyOf(x0, n);
    projectToBounds(x);

    // Initialize BFGS Hessian to identity
    hessian = new double[n][n];
    for (int i = 0; i < n; i++) {
      hessian[i][i] = 1.0;
    }

    // Initialize Lagrange multipliers
    lambdaEq = new double[mEq];
    lambdaIneq = new double[mIneq];

    double fBest = objectiveFunction.evaluate(x);
    double[] xBest = Arrays.copyOf(x, n);
    double[] gradF = computeGradient(objectiveFunction, x);

    int iterCount = 0;
    boolean converged = false;

    for (int iter = 0; iter < maxIterations; iter++) {
      iterCount = iter + 1;

      // Evaluate constraints
      double[] gEq = evaluateConstraints(equalityConstraints, x);
      double[] hIneq = evaluateConstraints(inequalityConstraints, x);

      // Compute constraint Jacobians (needed for both KKT check and QP)
      double[][] jacEq = computeJacobian(equalityConstraints, x);
      double[][] jacIneq = computeJacobian(inequalityConstraints, x);

      // Check KKT optimality (with Lagrange multiplier contributions)
      double kktError = computeKKTError(gradF, gEq, hIneq, jacEq, jacIneq, x);
      if (kktError < tolerance) {
        converged = true;
        break;
      }

      // Solve QP sub-problem for search direction
      double[] dx = solveQPSubproblem(gradF, gEq, hIneq, jacEq, jacIneq, x);

      // Line search on L1 merit function
      double alpha = lineSearch(x, dx, fBest, gEq, hIneq);

      // Store previous values for BFGS update
      double[] xPrev = Arrays.copyOf(x, n);
      double[] gradPrev = Arrays.copyOf(gradF, n);

      // Update x
      for (int i = 0; i < n; i++) {
        x[i] = x[i] + alpha * dx[i];
      }
      projectToBounds(x);

      // Evaluate new point
      fBest = objectiveFunction.evaluate(x);
      gradF = computeGradient(objectiveFunction, x);

      // BFGS Hessian update (damped)
      updateBFGS(x, xPrev, gradF, gradPrev, gEq, hIneq);

      // Track best feasible point
      boolean feasible = true;
      double[] gEqNew = evaluateConstraints(equalityConstraints, x);
      double[] hIneqNew = evaluateConstraints(inequalityConstraints, x);
      for (int i = 0; i < gEqNew.length; i++) {
        if (Math.abs(gEqNew[i]) > tolerance * 100.0) {
          feasible = false;
          break;
        }
      }
      if (feasible) {
        for (int j = 0; j < hIneqNew.length; j++) {
          if (hIneqNew[j] < -tolerance * 100.0) {
            feasible = false;
            break;
          }
        }
      }
      if (feasible && fBest < objectiveFunction.evaluate(xBest)) {
        xBest = Arrays.copyOf(x, n);
      }
    }

    // For constrained problems, prefer the final converged iterate over xBest
    // since xBest may have been set from an infeasible point
    double[] xFinal;
    if (converged) {
      xFinal = x;
    } else {
      xFinal = xBest;
    }
    double fFinal = objectiveFunction.evaluate(xFinal);

    return new OptimizationResult(xFinal, fFinal, iterCount, converged,
        computeKKTError(computeGradient(objectiveFunction, xFinal),
            evaluateConstraints(equalityConstraints, xFinal),
            evaluateConstraints(inequalityConstraints, xFinal),
            computeJacobian(equalityConstraints, xFinal),
            computeJacobian(inequalityConstraints, xFinal), xFinal));
  }

  /**
   * Project point onto variable bounds.
   *
   * @param x point to project (modified in place)
   */
  private void projectToBounds(double[] x) {
    if (lowerBounds != null && upperBounds != null) {
      for (int i = 0; i < n; i++) {
        x[i] = Math.max(x[i], lowerBounds[i]);
        x[i] = Math.min(x[i], upperBounds[i]);
      }
    }
  }

  /**
   * Compute finite-difference gradient of a function.
   *
   * @param func function to differentiate
   * @param x evaluation point
   * @return gradient vector
   */
  private double[] computeGradient(ObjectiveFunc func, double[] x) {
    double[] grad = new double[n];
    for (int i = 0; i < n; i++) {
      double step = finiteDifferenceStep * Math.max(1.0, Math.abs(x[i]));
      double[] xp = Arrays.copyOf(x, n);
      double[] xm = Arrays.copyOf(x, n);
      xp[i] += step;
      xm[i] -= step;
      grad[i] = (func.evaluate(xp) - func.evaluate(xm)) / (2.0 * step);
    }
    return grad;
  }

  /**
   * Evaluate a list of constraint functions.
   *
   * @param constraints list of constraint functions
   * @param x evaluation point
   * @return array of constraint values
   */
  private double[] evaluateConstraints(List<ConstraintFunc> constraints, double[] x) {
    double[] vals = new double[constraints.size()];
    for (int i = 0; i < constraints.size(); i++) {
      vals[i] = constraints.get(i).evaluate(x);
    }
    return vals;
  }

  /**
   * Compute Jacobian of constraint functions via finite differences.
   *
   * @param constraints list of constraint functions
   * @param x evaluation point
   * @return Jacobian matrix [m x n]
   */
  private double[][] computeJacobian(List<ConstraintFunc> constraints, double[] x) {
    int m = constraints.size();
    double[][] jac = new double[m][n];
    for (int j = 0; j < n; j++) {
      double step = finiteDifferenceStep * Math.max(1.0, Math.abs(x[j]));
      double[] xp = Arrays.copyOf(x, n);
      double[] xm = Arrays.copyOf(x, n);
      xp[j] += step;
      xm[j] -= step;
      double[] cp = evaluateConstraints(constraints, xp);
      double[] cm = evaluateConstraints(constraints, xm);
      for (int i = 0; i < m; i++) {
        jac[i][j] = (cp[i] - cm[i]) / (2.0 * step);
      }
    }
    return jac;
  }

  /**
   * Compute KKT optimality error.
   *
   * @param gradF gradient of objective
   * @param gEq equality constraint values
   * @param hIneq inequality constraint values
   * @param jacEq equality constraint Jacobian [mEq x n]
   * @param jacIneq inequality constraint Jacobian [mIneq x n]
   * @param x current point
   * @return KKT error (inf-norm)
   */
  private double computeKKTError(double[] gradF, double[] gEq, double[] hIneq, double[][] jacEq,
      double[][] jacIneq, double[] x) {
    double maxError = 0.0;

    // Compute Lagrangian gradient: grad_L = gradF - jacEq^T * lambdaEq - jacIneq^T * lambdaIneq
    double[] gradL = Arrays.copyOf(gradF, n);
    if (lambdaEq != null && jacEq != null) {
      for (int j = 0; j < lambdaEq.length; j++) {
        for (int i = 0; i < n; i++) {
          gradL[i] -= jacEq[j][i] * lambdaEq[j];
        }
      }
    }
    if (lambdaIneq != null && jacIneq != null) {
      for (int j = 0; j < lambdaIneq.length; j++) {
        for (int i = 0; i < n; i++) {
          gradL[i] -= jacIneq[j][i] * lambdaIneq[j];
        }
      }
    }

    // Stationarity: for free variables, |grad_L_i| should be zero.
    // At active bounds, the gradient may be non-zero if it pushes against the bound.
    for (int i = 0; i < n; i++) {
      double g = gradL[i];
      boolean atLower = lowerBounds != null && Math.abs(x[i] - lowerBounds[i]) < tolerance * 10.0;
      boolean atUpper = upperBounds != null && Math.abs(x[i] - upperBounds[i]) < tolerance * 10.0;

      if (atLower && g >= 0.0) {
        continue; // KKT satisfied at lower bound (gradient pushes into bound)
      }
      if (atUpper && g <= 0.0) {
        continue; // KKT satisfied at upper bound (gradient pushes into bound)
      }
      maxError = Math.max(maxError, Math.abs(g));
    }

    // Primal feasibility: equality constraints
    for (int i = 0; i < gEq.length; i++) {
      maxError = Math.max(maxError, Math.abs(gEq[i]));
    }

    // Primal feasibility: inequality constraints h(x) >= 0, violation when h < 0
    for (int j = 0; j < hIneq.length; j++) {
      if (hIneq[j] < 0) {
        maxError = Math.max(maxError, -hIneq[j]);
      }
    }

    // Bound feasibility
    if (lowerBounds != null && upperBounds != null) {
      for (int i = 0; i < n; i++) {
        if (x[i] < lowerBounds[i]) {
          maxError = Math.max(maxError, lowerBounds[i] - x[i]);
        }
        if (x[i] > upperBounds[i]) {
          maxError = Math.max(maxError, x[i] - upperBounds[i]);
        }
      }
    }

    return maxError;
  }

  /**
   * Solve the QP sub-problem for the SQP search direction.
   *
   * <p>
   * Minimizes: 0.5 * d^T * H * d + grad_f^T * d subject to linearized constraints. Uses a
   * simplified projected gradient approach with active-set handling.
   * </p>
   *
   * @param gradF gradient of objective
   * @param gEq equality constraint values
   * @param hIneq inequality constraint values
   * @param jacEq equality constraint Jacobian
   * @param jacIneq inequality constraint Jacobian
   * @param x current point
   * @return search direction d
   */
  private double[] solveQPSubproblem(double[] gradF, double[] gEq, double[] hIneq, double[][] jacEq,
      double[][] jacIneq, double[] x) {
    int mEq = gEq.length;
    int mIneq = hIneq.length;

    // For unconstrained or bounds-only: Newton direction d = -H^{-1} * grad_f
    if (mEq == 0 && mIneq == 0) {
      return solveLinearSystem(hessian, negateVector(gradF));
    }

    // With constraints: solve reduced KKT system
    // [H -A^T] [d ] [-grad_f ]
    // [A 0 ] [lambda ] = [-c(x) ]
    // where A = [jacEq; jacIneq_active], c = [gEq; hIneq_active]

    // Identify active inequality constraints (those near zero or violated)
    List<Integer> activeIneq = new ArrayList<Integer>();
    for (int j = 0; j < mIneq; j++) {
      if (hIneq[j] < tolerance * 10.0) {
        activeIneq.add(j);
      }
    }

    int mActive = mEq + activeIneq.size();
    if (mActive == 0) {
      return solveLinearSystem(hessian, negateVector(gradF));
    }

    // Build the combined constraint matrix A and residual vector
    double[][] aMatrix = new double[mActive][n];
    double[] residual = new double[mActive];

    for (int i = 0; i < mEq; i++) {
      System.arraycopy(jacEq[i], 0, aMatrix[i], 0, n);
      residual[i] = -gEq[i];
    }
    for (int k = 0; k < activeIneq.size(); k++) {
      int j = activeIneq.get(k);
      System.arraycopy(jacIneq[j], 0, aMatrix[mEq + k], 0, n);
      residual[mEq + k] = -hIneq[j];
    }

    // Solve augmented KKT system via Schur complement:
    // d = H^{-1} * (-gradF + A^T * lambda)
    // A * H^{-1} * A^T * lambda = A * H^{-1} * gradF + c
    double[] dUnconstrained = solveLinearSystem(hessian, negateVector(gradF));
    double[][] hInv = invertMatrix(hessian);

    // S = A * H^{-1} * A^T (Schur complement)
    double[][] hInvAt = new double[n][mActive];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < mActive; j++) {
        double sum = 0.0;
        for (int k = 0; k < n; k++) {
          sum += hInv[i][k] * aMatrix[j][k];
        }
        hInvAt[i][j] = sum;
      }
    }

    double[][] schur = new double[mActive][mActive];
    for (int i = 0; i < mActive; i++) {
      for (int j = 0; j < mActive; j++) {
        double sum = 0.0;
        for (int k = 0; k < n; k++) {
          sum += aMatrix[i][k] * hInvAt[k][j];
        }
        schur[i][j] = sum;
      }
    }

    // RHS = residual - A * dUnconstrained
    double[] rhs = new double[mActive];
    for (int i = 0; i < mActive; i++) {
      double aDu = 0.0;
      for (int k = 0; k < n; k++) {
        aDu += aMatrix[i][k] * dUnconstrained[k];
      }
      rhs[i] = residual[i] - aDu;
    }

    // Solve for multipliers
    double[] lambdaActive = solveLinearSystem(schur, rhs);

    // Update Lagrange multipliers
    for (int i = 0; i < mEq; i++) {
      lambdaEq[i] = lambdaActive[i];
    }
    for (int k = 0; k < activeIneq.size(); k++) {
      int j = activeIneq.get(k);
      lambdaIneq[j] = Math.max(0.0, lambdaActive[mEq + k]); // Non-negative for inequalities
    }

    // d = dUnconstrained + H^{-1} * A^T * lambda
    double[] dx = Arrays.copyOf(dUnconstrained, n);
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < mActive; j++) {
        dx[i] += hInvAt[i][j] * lambdaActive[j];
      }
    }

    return dx;
  }

  /**
   * L1 exact penalty merit function line search.
   *
   * @param x current point
   * @param dx search direction
   * @param f0 current objective value
   * @param gEq current equality constraint values
   * @param hIneq current inequality constraint values
   * @return step length alpha
   */
  private double lineSearch(double[] x, double[] dx, double f0, double[] gEq, double[] hIneq) {
    double merit0 = computeMerit(f0, gEq, hIneq);
    double alpha = 1.0;

    for (int ls = 0; ls < maxLineSearchIterations; ls++) {
      double[] xTrial = new double[n];
      for (int i = 0; i < n; i++) {
        xTrial[i] = x[i] + alpha * dx[i];
      }
      projectToBounds(xTrial);

      double fTrial = objectiveFunction.evaluate(xTrial);
      double[] gTrial = evaluateConstraints(equalityConstraints, xTrial);
      double[] hTrial = evaluateConstraints(inequalityConstraints, xTrial);
      double meritTrial = computeMerit(fTrial, gTrial, hTrial);

      // Armijo condition on merit function
      if (meritTrial <= merit0 - armijoC1 * alpha * merit0) {
        return alpha;
      }
      alpha *= 0.5;
    }

    return alpha; // Return whatever we have
  }

  /**
   * Compute L1 exact penalty merit function.
   *
   * @param f objective value
   * @param gEq equality constraint values
   * @param hIneq inequality constraint values
   * @return merit function value
   */
  private double computeMerit(double f, double[] gEq, double[] hIneq) {
    double merit = f;
    for (int i = 0; i < gEq.length; i++) {
      merit += penaltyParameter * Math.abs(gEq[i]);
    }
    for (int j = 0; j < hIneq.length; j++) {
      if (hIneq[j] < 0) {
        merit += penaltyParameter * (-hIneq[j]);
      }
    }
    return merit;
  }

  /**
   * Damped BFGS update of the Hessian approximation.
   *
   * @param x new point
   * @param xPrev previous point
   * @param gradF new gradient
   * @param gradPrev previous gradient
   * @param gEq equality constraints (for Lagrangian gradient)
   * @param hIneq inequality constraints (for Lagrangian gradient)
   */
  private void updateBFGS(double[] x, double[] xPrev, double[] gradF, double[] gradPrev,
      double[] gEq, double[] hIneq) {
    double[] s = new double[n]; // Step
    double[] y = new double[n]; // Gradient change

    for (int i = 0; i < n; i++) {
      s[i] = x[i] - xPrev[i];
      y[i] = gradF[i] - gradPrev[i];
    }

    double sy = dotProduct(s, y);
    double[] hs = matVecMult(hessian, s);
    double shs = dotProduct(s, hs);

    // Powell's damped BFGS: ensure positive definiteness
    double theta = 1.0;
    if (sy < 0.2 * shs) {
      theta = 0.8 * shs / (shs - sy);
    }

    double[] r = new double[n];
    for (int i = 0; i < n; i++) {
      r[i] = theta * y[i] + (1.0 - theta) * hs[i];
    }
    double sr = dotProduct(s, r);

    if (Math.abs(sr) < 1e-15 || Math.abs(shs) < 1e-15) {
      return; // Skip update to avoid numerical issues
    }

    // BFGS update: H_new = H - (H*s*s^T*H)/(s^T*H*s) + (r*r^T)/(s^T*r)
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        hessian[i][j] = hessian[i][j] - hs[i] * hs[j] / shs + r[i] * r[j] / sr;
      }
    }
  }

  // ======================== Linear algebra utilities ========================

  /**
   * Solve a linear system A*x = b using Gaussian elimination with partial pivoting.
   *
   * @param aMatrix coefficient matrix
   * @param b right-hand side
   * @return solution vector x
   */
  private double[] solveLinearSystem(double[][] aMatrix, double[] b) {
    int dim = b.length;
    // Augmented matrix
    double[][] aug = new double[dim][dim + 1];
    for (int i = 0; i < dim; i++) {
      System.arraycopy(aMatrix[i], 0, aug[i], 0, dim);
      aug[i][dim] = b[i];
    }

    // Forward elimination with partial pivoting
    for (int col = 0; col < dim; col++) {
      // Find pivot
      int maxRow = col;
      double maxVal = Math.abs(aug[col][col]);
      for (int row = col + 1; row < dim; row++) {
        if (Math.abs(aug[row][col]) > maxVal) {
          maxVal = Math.abs(aug[row][col]);
          maxRow = row;
        }
      }
      // Swap
      double[] temp = aug[col];
      aug[col] = aug[maxRow];
      aug[maxRow] = temp;

      if (Math.abs(aug[col][col]) < 1e-14) {
        // Near-singular: return gradient descent direction
        double[] result = new double[dim];
        for (int i = 0; i < dim; i++) {
          result[i] = -b[i];
        }
        return result;
      }

      // Eliminate
      for (int row = col + 1; row < dim; row++) {
        double factor = aug[row][col] / aug[col][col];
        for (int k = col; k <= dim; k++) {
          aug[row][k] -= factor * aug[col][k];
        }
      }
    }

    // Back substitution
    double[] result = new double[dim];
    for (int i = dim - 1; i >= 0; i--) {
      result[i] = aug[i][dim];
      for (int j = i + 1; j < dim; j++) {
        result[i] -= aug[i][j] * result[j];
      }
      result[i] /= aug[i][i];
    }
    return result;
  }

  /**
   * Invert a matrix using Gauss-Jordan elimination.
   *
   * @param matrix square matrix to invert
   * @return inverse matrix
   */
  private double[][] invertMatrix(double[][] matrix) {
    int dim = matrix.length;
    double[][] aug = new double[dim][2 * dim];

    for (int i = 0; i < dim; i++) {
      System.arraycopy(matrix[i], 0, aug[i], 0, dim);
      aug[i][dim + i] = 1.0;
    }

    for (int col = 0; col < dim; col++) {
      // Pivot
      int maxRow = col;
      for (int row = col + 1; row < dim; row++) {
        if (Math.abs(aug[row][col]) > Math.abs(aug[maxRow][col])) {
          maxRow = row;
        }
      }
      double[] temp = aug[col];
      aug[col] = aug[maxRow];
      aug[maxRow] = temp;

      double pivot = aug[col][col];
      if (Math.abs(pivot) < 1e-14) {
        // Return identity for near-singular
        double[][] identity = new double[dim][dim];
        for (int i = 0; i < dim; i++) {
          identity[i][i] = 1.0;
        }
        return identity;
      }

      for (int k = 0; k < 2 * dim; k++) {
        aug[col][k] /= pivot;
      }

      for (int row = 0; row < dim; row++) {
        if (row != col) {
          double factor = aug[row][col];
          for (int k = 0; k < 2 * dim; k++) {
            aug[row][k] -= factor * aug[col][k];
          }
        }
      }
    }

    double[][] inv = new double[dim][dim];
    for (int i = 0; i < dim; i++) {
      System.arraycopy(aug[i], dim, inv[i], 0, dim);
    }
    return inv;
  }

  /**
   * Negate a vector.
   *
   * @param v input vector
   * @return negated vector
   */
  private double[] negateVector(double[] v) {
    double[] result = new double[v.length];
    for (int i = 0; i < v.length; i++) {
      result[i] = -v[i];
    }
    return result;
  }

  /**
   * Dot product of two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return dot product
   */
  private double dotProduct(double[] a, double[] b) {
    double sum = 0.0;
    for (int i = 0; i < a.length; i++) {
      sum += a[i] * b[i];
    }
    return sum;
  }

  /**
   * Matrix-vector multiplication.
   *
   * @param matrix the matrix
   * @param vec the vector
   * @return result vector
   */
  private double[] matVecMult(double[][] matrix, double[] vec) {
    int rows = matrix.length;
    double[] result = new double[rows];
    for (int i = 0; i < rows; i++) {
      double sum = 0.0;
      for (int j = 0; j < vec.length; j++) {
        sum += matrix[i][j] * vec[j];
      }
      result[i] = sum;
    }
    return result;
  }

  /**
   * Optimization result container.
   */
  public static class OptimizationResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    /** Optimal point. */
    private final double[] optimalPoint;

    /** Optimal objective value. */
    private final double optimalValue;

    /** Number of iterations performed. */
    private final int iterations;

    /** Whether the optimizer converged. */
    private final boolean converged;

    /** Final KKT error. */
    private final double kktError;

    /**
     * Constructor for OptimizationResult.
     *
     * @param optimalPoint optimal variable values
     * @param optimalValue optimal objective value
     * @param iterations number of iterations
     * @param converged whether converged
     * @param kktError final KKT error
     */
    public OptimizationResult(double[] optimalPoint, double optimalValue, int iterations,
        boolean converged, double kktError) {
      this.optimalPoint = Arrays.copyOf(optimalPoint, optimalPoint.length);
      this.optimalValue = optimalValue;
      this.iterations = iterations;
      this.converged = converged;
      this.kktError = kktError;
    }

    /**
     * Get the optimal point.
     *
     * @return optimal variable values
     */
    public double[] getOptimalPoint() {
      return Arrays.copyOf(optimalPoint, optimalPoint.length);
    }

    /**
     * Get the optimal objective value.
     *
     * @return minimum objective value found
     */
    public double getOptimalValue() {
      return optimalValue;
    }

    /**
     * Get the number of iterations.
     *
     * @return iteration count
     */
    public int getIterations() {
      return iterations;
    }

    /**
     * Check if the optimizer converged.
     *
     * @return true if converged to tolerance
     */
    public boolean isConverged() {
      return converged;
    }

    /**
     * Get the final KKT error.
     *
     * @return KKT optimality error
     */
    public double getKktError() {
      return kktError;
    }
  }
}
