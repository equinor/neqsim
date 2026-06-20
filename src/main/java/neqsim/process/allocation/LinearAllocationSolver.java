package neqsim.process.allocation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.simple.SimpleMatrix;

/**
 * Solves the linear production-allocation network per component by superposition.
 *
 * <p>
 * For every component {@code k} the steady-state node inlet flows {@code V_k} (one column per source) satisfy
 * {@code (I - A_k) V_k = B_k}, where {@code A_k} is the routing matrix from {@link AllocationNetwork} and {@code B_k}
 * injects each source's component flow at its entry node. A single LU factorization of {@code (I - A_k)} solves all
 * source right-hand sides at once.
 * </p>
 *
 * <p>
 * If the direct solve fails or returns a non-finite result, the solver falls back to a fixed-point (Neumann series)
 * iteration {@code V &#8592; B + A_k V}, which converges because the network loop gain is below one. Small negative
 * node flows arising from numerical round-off are clipped to zero.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class LinearAllocationSolver implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(LinearAllocationSolver.class);

  /** Maximum fixed-point iterations for the fallback solver. */
  private int maxIterations = 1000;

  /** Convergence tolerance (relative Frobenius residual) for the fallback solver. */
  private double tolerance = 1.0e-10;

  /** Magnitude below which a negative node flow is silently clipped to zero. */
  private double negativeClipTolerance = 1.0e-9;

  /**
   * Result of a network solve: per-component node inlet flows plus per-component diagnostics.
   */
  public static class SolverResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Node inlet flows indexed {@code nodeFlow[componentIndex][nodeIndex][sourceIndex]}. */
    private final double[][][] nodeFlow;

    /** Per-component diagnostics. */
    private final List<ComponentDiagnostics> diagnostics;

    /**
     * Creates a solver result.
     *
     * @param nodeFlow node inlet flows; must be non-null
     * @param diagnostics per-component diagnostics; must be non-null
     */
    SolverResult(double[][][] nodeFlow, List<ComponentDiagnostics> diagnostics) {
      this.nodeFlow = nodeFlow;
      this.diagnostics = diagnostics;
    }

    /**
     * Gets the node inlet flow for a component, node and source.
     *
     * @param componentIndex zero-based component index
     * @param nodeIndex zero-based node index
     * @param sourceIndex zero-based source index
     * @return the node inlet molar flow attributable to the source (mole/sec)
     */
    public double getNodeFlow(int componentIndex, int nodeIndex, int sourceIndex) {
      return nodeFlow[componentIndex][nodeIndex][sourceIndex];
    }

    /**
     * Gets the per-component diagnostics.
     *
     * @return the diagnostics list
     */
    public List<ComponentDiagnostics> getDiagnostics() {
      return diagnostics;
    }
  }

  /**
   * Diagnostics for the solve of a single component.
   */
  public static class ComponentDiagnostics implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Component index on the master slate. */
    private final int componentIndex;

    /** Solve method used: {@code "direct"} or {@code "iterative"}. */
    private final String method;

    /** Relative Frobenius residual of the solution. */
    private final double residual;

    /** Number of fixed-point iterations used (zero for direct solves). */
    private final int iterations;

    /**
     * Creates component diagnostics.
     *
     * @param componentIndex component index
     * @param method solve method label
     * @param residual relative residual
     * @param iterations iteration count
     */
    ComponentDiagnostics(int componentIndex, String method, double residual, int iterations) {
      this.componentIndex = componentIndex;
      this.method = method;
      this.residual = residual;
      this.iterations = iterations;
    }

    /**
     * Gets the component index.
     *
     * @return the component index
     */
    public int getComponentIndex() {
      return componentIndex;
    }

    /**
     * Gets the solve method label.
     *
     * @return {@code "direct"} or {@code "iterative"}
     */
    public String getMethod() {
      return method;
    }

    /**
     * Gets the relative Frobenius residual.
     *
     * @return the residual
     */
    public double getResidual() {
      return residual;
    }

    /**
     * Gets the fixed-point iteration count.
     *
     * @return the iteration count
     */
    public int getIterations() {
      return iterations;
    }
  }

  /**
   * Solves the allocation network for all components and sources.
   *
   * @param network the assembled proxy network; must be non-null
   * @param sourceEntryUnits node index each source enters, length {@code S}; must be non-null
   * @param sourceInjections per-source per-component molar injection {@code [S][numComponents]} in mole/sec; must be
   * non-null
   * @return a {@link SolverResult} with node flows and diagnostics
   */
  public SolverResult solve(AllocationNetwork network, int[] sourceEntryUnits, double[][] sourceInjections) {
    int n = network.getNodeCount();
    int numComp = network.getComponentCount();
    int numSources = sourceEntryUnits.length;

    double[][][] nodeFlow = new double[numComp][n][numSources];
    List<ComponentDiagnostics> diagnostics = new ArrayList<>();

    for (int k = 0; k < numComp; k++) {
      double[][] a = network.buildRoutingMatrix(k);

      // Build B_k (n x S) injecting each source's component-k flow at its entry node.
      SimpleMatrix b = new SimpleMatrix(n, numSources);
      boolean anyInjection = false;
      for (int j = 0; j < numSources; j++) {
	int entry = sourceEntryUnits[j];
	if (entry >= 0) {
	  double inj = sourceInjections[j][k];
	  if (inj != 0.0) {
	    b.set(entry, j, inj);
	    anyInjection = true;
	  }
	}
      }

      if (!anyInjection) {
	diagnostics.add(new ComponentDiagnostics(k, "direct", 0.0, 0));
	continue;
      }

      SimpleMatrix mMatrix = SimpleMatrix.identity(n).minus(toSimple(a));
      SimpleMatrix v = null;
      String method = "direct";
      int iterations = 0;
      try {
	v = mMatrix.solve(b);
	if (!isFinite(v)) {
	  v = null;
	}
      } catch (RuntimeException e) {
	logger.warn("Direct solve failed for component {} ({}); using iterative fallback.", k, e.getMessage());
	v = null;
      }

      if (v == null) {
	SimpleMatrix aSimple = toSimple(a);
	v = b.copy();
	method = "iterative";
	double bNorm = Math.max(b.normF(), 1.0e-30);
	for (iterations = 1; iterations <= maxIterations; iterations++) {
	  SimpleMatrix next = b.plus(aSimple.mult(v));
	  double change = next.minus(v).normF() / bNorm;
	  v = next;
	  if (change < tolerance) {
	    break;
	  }
	}
      }

      double residual = mMatrix.mult(v).minus(b).normF() / Math.max(b.normF(), 1.0e-30);
      diagnostics.add(new ComponentDiagnostics(k, method, residual, iterations));

      for (int u = 0; u < n; u++) {
	for (int j = 0; j < numSources; j++) {
	  double value = v.get(u, j);
	  if (value < 0.0 && value > -negativeClipTolerance) {
	    value = 0.0;
	  }
	  nodeFlow[k][u][j] = value;
	}
      }
    }

    return new SolverResult(nodeFlow, diagnostics);
  }

  /**
   * Converts a primitive matrix to an EJML {@link SimpleMatrix}.
   *
   * @param a the matrix; must be non-null and rectangular
   * @return the equivalent {@code SimpleMatrix}
   */
  private static SimpleMatrix toSimple(double[][] a) {
    int rows = a.length;
    int cols = rows == 0 ? 0 : a[0].length;
    SimpleMatrix m = new SimpleMatrix(rows, cols);
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
	m.set(i, j, a[i][j]);
      }
    }
    return m;
  }

  /**
   * Checks whether all entries of a matrix are finite.
   *
   * @param m the matrix; must be non-null
   * @return {@code true} if every entry is finite
   */
  private static boolean isFinite(SimpleMatrix m) {
    for (int i = 0; i < m.getNumRows(); i++) {
      for (int j = 0; j < m.getNumCols(); j++) {
	if (!Double.isFinite(m.get(i, j))) {
	  return false;
	}
      }
    }
    return true;
  }

  /**
   * Sets the maximum number of fixed-point iterations for the fallback solver.
   *
   * @param maxIterations the iteration cap; must be positive
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Sets the relative residual tolerance for the fallback solver.
   *
   * @param tolerance the tolerance; must be positive
   */
  public void setTolerance(double tolerance) {
    this.tolerance = tolerance;
  }

  /**
   * Sets the magnitude below which negative node flows are clipped to zero.
   *
   * @param negativeClipTolerance the clip tolerance; must be non-negative
   */
  public void setNegativeClipTolerance(double negativeClipTolerance) {
    this.negativeClipTolerance = negativeClipTolerance;
  }
}
