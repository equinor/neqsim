package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global pressure-flow solver for an integrated production network.
 *
 * <p>
 * This solver mirrors the architecture of commercial network tools (Petex GAP, Schlumberger Pipesim): a set of
 * {@link NetworkNode nodes} are connected by {@link NetworkBranch branches} (wells, flowlines, chokes). Some node
 * pressures are fixed boundary conditions (reservoir datum, separator/export header); the remaining free node pressures
 * are found by enforcing volumetric mass balance at every free node.
 * </p>
 *
 * <p>
 * The unknowns are the free-node pressures. For each free node the residual is
 * </p>
 *
 * <p>
 * R = Q_external + &Sigma;(branch flows in) - &Sigma;(branch flows out)
 * </p>
 *
 * <p>
 * which must be zero at convergence. The system is solved with a damped global Newton-Raphson iteration using a
 * numerical (finite-difference) Jacobian and a self-contained Gaussian elimination with partial pivoting. If Newton
 * stalls, a successive-substitution fallback is used. Branch flow evaluations are cheap because each branch hides its
 * physics behind a surrogate (deliverability curve, quadratic pressure drop), so no thermodynamic flash runs inside the
 * Jacobian loop.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see NetworkNode
 * @see NetworkBranch
 * @see WellBranch
 * @see FlowlineBranch
 */
public class NetworkNewtonSolver implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Map<String, NetworkNode> nodes = new LinkedHashMap<String, NetworkNode>();
  private final List<NetworkBranch> branches = new ArrayList<NetworkBranch>();

  private int maxIterations = 100;
  private double tolerance = 1.0; // Sm3/day mass-balance residual tolerance
  private double minPressure = 1.0; // bara lower bound for free nodes

  /**
   * Result of a network solve.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class NetworkSolutionResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final boolean converged;
    private final int iterations;
    private final double maxResidual;
    private final Map<String, Double> nodePressures;
    private final Map<String, Double> branchFlows;
    private final String method;

    /**
     * Creates a solution result.
     *
     * @param converged whether the solve converged
     * @param iterations number of iterations used
     * @param maxResidual final maximum absolute node residual in Sm3/day
     * @param nodePressures solved node pressures in bara keyed by node name
     * @param branchFlows branch flows in Sm3/day keyed by branch name
     * @param method solver method that produced the result
     */
    public NetworkSolutionResult(boolean converged, int iterations, double maxResidual,
	Map<String, Double> nodePressures, Map<String, Double> branchFlows, String method) {
      this.converged = converged;
      this.iterations = iterations;
      this.maxResidual = maxResidual;
      this.nodePressures = nodePressures;
      this.branchFlows = branchFlows;
      this.method = method;
    }

    /**
     * Returns whether the solve converged.
     *
     * @return true if converged
     */
    public boolean isConverged() {
      return converged;
    }

    /**
     * Returns the number of iterations.
     *
     * @return iteration count
     */
    public int getIterations() {
      return iterations;
    }

    /**
     * Returns the final maximum absolute node residual.
     *
     * @return residual in Sm3/day
     */
    public double getMaxResidual() {
      return maxResidual;
    }

    /**
     * Returns the solved node pressures.
     *
     * @return map of node name to pressure in bara
     */
    public Map<String, Double> getNodePressures() {
      return nodePressures;
    }

    /**
     * Returns the branch flows.
     *
     * @return map of branch name to flow in Sm3/day
     */
    public Map<String, Double> getBranchFlows() {
      return branchFlows;
    }

    /**
     * Returns the solver method used.
     *
     * @return solver method name
     */
    public String getMethod() {
      return method;
    }
  }

  /**
   * Adds a node to the network.
   *
   * @param node the node to add
   * @return this solver for chaining
   */
  public NetworkNewtonSolver addNode(NetworkNode node) {
    nodes.put(node.getName(), node);
    return this;
  }

  /**
   * Adds a branch to the network.
   *
   * @param branch the branch to add
   * @return this solver for chaining
   */
  public NetworkNewtonSolver addBranch(NetworkBranch branch) {
    branches.add(branch);
    return this;
  }

  /**
   * Sets the maximum number of iterations.
   *
   * @param maxIterations iteration limit
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Sets the mass-balance residual tolerance.
   *
   * @param tolerance residual tolerance in Sm3/day
   */
  public void setTolerance(double tolerance) {
    this.tolerance = tolerance;
  }

  /**
   * Sets the minimum allowed pressure for free nodes.
   *
   * @param minPressureBara lower pressure bound in bara
   */
  public void setMinPressure(double minPressureBara) {
    this.minPressure = minPressureBara;
  }

  /**
   * Returns a node by name.
   *
   * @param name node name
   * @return the node, or null if not present
   */
  public NetworkNode getNode(String name) {
    return nodes.get(name);
  }

  /**
   * Returns the branches.
   *
   * @return list of branches
   */
  public List<NetworkBranch> getBranches() {
    return branches;
  }

  /**
   * Solves the network pressure-flow balance.
   *
   * @return the network solution result
   */
  public NetworkSolutionResult solve() {
    List<NetworkNode> free = new ArrayList<NetworkNode>();
    for (NetworkNode n : nodes.values()) {
      if (!n.isPressureFixed()) {
	free.add(n);
      }
    }
    int m = free.size();
    if (m == 0) {
      return buildResult(true, 0, 0.0, "trivial");
    }

    double[] p = new double[m];
    for (int i = 0; i < m; i++) {
      p[i] = Math.max(minPressure, free.get(i).getPressure());
    }

    boolean converged = false;
    int iter = 0;
    double maxRes = Double.MAX_VALUE;
    for (iter = 1; iter <= maxIterations; iter++) {
      applyPressures(free, p);
      double[] r = residuals(free);
      maxRes = maxAbs(r);
      if (maxRes < tolerance) {
	converged = true;
	break;
      }
      double[][] j = jacobian(free, p, r);
      double[] dp = gaussianSolve(j, negate(r));
      if (dp == null) {
	break; // singular; fall through to successive substitution
      }
      // Damped line search on the infinity-norm of the residual.
      double lambda = 1.0;
      double bestRes = maxRes;
      double[] bestP = p.clone();
      for (int ls = 0; ls < 8; ls++) {
	double[] trial = new double[m];
	for (int i = 0; i < m; i++) {
	  trial[i] = Math.max(minPressure, p[i] + lambda * dp[i]);
	}
	applyPressures(free, trial);
	double res = maxAbs(residuals(free));
	if (res < bestRes) {
	  bestRes = res;
	  bestP = trial;
	  break;
	}
	lambda *= 0.5;
      }
      p = bestP;
    }

    if (!converged) {
      // Successive-substitution fallback: relax each free node toward balance.
      NetworkSolutionResult ss = successiveSubstitution(free, p);
      if (ss != null) {
	return ss;
      }
    }
    applyPressures(free, p);
    return buildResult(converged, iter, maxRes, "newton");
  }

  /**
   * Successive-substitution fallback solver.
   *
   * @param free free nodes
   * @param startPressures starting pressures in bara
   * @return a result if it converges, otherwise null
   */
  private NetworkSolutionResult successiveSubstitution(List<NetworkNode> free, double[] startPressures) {
    int m = free.size();
    double[] p = startPressures.clone();
    double relax = 0.3;
    double maxRes = Double.MAX_VALUE;
    for (int iter = 1; iter <= maxIterations * 4; iter++) {
      applyPressures(free, p);
      double[] r = residuals(free);
      maxRes = maxAbs(r);
      if (maxRes < tolerance) {
	applyPressures(free, p);
	return buildResult(true, iter, maxRes, "successive-substitution");
      }
      for (int i = 0; i < m; i++) {
	// Numerical sensitivity of residual i to its own pressure.
	double dpi = 0.01 * Math.max(1.0, p[i]);
	double base = nodeResidual(free.get(i));
	double saved = p[i];
	p[i] = Math.max(minPressure, saved + dpi);
	applyPressures(free, p);
	double pert = nodeResidual(free.get(i));
	double deriv = (pert - base) / dpi;
	p[i] = saved;
	applyPressures(free, p);
	if (Math.abs(deriv) > 1.0e-12) {
	  p[i] = Math.max(minPressure, p[i] - relax * base / deriv);
	}
      }
    }
    applyPressures(free, p);
    return buildResult(false, maxIterations * 4, maxRes, "successive-substitution");
  }

  /**
   * Applies a pressure vector to the free nodes.
   *
   * @param free free nodes
   * @param p pressures in bara
   */
  private void applyPressures(List<NetworkNode> free, double[] p) {
    for (int i = 0; i < free.size(); i++) {
      free.get(i).setPressure(p[i]);
    }
  }

  /**
   * Computes the residual vector over the free nodes.
   *
   * @param free free nodes
   * @return residuals in Sm3/day
   */
  private double[] residuals(List<NetworkNode> free) {
    double[] r = new double[free.size()];
    for (int i = 0; i < free.size(); i++) {
      r[i] = nodeResidual(free.get(i));
    }
    return r;
  }

  /**
   * Computes the volumetric balance residual at a node.
   *
   * @param node the node
   * @return residual in Sm3/day (inflow - outflow + external)
   */
  private double nodeResidual(NetworkNode node) {
    double sum = node.getExternalRate();
    for (NetworkBranch b : branches) {
      NetworkNode from = nodes.get(b.getFromNode());
      NetworkNode to = nodes.get(b.getToNode());
      if (from == null || to == null) {
	continue;
      }
      if (b.getToNode().equals(node.getName())) {
	sum += b.flow(from.getPressure(), to.getPressure());
      } else if (b.getFromNode().equals(node.getName())) {
	sum -= b.flow(from.getPressure(), to.getPressure());
      }
    }
    return sum;
  }

  /**
   * Builds the finite-difference Jacobian of the residuals with respect to free-node pressures.
   *
   * @param free free nodes
   * @param p current pressures in bara
   * @param r residuals at the current pressures
   * @return the Jacobian matrix
   */
  private double[][] jacobian(List<NetworkNode> free, double[] p, double[] r) {
    int m = free.size();
    double[][] jac = new double[m][m];
    for (int col = 0; col < m; col++) {
      double dp = 0.001 * Math.max(1.0, p[col]);
      double saved = p[col];
      p[col] = saved + dp;
      applyPressures(free, p);
      double[] rp = residuals(free);
      p[col] = saved;
      applyPressures(free, p);
      for (int row = 0; row < m; row++) {
	jac[row][col] = (rp[row] - r[row]) / dp;
      }
    }
    return jac;
  }

  /**
   * Solves a dense linear system A x = b by Gaussian elimination with partial pivoting.
   *
   * @param a coefficient matrix (modified in place)
   * @param b right-hand side (modified in place)
   * @return the solution vector, or null if the matrix is singular
   */
  private double[] gaussianSolve(double[][] a, double[] b) {
    int n = b.length;
    for (int col = 0; col < n; col++) {
      int pivot = col;
      double max = Math.abs(a[col][col]);
      for (int row = col + 1; row < n; row++) {
	if (Math.abs(a[row][col]) > max) {
	  max = Math.abs(a[row][col]);
	  pivot = row;
	}
      }
      if (max < 1.0e-14) {
	return null;
      }
      if (pivot != col) {
	double[] tmp = a[col];
	a[col] = a[pivot];
	a[pivot] = tmp;
	double tb = b[col];
	b[col] = b[pivot];
	b[pivot] = tb;
      }
      for (int row = col + 1; row < n; row++) {
	double factor = a[row][col] / a[col][col];
	for (int k = col; k < n; k++) {
	  a[row][k] -= factor * a[col][k];
	}
	b[row] -= factor * b[col];
      }
    }
    double[] x = new double[n];
    for (int row = n - 1; row >= 0; row--) {
      double sum = b[row];
      for (int k = row + 1; k < n; k++) {
	sum -= a[row][k] * x[k];
      }
      x[row] = sum / a[row][row];
    }
    return x;
  }

  /**
   * Negates a vector.
   *
   * @param v input vector
   * @return the negated vector
   */
  private double[] negate(double[] v) {
    double[] out = new double[v.length];
    for (int i = 0; i < v.length; i++) {
      out[i] = -v[i];
    }
    return out;
  }

  /**
   * Returns the maximum absolute value of a vector.
   *
   * @param v input vector
   * @return infinity norm
   */
  private double maxAbs(double[] v) {
    double max = 0.0;
    for (int i = 0; i < v.length; i++) {
      max = Math.max(max, Math.abs(v[i]));
    }
    return max;
  }

  /**
   * Builds a solution result snapshot from the current node pressures and branch flows.
   *
   * @param converged whether the solve converged
   * @param iterations number of iterations
   * @param maxResidual final maximum residual in Sm3/day
   * @param method solver method name
   * @return the solution result
   */
  private NetworkSolutionResult buildResult(boolean converged, int iterations, double maxResidual, String method) {
    Map<String, Double> pressures = new LinkedHashMap<String, Double>();
    for (NetworkNode n : nodes.values()) {
      pressures.put(n.getName(), n.getPressure());
    }
    Map<String, Double> flows = new LinkedHashMap<String, Double>();
    for (NetworkBranch b : branches) {
      NetworkNode from = nodes.get(b.getFromNode());
      NetworkNode to = nodes.get(b.getToNode());
      if (from != null && to != null) {
	flows.put(b.getName(), b.flow(from.getPressure(), to.getPressure()));
      }
    }
    return new NetworkSolutionResult(converged, iterations, maxResidual, pressures, flows, method);
  }
}
