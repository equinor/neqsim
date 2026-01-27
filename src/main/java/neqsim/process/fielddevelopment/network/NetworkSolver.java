package neqsim.process.fielddevelopment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.reservoir.WellSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Production network solver for multi-well gathering systems.
 *
 * <p>
 * This class solves the pressure-flow equilibrium in a gathering network
 * connecting multiple wells
 * to a common manifold or host facility. It supports:
 * </p>
 * <ul>
 * <li>Multiple production wells with IPR+VLP models</li>
 * <li>Flowlines from wellhead to manifold</li>
 * <li>Common manifold pressure constraint</li>
 * <li>Total production rate constraint (facility capacity)</li>
 * <li>Choke/valve allocation for production optimization</li>
 * </ul>
 *
 * <h2>Network Topology</h2>
 * 
 * <pre>
 * Well-1 (IPR+VLP) ──┬─ Flowline-1 ──┐
 * Well-2 (IPR+VLP) ──┼─ Flowline-2 ──┼── Manifold ── Export
 * Well-3 (IPR+VLP) ──┴─ Flowline-3 ──┘
 * </pre>
 *
 * <h2>Solution Method</h2>
 * <p>
 * The solver uses successive substitution with under-relaxation:
 * </p>
 * <ol>
 * <li>Assume manifold pressure</li>
 * <li>Calculate each well's production at that backpressure</li>
 * <li>Calculate flowline pressure drops</li>
 * <li>Adjust manifold pressure to satisfy pressure balance</li>
 * <li>Repeat until convergence</li>
 * </ol>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * NetworkSolver network = new NetworkSolver("Subsea Gathering");
 * 
 * // Add wells
 * network.addWell(well1, 5.0); // 5 km flowline
 * network.addWell(well2, 8.0); // 8 km flowline
 * network.addWell(well3, 3.0); // 3 km flowline
 * 
 * // Set constraints
 * network.setManifoldPressure(50.0, "bara");
 * network.setMaxTotalRate(15.0e6, "Sm3/day");
 * 
 * // Solve
 * NetworkResult result = network.solve();
 * System.out.println("Total rate: " + result.getTotalRate("MSm3/day"));
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see WellSystem
 */
public class NetworkSolver implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Solution mode for the network.
   */
  public enum SolutionMode {
    /** Fixed manifold pressure - calculate resulting rates. */
    FIXED_MANIFOLD_PRESSURE,
    /** Fixed total rate - calculate required manifold pressure. */
    FIXED_TOTAL_RATE,
    /** Optimize allocation across wells. */
    OPTIMIZE_ALLOCATION
  }

  // Configuration
  private String name;
  private SolutionMode solutionMode = SolutionMode.FIXED_MANIFOLD_PRESSURE;
  private double manifoldPressure = 50.0; // bara
  private double maxTotalRate = Double.MAX_VALUE; // Sm3/day
  private double targetTotalRate = 0; // Sm3/day (for FIXED_TOTAL_RATE mode)

  // Wells and flowlines
  private List<WellNode> wellNodes = new ArrayList<>();
  private SystemInterface referenceFluid;

  // Solver parameters
  private double tolerance = 0.001; // 0.1% convergence
  private int maxIterations = 100;
  private double relaxationFactor = 0.5;

  // Results
  private boolean solved = false;
  private int lastIterations;
  private double lastResidual;

  /**
   * Well node in the network.
   */
  public static class WellNode implements Serializable {
    private static final long serialVersionUID = 1000L;

    String name;
    WellSystem well;
    double flowlineLength; // km
    double flowlineDiameter = 0.15; // m (6 inch default)
    double flowlineRoughness = 0.00005; // m
    boolean enabled = true;
    double chokeOpening = 1.0; // 0-1
    double allocatedRate; // Sm3/day (result)
    double wellheadPressure; // bara (result)
    double flowlinePressureDrop; // bar (result)

    WellNode(String name, WellSystem well, double flowlineLength) {
      this.name = name;
      this.well = well;
      this.flowlineLength = flowlineLength;
    }
  }

  /**
   * Creates a new network solver.
   *
   * @param name network name
   */
  public NetworkSolver(String name) {
    this.name = name;
  }

  // ============================================================================
  // CONFIGURATION METHODS
  // ============================================================================

  /**
   * Adds a well to the network.
   *
   * @param well             integrated well system
   * @param flowlineLengthKm flowline length in km
   * @return this for chaining
   */
  public NetworkSolver addWell(WellSystem well, double flowlineLengthKm) {
    wellNodes.add(new WellNode(well.getName(), well, flowlineLengthKm));
    return this;
  }

  /**
   * Adds a well with flowline specifications.
   *
   * @param well              integrated well system
   * @param flowlineLengthKm  flowline length in km
   * @param flowlineDiameterM flowline inner diameter in meters
   * @return this for chaining
   */
  public NetworkSolver addWell(WellSystem well, double flowlineLengthKm, double flowlineDiameterM) {
    WellNode node = new WellNode(well.getName(), well, flowlineLengthKm);
    node.flowlineDiameter = flowlineDiameterM;
    wellNodes.add(node);
    return this;
  }

  /**
   * Sets the solution mode.
   *
   * @param mode solution mode
   * @return this for chaining
   */
  public NetworkSolver setSolutionMode(SolutionMode mode) {
    this.solutionMode = mode;
    return this;
  }

  /**
   * Sets the manifold pressure constraint.
   *
   * @param pressure manifold pressure
   * @param unit     pressure unit
   * @return this for chaining
   */
  public NetworkSolver setManifoldPressure(double pressure, String unit) {
    if (unit.equalsIgnoreCase("bara") || unit.equalsIgnoreCase("bar")) {
      this.manifoldPressure = pressure;
    } else if (unit.equalsIgnoreCase("psia") || unit.equalsIgnoreCase("psi")) {
      this.manifoldPressure = pressure / 14.504;
    } else if (unit.equalsIgnoreCase("MPa")) {
      this.manifoldPressure = pressure * 10.0;
    }
    return this;
  }

  /**
   * Sets the maximum total production rate constraint.
   *
   * @param rate maximum rate
   * @param unit rate unit
   * @return this for chaining
   */
  public NetworkSolver setMaxTotalRate(double rate, String unit) {
    this.maxTotalRate = convertRateToSm3PerDay(rate, unit);
    return this;
  }

  /**
   * Sets the target total rate for FIXED_TOTAL_RATE mode.
   *
   * @param rate target rate
   * @param unit rate unit
   * @return this for chaining
   */
  public NetworkSolver setTargetTotalRate(double rate, String unit) {
    this.targetTotalRate = convertRateToSm3PerDay(rate, unit);
    this.solutionMode = SolutionMode.FIXED_TOTAL_RATE;
    return this;
  }

  /**
   * Sets the reference fluid for pressure drop calculations.
   *
   * @param fluid thermodynamic system
   * @return this for chaining
   */
  public NetworkSolver setReferenceFluid(SystemInterface fluid) {
    this.referenceFluid = fluid;
    return this;
  }

  /**
   * Enables or disables a well.
   *
   * @param wellName well name
   * @param enabled  true to enable, false to shut in
   * @return this for chaining
   */
  public NetworkSolver setWellEnabled(String wellName, boolean enabled) {
    for (WellNode node : wellNodes) {
      if (node.name.equals(wellName)) {
        node.enabled = enabled;
        break;
      }
    }
    return this;
  }

  /**
   * Sets the choke opening for a well.
   *
   * @param wellName well name
   * @param opening  choke opening (0-1)
   * @return this for chaining
   */
  public NetworkSolver setChokeOpening(String wellName, double opening) {
    for (WellNode node : wellNodes) {
      if (node.name.equals(wellName)) {
        node.chokeOpening = Math.max(0, Math.min(1, opening));
        break;
      }
    }
    return this;
  }

  /**
   * Sets solver parameters.
   *
   * @param tolerance  convergence tolerance (fraction)
   * @param maxIter    maximum iterations
   * @param relaxation under-relaxation factor
   * @return this for chaining
   */
  public NetworkSolver setSolverParameters(double tolerance, int maxIter, double relaxation) {
    this.tolerance = tolerance;
    this.maxIterations = maxIter;
    this.relaxationFactor = relaxation;
    return this;
  }

  // ============================================================================
  // SOLUTION METHODS
  // ============================================================================

  /**
   * Solves the network for pressure-flow equilibrium.
   *
   * @return network result
   */
  public NetworkResult solve() {
    if (wellNodes.isEmpty()) {
      throw new IllegalStateException("No wells in network");
    }

    solved = false;

    switch (solutionMode) {
      case FIXED_MANIFOLD_PRESSURE:
        solveFixedManifoldPressure();
        break;
      case FIXED_TOTAL_RATE:
        solveFixedTotalRate();
        break;
      case OPTIMIZE_ALLOCATION:
        solveOptimizeAllocation();
        break;
    }

    solved = true;
    return buildResult();
  }

  /**
   * Solves with fixed manifold pressure - calculates resulting well rates.
   */
  private void solveFixedManifoldPressure() {
    for (int iter = 0; iter < maxIterations; iter++) {
      double totalRate = 0;
      double maxChange = 0;

      for (WellNode node : wellNodes) {
        if (!node.enabled) {
          node.allocatedRate = 0;
          continue;
        }

        // Calculate wellhead pressure required to deliver to manifold
        double whpRequired = manifoldPressure + estimateFlowlinePressureDrop(node);

        // Set wellhead pressure on well and solve
        node.well.setWellheadPressure(whpRequired, "bara");
        node.well.run();

        double newRate = node.well.getOperatingFlowRate("Sm3/day") * node.chokeOpening;
        double change = Math.abs(newRate - node.allocatedRate) / Math.max(newRate, 1);
        maxChange = Math.max(maxChange, change);

        node.allocatedRate = node.allocatedRate * (1 - relaxationFactor) + newRate * relaxationFactor;
        node.wellheadPressure = whpRequired;
        node.flowlinePressureDrop = estimateFlowlinePressureDrop(node);

        totalRate += node.allocatedRate;
      }

      lastIterations = iter + 1;
      lastResidual = maxChange;

      if (maxChange < tolerance) {
        break;
      }
    }

    // Apply facility capacity constraint
    double totalRate = wellNodes.stream().filter(n -> n.enabled).mapToDouble(n -> n.allocatedRate).sum();

    if (totalRate > maxTotalRate) {
      double scaleFactor = maxTotalRate / totalRate;
      for (WellNode node : wellNodes) {
        node.allocatedRate *= scaleFactor;
      }
    }
  }

  /**
   * Solves with fixed total rate - adjusts manifold pressure to achieve target.
   */
  private void solveFixedTotalRate() {
    double pManifoldLow = 10.0; // bara
    double pManifoldHigh = 150.0; // bara

    for (int iter = 0; iter < maxIterations; iter++) {
      double pManifoldMid = (pManifoldLow + pManifoldHigh) / 2;
      manifoldPressure = pManifoldMid;

      solveFixedManifoldPressure();

      double totalRate = wellNodes.stream().filter(n -> n.enabled).mapToDouble(n -> n.allocatedRate).sum();

      double error = (totalRate - targetTotalRate) / targetTotalRate;
      lastResidual = Math.abs(error);
      lastIterations = iter + 1;

      if (Math.abs(error) < tolerance) {
        break;
      }

      // Bisection: higher manifold pressure = lower rate
      if (totalRate > targetTotalRate) {
        pManifoldLow = pManifoldMid;
      } else {
        pManifoldHigh = pManifoldMid;
      }
    }
  }

  /**
   * Optimizes production allocation across wells.
   */
  private void solveOptimizeAllocation() {
    // Simple proportional allocation based on well potential
    // More sophisticated methods could use LP/NLP optimization

    // First, calculate potential of each well at minimum backpressure
    double totalPotential = 0;
    Map<WellNode, Double> potentials = new HashMap<>();

    for (WellNode node : wellNodes) {
      if (!node.enabled) {
        continue;
      }
      node.well.setWellheadPressure(manifoldPressure + 10, "bara");
      node.well.run();
      double potential = node.well.getOperatingFlowRate("Sm3/day");
      potentials.put(node, potential);
      totalPotential += potential;
    }

    // Allocate proportionally, respecting max total rate
    double targetRate = Math.min(totalPotential, maxTotalRate);
    for (WellNode node : wellNodes) {
      if (!node.enabled) {
        node.allocatedRate = 0;
        continue;
      }
      double potential = potentials.getOrDefault(node, 0.0);
      node.allocatedRate = (potential / totalPotential) * targetRate;
    }

    // Recalculate wellhead pressures for allocated rates
    for (WellNode node : wellNodes) {
      if (!node.enabled || node.allocatedRate <= 0) {
        continue;
      }
      // Find WHP that gives allocated rate
      node.wellheadPressure = findWHPForRate(node, node.allocatedRate);
      node.flowlinePressureDrop = estimateFlowlinePressureDrop(node);
    }

    lastIterations = 1;
    lastResidual = 0;
  }

  /**
   * Estimates flowline pressure drop using simplified Beggs-Brill.
   *
   * @param node the well node to calculate pressure drop for
   * @return estimated pressure drop in bar
   */
  private double estimateFlowlinePressureDrop(WellNode node) {
    if (node.allocatedRate <= 0) {
      return 0;
    }

    // Simplified single-phase gas pressure drop (bar/km)
    // dP/dL ≈ f * ρ * v² / (2 * D)
    // Approximate: 0.5-2 bar/km for typical subsea flowlines

    double lengthKm = node.flowlineLength;
    double diameterM = node.flowlineDiameter;

    // Simplified correlation based on rate and diameter
    double rateMSm3d = node.allocatedRate / 1e6;
    double velocityFactor = rateMSm3d / (diameterM * diameterM * 1000);

    // Approximate pressure gradient (bar/km)
    double gradientBarPerKm = 0.3 + 0.5 * velocityFactor;

    return gradientBarPerKm * lengthKm;
  }

  /**
   * Finds wellhead pressure required to achieve target rate.
   *
   * @param node       the well node
   * @param targetRate target production rate in Sm3/day
   * @return wellhead pressure in bara required to achieve target rate
   */
  private double findWHPForRate(WellNode node, double targetRate) {
    double whpLow = manifoldPressure + 5;
    double whpHigh = 200.0;

    for (int iter = 0; iter < 20; iter++) {
      double whpMid = (whpLow + whpHigh) / 2;
      node.well.setWellheadPressure(whpMid, "bara");
      node.well.run();
      double rate = node.well.getOperatingFlowRate("Sm3/day");

      if (Math.abs(rate - targetRate) / targetRate < 0.01) {
        return whpMid;
      }

      // Higher WHP = lower rate
      if (rate > targetRate) {
        whpLow = whpMid;
      } else {
        whpHigh = whpMid;
      }
    }

    return (whpLow + whpHigh) / 2;
  }

  /**
   * Builds the result object.
   *
   * @return the network solution result
   */
  private NetworkResult buildResult() {
    NetworkResult result = new NetworkResult(name);
    result.manifoldPressure = manifoldPressure;
    result.solutionMode = solutionMode;
    result.iterations = lastIterations;
    result.residual = lastResidual;
    result.converged = lastResidual < tolerance;

    for (WellNode node : wellNodes) {
      result.wellRates.put(node.name, node.allocatedRate);
      result.wellheadPressures.put(node.name, node.wellheadPressure);
      result.flowlinePressureDrops.put(node.name, node.flowlinePressureDrop);
      result.wellEnabled.put(node.name, node.enabled);
    }

    result.totalRate = wellNodes.stream().filter(n -> n.enabled).mapToDouble(n -> n.allocatedRate).sum();

    return result;
  }

  // ============================================================================
  // UTILITY METHODS
  // ============================================================================

  private double convertRateToSm3PerDay(double rate, String unit) {
    if (unit.equalsIgnoreCase("Sm3/day") || unit.equalsIgnoreCase("Sm3/d")) {
      return rate;
    } else if (unit.equalsIgnoreCase("MSm3/day") || unit.equalsIgnoreCase("MSm3/d")) {
      return rate * 1e6;
    } else if (unit.equalsIgnoreCase("bbl/day") || unit.equalsIgnoreCase("bpd")) {
      return rate * 0.159; // barrel to Sm3
    } else if (unit.equalsIgnoreCase("Mbbl/day") || unit.equalsIgnoreCase("Mbpd")) {
      return rate * 1000 * 0.159;
    }
    return rate;
  }

  // ============================================================================
  // GETTERS
  // ============================================================================

  /**
   * Gets the network name.
   *
   * @return network name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the number of wells.
   *
   * @return well count
   */
  public int getWellCount() {
    return wellNodes.size();
  }

  /**
   * Gets the number of enabled wells.
   *
   * @return enabled well count
   */
  public int getEnabledWellCount() {
    return (int) wellNodes.stream().filter(n -> n.enabled).count();
  }

  /**
   * Checks if the network has been solved.
   *
   * @return true if solved
   */
  public boolean isSolved() {
    return solved;
  }

  /**
   * Gets the combined outlet stream from all wells.
   *
   * @return combined stream (requires referenceFluid to be set)
   */
  public StreamInterface getCombinedStream() {
    if (referenceFluid == null) {
      throw new IllegalStateException("Reference fluid must be set");
    }

    double totalRate = wellNodes.stream().filter(n -> n.enabled).mapToDouble(n -> n.allocatedRate).sum();

    SystemInterface combinedFluid = referenceFluid.clone();
    Stream combined = new Stream(name + " Combined", combinedFluid);
    combined.setFlowRate(totalRate, "Sm3/day");
    combined.setPressure(manifoldPressure, "bara");

    return combined;
  }
}
