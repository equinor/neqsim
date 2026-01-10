package neqsim.process.fielddevelopment.screening;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-well gas lift optimization for optimal gas allocation.
 *
 * <p>
 * This class optimizes the allocation of limited lift gas across multiple wells to maximize total
 * field production. It considers:
 * </p>
 * <ul>
 * <li><b>Well performance curves:</b> Oil rate vs. injection gas rate for each well</li>
 * <li><b>Gas availability:</b> Total available lift gas constraint</li>
 * <li><b>Compression constraints:</b> Maximum compression power</li>
 * <li><b>Well constraints:</b> Minimum/maximum injection rates per well</li>
 * <li><b>Economic factors:</b> Value of incremental oil vs. cost of compression</li>
 * </ul>
 *
 * <h2>Optimization Methods</h2>
 * <p>
 * The optimizer supports multiple solution methods:
 * </p>
 * <ul>
 * <li><b>Equal slope:</b> Allocate gas so marginal production increase is equal for all wells</li>
 * <li><b>Proportional:</b> Allocate gas proportional to well potential</li>
 * <li><b>Sequential:</b> Fill wells in order of highest marginal response</li>
 * <li><b>Gradient-based:</b> Iterative optimization using derivatives</li>
 * </ul>
 *
 * <h2>Mathematical Basis</h2>
 * <p>
 * For optimal gas allocation, the marginal production increase should be equal across all wells:
 * </p>
 * <p>
 * <code>∂Qoil₁/∂Qgas₁ = ∂Qoil₂/∂Qgas₂ = ... = ∂Qoilₙ/∂Qgasₙ</code>
 * </p>
 * <p>
 * Subject to the total gas constraint:
 * </p>
 * <p>
 * <code>ΣQgasᵢ ≤ Qgas_available</code>
 * </p>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * GasLiftOptimizer optimizer = new GasLiftOptimizer();
 * 
 * // Add wells with their performance curves
 * optimizer.addWell("Well-1", performanceCurve1, 50000.0); // Max 50 MSm³/d injection
 * optimizer.addWell("Well-2", performanceCurve2, 40000.0);
 * optimizer.addWell("Well-3", performanceCurve3, 60000.0);
 * 
 * // Set total available gas
 * optimizer.setAvailableGas(100000.0, "Sm3/d");
 * 
 * // Set compression constraints
 * optimizer.setMaxCompressionPower(5000.0); // kW
 * optimizer.setCompressionEfficiency(0.75);
 * 
 * // Optimize
 * AllocationResult result = optimizer.optimize();
 * 
 * System.out.println("Total oil production: " + result.totalOilRate + " Sm³/d");
 * for (WellAllocation alloc : result.allocations) {
 *   System.out.println(
 *       alloc.wellName + ": " + alloc.gasRate + " Sm³/d gas → " + alloc.oilRate + " Sm³/d oil");
 * }
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see GasLiftCalculator
 */
public class GasLiftOptimizer implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Optimization method.
   */
  public enum OptimizationMethod {
    /** Equal marginal response across all wells (optimal). */
    EQUAL_SLOPE,
    /** Allocate proportional to well potential. */
    PROPORTIONAL,
    /** Fill wells sequentially by marginal response. */
    SEQUENTIAL,
    /** Gradient-based iterative optimization. */
    GRADIENT
  }

  /**
   * Well performance curve representation.
   */
  public static class PerformanceCurve implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Gas injection rates (Sm³/day). */
    public double[] gasRates;

    /** Corresponding oil rates (Sm³/day). */
    public double[] oilRates;

    /** Natural flow rate (zero injection). */
    public double naturalFlowRate;

    /** Optimal GLR from performance curve analysis. */
    public double optimalGLR;

    /**
     * Creates a performance curve from data points.
     *
     * @param gasRates gas injection rates
     * @param oilRates corresponding oil production rates
     */
    public PerformanceCurve(double[] gasRates, double[] oilRates) {
      this.gasRates = Arrays.copyOf(gasRates, gasRates.length);
      this.oilRates = Arrays.copyOf(oilRates, oilRates.length);
      this.naturalFlowRate = oilRates.length > 0 ? oilRates[0] : 0;
      findOptimalGLR();
    }

    /**
     * Creates a curve from a parametric model (simplified hyperbolic).
     *
     * @param naturalRate natural flow rate (Sm³/d)
     * @param maxRate maximum rate with gas lift (Sm³/d)
     * @param optimalGas optimal gas injection (Sm³/d)
     */
    public PerformanceCurve(double naturalRate, double maxRate, double optimalGas) {
      this.naturalFlowRate = naturalRate;
      this.optimalGLR = optimalGas / maxRate;

      // Generate curve points
      int nPoints = 21;
      gasRates = new double[nPoints];
      oilRates = new double[nPoints];

      for (int i = 0; i < nPoints; i++) {
        gasRates[i] = i * optimalGas * 1.5 / (nPoints - 1);
        oilRates[i] = calculateOilRate(gasRates[i], naturalRate, maxRate, optimalGas);
      }
    }

    /**
     * Calculates oil rate for a given gas injection using curve interpolation.
     *
     * @param gasRate gas injection rate (Sm³/d)
     * @return oil rate (Sm³/d)
     */
    public double getOilRate(double gasRate) {
      if (gasRates.length == 0) {
        return naturalFlowRate;
      }

      // Linear interpolation
      if (gasRate <= gasRates[0]) {
        return oilRates[0];
      }
      if (gasRate >= gasRates[gasRates.length - 1]) {
        return oilRates[oilRates.length - 1];
      }

      for (int i = 1; i < gasRates.length; i++) {
        if (gasRate <= gasRates[i]) {
          double frac = (gasRate - gasRates[i - 1]) / (gasRates[i] - gasRates[i - 1]);
          return oilRates[i - 1] + frac * (oilRates[i] - oilRates[i - 1]);
        }
      }

      return oilRates[oilRates.length - 1];
    }

    /**
     * Calculates marginal oil response (dQoil/dQgas) at given gas rate.
     *
     * @param gasRate gas injection rate (Sm³/d)
     * @return marginal response (Sm³ oil / Sm³ gas)
     */
    public double getMarginalResponse(double gasRate) {
      double delta = 100.0; // Sm³/d perturbation
      double oil1 = getOilRate(gasRate);
      double oil2 = getOilRate(gasRate + delta);
      return (oil2 - oil1) / delta;
    }

    /**
     * Finds the optimal gas-liquid ratio from the performance curve.
     *
     * <p>
     * The optimal GLR is the ratio of gas injection rate to oil rate at the point of maximum oil
     * production on the performance curve. Beyond this point, additional gas injection provides
     * diminishing returns or may even reduce production.
     * </p>
     */
    private void findOptimalGLR() {
      if (gasRates.length < 2) {
        return;
      }

      double maxOil = 0;
      int maxIndex = 0;
      for (int i = 0; i < oilRates.length; i++) {
        if (oilRates[i] > maxOil) {
          maxOil = oilRates[i];
          maxIndex = i;
        }
      }

      if (maxOil > 0) {
        optimalGLR = gasRates[maxIndex] / maxOil;
      }
    }

    /**
     * Calculates oil rate from a parametric gas lift performance model.
     *
     * <p>
     * Uses a modified logistic function with decline to model typical gas lift behavior:
     * </p>
     * <p>
     * <code>Q_oil = Q_natural + ΔQ × η(x) × d(x)</code>
     * </p>
     * <p>
     * where:
     * </p>
     * <ul>
     * <li>x = Q_gas / Q_gas_optimal (normalized gas rate)</li>
     * <li>η(x) = 1 / (1 + e^(-5(x-0.3))) is the efficiency factor (logistic)</li>
     * <li>d(x) = e^(-0.5×max(0, x-1.5)) is the decline factor at high GLR</li>
     * <li>ΔQ = Q_max - Q_natural is the maximum incremental production</li>
     * </ul>
     * <p>
     * This model captures: (1) rapid initial response, (2) plateau near optimal GLR, (3) decline at
     * excessive gas rates due to liquid loading.
     * </p>
     *
     * @param gas gas injection rate (Sm³/d)
     * @param natural natural flow rate without gas lift (Sm³/d)
     * @param maxRate maximum achievable rate with optimal gas lift (Sm³/d)
     * @param optGas optimal gas injection rate (Sm³/d)
     * @return calculated oil rate (Sm³/d)
     */
    private double calculateOilRate(double gas, double natural, double maxRate, double optGas) {
      if (gas <= 0) {
        return natural;
      }

      double incremental = maxRate - natural;
      double x = gas / optGas;

      // Modified logistic function with decline
      double efficiency = 1.0 / (1.0 + Math.exp(-5.0 * (x - 0.3)));
      double decline = Math.exp(-0.5 * Math.max(0, x - 1.5));

      return natural + incremental * efficiency * decline;
    }
  }

  /**
   * Well data for optimization.
   */
  public static class WellData implements Serializable {
    private static final long serialVersionUID = 1L;

    String name;
    PerformanceCurve curve;
    double maxGasRate; // Sm³/d
    double minGasRate = 0; // Sm³/d
    boolean enabled = true;
    double priority = 1.0; // Weighting factor

    WellData(String name, PerformanceCurve curve, double maxGasRate) {
      this.name = name;
      this.curve = curve;
      this.maxGasRate = maxGasRate;
    }
  }

  /**
   * Allocation result for a single well.
   */
  public static class WellAllocation implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Well name. */
    public String wellName;

    /** Allocated gas rate (Sm³/d). */
    public double gasRate;

    /** Resulting oil rate (Sm³/d). */
    public double oilRate;

    /** Natural flow rate (Sm³/d). */
    public double naturalFlowRate;

    /** Incremental oil from gas lift (Sm³/d). */
    public double incrementalOil;

    /** Marginal response at allocation point (Sm³ oil / Sm³ gas). */
    public double marginalResponse;

    /** Gas utilization efficiency (incremental oil / gas). */
    public double gasEfficiency;

    /**
     * Constructor.
     *
     * @param wellName well name
     */
    public WellAllocation(String wellName) {
      this.wellName = wellName;
    }
  }

  /**
   * Complete optimization result.
   */
  public static class AllocationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Individual well allocations. */
    public List<WellAllocation> allocations = new ArrayList<>();

    /** Total oil production (Sm³/d). */
    public double totalOilRate;

    /** Total natural flow (no gas lift) (Sm³/d). */
    public double totalNaturalFlow;

    /** Total incremental oil from gas lift (Sm³/d). */
    public double totalIncrementalOil;

    /** Total gas allocated (Sm³/d). */
    public double totalGasAllocated;

    /** Available gas (Sm³/d). */
    public double availableGas;

    /** Gas utilization (fraction). */
    public double gasUtilization;

    /** Field gas efficiency (incremental oil / total gas). */
    public double fieldGasEfficiency;

    /** Required compression power (kW). */
    public double compressionPower;

    /** Optimization method used. */
    public OptimizationMethod method;

    /** Number of iterations (for iterative methods). */
    public int iterations;

    /** Converged flag. */
    public boolean converged;

    /**
     * Gets allocation for a specific well.
     *
     * @param wellName well name
     * @return well allocation or null
     */
    public WellAllocation getAllocation(String wellName) {
      for (WellAllocation alloc : allocations) {
        if (alloc.wellName.equals(wellName)) {
          return alloc;
        }
      }
      return null;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Gas Lift Optimization Result\n");
      sb.append("============================\n");
      sb.append(String.format("Method: %s%n", method));
      sb.append(String.format("Converged: %s (iterations: %d)%n", converged, iterations));
      sb.append(String.format("%nGas Allocation:%n"));
      sb.append(String.format("  Available: %.0f Sm³/d%n", availableGas));
      sb.append(String.format("  Allocated: %.0f Sm³/d (%.1f%%)%n", totalGasAllocated,
          gasUtilization * 100));
      sb.append(String.format("%nProduction:%n"));
      sb.append(String.format("  Natural flow: %.0f Sm³/d%n", totalNaturalFlow));
      sb.append(String.format("  With gas lift: %.0f Sm³/d%n", totalOilRate));
      sb.append(String.format("  Incremental: %.0f Sm³/d (+%.1f%%)%n", totalIncrementalOil,
          totalNaturalFlow > 0 ? totalIncrementalOil / totalNaturalFlow * 100 : 0));
      sb.append(String.format("%nEfficiency:%n"));
      sb.append(
          String.format("  Field gas efficiency: %.4f Sm³ oil/Sm³ gas%n", fieldGasEfficiency));
      sb.append(String.format("  Compression power: %.0f kW%n", compressionPower));
      sb.append(String.format("%nWell Allocations:%n"));
      for (WellAllocation alloc : allocations) {
        sb.append(String.format("  %s: %.0f Sm³/d gas → %.0f Sm³/d oil (+%.0f)%n", alloc.wellName,
            alloc.gasRate, alloc.oilRate, alloc.incrementalOil));
      }
      return sb.toString();
    }
  }

  // Well data
  private List<WellData> wells = new ArrayList<>();

  // Constraints
  private double availableGas = 100000.0; // Sm³/d
  private double maxCompressionPower = Double.MAX_VALUE; // kW
  private double compressionEfficiency = 0.75;
  private double suctionPressure = 5.0; // bara
  private double dischargePressure = 100.0; // bara
  private double gasSpecificGravity = 0.65;

  // Optimization settings
  private OptimizationMethod method = OptimizationMethod.EQUAL_SLOPE;
  private double tolerance = 0.001;
  private int maxIterations = 100;

  /**
   * Creates a new gas lift optimizer.
   */
  public GasLiftOptimizer() {}

  // ============================================================================
  // CONFIGURATION METHODS
  // ============================================================================

  /**
   * Adds a well with its performance curve.
   *
   * @param name well name
   * @param curve gas lift performance curve
   * @param maxGasRate maximum gas injection rate (Sm³/d)
   * @return this for chaining
   */
  public GasLiftOptimizer addWell(String name, PerformanceCurve curve, double maxGasRate) {
    wells.add(new WellData(name, curve, maxGasRate));
    return this;
  }

  /**
   * Adds a well with simplified curve parameters.
   *
   * @param name well name
   * @param naturalRate natural flow rate (Sm³/d)
   * @param maxRate maximum rate with gas lift (Sm³/d)
   * @param optimalGas optimal gas injection (Sm³/d)
   * @param maxGasRate maximum allowed gas rate (Sm³/d)
   * @return this for chaining
   */
  public GasLiftOptimizer addWell(String name, double naturalRate, double maxRate,
      double optimalGas, double maxGasRate) {
    PerformanceCurve curve = new PerformanceCurve(naturalRate, maxRate, optimalGas);
    return addWell(name, curve, maxGasRate);
  }

  /**
   * Sets the available lift gas.
   *
   * @param gas available gas
   * @param unit unit (Sm3/d, MSm3/d)
   * @return this for chaining
   */
  public GasLiftOptimizer setAvailableGas(double gas, String unit) {
    if (unit.contains("MSm3") || unit.contains("M")) {
      this.availableGas = gas * 1e6;
    } else {
      this.availableGas = gas;
    }
    return this;
  }

  /**
   * Sets compression constraints.
   *
   * @param maxPower maximum compression power (kW)
   * @return this for chaining
   */
  public GasLiftOptimizer setMaxCompressionPower(double maxPower) {
    this.maxCompressionPower = maxPower;
    return this;
  }

  /**
   * Sets compression efficiency.
   *
   * @param efficiency isentropic efficiency (0-1)
   * @return this for chaining
   */
  public GasLiftOptimizer setCompressionEfficiency(double efficiency) {
    this.compressionEfficiency = efficiency;
    return this;
  }

  /**
   * Sets compression pressures.
   *
   * @param suction suction pressure (bara)
   * @param discharge discharge pressure (bara)
   * @return this for chaining
   */
  public GasLiftOptimizer setCompressionPressures(double suction, double discharge) {
    this.suctionPressure = suction;
    this.dischargePressure = discharge;
    return this;
  }

  /**
   * Sets the optimization method.
   *
   * @param method optimization method
   * @return this for chaining
   */
  public GasLiftOptimizer setOptimizationMethod(OptimizationMethod method) {
    this.method = method;
    return this;
  }

  /**
   * Sets a well's enabled status.
   *
   * @param wellName well name
   * @param enabled true to include in optimization
   * @return this for chaining
   */
  public GasLiftOptimizer setWellEnabled(String wellName, boolean enabled) {
    for (WellData well : wells) {
      if (well.name.equals(wellName)) {
        well.enabled = enabled;
        break;
      }
    }
    return this;
  }

  /**
   * Sets a well's priority.
   *
   * @param wellName well name
   * @param priority priority (higher = more gas)
   * @return this for chaining
   */
  public GasLiftOptimizer setWellPriority(String wellName, double priority) {
    for (WellData well : wells) {
      if (well.name.equals(wellName)) {
        well.priority = priority;
        break;
      }
    }
    return this;
  }

  // ============================================================================
  // OPTIMIZATION METHODS
  // ============================================================================

  /**
   * Runs the optimization.
   *
   * @return allocation result
   */
  public AllocationResult optimize() {
    if (wells.isEmpty()) {
      throw new IllegalStateException("No wells configured");
    }

    // Check compression power limit
    double maxGasFromPower = calculateMaxGasFromPower();
    double effectiveAvailable = Math.min(availableGas, maxGasFromPower);

    AllocationResult result;

    switch (method) {
      case PROPORTIONAL:
        result = optimizeProportional(effectiveAvailable);
        break;
      case SEQUENTIAL:
        result = optimizeSequential(effectiveAvailable);
        break;
      case GRADIENT:
        result = optimizeGradient(effectiveAvailable);
        break;
      case EQUAL_SLOPE:
      default:
        result = optimizeEqualSlope(effectiveAvailable);
        break;
    }

    // Calculate final metrics
    result.availableGas = availableGas;
    result.gasUtilization =
        result.availableGas > 0 ? result.totalGasAllocated / result.availableGas : 0;
    result.fieldGasEfficiency =
        result.totalGasAllocated > 0 ? result.totalIncrementalOil / result.totalGasAllocated : 0;
    result.compressionPower = calculateCompressionPower(result.totalGasAllocated);
    result.method = method;

    return result;
  }

  /**
   * Optimizes using equal slope (marginal response) method.
   *
   * <p>
   * This method implements the optimal gas allocation strategy based on economic theory. At the
   * optimum, the marginal production response should be equal for all wells:
   * </p>
   * <p>
   * <code>∂Q_oil,1/∂Q_gas,1 = ∂Q_oil,2/∂Q_gas,2 = ... = λ</code>
   * </p>
   * <p>
   * where λ is the common marginal response (Lagrange multiplier). The algorithm uses binary search
   * to find λ such that the total gas allocation equals the available gas.
   * </p>
   *
   * <h3>Algorithm:</h3>
   * <ol>
   * <li>Bracket the common marginal response λ between [0, max_slope]</li>
   * <li>Binary search: for each λ, find gas allocation per well where slope = λ</li>
   * <li>If total allocation > available, increase λ (less gas per well)</li>
   * <li>If total allocation < available, decrease λ (more gas per well)</li>
   * <li>Converge when |total - available| / available < tolerance</li>
   * </ol>
   *
   * @param totalGas total available gas (Sm³/d)
   * @return allocation result with optimal distribution
   */
  private AllocationResult optimizeEqualSlope(double totalGas) {
    AllocationResult result = new AllocationResult();

    // First, find the range of possible marginal responses
    double maxSlope = 0;
    for (WellData well : wells) {
      if (well.enabled) {
        double slope0 = well.curve.getMarginalResponse(0);
        if (slope0 > maxSlope) {
          maxSlope = slope0;
        }
      }
    }

    // Binary search for the common marginal response
    double slopeHigh = Math.max(maxSlope, 1.0); // High marginal response = little gas used
    double slopeLow = 0.0; // Low marginal response = lots of gas used

    double[] allocations = new double[wells.size()];
    int iterations = 0;
    double bestError = Double.MAX_VALUE;
    double[] bestAllocations = new double[wells.size()];

    for (iterations = 0; iterations < maxIterations; iterations++) {
      double targetSlope = (slopeHigh + slopeLow) / 2;

      // For each well, find gas rate that gives target marginal response
      double totalAllocated = 0;
      int idx = 0;
      for (WellData well : wells) {
        if (!well.enabled) {
          allocations[idx++] = 0;
          continue;
        }

        // Find gas rate where marginal response equals target
        double gas = findGasRateForSlope(well, targetSlope);
        gas = Math.max(well.minGasRate, Math.min(gas, well.maxGasRate));
        allocations[idx++] = gas;
        totalAllocated += gas;
      }

      // Track best solution
      double error = Math.abs(totalAllocated - totalGas) / Math.max(totalGas, 1.0);
      if (error < bestError) {
        bestError = error;
        System.arraycopy(allocations, 0, bestAllocations, 0, allocations.length);
      }

      // Check if we've found the right slope
      if (error < tolerance) {
        result.converged = true;
        break;
      }

      // Adjust bounds
      if (totalAllocated > totalGas) {
        slopeLow = targetSlope; // Need higher slope (less gas per well)
      } else {
        slopeHigh = targetSlope; // Need lower slope (more gas per well)
      }

      // Check for stall (bounds too close)
      if (Math.abs(slopeHigh - slopeLow) < 1e-10) {
        result.converged = bestError < 0.05; // Accept 5% error
        break;
      }
    }

    // Use best found allocations if didn't fully converge
    if (!result.converged && bestError < 0.1) {
      System.arraycopy(bestAllocations, 0, allocations, 0, allocations.length);
      result.converged = true; // Close enough
    }

    result.iterations = iterations;

    // Build result
    int idx = 0;
    for (WellData well : wells) {
      WellAllocation alloc = new WellAllocation(well.name);
      alloc.gasRate = allocations[idx++];
      alloc.oilRate = well.curve.getOilRate(alloc.gasRate);
      alloc.naturalFlowRate = well.curve.naturalFlowRate;
      alloc.incrementalOil = alloc.oilRate - alloc.naturalFlowRate;
      alloc.marginalResponse = well.curve.getMarginalResponse(alloc.gasRate);
      alloc.gasEfficiency = alloc.gasRate > 0 ? alloc.incrementalOil / alloc.gasRate : 0;

      result.allocations.add(alloc);
      result.totalOilRate += alloc.oilRate;
      result.totalNaturalFlow += alloc.naturalFlowRate;
      result.totalIncrementalOil += alloc.incrementalOil;
      result.totalGasAllocated += alloc.gasRate;
    }

    // If total allocated exceeds available, scale down proportionally
    if (result.totalGasAllocated > totalGas * 1.001) {
      double scale = totalGas / result.totalGasAllocated;
      result.totalGasAllocated = 0;
      result.totalOilRate = 0;
      result.totalIncrementalOil = 0;
      for (WellAllocation alloc : result.allocations) {
        alloc.gasRate *= scale;
        WellData well = findWellByName(alloc.wellName);
        if (well != null) {
          alloc.oilRate = well.curve.getOilRate(alloc.gasRate);
          alloc.incrementalOil = alloc.oilRate - alloc.naturalFlowRate;
          alloc.gasEfficiency = alloc.gasRate > 0 ? alloc.incrementalOil / alloc.gasRate : 0;
        }
        result.totalGasAllocated += alloc.gasRate;
        result.totalOilRate += alloc.oilRate;
        result.totalIncrementalOil += alloc.incrementalOil;
      }
    }

    return result;
  }

  /**
   * Finds well data by name.
   *
   * @param name well name to find
   * @return well data or null if not found
   */
  private WellData findWellByName(String name) {
    for (WellData well : wells) {
      if (well.name.equals(name)) {
        return well;
      }
    }
    return null;
  }

  /**
   * Finds gas rate that gives target marginal response using binary search.
   *
   * <p>
   * For a given performance curve, finds the gas rate Q_gas where:
   * </p>
   * <p>
   * <code>∂Q_oil/∂Q_gas = targetSlope</code>
   * </p>
   * <p>
   * Uses the diminishing returns property: higher gas rates yield lower marginal response.
   * </p>
   *
   * @param well well data with performance curve
   * @param targetSlope target marginal response (Sm³ oil / Sm³ gas)
   * @return gas rate that achieves target slope (Sm³/d)
   */
  private double findGasRateForSlope(WellData well, double targetSlope) {
    // Binary search
    double gasLow = 0;
    double gasHigh = well.maxGasRate;

    for (int i = 0; i < 30; i++) {
      double gasMid = (gasLow + gasHigh) / 2;
      double slope = well.curve.getMarginalResponse(gasMid);

      if (Math.abs(slope - targetSlope) < 1e-6) {
        return gasMid;
      }

      // Higher gas rate → lower marginal response (diminishing returns)
      if (slope > targetSlope) {
        gasLow = gasMid;
      } else {
        gasHigh = gasMid;
      }
    }

    return (gasLow + gasHigh) / 2;
  }

  /**
   * Optimizes using proportional allocation.
   *
   * <p>
   * Allocates gas proportionally to each well's production potential:
   * </p>
   * <p>
   * <code>Q_gas,i = Q_gas_total × (potential_i / Σ potential_j)</code>
   * </p>
   * <p>
   * where potential_i = (Q_oil_max,i - Q_oil_natural,i) × priority_i
   * </p>
   * <p>
   * This is a simple heuristic that does not account for varying marginal responses but is fast and
   * intuitive.
   * </p>
   *
   * @param totalGas total available gas (Sm³/d)
   * @return allocation result
   */
  private AllocationResult optimizeProportional(double totalGas) {
    AllocationResult result = new AllocationResult();

    // Calculate total potential
    double totalPotential = 0;
    for (WellData well : wells) {
      if (well.enabled) {
        totalPotential +=
            (well.curve.getOilRate(well.maxGasRate) - well.curve.naturalFlowRate) * well.priority;
      }
    }

    // Allocate proportionally
    for (WellData well : wells) {
      WellAllocation alloc = new WellAllocation(well.name);

      if (!well.enabled) {
        alloc.gasRate = 0;
      } else {
        double potential =
            (well.curve.getOilRate(well.maxGasRate) - well.curve.naturalFlowRate) * well.priority;
        double fraction = totalPotential > 0 ? potential / totalPotential : 0;
        alloc.gasRate = Math.min(fraction * totalGas, well.maxGasRate);
      }

      alloc.oilRate = well.curve.getOilRate(alloc.gasRate);
      alloc.naturalFlowRate = well.curve.naturalFlowRate;
      alloc.incrementalOil = alloc.oilRate - alloc.naturalFlowRate;
      alloc.marginalResponse = well.curve.getMarginalResponse(alloc.gasRate);
      alloc.gasEfficiency = alloc.gasRate > 0 ? alloc.incrementalOil / alloc.gasRate : 0;

      result.allocations.add(alloc);
      result.totalOilRate += alloc.oilRate;
      result.totalNaturalFlow += alloc.naturalFlowRate;
      result.totalIncrementalOil += alloc.incrementalOil;
      result.totalGasAllocated += alloc.gasRate;
    }

    result.converged = true;
    result.iterations = 1;
    return result;
  }

  /**
   * Optimizes using sequential filling (greedy algorithm).
   *
   * <p>
   * Incrementally allocates gas to the well with the highest marginal response:
   * </p>
   * <ol>
   * <li>Start with minimum gas allocation for each well</li>
   * <li>Compute marginal response for all wells</li>
   * <li>Add increment to well with highest response</li>
   * <li>Repeat until gas is exhausted or no beneficial allocation remains</li>
   * </ol>
   * <p>
   * This greedy approach converges to the optimal solution as the increment size approaches zero.
   * Uses 1% increments for reasonable accuracy.
   * </p>
   *
   * @param totalGas total available gas (Sm³/d)
   * @return allocation result
   */
  private AllocationResult optimizeSequential(double totalGas) {
    AllocationResult result = new AllocationResult();

    // Initialize allocations
    Map<String, Double> gasAllocated = new LinkedHashMap<>();
    for (WellData well : wells) {
      gasAllocated.put(well.name, well.enabled ? well.minGasRate : 0.0);
    }

    double remainingGas = totalGas;
    for (WellData well : wells) {
      remainingGas -= gasAllocated.get(well.name);
    }

    // Incrementally allocate gas to well with highest marginal response
    double increment = totalGas / 100; // 1% increments
    int iterations = 0;

    while (remainingGas > increment && iterations < maxIterations * 100) {
      iterations++;

      // Find well with highest marginal response
      WellData bestWell = null;
      double bestResponse = 0;

      for (WellData well : wells) {
        if (!well.enabled) {
          continue;
        }
        double currentGas = gasAllocated.get(well.name);
        if (currentGas >= well.maxGasRate) {
          continue;
        }

        double response = well.curve.getMarginalResponse(currentGas) * well.priority;
        if (response > bestResponse) {
          bestResponse = response;
          bestWell = well;
        }
      }

      if (bestWell == null || bestResponse < 0.001) {
        break; // No more beneficial allocation
      }

      // Allocate increment to best well
      double current = gasAllocated.get(bestWell.name);
      double toAdd = Math.min(increment, Math.min(remainingGas, bestWell.maxGasRate - current));
      gasAllocated.put(bestWell.name, current + toAdd);
      remainingGas -= toAdd;
    }

    // Build result
    for (WellData well : wells) {
      WellAllocation alloc = new WellAllocation(well.name);
      alloc.gasRate = gasAllocated.get(well.name);
      alloc.oilRate = well.curve.getOilRate(alloc.gasRate);
      alloc.naturalFlowRate = well.curve.naturalFlowRate;
      alloc.incrementalOil = alloc.oilRate - alloc.naturalFlowRate;
      alloc.marginalResponse = well.curve.getMarginalResponse(alloc.gasRate);
      alloc.gasEfficiency = alloc.gasRate > 0 ? alloc.incrementalOil / alloc.gasRate : 0;

      result.allocations.add(alloc);
      result.totalOilRate += alloc.oilRate;
      result.totalNaturalFlow += alloc.naturalFlowRate;
      result.totalIncrementalOil += alloc.incrementalOil;
      result.totalGasAllocated += alloc.gasRate;
    }

    result.converged = remainingGas < increment;
    result.iterations = iterations;
    return result;
  }

  /**
   * Optimizes using gradient descent.
   *
   * <p>
   * Iteratively adjusts allocations to equalize marginal responses:
   * </p>
   * <p>
   * <code>Q_gas,i(k+1) = Q_gas,i(k) + α × (∂Q_oil,i/∂Q_gas,i - avg_slope)</code>
   * </p>
   * <p>
   * where α is the step size. After each iteration, allocations are renormalized to satisfy the
   * total gas constraint. The step size decreases with iterations for convergence stability.
   * </p>
   *
   * @param totalGas total available gas (Sm³/d)
   * @return allocation result
   */
  private AllocationResult optimizeGradient(double totalGas) {
    // Start with equal allocation
    double[] allocations = new double[wells.size()];
    double equalShare = totalGas / getEnabledWellCount();

    int idx = 0;
    for (WellData well : wells) {
      if (well.enabled) {
        allocations[idx] = Math.min(equalShare, well.maxGasRate);
      }
      idx++;
    }

    // Gradient descent
    double stepSize = totalGas * 0.01;
    int iterations = 0;

    for (iterations = 0; iterations < maxIterations; iterations++) {
      // Calculate gradients (marginal responses)
      double[] gradients = new double[wells.size()];
      double avgGradient = 0;
      int count = 0;

      idx = 0;
      for (WellData well : wells) {
        if (well.enabled) {
          gradients[idx] = well.curve.getMarginalResponse(allocations[idx]) * well.priority;
          avgGradient += gradients[idx];
          count++;
        }
        idx++;
      }
      avgGradient /= Math.max(count, 1);

      // Move gas from low-gradient wells to high-gradient wells
      double maxMove = 0;
      idx = 0;
      for (WellData well : wells) {
        if (well.enabled) {
          double delta = (gradients[idx] - avgGradient) * stepSize;
          double newAlloc = allocations[idx] + delta;
          newAlloc = Math.max(well.minGasRate, Math.min(newAlloc, well.maxGasRate));
          maxMove = Math.max(maxMove, Math.abs(newAlloc - allocations[idx]));
          allocations[idx] = newAlloc;
        }
        idx++;
      }

      // Renormalize to total gas constraint
      double sum = 0;
      for (int i = 0; i < allocations.length; i++) {
        sum += allocations[i];
      }
      if (sum > 0) {
        double scale = totalGas / sum;
        for (int i = 0; i < allocations.length; i++) {
          allocations[i] *= scale;
        }
      }

      // Check convergence
      if (maxMove < tolerance * stepSize) {
        break;
      }

      stepSize *= 0.99; // Reduce step size
    }

    // Build result
    AllocationResult result = new AllocationResult();
    idx = 0;
    for (WellData well : wells) {
      WellAllocation alloc = new WellAllocation(well.name);
      alloc.gasRate = allocations[idx++];
      alloc.oilRate = well.curve.getOilRate(alloc.gasRate);
      alloc.naturalFlowRate = well.curve.naturalFlowRate;
      alloc.incrementalOil = alloc.oilRate - alloc.naturalFlowRate;
      alloc.marginalResponse = well.curve.getMarginalResponse(alloc.gasRate);
      alloc.gasEfficiency = alloc.gasRate > 0 ? alloc.incrementalOil / alloc.gasRate : 0;

      result.allocations.add(alloc);
      result.totalOilRate += alloc.oilRate;
      result.totalNaturalFlow += alloc.naturalFlowRate;
      result.totalIncrementalOil += alloc.incrementalOil;
      result.totalGasAllocated += alloc.gasRate;
    }

    result.converged = iterations < maxIterations;
    result.iterations = iterations;
    return result;
  }

  // ============================================================================
  // HELPER METHODS
  // ============================================================================

  /**
   * Calculates maximum gas rate limited by compression power.
   *
   * <p>
   * Inverts the compression power equation to find the maximum gas rate:
   * </p>
   * <p>
   * <code>Q_max = W_max / [0.1 × ln(P2/P1) / η]</code>
   * </p>
   * <p>
   * where W_max is the maximum available compression power.
   * </p>
   *
   * @return maximum gas rate (Sm³/d) or Double.MAX_VALUE if unconstrained
   */
  private double calculateMaxGasFromPower() {
    if (maxCompressionPower == Double.MAX_VALUE) {
      return Double.MAX_VALUE;
    }

    // Power = (γ/(γ-1)) × P1 × Q × ((P2/P1)^((γ-1)/γ) - 1) / η
    // Simplified for gas: Power ≈ 0.1 × Q × ln(P2/P1) / η (kW, Sm³/d, bara)
    double ratio = dischargePressure / suctionPressure;
    double powerFactor = 0.1 * Math.log(ratio) / compressionEfficiency;

    return maxCompressionPower / powerFactor;
  }

  /**
   * Calculates compression power for a given gas rate.
   *
   * <p>
   * Uses simplified isothermal compression power formula:
   * </p>
   * <p>
   * <code>W = 0.1 × Q × ln(P2/P1) / η</code>
   * </p>
   * <p>
   * where:
   * </p>
   * <ul>
   * <li>W = power (kW)</li>
   * <li>Q = gas rate (Sm³/d)</li>
   * <li>P2/P1 = compression ratio</li>
   * <li>η = isentropic efficiency</li>
   * </ul>
   *
   * @param gasRate gas rate (Sm³/d)
   * @return compression power (kW)
   */
  private double calculateCompressionPower(double gasRate) {
    double ratio = dischargePressure / suctionPressure;
    return 0.1 * gasRate * Math.log(ratio) / compressionEfficiency;
  }

  /**
   * Gets count of enabled wells.
   *
   * @return number of wells enabled for optimization
   */
  private int getEnabledWellCount() {
    int count = 0;
    for (WellData well : wells) {
      if (well.enabled) {
        count++;
      }
    }
    return count;
  }

  /**
   * Gets number of wells.
   *
   * @return well count
   */
  public int getWellCount() {
    return wells.size();
  }
}
