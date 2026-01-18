package neqsim.process.util.optimization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChartGenerator;
import neqsim.process.equipment.compressor.CompressorChartInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimization.FlowRateOptimizationResult.ConstraintViolation;
import neqsim.process.util.optimization.FlowRateOptimizationResult.Status;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Optimizer for finding flow rate given inlet and outlet pressure constraints.
 *
 * <p>
 * This class determines the flow rate required to achieve a specified pressure drop across process
 * systems containing equipment such as pipelines, compressors, separators, and heat exchangers. It
 * uses bisection search with constraint checking to find the optimal operating point.
 * </p>
 *
 * <h2>Supported Configurations</h2>
 * <ul>
 * <li><b>ProcessSystem</b> - Single process system with inlet/outlet streams</li>
 * <li><b>ProcessModel</b> - Multiple coordinated ProcessSystems</li>
 * </ul>
 *
 * <h2>Key Features for Production Optimization</h2>
 * <ul>
 * <li>Automatic compressor chart generation based on design point</li>
 * <li>Equipment capacity constraint checking (surge, stonewall, power limits)</li>
 * <li>Lift curve generation for Eclipse/E300 reservoir simulator integration</li>
 * <li>Total power optimization across multiple compressors</li>
 * <li>Equipment utilization tracking and reporting</li>
 * </ul>
 *
 * <h2>Constraint Handling</h2>
 * <p>
 * The optimizer checks capacity constraints at each iteration including:
 * <ul>
 * <li>Compressor surge margin (configurable via {@link #setMinSurgeMargin(double)})</li>
 * <li>Compressor stonewall limits</li>
 * <li>Power limits (individual and total)</li>
 * <li>Speed limits</li>
 * <li>Equipment utilization limits</li>
 * </ul>
 * If hard constraints are violated at all feasible flow rates, the result is marked as
 * INFEASIBLE_CONSTRAINT.
 * </p>
 *
 * <h2>Example Usage - ProcessSystem with Compressors</h2>
 * 
 * <pre>
 * // Create a compression process
 * SystemInterface gas = new SystemSrkEos(298.15, 50.0);
 * gas.addComponent("methane", 0.9);
 * gas.addComponent("ethane", 0.1);
 * gas.setMixingRule("classic");
 * 
 * Stream feed = new Stream("Feed", gas);
 * feed.setFlowRate(50000, "kg/hr");
 * 
 * Compressor comp1 = new Compressor("LP Compressor", feed);
 * comp1.setOutletPressure(100.0, "bara");
 * 
 * Compressor comp2 = new Compressor("HP Compressor", comp1.getOutletStream());
 * comp2.setOutletPressure(150.0, "bara");
 * 
 * ProcessSystem process = new ProcessSystem();
 * process.add(feed);
 * process.add(comp1);
 * process.add(comp2);
 * process.run();
 * 
 * // Create optimizer and generate lift curves
 * FlowRateOptimizer optimizer = new FlowRateOptimizer(process, "Feed", "HP Compressor");
 * optimizer.setMinSurgeMargin(0.15); // 15% surge margin
 * optimizer.setMaxPowerLimit(5000.0); // 5 MW per compressor
 * optimizer.configureProcessCompressorCharts();
 * 
 * // Generate capacity table for reservoir simulator
 * double[] inletPressures = {50, 60, 70, 80}; // bara
 * double[] outletPressures = {140, 145, 150, 155}; // bara
 * ProcessCapacityTable table =
 *     optimizer.generateProcessCapacityTable(inletPressures, outletPressures, "bara", 0.95);
 * 
 * System.out.println(table.toEclipseFormat());
 * </pre>
 *
 * <h2>Example Usage - ProcessModel</h2>
 * 
 * <pre>
 * ProcessModel model = new ProcessModel();
 * model.add("upstream", upstreamSystem);
 * model.add("downstream", downstreamSystem);
 * 
 * FlowRateOptimizer optimizer = new FlowRateOptimizer(model, "inletStream", "outletStream");
 * FlowRateOptimizationResult result = optimizer.findFlowRate(150.0, 50.0, "bara");
 * </pre>
 *
 * @author ESOL
 * @version 2.0
 * @see ProcessCapacityTable
 * @see ProcessLiftCurveTable
 * @see LiftCurveGenerator
 */
public class FlowRateOptimizer implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(FlowRateOptimizer.class);

  /**
   * Mode of operation for the optimizer.
   */
  public enum Mode {
    /**
     * Operating on a single ProcessSystem.
     */
    PROCESS_SYSTEM,

    /**
     * Operating on a ProcessModel with multiple ProcessSystems.
     */
    PROCESS_MODEL
  }

  // Configuration
  private Mode mode;
  private ProcessSystem processSystem;
  private ProcessModel processModel;
  private String inletStreamName;
  private String outletStreamName;

  // Optimization parameters
  private int maxIterations = 50;
  private double tolerance = 1e-4;
  private double minFlowRate = 0.001; // kg/hr
  private double maxFlowRate = 1e9; // kg/hr
  private double initialFlowGuess = 1000.0; // kg/hr

  // Constraint parameters
  private double maxVelocity = Double.MAX_VALUE; // m/s
  private boolean checkCapacityConstraints = true;

  // Compressor-specific parameters
  private double minSurgeMargin = 0.10; // 10% minimum surge margin
  private boolean solveSpeed = true; // Whether to solve for compressor speed
  private double maxPowerLimit = Double.MAX_VALUE; // Maximum power in kW
  private double maxSpeedLimit = Double.MAX_VALUE; // Maximum speed in RPM
  private double minSpeedLimit = 0.0; // Minimum speed in RPM
  private boolean autoGenerateCompressorChart = true; // Auto-generate chart if not configured
  private int numberOfChartSpeeds = 5; // Number of speed lines for auto-generated chart
  private double speedMarginAboveDesign = 0.15; // 15% margin above design speed for max speed

  // Process-level optimization parameters
  private boolean autoConfigureProcessCompressors = true; // Auto-setup compressor charts
  private double maxTotalPowerLimit = Double.MAX_VALUE; // Maximum total power for all compressors
  private double maxEquipmentUtilization = 1.0; // Maximum utilization for any equipment (1.0=100%)

  // Progress monitoring
  private transient ProgressCallback progressCallback;
  private boolean enableProgressLogging = false;

  // Internal state
  private StreamInterface inletStream;
  private StreamInterface outletStream;
  private ProcessEquipmentInterface outletEquipment; // The outlet equipment (may be same as
                                                     // outletStream)
  private SystemInterface baseFluid;

  /**
   * Callback interface for monitoring optimization progress.
   *
   * <p>
   * Implement this interface to receive progress updates during lift curve generation or other
   * long-running operations. This is useful for GUI integration or progress reporting.
   * </p>
   */
  public interface ProgressCallback {
    /**
     * Called to report progress.
     *
     * @param current current step number
     * @param total total number of steps
     * @param message description of current operation
     */
    void onProgress(int current, int total, String message);
  }

  /**
   * Creates a flow rate optimizer for a ProcessSystem.
   *
   * @param processSystem the process system to optimize
   * @param inletStreamName name of the inlet stream
   * @param outletStreamName name of the outlet stream
   */
  public FlowRateOptimizer(ProcessSystem processSystem, String inletStreamName,
      String outletStreamName) {
    this.mode = Mode.PROCESS_SYSTEM;
    this.processSystem = processSystem;
    this.inletStreamName = inletStreamName;
    this.outletStreamName = outletStreamName;

    // Find streams
    ProcessEquipmentInterface inletUnit = processSystem.getUnit(inletStreamName);
    ProcessEquipmentInterface outletUnit = processSystem.getUnit(outletStreamName);

    this.outletEquipment = outletUnit; // Store the outlet equipment

    if (inletUnit instanceof StreamInterface) {
      this.inletStream = (StreamInterface) inletUnit;
    }
    if (outletUnit instanceof StreamInterface) {
      this.outletStream = (StreamInterface) outletUnit;
    } else if (outletUnit instanceof TwoPortInterface) {
      // Try to get outlet stream from two-port equipment
      this.outletStream = ((TwoPortInterface) outletUnit).getOutletStream();
    }

    if (inletStream != null && inletStream.getThermoSystem() != null) {
      this.baseFluid = inletStream.getThermoSystem().clone();
      double currentFlow = baseFluid.getFlowRate("kg/hr");
      if (currentFlow > 0) {
        this.initialFlowGuess = currentFlow;
      }
    }
  }

  /**
   * Creates a flow rate optimizer for a ProcessModel with multiple ProcessSystems.
   *
   * @param processModel the process model to optimize
   * @param inletStreamName name of the inlet stream (in first ProcessSystem)
   * @param outletStreamName name of the outlet stream (in last ProcessSystem)
   */
  public FlowRateOptimizer(ProcessModel processModel, String inletStreamName,
      String outletStreamName) {
    this.mode = Mode.PROCESS_MODEL;
    this.processModel = processModel;
    this.inletStreamName = inletStreamName;
    this.outletStreamName = outletStreamName;

    // Find streams across all ProcessSystems
    for (ProcessSystem ps : processModel.getAllProcesses()) {
      if (this.inletStream == null) {
        ProcessEquipmentInterface inletUnit = ps.getUnit(inletStreamName);
        if (inletUnit instanceof StreamInterface) {
          this.inletStream = (StreamInterface) inletUnit;
        }
      }
      if (this.outletStream == null) {
        ProcessEquipmentInterface outletUnit = ps.getUnit(outletStreamName);
        if (outletUnit != null) {
          this.outletEquipment = outletUnit; // Store the outlet equipment
        }
        if (outletUnit instanceof StreamInterface) {
          this.outletStream = (StreamInterface) outletUnit;
        } else if (outletUnit instanceof TwoPortInterface) {
          this.outletStream = ((TwoPortInterface) outletUnit).getOutletStream();
        }
      }
    }

    if (inletStream != null && inletStream.getThermoSystem() != null) {
      this.baseFluid = inletStream.getThermoSystem().clone();
      double currentFlow = baseFluid.getFlowRate("kg/hr");
      if (currentFlow > 0) {
        this.initialFlowGuess = currentFlow;
      }
    }
  }

  /**
   * Validates the optimizer configuration before running optimization.
   *
   * <p>
   * This method checks that:
   * <ul>
   * <li>Inlet stream is properly configured with a valid thermo system</li>
   * <li>Outlet stream/equipment is accessible</li>
   * <li>Process system/model is ready for simulation</li>
   * <li>Compressors have charts configured (if auto-configure is disabled)</li>
   * </ul>
   * </p>
   *
   * @return list of validation issues (empty if valid)
   */
  public List<String> validateConfiguration() {
    List<String> issues = new ArrayList<String>();

    // Check inlet stream
    if (inletStream == null) {
      issues.add("Inlet stream not found. Check stream name '" + inletStreamName + "'.");
    } else if (inletStream.getThermoSystem() == null) {
      issues.add("Inlet stream has no thermodynamic system configured.");
    } else if (inletStream.getThermoSystem().getNumberOfComponents() == 0) {
      issues.add("Inlet stream thermo system has no components defined.");
    }

    // Check outlet stream
    if (outletStream == null && outletEquipment == null) {
      issues.add("Outlet stream not found. Check stream name '" + outletStreamName + "'.");
    }

    // Check process configuration
    if (mode == Mode.PROCESS_SYSTEM && processSystem == null) {
      issues.add("ProcessSystem is null.");
    } else if (mode == Mode.PROCESS_MODEL && processModel == null) {
      issues.add("ProcessModel is null.");
    }

    // Check flow rate bounds
    if (minFlowRate >= maxFlowRate) {
      issues.add("Invalid flow rate bounds: minFlowRate (" + minFlowRate
          + ") must be less than maxFlowRate (" + maxFlowRate + ").");
    }

    // Check compressor charts if not auto-configuring
    if (!autoConfigureProcessCompressors) {
      List<Compressor> compressors = getProcessCompressors();
      for (Compressor comp : compressors) {
        CompressorChartInterface chart = comp.getCompressorChart();
        if (chart == null || !chart.isUseCompressorChart()) {
          issues.add("Compressor '" + comp.getName() + "' has no chart configured. "
              + "Either configure a chart or enable autoConfigureProcessCompressors.");
        }
      }
    }

    return issues;
  }

  /**
   * Validates configuration and throws an exception if invalid.
   *
   * @throws IllegalStateException if configuration is invalid
   */
  public void validateOrThrow() {
    List<String> issues = validateConfiguration();
    if (!issues.isEmpty()) {
      StringBuilder msg = new StringBuilder("FlowRateOptimizer configuration errors:\n");
      for (int i = 0; i < issues.size(); i++) {
        msg.append("  ").append(i + 1).append(". ").append(issues.get(i)).append("\n");
      }
      throw new IllegalStateException(msg.toString());
    }
  }

  /**
   * Finds the flow rate required to achieve the specified outlet pressure given the inlet pressure.
   *
   * <p>
   * This method sets the inlet stream to the specified pressure and temperature, then iterates to
   * find the flow rate that produces the target outlet pressure.
   * </p>
   *
   * @param inletPressure inlet pressure
   * @param outletPressure target outlet pressure
   * @param pressureUnit unit of pressure (e.g., "bara", "barg", "psia")
   * @return optimization result
   */
  public FlowRateOptimizationResult findFlowRate(double inletPressure, double outletPressure,
      String pressureUnit) {
    long startTime = System.currentTimeMillis();

    // Validate inputs - note: process systems can have either pressure increase (compressors)
    // or pressure decrease (pipelines, valves), so we don't enforce a direction here.
    // The process itself will determine what is feasible.

    if (inletStream == null) {
      return FlowRateOptimizationResult.error("Inlet stream not found or not configured");
    }

    FlowRateOptimizationResult result = new FlowRateOptimizationResult();
    result.setTargetInletPressure(inletPressure);
    result.setTargetOutletPressure(outletPressure);
    result.setPressureUnit(pressureUnit);
    result.setFlowRateUnit("kg/hr");

    try {
      // Use bisection to find flow rate
      double flowLow = minFlowRate;
      double flowHigh = Math.min(maxFlowRate, initialFlowGuess * 1000.0);

      // Evaluate at low flow - should give high outlet pressure (low pressure drop)
      double pOutLow = evaluateOutletPressure(flowLow, inletPressure, pressureUnit);

      // Evaluate at high flow - should give low outlet pressure (high pressure drop)
      double pOutHigh = evaluateOutletPressure(flowHigh, inletPressure, pressureUnit);

      // Handle NaN at boundaries
      if (Double.isNaN(pOutLow)) {
        // Try increasing the low bound to find valid region
        int searchIter = 0;
        while (Double.isNaN(pOutLow) && searchIter < 20) {
          flowLow *= 2.0;
          if (flowLow > flowHigh) {
            result.setStatus(Status.ERROR);
            result.setInfeasibilityReason(
                "Cannot find valid flow region - calculation failed at all flow rates");
            result.setComputationTimeMs(System.currentTimeMillis() - startTime);
            return result;
          }
          pOutLow = evaluateOutletPressure(flowLow, inletPressure, pressureUnit);
          searchIter++;
        }
      }

      if (Double.isNaN(pOutHigh)) {
        // Try decreasing the high bound to find valid region
        int searchIter = 0;
        while (Double.isNaN(pOutHigh) && searchIter < 20) {
          flowHigh /= 2.0;
          if (flowHigh < flowLow) {
            result.setStatus(Status.ERROR);
            result.setInfeasibilityReason(
                "Cannot find valid flow region - calculation failed at high flow rates");
            result.setComputationTimeMs(System.currentTimeMillis() - startTime);
            return result;
          }
          pOutHigh = evaluateOutletPressure(flowHigh, inletPressure, pressureUnit);
          searchIter++;
        }
      }

      // Check bounds
      // If even at minimum flow, outlet pressure is below target, target is infeasible
      if (!Double.isNaN(pOutLow) && pOutLow < outletPressure) {
        // Try even lower flow
        int boundIter = 0;
        while (pOutLow < outletPressure && boundIter < 20) {
          flowLow /= 10.0;
          if (flowLow < 1e-10) {
            result.setStatus(Status.INFEASIBLE_PRESSURE);
            result
                .setInfeasibilityReason("Cannot achieve target outlet pressure even at zero flow. "
                    + "Minimum outlet pressure at near-zero flow: " + String.format("%.4f", pOutLow)
                    + " " + pressureUnit);
            result.setComputationTimeMs(System.currentTimeMillis() - startTime);
            return result;
          }
          pOutLow = evaluateOutletPressure(flowLow, inletPressure, pressureUnit);
          if (Double.isNaN(pOutLow)) {
            break; // Can't go lower
          }
          boundIter++;
        }
      }

      // If at maximum flow, outlet pressure is still above target, need more flow
      int boundIter = 0;
      while (!Double.isNaN(pOutHigh) && pOutHigh > outletPressure && boundIter < 20) {
        flowHigh *= 10.0;
        if (flowHigh > maxFlowRate) {
          result.setStatus(Status.INFEASIBLE_PRESSURE);
          result.setInfeasibilityReason("Cannot achieve target outlet pressure within flow limits. "
              + "Outlet pressure at max flow: " + String.format("%.4f", pOutHigh) + " "
              + pressureUnit);
          result.setComputationTimeMs(System.currentTimeMillis() - startTime);
          return result;
        }
        pOutHigh = evaluateOutletPressure(flowHigh, inletPressure, pressureUnit);
        boundIter++;
      }

      // Bisection iteration
      double flowMid = 0;
      double pOutMid = 0;
      List<ConstraintViolation> violations = new ArrayList<ConstraintViolation>();
      int iter = 0;
      int nanCount = 0;

      for (iter = 0; iter < maxIterations; iter++) {
        flowMid = (flowLow + flowHigh) / 2.0;
        pOutMid = evaluateOutletPressure(flowMid, inletPressure, pressureUnit);

        // Handle NaN in bisection - move toward the valid region
        if (Double.isNaN(pOutMid)) {
          nanCount++;
          if (nanCount > 10) {
            result.setStatus(Status.ERROR);
            result.setInfeasibilityReason("Too many invalid evaluations during optimization");
            result.setComputationTimeMs(System.currentTimeMillis() - startTime);
            return result;
          }
          // Assume NaN means flow is too extreme, reduce range toward valid region
          if (!Double.isNaN(pOutLow)) {
            flowHigh = flowMid;
          } else if (!Double.isNaN(pOutHigh)) {
            flowLow = flowMid;
          }
          continue;
        }

        // Check constraints at this flow rate
        violations = checkConstraints();

        // Check for hard constraint violations
        boolean hasHardViolation = false;
        for (ConstraintViolation v : violations) {
          if (v.isHardViolation()) {
            hasHardViolation = true;
            break;
          }
        }

        double relError = Math.abs(pOutMid - outletPressure) / outletPressure;
        result.setConvergenceError(relError);

        if (relError < tolerance) {
          // Converged - check if constraints allow this solution
          if (hasHardViolation) {
            // Try to find a feasible region
            // If high flow violates, search in lower range
            // This is a simplified approach - could be more sophisticated
            logger.debug("Converged but hard constraint violated at flow {}", flowMid);
          }
          break;
        }

        // Bisection logic: higher flow = more pressure drop = lower outlet pressure
        if (pOutMid > outletPressure) {
          // Outlet pressure too high, need more pressure drop, increase flow
          flowLow = flowMid;
        } else {
          // Outlet pressure too low, need less pressure drop, decrease flow
          flowHigh = flowMid;
        }

        // Check for convergence by flow rate change
        if (Math.abs(flowHigh - flowLow) / flowMid < tolerance) {
          break;
        }
      }

      result.setIterationCount(iter + 1);

      // Final constraint check
      violations = checkConstraints();
      for (ConstraintViolation v : violations) {
        result.addConstraintViolation(v);
      }

      if (result.hasHardViolations()) {
        result.setStatus(Status.INFEASIBLE_CONSTRAINT);
        result.setInfeasibilityReason("Hard constraint violated: " + violations.get(0));
      } else if (iter >= maxIterations - 1
          && Math.abs(pOutMid - outletPressure) / outletPressure > tolerance) {
        result.setStatus(Status.NOT_CONVERGED);
        result.setInfeasibilityReason(
            String.format("Did not converge after %d iterations", maxIterations));
      } else {
        result.setStatus(Status.OPTIMAL);
        result.setFlowRate(flowMid);
        result.setInletPressure(inletPressure);
        result.setOutletPressure(pOutMid);
      }

    } catch (Exception e) {
      logger.error("Error during flow rate optimization", e);
      result.setStatus(Status.ERROR);
      result.setInfeasibilityReason("Error: " + e.getMessage());
    }

    result.setComputationTimeMs(System.currentTimeMillis() - startTime);
    return result;
  }

  /**
   * Finds the inlet pressure required to achieve the specified outlet pressure at a given flow
   * rate.
   *
   * <p>
   * This is a simpler forward calculation - just run the process at the specified flow rate and
   * read the inlet pressure required.
   * </p>
   *
   * @param flowRate flow rate
   * @param flowRateUnit unit of flow rate
   * @param outletPressure target outlet pressure
   * @param pressureUnit unit of pressure
   * @return optimization result with inlet pressure
   */
  public FlowRateOptimizationResult findInletPressure(double flowRate, String flowRateUnit,
      double outletPressure, String pressureUnit) {
    long startTime = System.currentTimeMillis();

    if (inletStream == null) {
      return FlowRateOptimizationResult.error("Inlet stream not found");
    }

    FlowRateOptimizationResult result = new FlowRateOptimizationResult();
    result.setTargetOutletPressure(outletPressure);
    result.setPressureUnit(pressureUnit);
    result.setFlowRate(flowRate);
    result.setFlowRateUnit(flowRateUnit);

    try {
      // This requires iterating on inlet pressure to achieve outlet pressure
      // Use bisection on inlet pressure

      double pInLow = outletPressure * 1.01; // Just above outlet
      double pInHigh = outletPressure * 10.0; // Much higher

      // Evaluate bounds
      double pOutAtLow = evaluateOutletPressureAtFlow(flowRate, flowRateUnit, pInLow, pressureUnit);
      double pOutAtHigh =
          evaluateOutletPressureAtFlow(flowRate, flowRateUnit, pInHigh, pressureUnit);

      // Expand high bound if needed
      int boundIter = 0;
      while (pOutAtHigh < outletPressure && boundIter < 20) {
        pInHigh *= 2.0;
        pOutAtHigh = evaluateOutletPressureAtFlow(flowRate, flowRateUnit, pInHigh, pressureUnit);
        boundIter++;
      }

      // Bisection
      double pInMid = 0;
      double pOutMid = 0;
      int iter = 0;

      for (iter = 0; iter < maxIterations; iter++) {
        pInMid = (pInLow + pInHigh) / 2.0;
        pOutMid = evaluateOutletPressureAtFlow(flowRate, flowRateUnit, pInMid, pressureUnit);

        double relError = Math.abs(pOutMid - outletPressure) / outletPressure;
        result.setConvergenceError(relError);

        if (relError < tolerance) {
          break;
        }

        // Higher inlet pressure = higher outlet pressure
        if (pOutMid < outletPressure) {
          pInLow = pInMid;
        } else {
          pInHigh = pInMid;
        }
      }

      result.setIterationCount(iter + 1);

      // Check constraints
      List<ConstraintViolation> violations = checkConstraints();
      for (ConstraintViolation v : violations) {
        result.addConstraintViolation(v);
      }

      if (result.hasHardViolations()) {
        result.setStatus(Status.INFEASIBLE_CONSTRAINT);
        result.setInfeasibilityReason("Hard constraint violated: " + violations.get(0));
      } else {
        result.setStatus(Status.OPTIMAL);
        result.setInletPressure(pInMid);
        result.setOutletPressure(pOutMid);
      }

    } catch (Exception e) {
      logger.error("Error during inlet pressure calculation", e);
      result.setStatus(Status.ERROR);
      result.setInfeasibilityReason("Error: " + e.getMessage());
    }

    result.setComputationTimeMs(System.currentTimeMillis() - startTime);
    return result;
  }

  /**
   * Evaluates the outlet pressure for a given flow rate and inlet pressure.
   *
   * @param flowRate flow rate in kg/hr
   * @param inletPressure inlet pressure
   * @param pressureUnit pressure unit
   * @return outlet pressure in the same unit
   */
  private double evaluateOutletPressure(double flowRate, double inletPressure,
      String pressureUnit) {
    return evaluateOutletPressureAtFlow(flowRate, "kg/hr", inletPressure, pressureUnit);
  }

  /**
   * Evaluates outlet pressure for given flow rate and inlet pressure.
   *
   * @param flowRate flow rate
   * @param flowRateUnit flow rate unit
   * @param inletPressure inlet pressure
   * @param pressureUnit pressure unit
   * @return outlet pressure
   */
  private double evaluateOutletPressureAtFlow(double flowRate, String flowRateUnit,
      double inletPressure, String pressureUnit) {
    switch (mode) {
      case PROCESS_SYSTEM:
        return evaluateProcessSystem(flowRate, flowRateUnit, inletPressure, pressureUnit);

      case PROCESS_MODEL:
        return evaluateProcessModel(flowRate, flowRateUnit, inletPressure, pressureUnit);

      default:
        throw new IllegalStateException("Unknown mode: " + mode);
    }
  }

  // ============ Process System Optimization Methods ============

  /**
   * Configures all compressors in a ProcessSystem with performance charts.
   *
   * <p>
   * This method sets up compressor performance charts for all compressors in the process system,
   * following the pattern established in ProcessOptimizationExampleTest. It:
   * <ul>
   * <li>Generates performance charts using CompressorChartGenerator</li>
   * <li>Sets maximum speed with appropriate margin above design speed</li>
   * <li>Reinitializes capacity constraints after chart setup</li>
   * </ul>
   * </p>
   *
   * <h3>Example Usage</h3>
   * 
   * <pre>
   * FlowRateOptimizer optimizer = new FlowRateOptimizer(process, "Feed", "Export");
   * optimizer.configureProcessCompressorCharts();
   * </pre>
   */
  public void configureProcessCompressorCharts() {
    if (mode != Mode.PROCESS_SYSTEM && mode != Mode.PROCESS_MODEL) {
      logger
          .warn("configureProcessCompressorCharts only applicable for PROCESS_SYSTEM/MODEL modes");
      return;
    }

    List<Compressor> compressors = getProcessCompressors();
    for (Compressor comp : compressors) {
      configureCompressorChart(comp);
    }

    // Re-run the process after chart setup
    if (mode == Mode.PROCESS_SYSTEM) {
      processSystem.run();
    } else {
      processModel.run();
    }

    // Reinitialize constraints after chart setup
    for (Compressor comp : compressors) {
      comp.reinitializeCapacityConstraints();
    }
  }

  /**
   * Configures a single compressor with a performance chart.
   *
   * @param comp the compressor to configure
   */
  private void configureCompressorChart(Compressor comp) {
    CompressorChartInterface chart = comp.getCompressorChart();

    // Check if chart needs to be generated
    boolean needsChart =
        (chart == null || !chart.isUseCompressorChart()) && autoGenerateCompressorChart;

    if (needsChart) {
      logger.info("Auto-generating compressor chart for {}", comp.getName());

      // Generate chart using CompressorChartGenerator
      CompressorChartGenerator gen = new CompressorChartGenerator(comp);
      gen.setChartType("interpolate and extrapolate");

      // Generate the chart with multiple speed lines
      CompressorChartInterface newChart =
          gen.generateCompressorChart("normal curves", numberOfChartSpeeds);
      comp.setCompressorChart(newChart);
      comp.getCompressorChart().setUseCompressorChart(true);

      // Set maximum speed with margin above design speed
      double designSpeed = comp.getSpeed();
      double maxSpeed = designSpeed * (1.0 + speedMarginAboveDesign);
      comp.setMaximumSpeed(maxSpeed);

      logger.info("Chart generated for {} with {} speed lines, max speed set to {} RPM",
          comp.getName(), numberOfChartSpeeds, maxSpeed);
    }
  }

  /**
   * Gets all compressors in the process system or model.
   *
   * @return list of all compressors
   */
  public List<Compressor> getProcessCompressors() {
    List<Compressor> compressors = new ArrayList<Compressor>();

    List<ProcessEquipmentInterface> equipment = getEquipmentList();
    for (ProcessEquipmentInterface equip : equipment) {
      if (equip instanceof Compressor) {
        compressors.add((Compressor) equip);
      }
    }

    return compressors;
  }

  /**
   * Gets all separators in the process system or model.
   *
   * @return list of all separators
   */
  public List<Separator> getProcessSeparators() {
    List<Separator> separators = new ArrayList<Separator>();

    List<ProcessEquipmentInterface> equipment = getEquipmentList();
    for (ProcessEquipmentInterface equip : equipment) {
      if (equip instanceof Separator) {
        separators.add((Separator) equip);
      }
    }

    return separators;
  }

  /**
   * Calculates the total power consumption of all compressors in the process.
   *
   * @param powerUnit the unit for power (e.g., "kW", "MW", "W")
   * @return total power consumption in the specified unit
   */
  public double calculateTotalCompressorPower(String powerUnit) {
    double totalPower = 0.0;

    List<Compressor> compressors = getProcessCompressors();
    for (Compressor comp : compressors) {
      double power = comp.getPower(powerUnit);
      if (!Double.isNaN(power) && power > 0) {
        totalPower += power;
      }
    }

    return totalPower;
  }

  /**
   * Gets the maximum equipment utilization across all capacity-constrained equipment.
   *
   * @return maximum utilization as a fraction (1.0 = 100%)
   */
  public double getMaxEquipmentUtilization() {
    double maxUtil = 0.0;

    List<ProcessEquipmentInterface> equipment = getEquipmentList();
    for (ProcessEquipmentInterface equip : equipment) {
      double capacity = equip.getCapacityMax();
      double duty = equip.getCapacityDuty();

      if (capacity > 0 && duty > 0) {
        double util = duty / capacity;
        if (!Double.isNaN(util) && !Double.isInfinite(util) && util > maxUtil) {
          maxUtil = util;
        }
      }
    }

    return maxUtil;
  }

  /**
   * Gets a detailed report of equipment utilization.
   *
   * @return map of equipment name to utilization data
   */
  public Map<String, EquipmentUtilizationData> getEquipmentUtilizationReport() {
    Map<String, EquipmentUtilizationData> report = new HashMap<String, EquipmentUtilizationData>();

    List<ProcessEquipmentInterface> equipment = getEquipmentList();
    for (ProcessEquipmentInterface equip : equipment) {
      EquipmentUtilizationData data = new EquipmentUtilizationData();
      data.setName(equip.getName());
      data.setEquipmentType(equip.getClass().getSimpleName());

      double capacity = equip.getCapacityMax();
      double duty = equip.getCapacityDuty();

      if (capacity > 0 && duty > 0) {
        data.setUtilization(duty / capacity);
        data.setCapacity(capacity);
        data.setDuty(duty);
      }

      // Add compressor-specific data
      if (equip instanceof Compressor) {
        Compressor comp = (Compressor) equip;
        data.setPower(comp.getPower("kW"));
        data.setSpeed(comp.getSpeed());
        data.setSurgeMargin(comp.getDistanceToSurge());
        data.setStonewallMargin(comp.getDistanceToStoneWall());
      }

      report.put(equip.getName(), data);
    }

    return report;
  }

  /**
   * Finds the process operating point at a given flow rate.
   *
   * <p>
   * This method runs the process at the specified flow rate and inlet pressure, then extracts
   * comprehensive operating data including:
   * <ul>
   * <li>Outlet pressure</li>
   * <li>Total compressor power and individual compressor powers</li>
   * <li>Equipment utilization data</li>
   * <li>Constraint status</li>
   * </ul>
   * </p>
   *
   * @param flowRate the flow rate
   * @param flowRateUnit the flow rate unit
   * @param inletPressure the inlet pressure
   * @param pressureUnit the pressure unit
   * @return the process operating point
   */
  public ProcessOperatingPoint findProcessOperatingPoint(double flowRate, String flowRateUnit,
      double inletPressure, String pressureUnit) {
    if (mode != Mode.PROCESS_SYSTEM && mode != Mode.PROCESS_MODEL) {
      throw new IllegalStateException(
          "findProcessOperatingPoint requires PROCESS_SYSTEM or PROCESS_MODEL mode");
    }

    // Auto-configure compressor charts if enabled
    if (autoConfigureProcessCompressors) {
      List<Compressor> compressors = getProcessCompressors();
      for (Compressor comp : compressors) {
        CompressorChartInterface chart = comp.getCompressorChart();
        if (chart == null || !chart.isUseCompressorChart()) {
          configureCompressorChart(comp);
        }
      }
    }

    ProcessOperatingPoint point = new ProcessOperatingPoint();
    point.setFlowRate(flowRate);
    point.setFlowRateUnit(flowRateUnit);
    point.setInletPressure(inletPressure);
    point.setPressureUnit(pressureUnit);

    try {
      // Evaluate process at the given conditions
      double outletPressure =
          evaluateProcessWithPower(flowRate, flowRateUnit, inletPressure, pressureUnit, point);

      point.setOutletPressure(outletPressure);

      // Calculate total power
      point.setTotalPower(calculateTotalCompressorPower("kW"));

      // Get equipment utilization
      point.setMaxUtilization(getMaxEquipmentUtilization());
      point.setEquipmentData(getEquipmentUtilizationReport());

      // Check constraints
      List<ConstraintViolation> violations = checkConstraints();
      point.setConstraintViolations(violations);

      // Determine feasibility
      boolean feasible = violations.isEmpty() || !hasHardViolations(violations);

      // Check total power limit
      if (maxTotalPowerLimit < Double.MAX_VALUE && point.getTotalPower() > maxTotalPowerLimit) {
        feasible = false;
      }

      // Check max utilization limit
      if (point.getMaxUtilization() > maxEquipmentUtilization) {
        feasible = false;
      }

      point.setFeasible(feasible);

    } catch (Exception e) {
      logger.error("Error finding process operating point: {}", e.getMessage());
      point.setFeasible(false);
    }

    return point;
  }

  /**
   * Evaluates process and captures power data for each compressor.
   */
  private double evaluateProcessWithPower(double flowRate, String flowRateUnit,
      double inletPressure, String pressureUnit, ProcessOperatingPoint point) {
    // Set inlet conditions
    SystemInterface fluid = inletStream.getThermoSystem().clone();
    fluid.setTotalFlowRate(flowRate, flowRateUnit);
    fluid.setPressure(inletPressure, pressureUnit);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    inletStream.setThermoSystem(fluid);

    // Run the process
    if (mode == Mode.PROCESS_SYSTEM) {
      processSystem.run();
    } else {
      processModel.run();
    }

    // Capture compressor data
    List<Compressor> compressors = getProcessCompressors();
    for (Compressor comp : compressors) {
      CompressorOperatingPoint compPoint = new CompressorOperatingPoint();
      compPoint.setFlowRate(comp.getInletStream().getFlowRate(flowRateUnit));
      compPoint.setFlowRateUnit(flowRateUnit);
      compPoint.setInletPressure(comp.getInletStream().getPressure(pressureUnit));
      compPoint.setOutletPressure(comp.getOutletStream().getPressure(pressureUnit));
      compPoint.setPressureUnit(pressureUnit);
      compPoint.setSpeed(comp.getSpeed());
      compPoint.setPower(comp.getPower("kW"));
      compPoint.setPolytropicHead(comp.getPolytropicFluidHead());
      compPoint.setPolytropicEfficiency(comp.getPolytropicEfficiency());
      compPoint.setSurgeMargin(comp.getDistanceToSurge());
      compPoint.setInSurge(comp.isSurge());
      compPoint.setAtStoneWall(comp.isStoneWall());
      compPoint.setFeasible(!comp.isSurge() && !comp.isStoneWall());

      point.addCompressorOperatingPoint(comp.getName(), compPoint);
    }

    // Get outlet pressure
    return outletStream.getPressure(pressureUnit);
  }

  /**
   * Checks if any violations are hard violations.
   */
  private boolean hasHardViolations(List<ConstraintViolation> violations) {
    for (ConstraintViolation v : violations) {
      if (v.isHardViolation()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Generates a comprehensive process performance table.
   *
   * <p>
   * This method creates a table of process operating points for different flow rates, reporting:
   * <ul>
   * <li>Outlet pressure at each flow rate</li>
   * <li>Total compressor power</li>
   * <li>Individual compressor powers</li>
   * <li>Maximum equipment utilization</li>
   * <li>Feasibility status</li>
   * </ul>
   * </p>
   *
   * <h3>Example Usage</h3>
   * 
   * <pre>
   * FlowRateOptimizer optimizer = new FlowRateOptimizer(process, "Feed", "Export");
   * optimizer.configureProcessCompressorCharts();
   * 
   * ProcessPerformanceTable table = optimizer.generateProcessPerformanceTable(
   *     new double[] {30000, 50000, 70000, 90000}, "kg/hr", 80.0, "bara");
   * 
   * System.out.println(table.toFormattedString());
   * System.out.println("Total power at 50000 kg/hr: " + table.getTotalPower(1) + " kW");
   * </pre>
   *
   * @param flowRates array of flow rates to evaluate
   * @param flowRateUnit the flow rate unit
   * @param inletPressure the inlet pressure
   * @param pressureUnit the pressure unit
   * @return a ProcessPerformanceTable with comprehensive process data
   */
  public ProcessPerformanceTable generateProcessPerformanceTable(double[] flowRates,
      String flowRateUnit, double inletPressure, String pressureUnit) {
    if (mode != Mode.PROCESS_SYSTEM && mode != Mode.PROCESS_MODEL) {
      throw new IllegalStateException(
          "generateProcessPerformanceTable requires PROCESS_SYSTEM or PROCESS_MODEL mode");
    }

    // Get compressor names for table structure
    List<Compressor> compressors = getProcessCompressors();
    List<String> compressorNames = new ArrayList<String>();
    for (Compressor comp : compressors) {
      compressorNames.add(comp.getName());
    }

    ProcessPerformanceTable table =
        new ProcessPerformanceTable("Process_Performance", flowRates, compressorNames);
    table.setFlowRateUnit(flowRateUnit);
    table.setPressureUnit(pressureUnit);
    table.setInletPressure(inletPressure);

    // Auto-configure compressor charts
    if (autoConfigureProcessCompressors) {
      configureProcessCompressorCharts();
    }

    // Evaluate at each flow rate
    for (int i = 0; i < flowRates.length; i++) {
      try {
        ProcessOperatingPoint point =
            findProcessOperatingPoint(flowRates[i], flowRateUnit, inletPressure, pressureUnit);
        table.setOperatingPoint(i, point);
      } catch (Exception e) {
        logger.debug("Error at flow={}: {}", flowRates[i], e.getMessage());
      }
    }

    return table;
  }

  /**
   * Generates a process lift curve suitable for reservoir simulator integration.
   *
   * <p>
   * This method creates a table of outlet pressures and total power for different flow rates and
   * inlet pressures. The output format is suitable for conversion to VFP (Vertical Flow
   * Performance) tables used by reservoir simulators like Eclipse.
   * </p>
   *
   * <h3>Example Usage</h3>
   * 
   * <pre>
   * double[] flowRates = {20000, 40000, 60000, 80000, 100000};
   * double[] inletPressures = {60, 70, 80, 90};
   * 
   * ProcessLiftCurveTable table =
   *     optimizer.generateProcessLiftCurve(flowRates, "kg/hr", inletPressures, "bara");
   * 
   * System.out.println(table.toEclipseFormat());
   * System.out.println("Minimum power operating point: " + table.findMinimumPowerPoint());
   * </pre>
   *
   * @param flowRates array of flow rates to evaluate
   * @param flowRateUnit the flow rate unit
   * @param inletPressures array of inlet pressures to evaluate
   * @param pressureUnit the pressure unit
   * @return a ProcessLiftCurveTable with lift curve data and power information
   */
  public ProcessLiftCurveTable generateProcessLiftCurve(double[] flowRates, String flowRateUnit,
      double[] inletPressures, String pressureUnit) {
    if (mode != Mode.PROCESS_SYSTEM && mode != Mode.PROCESS_MODEL) {
      throw new IllegalStateException(
          "generateProcessLiftCurve requires PROCESS_SYSTEM or PROCESS_MODEL mode");
    }

    // Get compressor names
    List<Compressor> compressors = getProcessCompressors();
    List<String> compressorNames = new ArrayList<String>();
    for (Compressor comp : compressors) {
      compressorNames.add(comp.getName());
    }

    ProcessLiftCurveTable table =
        new ProcessLiftCurveTable("Process_LiftCurve", flowRates, inletPressures, compressorNames);
    table.setFlowRateUnit(flowRateUnit);
    table.setPressureUnit(pressureUnit);

    // Auto-configure compressor charts
    if (autoConfigureProcessCompressors) {
      configureProcessCompressorCharts();
    }

    // Evaluate at each flow rate and inlet pressure
    for (int i = 0; i < flowRates.length; i++) {
      for (int j = 0; j < inletPressures.length; j++) {
        try {
          ProcessOperatingPoint point = findProcessOperatingPoint(flowRates[i], flowRateUnit,
              inletPressures[j], pressureUnit);
          table.setOperatingPoint(i, j, point);
        } catch (Exception e) {
          logger.debug("Error at flow={}, Pin={}: {}", flowRates[i], inletPressures[j],
              e.getMessage());
        }
      }
    }

    return table;
  }

  /**
   * Finds the flow rate that achieves minimum total power for a given pressure ratio.
   *
   * <p>
   * This method searches for the flow rate that results in minimum total compressor power
   * consumption while achieving the target outlet pressure and staying within all equipment
   * constraints.
   * </p>
   *
   * <h3>Example Usage</h3>
   * 
   * <pre>
   * ProcessOperatingPoint minPowerPoint =
   *     optimizer.findMinimumTotalPowerOperatingPoint(80.0, 150.0, "bara", 20000, 100000, "kg/hr");
   * 
   * System.out.println("Minimum total power: " + minPowerPoint.getTotalPower() + " kW");
   * System.out.println("At flow rate: " + minPowerPoint.getFlowRate() + " kg/hr");
   * </pre>
   *
   * @param inletPressure the inlet pressure
   * @param targetOutletPressure the target outlet pressure
   * @param pressureUnit the pressure unit
   * @param minFlow minimum flow rate to search
   * @param maxFlow maximum flow rate to search
   * @param flowRateUnit the flow rate unit
   * @return the process operating point with minimum total power, or null if no feasible point
   */
  public ProcessOperatingPoint findMinimumTotalPowerOperatingPoint(double inletPressure,
      double targetOutletPressure, String pressureUnit, double minFlow, double maxFlow,
      String flowRateUnit) {
    if (mode != Mode.PROCESS_SYSTEM && mode != Mode.PROCESS_MODEL) {
      throw new IllegalStateException(
          "findMinimumTotalPowerOperatingPoint requires PROCESS_SYSTEM or PROCESS_MODEL mode");
    }

    // Auto-configure compressor charts
    if (autoConfigureProcessCompressors) {
      configureProcessCompressorCharts();
    }

    ProcessOperatingPoint bestPoint = null;
    double minPower = Double.MAX_VALUE;

    // Golden section search for minimum power
    double phi = (1.0 + Math.sqrt(5.0)) / 2.0;
    double a = minFlow;
    double b = maxFlow;
    double c = b - (b - a) / phi;
    double d = a + (b - a) / phi;

    int maxIter = 30;
    double searchTolerance = (maxFlow - minFlow) * 0.01; // 1% of range

    for (int iter = 0; iter < maxIter && (b - a) > searchTolerance; iter++) {
      ProcessOperatingPoint pointC =
          findProcessOperatingPoint(c, flowRateUnit, inletPressure, pressureUnit);
      ProcessOperatingPoint pointD =
          findProcessOperatingPoint(d, flowRateUnit, inletPressure, pressureUnit);

      // Check if points achieve target outlet pressure (within tolerance)
      double pressureTol = targetOutletPressure * tolerance;
      boolean cAchievesTarget = pointC != null && pointC.isFeasible()
          && Math.abs(pointC.getOutletPressure() - targetOutletPressure) < pressureTol;
      boolean dAchievesTarget = pointD != null && pointD.isFeasible()
          && Math.abs(pointD.getOutletPressure() - targetOutletPressure) < pressureTol;

      double powerC = cAchievesTarget ? pointC.getTotalPower() : Double.MAX_VALUE;
      double powerD = dAchievesTarget ? pointD.getTotalPower() : Double.MAX_VALUE;

      // Track best feasible point
      if (pointC != null && pointC.isFeasible() && powerC < minPower) {
        minPower = powerC;
        bestPoint = pointC;
      }
      if (pointD != null && pointD.isFeasible() && powerD < minPower) {
        minPower = powerD;
        bestPoint = pointD;
      }

      if (powerC < powerD) {
        b = d;
        d = c;
        c = b - (b - a) / phi;
      } else {
        a = c;
        c = d;
        d = a + (b - a) / phi;
      }
    }

    // Final check at midpoint
    double midFlow = (a + b) / 2.0;
    ProcessOperatingPoint midPoint =
        findProcessOperatingPoint(midFlow, flowRateUnit, inletPressure, pressureUnit);
    if (midPoint != null && midPoint.isFeasible() && midPoint.getTotalPower() < minPower) {
      bestPoint = midPoint;
    }

    return bestPoint;
  }

  /**
   * Finds the maximum feasible flow rate within equipment constraints.
   *
   * <p>
   * This method uses bisection search to find the maximum flow rate that can be processed while
   * keeping all equipment within their capacity constraints.
   * </p>
   *
   * @param inletPressure the inlet pressure
   * @param pressureUnit the pressure unit
   * @param targetUtilization the target maximum utilization (e.g., 0.95 for 95%)
   * @return the process operating point at maximum feasible flow
   */
  public ProcessOperatingPoint findMaximumFeasibleFlowRate(double inletPressure,
      String pressureUnit, double targetUtilization) {
    if (mode != Mode.PROCESS_SYSTEM && mode != Mode.PROCESS_MODEL) {
      throw new IllegalStateException(
          "findMaximumFeasibleFlowRate requires PROCESS_SYSTEM or PROCESS_MODEL mode");
    }

    // Auto-configure compressor charts
    if (autoConfigureProcessCompressors) {
      configureProcessCompressorCharts();
    }

    double lowRate = minFlowRate;
    double highRate = maxFlowRate;
    double searchTolerance = (highRate - lowRate) * 0.001; // 0.1% of range

    ProcessOperatingPoint bestPoint = null;

    int iter = 0;
    while ((highRate - lowRate) > searchTolerance && iter < maxIterations) {
      double midRate = (lowRate + highRate) / 2.0;

      ProcessOperatingPoint point =
          findProcessOperatingPoint(midRate, "kg/hr", inletPressure, pressureUnit);

      if (point != null && point.isFeasible() && point.getMaxUtilization() <= targetUtilization) {
        // Feasible - try higher
        lowRate = midRate;
        bestPoint = point;
      } else {
        // Infeasible - try lower
        highRate = midRate;
      }

      iter++;
    }

    return bestPoint;
  }

  /**
   * Finds the maximum flow rate that achieves a specified outlet pressure while respecting
   * equipment capacity constraints.
   *
   * <p>
   * This method solves the inverse problem: given inlet and outlet pressure boundary conditions,
   * find the maximum feed flow rate that keeps all equipment within their capacity limits. This is
   * useful for generating lift curves for reservoir simulators where pressure boundary conditions
   * are known from reservoir deliverability.
   * </p>
   *
   * <p>
   * The algorithm uses bisection search on flow rate:
   * <ol>
   * <li>At each flow rate, run the process and check outlet pressure and utilization</li>
   * <li>If outlet pressure is above target and utilization is acceptable, increase flow</li>
   * <li>If outlet pressure is below target or utilization exceeds limit, decrease flow</li>
   * <li>Converge to the maximum flow that achieves target outlet pressure within constraints</li>
   * </ol>
   * </p>
   *
   * <h3>Example Usage</h3>
   * 
   * <pre>
   * FlowRateOptimizer optimizer = new FlowRateOptimizer(process, "Feed", "Export");
   * optimizer.configureProcessCompressorCharts();
   * 
   * // Find max flow rate for given pressure boundary conditions
   * ProcessOperatingPoint result =
   *     optimizer.findMaxFlowRateAtPressureBoundaries(80.0, 150.0, "bara", 0.95);
   * 
   * if (result != null && result.isFeasible()) {
   *   System.out.println("Max flow: " + result.getFlowRate() + " kg/hr");
   *   System.out.println("Total power: " + result.getTotalPower() + " kW");
   * }
   * </pre>
   *
   * @param inletPressure the inlet pressure boundary condition
   * @param targetOutletPressure the target outlet pressure boundary condition
   * @param pressureUnit the pressure unit (e.g., "bara", "barg")
   * @param maxUtilization the maximum allowed equipment utilization (e.g., 0.95 for 95%)
   * @return the process operating point at maximum flow, or null if no feasible point exists
   */
  public ProcessOperatingPoint findMaxFlowRateAtPressureBoundaries(double inletPressure,
      double targetOutletPressure, String pressureUnit, double maxUtilization) {
    if (mode != Mode.PROCESS_SYSTEM && mode != Mode.PROCESS_MODEL) {
      throw new IllegalStateException(
          "findMaxFlowRateAtPressureBoundaries requires PROCESS_SYSTEM or PROCESS_MODEL mode");
    }

    // Auto-configure compressor charts
    if (autoConfigureProcessCompressors) {
      configureProcessCompressorCharts();
    }

    // Set target outlet pressure on the last compressor (if outlet is a compressor)
    ProcessEquipmentInterface outletEquip = getOutletEquipment();
    Compressor lastCompressor = null;
    double originalOutletPressure = 0;
    if (outletEquip instanceof Compressor) {
      lastCompressor = (Compressor) outletEquip;
      originalOutletPressure = lastCompressor.getOutletPressure();
      lastCompressor.setOutletPressure(targetOutletPressure, pressureUnit);
    }

    double lowRate = minFlowRate;
    double highRate = maxFlowRate;
    double searchTolerance = (highRate - lowRate) * 0.001; // 0.1% of range
    double pressureTol = targetOutletPressure * 0.02; // 2% pressure tolerance

    ProcessOperatingPoint bestPoint = null;
    double bestFlow = 0.0;

    try {
      int iter = 0;
      while ((highRate - lowRate) > searchTolerance && iter < maxIterations) {
        double midRate = (lowRate + highRate) / 2.0;

        ProcessOperatingPoint point =
            findProcessOperatingPoint(midRate, "kg/hr", inletPressure, pressureUnit);

        if (point == null) {
          // Simulation failed - reduce flow
          highRate = midRate;
          iter++;
          continue;
        }

        double outletPressure = point.getOutletPressure();
        double util = point.getMaxUtilization();
        boolean utilizationOk = util <= maxUtilization;

        // Check if outlet pressure is within tolerance of target
        boolean pressureOk = Math.abs(outletPressure - targetOutletPressure) <= pressureTol;

        // Check compressor feasibility (surge, stonewall, power limits)
        boolean compressorsFeasible = checkCompressorsFeasible(point);

        boolean feasible = point.isFeasible() && utilizationOk && pressureOk && compressorsFeasible;

        logger.debug("Iter {}: flow={:.0f}, Pout={:.2f} (target={:.2f}), util={:.1f}%, feasible={}",
            iter, midRate, outletPressure, targetOutletPressure, util * 100, feasible);

        if (feasible) {
          // This flow rate works - try higher
          if (midRate > bestFlow) {
            bestFlow = midRate;
            bestPoint = point;
          }
          lowRate = midRate;
        } else {
          // Not feasible - try lower
          highRate = midRate;
        }

        iter++;
      }
    } finally {
      // Restore original outlet pressure setting
      if (lastCompressor != null) {
        lastCompressor.setOutletPressure(originalOutletPressure);
      }
    }

    return bestPoint;
  }

  /**
   * Gets the outlet equipment.
   *
   * @return the outlet equipment
   */
  private ProcessEquipmentInterface getOutletEquipment() {
    return outletEquipment;
  }

  /**
   * Checks if all compressors in the operating point are within feasible limits.
   *
   * @param point the process operating point
   * @return true if all compressors are feasible
   */
  private boolean checkCompressorsFeasible(ProcessOperatingPoint point) {
    Map<String, CompressorOperatingPoint> compPoints = point.getCompressorOperatingPoints();
    for (CompressorOperatingPoint cp : compPoints.values()) {
      // Check surge
      if (cp.isInSurge()) {
        return false;
      }
      // Check stonewall
      if (cp.isAtStoneWall()) {
        return false;
      }
      // Check surge margin
      if (cp.getSurgeMargin() < minSurgeMargin) {
        return false;
      }
      // Check power limit
      if (maxPowerLimit < Double.MAX_VALUE && cp.getPower() > maxPowerLimit) {
        return false;
      }
      // Check speed limits
      if (cp.getSpeed() > maxSpeedLimit || cp.getSpeed() < minSpeedLimit) {
        return false;
      }
    }
    return true;
  }

  /**
   * Generates a process capacity table showing maximum flow rate at different inlet/outlet pressure
   * combinations.
   *
   * <p>
   * This method creates a 2D table where each cell contains the maximum flow rate achievable for
   * the given inlet pressure (row) and outlet pressure (column) while respecting equipment
   * constraints. This is the natural format for lift curve generation where pressures are the
   * boundary conditions.
   * </p>
   *
   * <h3>Example Usage</h3>
   * 
   * <pre>
   * double[] inletPressures = {60, 70, 80, 90}; // bara
   * double[] outletPressures = {130, 140, 150, 160}; // bara
   * 
   * ProcessCapacityTable table =
   *     optimizer.generateProcessCapacityTable(inletPressures, outletPressures, "bara", 0.95);
   * 
   * System.out.println(table.toFormattedString());
   * System.out.println(table.toEclipseFormat());
   * </pre>
   *
   * @param inletPressures array of inlet pressures
   * @param outletPressures array of outlet pressures
   * @param pressureUnit the pressure unit
   * @param maxUtilization the maximum allowed equipment utilization
   * @return a ProcessCapacityTable with maximum flow rates and power data
   */
  public ProcessCapacityTable generateProcessCapacityTable(double[] inletPressures,
      double[] outletPressures, String pressureUnit, double maxUtilization) {
    if (mode != Mode.PROCESS_SYSTEM && mode != Mode.PROCESS_MODEL) {
      throw new IllegalStateException(
          "generateProcessCapacityTable requires PROCESS_SYSTEM or PROCESS_MODEL mode");
    }

    // Auto-configure compressor charts
    if (autoConfigureProcessCompressors) {
      configureProcessCompressorCharts();
    }

    // Get compressor names
    List<Compressor> compressors = getProcessCompressors();
    List<String> compressorNames = new ArrayList<String>();
    for (Compressor comp : compressors) {
      compressorNames.add(comp.getName());
    }

    ProcessCapacityTable table = new ProcessCapacityTable("Process_Capacity", inletPressures,
        outletPressures, compressorNames);
    table.setPressureUnit(pressureUnit);
    table.setMaxUtilization(maxUtilization);

    // Calculate total number of evaluations for progress reporting
    int totalEvals = inletPressures.length * outletPressures.length;
    int evalCount = 0;

    // Find max flow for each inlet/outlet pressure combination
    for (int i = 0; i < inletPressures.length; i++) {
      for (int j = 0; j < outletPressures.length; j++) {
        evalCount++;
        try {
          reportProgress(evalCount, totalEvals, String.format("Evaluating Pin=%.1f, Pout=%.1f %s",
              inletPressures[i], outletPressures[j], pressureUnit));

          ProcessOperatingPoint point = findMaxFlowRateAtPressureBoundaries(inletPressures[i],
              outletPressures[j], pressureUnit, maxUtilization);
          table.setOperatingPoint(i, j, point);
        } catch (Exception e) {
          logger.debug("Error at Pin={}, Pout={}: {}", inletPressures[i], outletPressures[j],
              e.getMessage());
        }
      }
    }

    reportProgress(totalEvals, totalEvals, "Capacity table generation complete");
    return table;
  }

  /**
   * Generates a detailed process capacity curve for a fixed inlet pressure.
   *
   * <p>
   * For each target outlet pressure, finds the maximum flow rate and reports total power, equipment
   * utilizations, and bottleneck information. This is useful for understanding process capacity
   * versus delivery pressure.
   * </p>
   *
   * @param inletPressure the inlet pressure (fixed)
   * @param outletPressures array of target outlet pressures
   * @param pressureUnit the pressure unit
   * @param maxUtilization maximum allowed equipment utilization
   * @param flowRateUnit unit for reporting flow rates
   * @return array of ProcessOperatingPoints for each outlet pressure
   */
  public ProcessOperatingPoint[] generateCapacityCurve(double inletPressure,
      double[] outletPressures, String pressureUnit, double maxUtilization, String flowRateUnit) {
    if (mode != Mode.PROCESS_SYSTEM && mode != Mode.PROCESS_MODEL) {
      throw new IllegalStateException(
          "generateCapacityCurve requires PROCESS_SYSTEM or PROCESS_MODEL mode");
    }

    // Auto-configure compressor charts
    if (autoConfigureProcessCompressors) {
      configureProcessCompressorCharts();
    }

    ProcessOperatingPoint[] curve = new ProcessOperatingPoint[outletPressures.length];

    for (int i = 0; i < outletPressures.length; i++) {
      curve[i] = findMaxFlowRateAtPressureBoundaries(inletPressure, outletPressures[i],
          pressureUnit, maxUtilization);

      if (curve[i] != null) {
        logger.info("Pout={} {}: Max flow={} {}, Power={} kW, Util={}%", outletPressures[i],
            pressureUnit, curve[i].getFlowRate(), flowRateUnit, curve[i].getTotalPower(),
            curve[i].getMaxUtilization() * 100);
      }
    }

    return curve;
  }

  /**
   * Evaluates ProcessSystem outlet pressure.
   */
  private double evaluateProcessSystem(double flowRate, String flowRateUnit, double inletPressure,
      String pressureUnit) {
    // Set inlet conditions directly on the stream's thermo system
    inletStream.getThermoSystem().setTotalFlowRate(flowRate, flowRateUnit);
    inletStream.getThermoSystem().setPressure(inletPressure, pressureUnit);

    // Run TPflash to equilibrate
    ThermodynamicOperations ops = new ThermodynamicOperations(inletStream.getThermoSystem());
    ops.TPflash();

    // Run the process system
    processSystem.run();

    // Get outlet pressure from the outlet equipment (which could be pipeline or stream)
    if (outletEquipment instanceof TwoPortInterface) {
      return ((TwoPortInterface) outletEquipment).getOutletStream().getPressure(pressureUnit);
    }
    return outletStream.getPressure(pressureUnit);
  }

  /**
   * Evaluates ProcessModel outlet pressure.
   */
  private double evaluateProcessModel(double flowRate, String flowRateUnit, double inletPressure,
      String pressureUnit) {
    // Set inlet conditions directly on the stream's thermo system
    inletStream.getThermoSystem().setTotalFlowRate(flowRate, flowRateUnit);
    inletStream.getThermoSystem().setPressure(inletPressure, pressureUnit);

    // Run TPflash to equilibrate
    ThermodynamicOperations ops = new ThermodynamicOperations(inletStream.getThermoSystem());
    ops.TPflash();

    // Run the process model (handles multi-system convergence)
    processModel.run();

    // Get outlet pressure from the outlet equipment (which could be pipeline or stream)
    if (outletEquipment instanceof TwoPortInterface) {
      return ((TwoPortInterface) outletEquipment).getOutletStream().getPressure(pressureUnit);
    }
    return outletStream.getPressure(pressureUnit);
  }

  /**
   * Checks capacity constraints on all equipment.
   *
   * @return list of constraint violations
   */
  private List<ConstraintViolation> checkConstraints() {
    List<ConstraintViolation> violations = new ArrayList<ConstraintViolation>();

    if (!checkCapacityConstraints) {
      return violations;
    }

    // Check capacity constraints on equipment
    List<ProcessEquipmentInterface> equipment = getEquipmentList();
    for (ProcessEquipmentInterface equip : equipment) {
      if (equip instanceof CapacityConstrainedEquipment) {
        try {
          CapacityConstrainedEquipment constrained = (CapacityConstrainedEquipment) equip;
          Map<String, CapacityConstraint> constraints = constrained.getCapacityConstraints();

          if (constraints != null) {
            for (Map.Entry<String, CapacityConstraint> entry : constraints.entrySet()) {
              CapacityConstraint c = entry.getValue();
              if (c.isViolated()) {
                boolean isHard = c.getType() == CapacityConstraint.ConstraintType.HARD
                    || c.getSeverity() == CapacityConstraint.ConstraintSeverity.CRITICAL
                    || c.getSeverity() == CapacityConstraint.ConstraintSeverity.HARD;

                violations.add(new ConstraintViolation(c.getName(), equip.getName(),
                    c.getCurrentValue(), c.getDesignValue(), c.getUnit(), isHard));
              }
            }
          }
        } catch (Exception e) {
          logger.debug("Could not check constraints for {}: {}", equip.getName(), e.getMessage());
        }
      }
    }

    return violations;
  }

  /**
   * Gets list of all equipment to check for constraints.
   */
  private List<ProcessEquipmentInterface> getEquipmentList() {
    List<ProcessEquipmentInterface> equipment = new ArrayList<ProcessEquipmentInterface>();

    switch (mode) {
      case PROCESS_SYSTEM:
        equipment.addAll(processSystem.getUnitOperations());
        break;

      case PROCESS_MODEL:
        for (ProcessSystem ps : processModel.getAllProcesses()) {
          equipment.addAll(ps.getUnitOperations());
        }
        break;
    }

    return equipment;
  }

  // ============ Getters and Setters ============

  /**
   * Gets the maximum number of iterations.
   *
   * @return max iterations
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Sets the maximum number of iterations.
   *
   * @param maxIterations max iterations
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Gets the convergence tolerance.
   *
   * @return tolerance (relative error)
   */
  public double getTolerance() {
    return tolerance;
  }

  /**
   * Sets the convergence tolerance.
   *
   * @param tolerance tolerance (relative error)
   */
  public void setTolerance(double tolerance) {
    this.tolerance = tolerance;
  }

  /**
   * Gets the minimum flow rate limit.
   *
   * @return minimum flow rate in kg/hr
   */
  public double getMinFlowRate() {
    return minFlowRate;
  }

  /**
   * Sets the minimum flow rate limit.
   *
   * @param minFlowRate minimum flow rate in kg/hr
   */
  public void setMinFlowRate(double minFlowRate) {
    this.minFlowRate = minFlowRate;
  }

  /**
   * Gets the maximum flow rate limit.
   *
   * @return maximum flow rate in kg/hr
   */
  public double getMaxFlowRate() {
    return maxFlowRate;
  }

  /**
   * Sets the maximum flow rate limit.
   *
   * @param maxFlowRate maximum flow rate in kg/hr
   */
  public void setMaxFlowRate(double maxFlowRate) {
    this.maxFlowRate = maxFlowRate;
  }

  /**
   * Gets the initial flow rate guess.
   *
   * @return initial flow rate in kg/hr
   */
  public double getInitialFlowGuess() {
    return initialFlowGuess;
  }

  /**
   * Sets the initial flow rate guess.
   *
   * @param initialFlowGuess initial flow rate in kg/hr
   */
  public void setInitialFlowGuess(double initialFlowGuess) {
    this.initialFlowGuess = initialFlowGuess;
  }

  /**
   * Gets the maximum velocity constraint.
   *
   * @return maximum velocity in m/s
   */
  public double getMaxVelocity() {
    return maxVelocity;
  }

  /**
   * Sets the maximum velocity constraint.
   *
   * @param maxVelocity maximum velocity in m/s
   */
  public void setMaxVelocity(double maxVelocity) {
    this.maxVelocity = maxVelocity;
  }

  /**
   * Checks if capacity constraints are being checked.
   *
   * @return true if checking constraints
   */
  public boolean isCheckCapacityConstraints() {
    return checkCapacityConstraints;
  }

  /**
   * Sets whether to check capacity constraints.
   *
   * @param checkCapacityConstraints true to check constraints
   */
  public void setCheckCapacityConstraints(boolean checkCapacityConstraints) {
    this.checkCapacityConstraints = checkCapacityConstraints;
  }

  /**
   * Gets the operation mode.
   *
   * @return the mode
   */
  public Mode getMode() {
    return mode;
  }

  /**
   * Gets the minimum surge margin for compressor operation.
   *
   * @return minimum surge margin as a fraction (0.1 = 10%)
   */
  public double getMinSurgeMargin() {
    return minSurgeMargin;
  }

  /**
   * Sets the minimum surge margin for compressor operation.
   *
   * <p>
   * Operating points with surge margin below this value will be marked as infeasible. Typical
   * values are 0.10 to 0.20 (10-20% margin).
   * </p>
   *
   * @param minSurgeMargin minimum surge margin as a fraction
   */
  public void setMinSurgeMargin(double minSurgeMargin) {
    this.minSurgeMargin = minSurgeMargin;
  }

  /**
   * Gets whether the optimizer will solve for compressor speed.
   *
   * @return true if solving for speed
   */
  public boolean isSolveSpeed() {
    return solveSpeed;
  }

  /**
   * Sets whether to solve for compressor speed.
   *
   * @param solveSpeed true to solve for speed
   */
  public void setSolveSpeed(boolean solveSpeed) {
    this.solveSpeed = solveSpeed;
  }

  /**
   * Gets the maximum power limit for compressor operation.
   *
   * @return maximum power in kW
   */
  public double getMaxPowerLimit() {
    return maxPowerLimit;
  }

  /**
   * Sets the maximum power limit for compressor operation.
   *
   * <p>
   * Operating points where compressor power exceeds this limit will be marked as infeasible. This
   * is typically set based on driver rated power or mechanical design limits.
   * </p>
   *
   * @param maxPowerLimit maximum power in kW
   */
  public void setMaxPowerLimit(double maxPowerLimit) {
    this.maxPowerLimit = maxPowerLimit;
  }

  /**
   * Gets the maximum speed limit for compressor operation.
   *
   * @return maximum speed in RPM
   */
  public double getMaxSpeedLimit() {
    return maxSpeedLimit;
  }

  /**
   * Sets the maximum speed limit for compressor operation.
   *
   * <p>
   * Operating points where compressor speed exceeds this limit will be marked as infeasible. This
   * is typically set based on mechanical design or compressor chart limits.
   * </p>
   *
   * @param maxSpeedLimit maximum speed in RPM
   */
  public void setMaxSpeedLimit(double maxSpeedLimit) {
    this.maxSpeedLimit = maxSpeedLimit;
  }

  /**
   * Gets the minimum speed limit for compressor operation.
   *
   * @return minimum speed in RPM
   */
  public double getMinSpeedLimit() {
    return minSpeedLimit;
  }

  /**
   * Sets the minimum speed limit for compressor operation.
   *
   * <p>
   * Operating points where compressor speed is below this limit will be marked as infeasible. This
   * may be set based on stability limits or minimum turndown.
   * </p>
   *
   * @param minSpeedLimit minimum speed in RPM
   */
  public void setMinSpeedLimit(double minSpeedLimit) {
    this.minSpeedLimit = minSpeedLimit;
  }

  /**
   * Gets whether compressor charts are auto-generated if not configured.
   *
   * @return true if auto-generating charts
   */
  public boolean isAutoGenerateCompressorChart() {
    return autoGenerateCompressorChart;
  }

  /**
   * Sets whether to auto-generate compressor charts if not configured.
   *
   * <p>
   * When enabled (default), if the compressor doesn't have a chart configured, one will be
   * auto-generated using the CompressorChartGenerator based on the current design point. This
   * follows the pattern used in the ProductionOptimizer framework.
   * </p>
   *
   * @param autoGenerateCompressorChart true to auto-generate charts
   */
  public void setAutoGenerateCompressorChart(boolean autoGenerateCompressorChart) {
    this.autoGenerateCompressorChart = autoGenerateCompressorChart;
  }

  /**
   * Gets the number of speed lines for auto-generated charts.
   *
   * @return number of speed lines
   */
  public int getNumberOfChartSpeeds() {
    return numberOfChartSpeeds;
  }

  /**
   * Sets the number of speed lines for auto-generated charts.
   *
   * @param numberOfChartSpeeds number of speed lines (typically 3-7)
   */
  public void setNumberOfChartSpeeds(int numberOfChartSpeeds) {
    this.numberOfChartSpeeds = numberOfChartSpeeds;
  }

  /**
   * Gets the speed margin above design for auto-generated charts.
   *
   * @return speed margin as fraction (0.15 = 15% above design)
   */
  public double getSpeedMarginAboveDesign() {
    return speedMarginAboveDesign;
  }

  /**
   * Sets the speed margin above design for auto-generated charts.
   *
   * <p>
   * When auto-generating charts, the maximum speed is set to the design speed times (1 + margin).
   * Typical values are 0.10 to 0.20 (10-20% margin above design).
   * </p>
   *
   * @param speedMarginAboveDesign speed margin as fraction
   */
  public void setSpeedMarginAboveDesign(double speedMarginAboveDesign) {
    this.speedMarginAboveDesign = speedMarginAboveDesign;
  }

  /**
   * Gets whether to auto-configure compressor charts for process systems.
   *
   * @return true if auto-configuring
   */
  public boolean isAutoConfigureProcessCompressors() {
    return autoConfigureProcessCompressors;
  }

  /**
   * Sets whether to auto-configure compressor charts for process systems.
   *
   * <p>
   * When enabled (default), all compressors in the process will have their charts auto-generated if
   * not already configured. This follows the pattern from ProcessOptimizationExampleTest.
   * </p>
   *
   * @param autoConfigureProcessCompressors true to auto-configure
   */
  public void setAutoConfigureProcessCompressors(boolean autoConfigureProcessCompressors) {
    this.autoConfigureProcessCompressors = autoConfigureProcessCompressors;
  }

  /**
   * Gets the maximum total power limit for all compressors.
   *
   * @return maximum total power in kW
   */
  public double getMaxTotalPowerLimit() {
    return maxTotalPowerLimit;
  }

  /**
   * Sets the maximum total power limit for all compressors.
   *
   * <p>
   * Operating points where the sum of all compressor powers exceeds this limit will be marked as
   * infeasible. This is useful for overall process power constraints.
   * </p>
   *
   * @param maxTotalPowerLimit maximum total power in kW
   */
  public void setMaxTotalPowerLimit(double maxTotalPowerLimit) {
    this.maxTotalPowerLimit = maxTotalPowerLimit;
  }

  /**
   * Gets the maximum equipment utilization limit.
   *
   * @return maximum utilization as a fraction (1.0 = 100%)
   */
  public double getMaxEquipmentUtilizationLimit() {
    return maxEquipmentUtilization;
  }

  /**
   * Sets the maximum equipment utilization limit.
   *
   * <p>
   * Operating points where any equipment exceeds this utilization will be marked as infeasible.
   * Default is 1.0 (100%). Set to 0.95 for a 5% margin.
   * </p>
   *
   * @param maxEquipmentUtilization maximum utilization as a fraction
   */
  public void setMaxEquipmentUtilizationLimit(double maxEquipmentUtilization) {
    this.maxEquipmentUtilization = maxEquipmentUtilization;
  }

  /**
   * Sets a progress callback for monitoring long-running operations.
   *
   * @param callback the callback to receive progress updates
   */
  public void setProgressCallback(ProgressCallback callback) {
    this.progressCallback = callback;
  }

  /**
   * Gets the progress callback.
   *
   * @return the progress callback, or null if not set
   */
  public ProgressCallback getProgressCallback() {
    return progressCallback;
  }

  /**
   * Enables or disables progress logging to the logger.
   *
   * <p>
   * When enabled, progress messages will be logged at INFO level during long-running operations
   * like lift curve generation.
   * </p>
   *
   * @param enabled true to enable progress logging
   */
  public void setEnableProgressLogging(boolean enabled) {
    this.enableProgressLogging = enabled;
  }

  /**
   * Reports progress to callback and/or logger.
   *
   * @param current current step
   * @param total total steps
   * @param message progress message
   */
  private void reportProgress(int current, int total, String message) {
    if (progressCallback != null) {
      progressCallback.onProgress(current, total, message);
    }
    if (enableProgressLogging) {
      logger.info("Progress {}/{}: {}", current, total, message);
    }
  }

  // ============ Professional Lift Curve Generation ============

  /**
   * Configuration class for professional lift curve generation.
   *
   * <p>
   * This builder class encapsulates best practices for generating lift curves suitable for
   * integration with reservoir simulators like Eclipse E300. It provides sensible defaults while
   * allowing customization of all parameters.
   * </p>
   *
   * <h3>Example Usage</h3>
   * 
   * <pre>
   * LiftCurveConfiguration config =
   *     new LiftCurveConfiguration().withInletPressureRange(50.0, 90.0, 5) // 50-90 bara, 5 points
   *         .withOutletPressureRange(130.0, 160.0, 4) // 130-160 bara, 4 points
   *         .withSurgeMargin(0.15) // 15% surge margin
   *         .withMaxPowerLimit(5000.0) // 5 MW limit
   *         .withMaxUtilization(0.95) // 95% max utilization
   *         .withProgressLogging(true);
   * 
   * LiftCurveResult result = optimizer.generateProfessionalLiftCurves(config);
   * System.out.println(result.getCapacityTable().toEclipseFormat());
   * </pre>
   *
   * @author ESOL
   * @version 1.0
   */
  public static class LiftCurveConfiguration implements Serializable {
    private static final long serialVersionUID = 1L;

    // Pressure ranges
    private double minInletPressure = 50.0;
    private double maxInletPressure = 100.0;
    private int numInletPressurePoints = 5;
    private double minOutletPressure = 120.0;
    private double maxOutletPressure = 180.0;
    private int numOutletPressurePoints = 5;
    private String pressureUnit = "bara";

    // Flow rate configuration
    private double minFlowRate = 10000.0;
    private double maxFlowRate = 200000.0;
    private int numFlowRatePoints = 10;
    private String flowRateUnit = "kg/hr";

    // Compressor constraints
    private double surgeMargin = 0.15; // 15% default
    private double maxPowerLimit = Double.MAX_VALUE;
    private double maxTotalPowerLimit = Double.MAX_VALUE;
    private double maxSpeedLimit = Double.MAX_VALUE;
    private double minSpeedLimit = 0.0;

    // Equipment constraints
    private double maxUtilization = 0.95; // 95% default for safety margin

    // Progress and logging
    private boolean enableProgressLogging = false;
    private ProgressCallback progressCallback;

    // Output options
    private boolean generateCapacityTable = true;
    private boolean generateLiftCurveTable = true;
    private boolean generatePerformanceTable = true;

    /**
     * Default constructor with sensible defaults for offshore gas compression.
     */
    public LiftCurveConfiguration() {}

    /**
     * Sets the inlet pressure range.
     *
     * @param minPressure minimum inlet pressure
     * @param maxPressure maximum inlet pressure
     * @param numPoints number of pressure points
     * @return this configuration for chaining
     */
    public LiftCurveConfiguration withInletPressureRange(double minPressure, double maxPressure,
        int numPoints) {
      this.minInletPressure = minPressure;
      this.maxInletPressure = maxPressure;
      this.numInletPressurePoints = numPoints;
      return this;
    }

    /**
     * Sets the outlet pressure range.
     *
     * @param minPressure minimum outlet pressure
     * @param maxPressure maximum outlet pressure
     * @param numPoints number of pressure points
     * @return this configuration for chaining
     */
    public LiftCurveConfiguration withOutletPressureRange(double minPressure, double maxPressure,
        int numPoints) {
      this.minOutletPressure = minPressure;
      this.maxOutletPressure = maxPressure;
      this.numOutletPressurePoints = numPoints;
      return this;
    }

    /**
     * Sets the flow rate range.
     *
     * @param minFlow minimum flow rate
     * @param maxFlow maximum flow rate
     * @param numPoints number of flow rate points
     * @return this configuration for chaining
     */
    public LiftCurveConfiguration withFlowRateRange(double minFlow, double maxFlow, int numPoints) {
      this.minFlowRate = minFlow;
      this.maxFlowRate = maxFlow;
      this.numFlowRatePoints = numPoints;
      return this;
    }

    /**
     * Sets the pressure unit.
     *
     * @param unit pressure unit (e.g., "bara", "barg", "psia")
     * @return this configuration for chaining
     */
    public LiftCurveConfiguration withPressureUnit(String unit) {
      this.pressureUnit = unit;
      return this;
    }

    /**
     * Sets the flow rate unit.
     *
     * @param unit flow rate unit (e.g., "kg/hr", "Sm3/day", "MMscfd")
     * @return this configuration for chaining
     */
    public LiftCurveConfiguration withFlowRateUnit(String unit) {
      this.flowRateUnit = unit;
      return this;
    }

    /**
     * Sets the minimum surge margin for compressors.
     *
     * @param margin surge margin as fraction (0.15 = 15%)
     * @return this configuration for chaining
     */
    public LiftCurveConfiguration withSurgeMargin(double margin) {
      this.surgeMargin = margin;
      return this;
    }

    /**
     * Sets the maximum power limit per compressor.
     *
     * @param powerKw maximum power in kW
     * @return this configuration for chaining
     */
    public LiftCurveConfiguration withMaxPowerLimit(double powerKw) {
      this.maxPowerLimit = powerKw;
      return this;
    }

    /**
     * Sets the maximum total power limit for all compressors.
     *
     * @param powerKw maximum total power in kW
     * @return this configuration for chaining
     */
    public LiftCurveConfiguration withMaxTotalPowerLimit(double powerKw) {
      this.maxTotalPowerLimit = powerKw;
      return this;
    }

    /**
     * Sets compressor speed limits.
     *
     * @param minSpeed minimum speed in RPM
     * @param maxSpeed maximum speed in RPM
     * @return this configuration for chaining
     */
    public LiftCurveConfiguration withSpeedLimits(double minSpeed, double maxSpeed) {
      this.minSpeedLimit = minSpeed;
      this.maxSpeedLimit = maxSpeed;
      return this;
    }

    /**
     * Sets the maximum equipment utilization.
     *
     * @param utilization maximum utilization as fraction (0.95 = 95%)
     * @return this configuration for chaining
     */
    public LiftCurveConfiguration withMaxUtilization(double utilization) {
      this.maxUtilization = utilization;
      return this;
    }

    /**
     * Enables or disables progress logging.
     *
     * @param enabled true to enable
     * @return this configuration for chaining
     */
    public LiftCurveConfiguration withProgressLogging(boolean enabled) {
      this.enableProgressLogging = enabled;
      return this;
    }

    /**
     * Sets a progress callback.
     *
     * @param callback the callback
     * @return this configuration for chaining
     */
    public LiftCurveConfiguration withProgressCallback(ProgressCallback callback) {
      this.progressCallback = callback;
      return this;
    }

    /**
     * Configures which tables to generate.
     *
     * @param capacity generate capacity table
     * @param liftCurve generate lift curve table
     * @param performance generate performance table
     * @return this configuration for chaining
     */
    public LiftCurveConfiguration withTables(boolean capacity, boolean liftCurve,
        boolean performance) {
      this.generateCapacityTable = capacity;
      this.generateLiftCurveTable = liftCurve;
      this.generatePerformanceTable = performance;
      return this;
    }

    /**
     * Generates the inlet pressure array based on configuration.
     *
     * @return array of inlet pressures
     */
    public double[] getInletPressures() {
      return generateLinearArray(minInletPressure, maxInletPressure, numInletPressurePoints);
    }

    /**
     * Generates the outlet pressure array based on configuration.
     *
     * @return array of outlet pressures
     */
    public double[] getOutletPressures() {
      return generateLinearArray(minOutletPressure, maxOutletPressure, numOutletPressurePoints);
    }

    /**
     * Generates the flow rate array based on configuration.
     *
     * @return array of flow rates
     */
    public double[] getFlowRates() {
      return generateLinearArray(minFlowRate, maxFlowRate, numFlowRatePoints);
    }

    private double[] generateLinearArray(double min, double max, int points) {
      if (points <= 1) {
        return new double[] {min};
      }
      double[] arr = new double[points];
      double step = (max - min) / (points - 1);
      for (int i = 0; i < points; i++) {
        arr[i] = min + i * step;
      }
      return arr;
    }

    // Getters
    /** @return the pressure unit */
    public String getPressureUnit() {
      return pressureUnit;
    }

    /** @return the flow rate unit */
    public String getFlowRateUnit() {
      return flowRateUnit;
    }

    /** @return the surge margin */
    public double getSurgeMargin() {
      return surgeMargin;
    }

    /** @return the max power limit */
    public double getMaxPowerLimit() {
      return maxPowerLimit;
    }

    /** @return the max total power limit */
    public double getMaxTotalPowerLimit() {
      return maxTotalPowerLimit;
    }

    /** @return the max speed limit */
    public double getMaxSpeedLimit() {
      return maxSpeedLimit;
    }

    /** @return the min speed limit */
    public double getMinSpeedLimit() {
      return minSpeedLimit;
    }

    /** @return the max utilization */
    public double getMaxUtilization() {
      return maxUtilization;
    }

    /** @return whether progress logging is enabled */
    public boolean isEnableProgressLogging() {
      return enableProgressLogging;
    }

    /** @return the progress callback */
    public ProgressCallback getProgressCallback() {
      return progressCallback;
    }

    /** @return whether to generate capacity table */
    public boolean isGenerateCapacityTable() {
      return generateCapacityTable;
    }

    /** @return whether to generate lift curve table */
    public boolean isGenerateLiftCurveTable() {
      return generateLiftCurveTable;
    }

    /** @return whether to generate performance table */
    public boolean isGeneratePerformanceTable() {
      return generatePerformanceTable;
    }
  }

  /**
   * Result container for professional lift curve generation.
   *
   * <p>
   * Contains all generated tables and summary information from a lift curve generation run.
   * </p>
   *
   * @author ESOL
   * @version 1.0
   */
  public static class LiftCurveResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private ProcessCapacityTable capacityTable;
    private ProcessLiftCurveTable liftCurveTable;
    private ProcessPerformanceTable performanceTable;
    private long generationTimeMs;
    private int totalEvaluations;
    private int feasiblePoints;
    private List<String> warnings = new ArrayList<String>();

    /**
     * Default constructor.
     */
    public LiftCurveResult() {}

    /** @return the capacity table */
    public ProcessCapacityTable getCapacityTable() {
      return capacityTable;
    }

    /** @param capacityTable the capacity table to set */
    public void setCapacityTable(ProcessCapacityTable capacityTable) {
      this.capacityTable = capacityTable;
    }

    /** @return the lift curve table */
    public ProcessLiftCurveTable getLiftCurveTable() {
      return liftCurveTable;
    }

    /** @param liftCurveTable the lift curve table to set */
    public void setLiftCurveTable(ProcessLiftCurveTable liftCurveTable) {
      this.liftCurveTable = liftCurveTable;
    }

    /** @return the performance table */
    public ProcessPerformanceTable getPerformanceTable() {
      return performanceTable;
    }

    /** @param performanceTable the performance table to set */
    public void setPerformanceTable(ProcessPerformanceTable performanceTable) {
      this.performanceTable = performanceTable;
    }

    /** @return generation time in milliseconds */
    public long getGenerationTimeMs() {
      return generationTimeMs;
    }

    /** @param generationTimeMs the generation time to set */
    public void setGenerationTimeMs(long generationTimeMs) {
      this.generationTimeMs = generationTimeMs;
    }

    /** @return total number of evaluations */
    public int getTotalEvaluations() {
      return totalEvaluations;
    }

    /** @param totalEvaluations the total evaluations to set */
    public void setTotalEvaluations(int totalEvaluations) {
      this.totalEvaluations = totalEvaluations;
    }

    /** @return number of feasible points */
    public int getFeasiblePoints() {
      return feasiblePoints;
    }

    /** @param feasiblePoints the feasible points to set */
    public void setFeasiblePoints(int feasiblePoints) {
      this.feasiblePoints = feasiblePoints;
    }

    /** @return list of warnings generated during calculation */
    public List<String> getWarnings() {
      return warnings;
    }

    /**
     * Adds a warning message.
     *
     * @param warning the warning message
     */
    public void addWarning(String warning) {
      this.warnings.add(warning);
    }

    /**
     * Gets the feasibility percentage.
     *
     * @return percentage of feasible points (0-100)
     */
    public double getFeasibilityPercentage() {
      if (totalEvaluations == 0) {
        return 0.0;
      }
      return 100.0 * feasiblePoints / totalEvaluations;
    }

    /**
     * Returns a summary string.
     *
     * @return summary of the lift curve generation
     */
    public String getSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== Lift Curve Generation Summary ===\n");
      sb.append(String.format("Generation Time: %.2f seconds\n", generationTimeMs / 1000.0));
      sb.append(String.format("Total Evaluations: %d\n", totalEvaluations));
      sb.append(String.format("Feasible Points: %d (%.1f%%)\n", feasiblePoints,
          getFeasibilityPercentage()));

      if (!warnings.isEmpty()) {
        sb.append("\nWarnings:\n");
        for (String w : warnings) {
          sb.append("  - ").append(w).append("\n");
        }
      }

      if (capacityTable != null) {
        ProcessOperatingPoint maxFlow = capacityTable.findMaxFlowRatePoint();
        if (maxFlow != null) {
          sb.append(String.format("\nMax Flow Rate: %.0f %s at Pin=%.1f, Pout=%.1f %s\n",
              maxFlow.getFlowRate(), capacityTable.getFlowRateUnit(), maxFlow.getInletPressure(),
              maxFlow.getOutletPressure(), capacityTable.getPressureUnit()));
        }

        ProcessOperatingPoint minPower = capacityTable.findMinimumPowerPoint();
        if (minPower != null) {
          sb.append(String.format("Min Power Point: %.1f kW at %.0f %s\n", minPower.getTotalPower(),
              minPower.getFlowRate(), capacityTable.getFlowRateUnit()));
        }
      }

      return sb.toString();
    }
  }

  /**
   * Generates professional lift curves using the specified configuration.
   *
   * <p>
   * This method applies all best practices for lift curve generation:
   * <ul>
   * <li>Validates configuration before starting</li>
   * <li>Configures compressor charts automatically</li>
   * <li>Applies all constraint limits</li>
   * <li>Generates multiple table formats</li>
   * <li>Reports progress during generation</li>
   * <li>Captures warnings and statistics</li>
   * </ul>
   * </p>
   *
   * @param config the lift curve configuration
   * @return the lift curve result containing all generated tables
   * @throws IllegalStateException if the optimizer is not properly configured
   */
  public LiftCurveResult generateProfessionalLiftCurves(LiftCurveConfiguration config) {
    long startTime = System.currentTimeMillis();
    LiftCurveResult result = new LiftCurveResult();

    // Validate configuration
    validateOrThrow();

    // Apply configuration settings to optimizer
    setMinSurgeMargin(config.getSurgeMargin());
    setMaxPowerLimit(config.getMaxPowerLimit());
    setMaxTotalPowerLimit(config.getMaxTotalPowerLimit());
    setMaxSpeedLimit(config.getMaxSpeedLimit());
    setMinSpeedLimit(config.getMinSpeedLimit());
    setMaxEquipmentUtilizationLimit(config.getMaxUtilization());
    setEnableProgressLogging(config.isEnableProgressLogging());

    if (config.getProgressCallback() != null) {
      setProgressCallback(config.getProgressCallback());
    }

    // Configure compressor charts
    configureProcessCompressorCharts();

    // Get pressure and flow arrays
    double[] inletPressures = config.getInletPressures();
    double[] outletPressures = config.getOutletPressures();
    double[] flowRates = config.getFlowRates();
    String pressureUnit = config.getPressureUnit();
    String flowRateUnit = config.getFlowRateUnit();
    double maxUtil = config.getMaxUtilization();

    int totalEvals = 0;
    int feasibleCount = 0;

    // Generate capacity table (Pin x Pout -> max flow)
    if (config.isGenerateCapacityTable()) {
      reportProgress(0, 100, "Generating capacity table...");

      ProcessCapacityTable capacityTable =
          generateProcessCapacityTable(inletPressures, outletPressures, pressureUnit, maxUtil);
      result.setCapacityTable(capacityTable);

      totalEvals += inletPressures.length * outletPressures.length;
      feasibleCount += capacityTable.countFeasiblePoints();

      // Check for low feasibility
      if (capacityTable.countFeasiblePoints() < inletPressures.length * outletPressures.length
          / 2) {
        result.addWarning("Less than 50% of pressure combinations are feasible. "
            + "Consider expanding pressure ranges or relaxing constraints.");
      }
    }

    // Generate lift curve table (Flow x Pin -> Pout)
    if (config.isGenerateLiftCurveTable()) {
      reportProgress(50, 100, "Generating lift curve table...");

      ProcessLiftCurveTable liftCurveTable =
          generateProcessLiftCurve(flowRates, flowRateUnit, inletPressures, pressureUnit);
      result.setLiftCurveTable(liftCurveTable);

      // Count feasible points in lift curve table
      int liftFeasible = 0;
      for (int i = 0; i < flowRates.length; i++) {
        for (int j = 0; j < inletPressures.length; j++) {
          ProcessOperatingPoint pt = liftCurveTable.getOperatingPoint(i, j);
          if (pt != null && pt.isFeasible()) {
            liftFeasible++;
          }
        }
      }
      totalEvals += flowRates.length * inletPressures.length;
      feasibleCount += liftFeasible;
    }

    // Generate performance table at mid-inlet pressure
    if (config.isGeneratePerformanceTable() && inletPressures.length > 0) {
      reportProgress(75, 100, "Generating performance table...");

      double midInletPressure = inletPressures[inletPressures.length / 2];
      ProcessPerformanceTable perfTable =
          generateProcessPerformanceTable(flowRates, flowRateUnit, midInletPressure, pressureUnit);
      result.setPerformanceTable(perfTable);
    }

    // Finalize result
    result.setTotalEvaluations(totalEvals);
    result.setFeasiblePoints(feasibleCount);
    result.setGenerationTimeMs(System.currentTimeMillis() - startTime);

    reportProgress(100, 100, "Lift curve generation complete");

    return result;
  }

  /**
   * Generates professional lift curves using default configuration.
   *
   * <p>
   * This convenience method uses sensible defaults suitable for typical offshore gas compression
   * applications. For customization, use
   * {@link #generateProfessionalLiftCurves(LiftCurveConfiguration)} with a custom configuration.
   * </p>
   *
   * @param minInletPressure minimum inlet pressure
   * @param maxInletPressure maximum inlet pressure
   * @param minOutletPressure minimum outlet pressure
   * @param maxOutletPressure maximum outlet pressure
   * @param pressureUnit pressure unit
   * @return the lift curve result
   */
  public LiftCurveResult generateProfessionalLiftCurves(double minInletPressure,
      double maxInletPressure, double minOutletPressure, double maxOutletPressure,
      String pressureUnit) {
    LiftCurveConfiguration config =
        new LiftCurveConfiguration().withInletPressureRange(minInletPressure, maxInletPressure, 5)
            .withOutletPressureRange(minOutletPressure, maxOutletPressure, 5).withSurgeMargin(0.15)
            .withMaxUtilization(0.95).withPressureUnit(pressureUnit).withProgressLogging(true);

    return generateProfessionalLiftCurves(config);
  }

  // ============ Inner Classes ============

  /**
   * Simple data class representing compressor operating state within a ProcessSystem.
   *
   * <p>
   * This is a simplified compressor operating point used for tracking compressor performance within
   * process system optimization. It stores the essential operating parameters.
   * </p>
   *
   * @author ESOL
   * @version 1.0
   */
  public static class CompressorOperatingPoint implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1001L;

    private double flowRate;
    private String flowRateUnit = "m3/hr";
    private double inletPressure;
    private double outletPressure;
    private String pressureUnit = "bara";
    private double speed;
    private double power;
    private double polytropicHead;
    private double polytropicEfficiency;
    private double surgeMargin;
    private boolean inSurge;
    private boolean atStoneWall;
    private boolean feasible;

    /** Default constructor. */
    public CompressorOperatingPoint() {}

    /** @return the flowRate */
    public double getFlowRate() {
      return flowRate;
    }

    /** @param flowRate the flowRate to set */
    public void setFlowRate(double flowRate) {
      this.flowRate = flowRate;
    }

    /** @return the flowRateUnit */
    public String getFlowRateUnit() {
      return flowRateUnit;
    }

    /** @param flowRateUnit the flowRateUnit to set */
    public void setFlowRateUnit(String flowRateUnit) {
      this.flowRateUnit = flowRateUnit;
    }

    /** @return the inletPressure */
    public double getInletPressure() {
      return inletPressure;
    }

    /** @param inletPressure the inletPressure to set */
    public void setInletPressure(double inletPressure) {
      this.inletPressure = inletPressure;
    }

    /** @return the outletPressure */
    public double getOutletPressure() {
      return outletPressure;
    }

    /** @param outletPressure the outletPressure to set */
    public void setOutletPressure(double outletPressure) {
      this.outletPressure = outletPressure;
    }

    /** @return the pressureUnit */
    public String getPressureUnit() {
      return pressureUnit;
    }

    /** @param pressureUnit the pressureUnit to set */
    public void setPressureUnit(String pressureUnit) {
      this.pressureUnit = pressureUnit;
    }

    /** @return the speed in RPM */
    public double getSpeed() {
      return speed;
    }

    /** @param speed the speed in RPM to set */
    public void setSpeed(double speed) {
      this.speed = speed;
    }

    /** @return the power in kW */
    public double getPower() {
      return power;
    }

    /** @param power the power in kW to set */
    public void setPower(double power) {
      this.power = power;
    }

    /** @return the polytropicHead */
    public double getPolytropicHead() {
      return polytropicHead;
    }

    /** @param polytropicHead the polytropicHead to set */
    public void setPolytropicHead(double polytropicHead) {
      this.polytropicHead = polytropicHead;
    }

    /** @return the polytropicEfficiency */
    public double getPolytropicEfficiency() {
      return polytropicEfficiency;
    }

    /** @param polytropicEfficiency the polytropicEfficiency to set */
    public void setPolytropicEfficiency(double polytropicEfficiency) {
      this.polytropicEfficiency = polytropicEfficiency;
    }

    /** @return the surgeMargin */
    public double getSurgeMargin() {
      return surgeMargin;
    }

    /** @param surgeMargin the surgeMargin to set */
    public void setSurgeMargin(double surgeMargin) {
      this.surgeMargin = surgeMargin;
    }

    /** @return true if in surge */
    public boolean isInSurge() {
      return inSurge;
    }

    /** @param inSurge whether in surge */
    public void setInSurge(boolean inSurge) {
      this.inSurge = inSurge;
    }

    /** @return true if at stone wall */
    public boolean isAtStoneWall() {
      return atStoneWall;
    }

    /** @param atStoneWall whether at stone wall */
    public void setAtStoneWall(boolean atStoneWall) {
      this.atStoneWall = atStoneWall;
    }

    /** @return true if feasible */
    public boolean isFeasible() {
      return feasible;
    }

    /** @param feasible whether feasible */
    public void setFeasible(boolean feasible) {
      this.feasible = feasible;
    }

    /**
     * Gets the pressure ratio.
     *
     * @return outlet pressure / inlet pressure
     */
    public double getPressureRatio() {
      if (inletPressure > 0) {
        return outletPressure / inletPressure;
      }
      return Double.NaN;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return String.format(
          "CompressorOperatingPoint[flow=%.1f %s, Pin=%.2f %s, Pout=%.2f %s, "
              + "speed=%.0f RPM, power=%.1f kW, surgeMargin=%.1f%%, feasible=%s]",
          flowRate, flowRateUnit, inletPressure, pressureUnit, outletPressure, pressureUnit, speed,
          power, surgeMargin * 100, feasible);
    }
  }

  // ============ Process-Level Inner Classes ============

  /**
   * Contains equipment utilization data for reporting.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class EquipmentUtilizationData implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1003L;

    private String name;
    private String equipmentType;
    private double utilization = Double.NaN;
    private double capacity = Double.NaN;
    private double duty = Double.NaN;
    private double power = Double.NaN;
    private double speed = Double.NaN;
    private double surgeMargin = Double.NaN;
    private double stonewallMargin = Double.NaN;

    /**
     * Default constructor.
     */
    public EquipmentUtilizationData() {}

    /** @return the name */
    public String getName() {
      return name;
    }

    /** @param name the name to set */
    public void setName(String name) {
      this.name = name;
    }

    /** @return the equipmentType */
    public String getEquipmentType() {
      return equipmentType;
    }

    /** @param equipmentType the equipmentType to set */
    public void setEquipmentType(String equipmentType) {
      this.equipmentType = equipmentType;
    }

    /** @return the utilization */
    public double getUtilization() {
      return utilization;
    }

    /** @param utilization the utilization to set */
    public void setUtilization(double utilization) {
      this.utilization = utilization;
    }

    /** @return the capacity */
    public double getCapacity() {
      return capacity;
    }

    /** @param capacity the capacity to set */
    public void setCapacity(double capacity) {
      this.capacity = capacity;
    }

    /** @return the duty */
    public double getDuty() {
      return duty;
    }

    /** @param duty the duty to set */
    public void setDuty(double duty) {
      this.duty = duty;
    }

    /** @return the power */
    public double getPower() {
      return power;
    }

    /** @param power the power to set */
    public void setPower(double power) {
      this.power = power;
    }

    /** @return the speed */
    public double getSpeed() {
      return speed;
    }

    /** @param speed the speed to set */
    public void setSpeed(double speed) {
      this.speed = speed;
    }

    /** @return the surgeMargin */
    public double getSurgeMargin() {
      return surgeMargin;
    }

    /** @param surgeMargin the surgeMargin to set */
    public void setSurgeMargin(double surgeMargin) {
      this.surgeMargin = surgeMargin;
    }

    /** @return the stonewallMargin */
    public double getStonewallMargin() {
      return stonewallMargin;
    }

    /** @param stonewallMargin the stonewallMargin to set */
    public void setStonewallMargin(double stonewallMargin) {
      this.stonewallMargin = stonewallMargin;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(name).append(" (").append(equipmentType).append("): ");
      if (!Double.isNaN(utilization)) {
        sb.append(String.format("Util=%.1f%%, ", utilization * 100));
      }
      if (!Double.isNaN(power)) {
        sb.append(String.format("Power=%.1f kW, ", power));
      }
      if (!Double.isNaN(speed)) {
        sb.append(String.format("Speed=%.0f RPM, ", speed));
      }
      if (!Double.isNaN(surgeMargin)) {
        sb.append(String.format("Surge=%.1f%%, ", surgeMargin * 100));
      }
      return sb.toString();
    }
  }

  /**
   * Represents a complete process operating point with all equipment data.
   *
   * <p>
   * This class encapsulates the complete operating state of a process system at a specific flow
   * rate and inlet pressure. It includes:
   * <ul>
   * <li>Overall process conditions (flow, pressures)</li>
   * <li>Total compressor power consumption</li>
   * <li>Individual compressor operating points</li>
   * <li>Equipment utilization data</li>
   * <li>Constraint status</li>
   * </ul>
   * </p>
   *
   * @author ESOL
   * @version 1.0
   */
  public static class ProcessOperatingPoint implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1004L;

    private double flowRate;
    private String flowRateUnit = "kg/hr";
    private double inletPressure;
    private double outletPressure;
    private String pressureUnit = "bara";
    private double totalPower; // Total compressor power in kW
    private double maxUtilization;
    private boolean feasible;
    private Map<String, CompressorOperatingPoint> compressorPoints =
        new HashMap<String, CompressorOperatingPoint>();
    private Map<String, EquipmentUtilizationData> equipmentData =
        new HashMap<String, EquipmentUtilizationData>();
    private List<ConstraintViolation> constraintViolations = new ArrayList<ConstraintViolation>();

    /**
     * Default constructor.
     */
    public ProcessOperatingPoint() {}

    /**
     * Adds a compressor operating point.
     *
     * @param compressorName the compressor name
     * @param point the operating point
     */
    public void addCompressorOperatingPoint(String compressorName, CompressorOperatingPoint point) {
      compressorPoints.put(compressorName, point);
    }

    /**
     * Gets a compressor operating point.
     *
     * @param compressorName the compressor name
     * @return the operating point, or null if not found
     */
    public CompressorOperatingPoint getCompressorOperatingPoint(String compressorName) {
      return compressorPoints.get(compressorName);
    }

    /**
     * Gets the power for a specific compressor.
     *
     * @param compressorName the compressor name
     * @return the power in kW, or NaN if not found
     */
    public double getCompressorPower(String compressorName) {
      CompressorOperatingPoint point = compressorPoints.get(compressorName);
      return (point != null) ? point.getPower() : Double.NaN;
    }

    /**
     * Gets all compressor names.
     *
     * @return list of compressor names
     */
    public List<String> getCompressorNames() {
      return new ArrayList<String>(compressorPoints.keySet());
    }

    /**
     * Gets all compressor operating points.
     *
     * @return map of compressor name to operating point
     */
    public Map<String, CompressorOperatingPoint> getCompressorOperatingPoints() {
      return new HashMap<String, CompressorOperatingPoint>(compressorPoints);
    }

    /** @return the flowRate */
    public double getFlowRate() {
      return flowRate;
    }

    /** @param flowRate the flowRate to set */
    public void setFlowRate(double flowRate) {
      this.flowRate = flowRate;
    }

    /** @return the flowRateUnit */
    public String getFlowRateUnit() {
      return flowRateUnit;
    }

    /** @param flowRateUnit the flowRateUnit to set */
    public void setFlowRateUnit(String flowRateUnit) {
      this.flowRateUnit = flowRateUnit;
    }

    /** @return the inletPressure */
    public double getInletPressure() {
      return inletPressure;
    }

    /** @param inletPressure the inletPressure to set */
    public void setInletPressure(double inletPressure) {
      this.inletPressure = inletPressure;
    }

    /** @return the outletPressure */
    public double getOutletPressure() {
      return outletPressure;
    }

    /** @param outletPressure the outletPressure to set */
    public void setOutletPressure(double outletPressure) {
      this.outletPressure = outletPressure;
    }

    /** @return the pressureUnit */
    public String getPressureUnit() {
      return pressureUnit;
    }

    /** @param pressureUnit the pressureUnit to set */
    public void setPressureUnit(String pressureUnit) {
      this.pressureUnit = pressureUnit;
    }

    /** @return the totalPower */
    public double getTotalPower() {
      return totalPower;
    }

    /** @param totalPower the totalPower to set */
    public void setTotalPower(double totalPower) {
      this.totalPower = totalPower;
    }

    /** @return the maxUtilization */
    public double getMaxUtilization() {
      return maxUtilization;
    }

    /** @param maxUtilization the maxUtilization to set */
    public void setMaxUtilization(double maxUtilization) {
      this.maxUtilization = maxUtilization;
    }

    /** @return the feasible flag */
    public boolean isFeasible() {
      return feasible;
    }

    /** @param feasible the feasible to set */
    public void setFeasible(boolean feasible) {
      this.feasible = feasible;
    }

    /** @return the equipmentData */
    public Map<String, EquipmentUtilizationData> getEquipmentData() {
      return equipmentData;
    }

    /** @param equipmentData the equipmentData to set */
    public void setEquipmentData(Map<String, EquipmentUtilizationData> equipmentData) {
      this.equipmentData = equipmentData;
    }

    /** @return the constraintViolations */
    public List<ConstraintViolation> getConstraintViolations() {
      return constraintViolations;
    }

    /** @param constraintViolations the constraintViolations to set */
    public void setConstraintViolations(List<ConstraintViolation> constraintViolations) {
      this.constraintViolations = constraintViolations;
    }

    /**
     * Gets the pressure ratio across the process.
     *
     * @return outletPressure / inletPressure
     */
    public double getPressureRatio() {
      if (inletPressure > 0) {
        return outletPressure / inletPressure;
      }
      return Double.NaN;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return String.format(
          "ProcessOperatingPoint[flow=%.1f %s, Pin=%.2f %s, Pout=%.2f %s, "
              + "totalPower=%.1f kW, maxUtil=%.1f%%, feasible=%s]",
          flowRate, flowRateUnit, inletPressure, pressureUnit, outletPressure, pressureUnit,
          totalPower, maxUtilization * 100, feasible);
    }

    /**
     * Returns a formatted string with detailed process data.
     *
     * @return formatted string
     */
    public String toDetailedString() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== Process Operating Point ===\n");
      sb.append(String.format("Flow Rate: %.1f %s\n", flowRate, flowRateUnit));
      sb.append(String.format("Inlet Pressure: %.2f %s\n", inletPressure, pressureUnit));
      sb.append(String.format("Outlet Pressure: %.2f %s\n", outletPressure, pressureUnit));
      sb.append(String.format("Pressure Ratio: %.2f\n", getPressureRatio()));
      sb.append(String.format("Total Power: %.1f kW\n", totalPower));
      sb.append(String.format("Max Utilization: %.1f%%\n", maxUtilization * 100));
      sb.append(String.format("Feasible: %s\n\n", feasible));

      sb.append("--- Compressor Details ---\n");
      for (Map.Entry<String, CompressorOperatingPoint> entry : compressorPoints.entrySet()) {
        CompressorOperatingPoint cp = entry.getValue();
        sb.append(String.format("%s: Power=%.1f kW, Speed=%.0f RPM, SurgeMargin=%.1f%%\n",
            entry.getKey(), cp.getPower(), cp.getSpeed(), cp.getSurgeMargin() * 100));
      }

      if (!constraintViolations.isEmpty()) {
        sb.append("\n--- Constraint Violations ---\n");
        for (ConstraintViolation v : constraintViolations) {
          sb.append(v.toString()).append("\n");
        }
      }

      return sb.toString();
    }
  }

  /**
   * Performance table for a process system at different flow rates.
   *
   * <p>
   * Stores operating points for a range of flow rates at a fixed inlet pressure, providing methods
   * to access and format process performance data.
   * </p>
   *
   * @author ESOL
   * @version 1.0
   */
  public static class ProcessPerformanceTable implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1005L;

    private String tableName;
    private double[] flowRates;
    private List<String> compressorNames;
    private String flowRateUnit = "kg/hr";
    private String pressureUnit = "bara";
    private double inletPressure;
    private ProcessOperatingPoint[] operatingPoints;

    /**
     * Creates a new process performance table.
     *
     * @param tableName name of the table
     * @param flowRates array of flow rates
     * @param compressorNames list of compressor names in the process
     */
    public ProcessPerformanceTable(String tableName, double[] flowRates,
        List<String> compressorNames) {
      this.tableName = tableName;
      this.flowRates = flowRates.clone();
      this.compressorNames = new ArrayList<String>(compressorNames);
      this.operatingPoints = new ProcessOperatingPoint[flowRates.length];
    }

    /**
     * Sets an operating point at the specified index.
     *
     * @param index flow rate index
     * @param point the operating point
     */
    public void setOperatingPoint(int index, ProcessOperatingPoint point) {
      operatingPoints[index] = point;
    }

    /**
     * Gets an operating point at the specified index.
     *
     * @param index flow rate index
     * @return the operating point, or null if not set
     */
    public ProcessOperatingPoint getOperatingPoint(int index) {
      return operatingPoints[index];
    }

    /**
     * Gets the total power at the specified flow rate index.
     *
     * @param index flow rate index
     * @return total power in kW, or NaN if not feasible
     */
    public double getTotalPower(int index) {
      ProcessOperatingPoint point = operatingPoints[index];
      return (point != null && point.isFeasible()) ? point.getTotalPower() : Double.NaN;
    }

    /**
     * Gets the outlet pressure at the specified flow rate index.
     *
     * @param index flow rate index
     * @return outlet pressure, or NaN if not feasible
     */
    public double getOutletPressure(int index) {
      ProcessOperatingPoint point = operatingPoints[index];
      return (point != null && point.isFeasible()) ? point.getOutletPressure() : Double.NaN;
    }

    /**
     * Finds the operating point with minimum total power.
     *
     * @return the minimum power operating point, or null if no feasible points
     */
    public ProcessOperatingPoint findMinimumPowerPoint() {
      ProcessOperatingPoint minPoint = null;
      double minPower = Double.MAX_VALUE;

      for (ProcessOperatingPoint point : operatingPoints) {
        if (point != null && point.isFeasible() && point.getTotalPower() < minPower) {
          minPower = point.getTotalPower();
          minPoint = point;
        }
      }
      return minPoint;
    }

    /**
     * Returns a formatted string representation of the table.
     *
     * @return formatted table string
     */
    public String toFormattedString() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== Process Performance Table: ").append(tableName).append(" ===\n");
      sb.append("Inlet Pressure: ").append(inletPressure).append(" ").append(pressureUnit)
          .append("\n\n");

      // Header
      sb.append(String.format("%-12s %-10s %-12s %-10s", "Flow", "Pout", "TotalPower", "MaxUtil"));
      for (String compName : compressorNames) {
        sb.append(String.format(" %-15s", compName));
      }
      sb.append(" Feasible\n");

      // Units row
      sb.append(String.format("%-12s %-10s %-12s %-10s", flowRateUnit, pressureUnit, "kW", "%"));
      for (int i = 0; i < compressorNames.size(); i++) {
        sb.append(String.format(" %-15s", "kW"));
      }
      sb.append("\n");

      // Data rows
      for (int i = 0; i < flowRates.length; i++) {
        ProcessOperatingPoint point = operatingPoints[i];
        if (point != null) {
          sb.append(String.format("%-12.1f %-10.2f %-12.1f %-10.1f", flowRates[i],
              point.getOutletPressure(), point.getTotalPower(), point.getMaxUtilization() * 100));
          for (String compName : compressorNames) {
            double compPower = point.getCompressorPower(compName);
            sb.append(String.format(" %-15.1f", compPower));
          }
          sb.append(" ").append(point.isFeasible() ? "Yes" : "No").append("\n");
        } else {
          sb.append(String.format("%-12.1f %-10s %-12s %-10s", flowRates[i], "NaN", "NaN", "NaN"));
          for (int j = 0; j < compressorNames.size(); j++) {
            sb.append(String.format(" %-15s", "NaN"));
          }
          sb.append(" No\n");
        }
      }

      // Summary
      ProcessOperatingPoint minPower = findMinimumPowerPoint();
      if (minPower != null) {
        sb.append("\nMinimum Power: ").append(String.format("%.1f kW", minPower.getTotalPower()));
        sb.append(" at flow ")
            .append(String.format("%.1f %s", minPower.getFlowRate(), flowRateUnit));
        sb.append("\n");
      }

      return sb.toString();
    }

    // Getters and setters
    /** @return the tableName */
    public String getTableName() {
      return tableName;
    }

    /** @param tableName the tableName to set */
    public void setTableName(String tableName) {
      this.tableName = tableName;
    }

    /** @return the flowRates */
    public double[] getFlowRates() {
      return flowRates.clone();
    }

    /** @return the flowRateUnit */
    public String getFlowRateUnit() {
      return flowRateUnit;
    }

    /** @param flowRateUnit the flowRateUnit to set */
    public void setFlowRateUnit(String flowRateUnit) {
      this.flowRateUnit = flowRateUnit;
    }

    /** @return the pressureUnit */
    public String getPressureUnit() {
      return pressureUnit;
    }

    /** @param pressureUnit the pressureUnit to set */
    public void setPressureUnit(String pressureUnit) {
      this.pressureUnit = pressureUnit;
    }

    /** @return the inletPressure */
    public double getInletPressure() {
      return inletPressure;
    }

    /** @param inletPressure the inletPressure to set */
    public void setInletPressure(double inletPressure) {
      this.inletPressure = inletPressure;
    }
  }

  /**
   * Process lift curve table for reservoir simulator integration.
   *
   * <p>
   * Stores operating points for a grid of flow rates and inlet pressures, providing methods to
   * access pressure, power, and utilization data. Output can be formatted for Eclipse VFP tables.
   * </p>
   *
   * @author ESOL
   * @version 1.0
   */
  public static class ProcessLiftCurveTable implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1006L;

    private String tableName;
    private double[] flowRates;
    private double[] inletPressures;
    private List<String> compressorNames;
    private String flowRateUnit = "kg/hr";
    private String pressureUnit = "bara";
    private ProcessOperatingPoint[][] operatingPoints;

    /**
     * Creates a new process lift curve table.
     *
     * @param tableName name of the table
     * @param flowRates array of flow rates
     * @param inletPressures array of inlet pressures
     * @param compressorNames list of compressor names
     */
    public ProcessLiftCurveTable(String tableName, double[] flowRates, double[] inletPressures,
        List<String> compressorNames) {
      this.tableName = tableName;
      this.flowRates = flowRates.clone();
      this.inletPressures = inletPressures.clone();
      this.compressorNames = new ArrayList<String>(compressorNames);
      this.operatingPoints = new ProcessOperatingPoint[flowRates.length][inletPressures.length];
    }

    /**
     * Sets an operating point at the specified indices.
     *
     * @param flowIndex flow rate index
     * @param pressureIndex inlet pressure index
     * @param point the operating point
     */
    public void setOperatingPoint(int flowIndex, int pressureIndex, ProcessOperatingPoint point) {
      operatingPoints[flowIndex][pressureIndex] = point;
    }

    /**
     * Gets an operating point at the specified indices.
     *
     * @param flowIndex flow rate index
     * @param pressureIndex inlet pressure index
     * @return the operating point, or null if not set
     */
    public ProcessOperatingPoint getOperatingPoint(int flowIndex, int pressureIndex) {
      return operatingPoints[flowIndex][pressureIndex];
    }

    /**
     * Gets the outlet pressure values as a 2D array.
     *
     * @return outlet pressures [flowIndex][pressureIndex], NaN for infeasible points
     */
    public double[][] getOutletPressureValues() {
      double[][] pressure = new double[flowRates.length][inletPressures.length];
      for (int i = 0; i < flowRates.length; i++) {
        for (int j = 0; j < inletPressures.length; j++) {
          ProcessOperatingPoint point = operatingPoints[i][j];
          pressure[i][j] =
              (point != null && point.isFeasible()) ? point.getOutletPressure() : Double.NaN;
        }
      }
      return pressure;
    }

    /**
     * Gets the total power values as a 2D array.
     *
     * @return total power [flowIndex][pressureIndex], NaN for infeasible points
     */
    public double[][] getTotalPowerValues() {
      double[][] power = new double[flowRates.length][inletPressures.length];
      for (int i = 0; i < flowRates.length; i++) {
        for (int j = 0; j < inletPressures.length; j++) {
          ProcessOperatingPoint point = operatingPoints[i][j];
          power[i][j] = (point != null && point.isFeasible()) ? point.getTotalPower() : Double.NaN;
        }
      }
      return power;
    }

    /**
     * Gets the maximum utilization values as a 2D array.
     *
     * @return max utilization [flowIndex][pressureIndex], NaN for infeasible points
     */
    public double[][] getMaxUtilizationValues() {
      double[][] util = new double[flowRates.length][inletPressures.length];
      for (int i = 0; i < flowRates.length; i++) {
        for (int j = 0; j < inletPressures.length; j++) {
          ProcessOperatingPoint point = operatingPoints[i][j];
          util[i][j] =
              (point != null && point.isFeasible()) ? point.getMaxUtilization() : Double.NaN;
        }
      }
      return util;
    }

    /**
     * Finds the operating point with minimum total power across all feasible points.
     *
     * @return the minimum power operating point, or null if no feasible points
     */
    public ProcessOperatingPoint findMinimumPowerPoint() {
      ProcessOperatingPoint minPoint = null;
      double minPower = Double.MAX_VALUE;

      for (int i = 0; i < flowRates.length; i++) {
        for (int j = 0; j < inletPressures.length; j++) {
          ProcessOperatingPoint point = operatingPoints[i][j];
          if (point != null && point.isFeasible() && point.getTotalPower() < minPower) {
            minPower = point.getTotalPower();
            minPoint = point;
          }
        }
      }
      return minPoint;
    }

    /**
     * Returns a formatted string representation suitable for display.
     *
     * @return formatted table string
     */
    public String toFormattedString() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== Process Lift Curve Table: ").append(tableName).append(" ===\n\n");

      // Outlet pressure table
      sb.append("--- Outlet Pressure (").append(pressureUnit).append(") ---\n");
      sb.append(String.format("%-12s", "Flow\\Pin"));
      for (double p : inletPressures) {
        sb.append(String.format("%10.1f", p));
      }
      sb.append("\n");

      for (int i = 0; i < flowRates.length; i++) {
        sb.append(String.format("%-12.1f", flowRates[i]));
        for (int j = 0; j < inletPressures.length; j++) {
          ProcessOperatingPoint point = operatingPoints[i][j];
          if (point != null && point.isFeasible()) {
            sb.append(String.format("%10.2f", point.getOutletPressure()));
          } else {
            sb.append(String.format("%10s", "NaN"));
          }
        }
        sb.append("\n");
      }

      // Total power table
      sb.append("\n--- Total Power (kW) ---\n");
      sb.append(String.format("%-12s", "Flow\\Pin"));
      for (double p : inletPressures) {
        sb.append(String.format("%10.1f", p));
      }
      sb.append("\n");

      for (int i = 0; i < flowRates.length; i++) {
        sb.append(String.format("%-12.1f", flowRates[i]));
        for (int j = 0; j < inletPressures.length; j++) {
          ProcessOperatingPoint point = operatingPoints[i][j];
          if (point != null && point.isFeasible()) {
            sb.append(String.format("%10.1f", point.getTotalPower()));
          } else {
            sb.append(String.format("%10s", "NaN"));
          }
        }
        sb.append("\n");
      }

      // Max utilization table
      sb.append("\n--- Max Utilization (%) ---\n");
      sb.append(String.format("%-12s", "Flow\\Pin"));
      for (double p : inletPressures) {
        sb.append(String.format("%10.1f", p));
      }
      sb.append("\n");

      for (int i = 0; i < flowRates.length; i++) {
        sb.append(String.format("%-12.1f", flowRates[i]));
        for (int j = 0; j < inletPressures.length; j++) {
          ProcessOperatingPoint point = operatingPoints[i][j];
          if (point != null && point.isFeasible()) {
            sb.append(String.format("%10.1f", point.getMaxUtilization() * 100));
          } else {
            sb.append(String.format("%10s", "NaN"));
          }
        }
        sb.append("\n");
      }

      // Summary
      ProcessOperatingPoint minPower = findMinimumPowerPoint();
      if (minPower != null) {
        sb.append("\n--- Summary ---\n");
        sb.append(String.format("Minimum Power Point: %.1f kW\n", minPower.getTotalPower()));
        sb.append(String.format("  At flow=%.1f %s, Pin=%.1f %s, Pout=%.1f %s\n",
            minPower.getFlowRate(), flowRateUnit, minPower.getInletPressure(), pressureUnit,
            minPower.getOutletPressure(), pressureUnit));
      }

      return sb.toString();
    }

    /**
     * Returns a formatted string in Eclipse VFP-like format.
     *
     * @return Eclipse-compatible formatted string
     */
    public String toEclipseFormat() {
      StringBuilder sb = new StringBuilder();
      sb.append("-- Process Lift Curve Table: ").append(tableName).append("\n");
      sb.append("-- Flow rates (").append(flowRateUnit).append(")\n");
      sb.append("-- THP (inlet pressure, ").append(pressureUnit).append(")\n");
      sb.append("-- BHP = outlet pressure (").append(pressureUnit).append(")\n\n");

      // Header with inlet pressures
      sb.append("-- Flow\\THP ");
      for (double p : inletPressures) {
        sb.append(String.format("%8.1f ", p));
      }
      sb.append("\n");

      // Data rows
      for (int i = 0; i < flowRates.length; i++) {
        sb.append(String.format("-- %8.1f", flowRates[i]));
        for (int j = 0; j < inletPressures.length; j++) {
          ProcessOperatingPoint point = operatingPoints[i][j];
          if (point != null && point.isFeasible()) {
            sb.append(String.format("%8.2f ", point.getOutletPressure()));
          } else {
            sb.append("     NaN ");
          }
        }
        sb.append("\n");
      }

      return sb.toString();
    }

    /**
     * Converts the table to JSON format.
     *
     * @return JSON string representation
     */
    public String toJson() {
      StringBuilder sb = new StringBuilder();
      sb.append("{\n");
      sb.append("  \"tableName\": \"").append(tableName).append("\",\n");
      sb.append("  \"flowRateUnit\": \"").append(flowRateUnit).append("\",\n");
      sb.append("  \"pressureUnit\": \"").append(pressureUnit).append("\",\n");

      // Flow rates
      sb.append("  \"flowRates\": [");
      for (int i = 0; i < flowRates.length; i++) {
        sb.append(flowRates[i]);
        if (i < flowRates.length - 1) {
          sb.append(", ");
        }
      }
      sb.append("],\n");

      // Inlet pressures
      sb.append("  \"inletPressures\": [");
      for (int i = 0; i < inletPressures.length; i++) {
        sb.append(inletPressures[i]);
        if (i < inletPressures.length - 1) {
          sb.append(", ");
        }
      }
      sb.append("],\n");

      // Compressor names
      sb.append("  \"compressorNames\": [");
      for (int i = 0; i < compressorNames.size(); i++) {
        sb.append("\"").append(compressorNames.get(i)).append("\"");
        if (i < compressorNames.size() - 1) {
          sb.append(", ");
        }
      }
      sb.append("],\n");

      // Outlet pressure values
      sb.append("  \"outletPressures\": [\n");
      double[][] pout = getOutletPressureValues();
      for (int i = 0; i < pout.length; i++) {
        sb.append("    [");
        for (int j = 0; j < pout[i].length; j++) {
          if (Double.isNaN(pout[i][j])) {
            sb.append("null");
          } else {
            sb.append(String.format("%.2f", pout[i][j]));
          }
          if (j < pout[i].length - 1) {
            sb.append(", ");
          }
        }
        sb.append("]");
        if (i < pout.length - 1) {
          sb.append(",");
        }
        sb.append("\n");
      }
      sb.append("  ],\n");

      // Total power values
      sb.append("  \"totalPower\": [\n");
      double[][] power = getTotalPowerValues();
      for (int i = 0; i < power.length; i++) {
        sb.append("    [");
        for (int j = 0; j < power[i].length; j++) {
          if (Double.isNaN(power[i][j])) {
            sb.append("null");
          } else {
            sb.append(String.format("%.2f", power[i][j]));
          }
          if (j < power[i].length - 1) {
            sb.append(", ");
          }
        }
        sb.append("]");
        if (i < power.length - 1) {
          sb.append(",");
        }
        sb.append("\n");
      }
      sb.append("  ],\n");

      // Minimum power point
      ProcessOperatingPoint minPower = findMinimumPowerPoint();
      if (minPower != null) {
        sb.append("  \"minimumPowerPoint\": {\n");
        sb.append("    \"flowRate\": ").append(minPower.getFlowRate()).append(",\n");
        sb.append("    \"inletPressure\": ").append(minPower.getInletPressure()).append(",\n");
        sb.append("    \"outletPressure\": ").append(minPower.getOutletPressure()).append(",\n");
        sb.append("    \"totalPower\": ").append(minPower.getTotalPower()).append("\n");
        sb.append("  }\n");
      } else {
        sb.append("  \"minimumPowerPoint\": null\n");
      }

      sb.append("}");
      return sb.toString();
    }

    // Getters and setters
    /** @return the tableName */
    public String getTableName() {
      return tableName;
    }

    /** @param tableName the tableName to set */
    public void setTableName(String tableName) {
      this.tableName = tableName;
    }

    /** @return the flowRates */
    public double[] getFlowRates() {
      return flowRates.clone();
    }

    /** @return the inletPressures */
    public double[] getInletPressures() {
      return inletPressures.clone();
    }

    /** @return the compressorNames */
    public List<String> getCompressorNames() {
      return new ArrayList<String>(compressorNames);
    }

    /** @return the flowRateUnit */
    public String getFlowRateUnit() {
      return flowRateUnit;
    }

    /** @param flowRateUnit the flowRateUnit to set */
    public void setFlowRateUnit(String flowRateUnit) {
      this.flowRateUnit = flowRateUnit;
    }

    /** @return the pressureUnit */
    public String getPressureUnit() {
      return pressureUnit;
    }

    /** @param pressureUnit the pressureUnit to set */
    public void setPressureUnit(String pressureUnit) {
      this.pressureUnit = pressureUnit;
    }
  }

  /**
   * Represents a 2D capacity table showing maximum flow rates at different pressure boundary
   * conditions.
   *
   * <p>
   * This table maps inlet/outlet pressure combinations to maximum achievable flow rates while
   * respecting equipment constraints. It is the inverse of the lift curve representation - instead
   * of "flow vs outlet pressure at fixed inlet pressure", it shows "max flow for each pressure
   * combination".
   * </p>
   *
   * <p>
   * This format is particularly useful for reservoir simulators where pressures are known from
   * deliverability curves and the question is "what flow rate can the production system handle?"
   * </p>
   *
   * <h3>Table Structure</h3>
   * 
   * <pre>
   *               Pout=130   Pout=140   Pout=150   Pout=160
   * Pin=60         12000      10500       8000       5000
   * Pin=70         15000      13000      11000       8500
   * Pin=80         18000      16000      14000      12000
   * Pin=90         20000      18500      17000      15500
   * </pre>
   *
   * @author EquinorASA
   * @version 1.0
   */
  public static class ProcessCapacityTable implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1006L;

    private String tableName;
    private double[] inletPressures;
    private double[] outletPressures;
    private List<String> compressorNames;
    private ProcessOperatingPoint[][] operatingPoints;
    private String pressureUnit = "bara";
    private String flowRateUnit = "kg/hr";
    private double maxUtilization = 1.0;

    /**
     * Constructs a capacity table.
     *
     * @param tableName the table name
     * @param inletPressures array of inlet pressures (rows)
     * @param outletPressures array of outlet pressures (columns)
     * @param compressorNames list of compressor names in the process
     */
    public ProcessCapacityTable(String tableName, double[] inletPressures, double[] outletPressures,
        List<String> compressorNames) {
      this.tableName = tableName;
      this.inletPressures = inletPressures.clone();
      this.outletPressures = outletPressures.clone();
      this.compressorNames = new ArrayList<String>(compressorNames);
      this.operatingPoints =
          new ProcessOperatingPoint[inletPressures.length][outletPressures.length];
    }

    /**
     * Sets the operating point for a specific inlet/outlet pressure combination.
     *
     * @param inletIndex index into inlet pressures array
     * @param outletIndex index into outlet pressures array
     * @param point the operating point (or null if infeasible)
     */
    public void setOperatingPoint(int inletIndex, int outletIndex, ProcessOperatingPoint point) {
      operatingPoints[inletIndex][outletIndex] = point;
    }

    /**
     * Gets the operating point for a specific inlet/outlet pressure combination.
     *
     * @param inletIndex index into inlet pressures array
     * @param outletIndex index into outlet pressures array
     * @return the operating point, or null if infeasible
     */
    public ProcessOperatingPoint getOperatingPoint(int inletIndex, int outletIndex) {
      return operatingPoints[inletIndex][outletIndex];
    }

    /**
     * Extracts maximum flow rates as a 2D array.
     *
     * @return 2D array [inletPressures][outletPressures] of flow rates
     */
    public double[][] getFlowRateValues() {
      double[][] values = new double[inletPressures.length][outletPressures.length];
      for (int i = 0; i < inletPressures.length; i++) {
        for (int j = 0; j < outletPressures.length; j++) {
          if (operatingPoints[i][j] != null) {
            values[i][j] = operatingPoints[i][j].getFlowRate();
          } else {
            values[i][j] = Double.NaN;
          }
        }
      }
      return values;
    }

    /**
     * Extracts total power values as a 2D array.
     *
     * @return 2D array [inletPressures][outletPressures] of total power (kW)
     */
    public double[][] getTotalPowerValues() {
      double[][] values = new double[inletPressures.length][outletPressures.length];
      for (int i = 0; i < inletPressures.length; i++) {
        for (int j = 0; j < outletPressures.length; j++) {
          if (operatingPoints[i][j] != null) {
            values[i][j] = operatingPoints[i][j].getTotalPower();
          } else {
            values[i][j] = Double.NaN;
          }
        }
      }
      return values;
    }

    /**
     * Extracts maximum utilization values as a 2D array.
     *
     * @return 2D array [inletPressures][outletPressures] of max utilization
     */
    public double[][] getMaxUtilizationValues() {
      double[][] values = new double[inletPressures.length][outletPressures.length];
      for (int i = 0; i < inletPressures.length; i++) {
        for (int j = 0; j < outletPressures.length; j++) {
          if (operatingPoints[i][j] != null) {
            values[i][j] = operatingPoints[i][j].getMaxUtilization();
          } else {
            values[i][j] = Double.NaN;
          }
        }
      }
      return values;
    }

    /**
     * Finds the operating point with maximum flow rate.
     *
     * @return the operating point with highest flow rate, or null if empty
     */
    public ProcessOperatingPoint findMaxFlowRatePoint() {
      ProcessOperatingPoint maxPoint = null;
      double maxFlow = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < inletPressures.length; i++) {
        for (int j = 0; j < outletPressures.length; j++) {
          ProcessOperatingPoint point = operatingPoints[i][j];
          if (point != null && point.isFeasible() && point.getFlowRate() > maxFlow) {
            maxFlow = point.getFlowRate();
            maxPoint = point;
          }
        }
      }
      return maxPoint;
    }

    /**
     * Finds the operating point with minimum total power.
     *
     * @return the operating point with lowest total power, or null if empty
     */
    public ProcessOperatingPoint findMinimumPowerPoint() {
      ProcessOperatingPoint minPoint = null;
      double minPower = Double.POSITIVE_INFINITY;
      for (int i = 0; i < inletPressures.length; i++) {
        for (int j = 0; j < outletPressures.length; j++) {
          ProcessOperatingPoint point = operatingPoints[i][j];
          if (point != null && point.isFeasible() && point.getTotalPower() < minPower
              && point.getTotalPower() > 0) {
            minPower = point.getTotalPower();
            minPoint = point;
          }
        }
      }
      return minPoint;
    }

    /**
     * Gets number of feasible operating points.
     *
     * @return count of feasible points
     */
    public int countFeasiblePoints() {
      int count = 0;
      for (int i = 0; i < inletPressures.length; i++) {
        for (int j = 0; j < outletPressures.length; j++) {
          if (operatingPoints[i][j] != null && operatingPoints[i][j].isFeasible()) {
            count++;
          }
        }
      }
      return count;
    }

    /**
     * Generates a formatted string representation.
     *
     * @return formatted table string
     */
    public String toFormattedString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Process Capacity Table: ").append(tableName).append("\n");
      sb.append("Max Utilization: ").append(maxUtilization * 100).append("%\n");
      sb.append("Pressure Unit: ").append(pressureUnit).append("\n");
      sb.append("Flow Rate Unit: ").append(flowRateUnit).append("\n\n");

      // Header row with outlet pressures
      sb.append(String.format("%12s", "Pin\\Pout"));
      for (double pout : outletPressures) {
        sb.append(String.format("%12.1f", pout));
      }
      sb.append("\n");

      // Data rows
      for (int i = 0; i < inletPressures.length; i++) {
        sb.append(String.format("%12.1f", inletPressures[i]));
        for (int j = 0; j < outletPressures.length; j++) {
          if (operatingPoints[i][j] != null && operatingPoints[i][j].isFeasible()) {
            sb.append(String.format("%12.0f", operatingPoints[i][j].getFlowRate()));
          } else {
            sb.append(String.format("%12s", "-"));
          }
        }
        sb.append("\n");
      }

      // Summary
      ProcessOperatingPoint maxFlow = findMaxFlowRatePoint();
      if (maxFlow != null) {
        sb.append("\nMax Flow Rate: ").append(String.format("%.0f", maxFlow.getFlowRate()))
            .append(" ").append(flowRateUnit).append(" at Pin=")
            .append(String.format("%.1f", maxFlow.getInletPressure())).append(", Pout=")
            .append(String.format("%.1f", maxFlow.getOutletPressure())).append(" ")
            .append(pressureUnit);
      }

      return sb.toString();
    }

    /**
     * Generates Eclipse VFP table format output.
     *
     * <p>
     * This format is compatible with Eclipse 300 reservoir simulator lift tables. The output
     * generates VFPPROD tables that can be directly included in Eclipse DATA files.
     * </p>
     *
     * <p>
     * The generated table uses the following Eclipse conventions:
     * <ul>
     * <li>FLO = flow rate (in flowRateUnit)</li>
     * <li>THP = tubing head pressure (inlet pressure / wellhead pressure)</li>
     * <li>BHP = bottom hole pressure (outlet pressure in this context)</li>
     * </ul>
     * </p>
     *
     * @return Eclipse VFP format string ready for inclusion in Eclipse DATA file
     */
    public String toEclipseFormat() {
      StringBuilder sb = new StringBuilder();
      sb.append("-- =============================================================\n");
      sb.append("-- Process Capacity Table: ").append(tableName).append("\n");
      sb.append("-- Generated by NeqSim FlowRateOptimizer\n");
      sb.append("-- Generation Date: ").append(java.time.LocalDateTime.now()).append("\n");
      sb.append("-- Max Utilization Constraint: ").append(maxUtilization * 100).append("%\n");
      sb.append("-- Pressure Unit: ").append(pressureUnit).append("\n");
      sb.append("-- Flow Rate Unit: ").append(flowRateUnit).append("\n");
      sb.append("-- =============================================================\n\n");

      // Collect all unique feasible flow rates
      java.util.TreeSet<Double> flowRateSet = new java.util.TreeSet<Double>();
      for (int i = 0; i < inletPressures.length; i++) {
        for (int j = 0; j < outletPressures.length; j++) {
          if (operatingPoints[i][j] != null && operatingPoints[i][j].isFeasible()) {
            flowRateSet.add(operatingPoints[i][j].getFlowRate());
          }
        }
      }

      // Convert to array
      Double[] sortedFlows = flowRateSet.toArray(new Double[0]);

      // Generate VFPPROD table
      sb.append("VFPPROD\n");
      sb.append("-- Table number, datum depth, FLO type, WFR type, GFR type, THP type, ");
      sb.append("ALQ type, UNITS, TAB type\n");
      sb.append(String.format("   1   0.0   'LIQ'   'WCT'   'GOR'   'THP'   ''   '%s'   'BHP'  /\n",
          "METRIC".equalsIgnoreCase(pressureUnit) ? "METRIC" : "FIELD"));
      sb.append("\n");

      // Flow rate axis (FLO)
      sb.append("-- Flow rates (").append(flowRateUnit).append(")\n");
      for (Double flow : sortedFlows) {
        sb.append(String.format("   %.2f\n", flow));
      }
      sb.append("/\n\n");

      // THP axis (inlet pressures)
      sb.append("-- THP values (inlet pressures in ").append(pressureUnit).append(")\n");
      for (double pin : inletPressures) {
        sb.append(String.format("   %.2f\n", pin));
      }
      sb.append("/\n\n");

      // Default WCT (water cut) - single value for now
      sb.append("-- WCT values (water cut fraction)\n");
      sb.append("   0.0 /\n\n");

      // Default GOR
      sb.append("-- GOR values\n");
      sb.append("   0.0 /\n\n");

      // Default ALQ (artificial lift quantity)
      sb.append("-- ALQ values\n");
      sb.append("   0.0 /\n\n");

      // BHP values (outlet pressures) as 3D table
      sb.append("-- BHP values (outlet pressures in ").append(pressureUnit).append(")\n");
      sb.append("-- Format: BHP for each FLO at each THP (WCT=0, GOR=0, ALQ=0)\n");

      for (int thpIdx = 0; thpIdx < inletPressures.length; thpIdx++) {
        sb.append(String.format("-- THP = %.2f %s\n", inletPressures[thpIdx], pressureUnit));
        for (Double targetFlow : sortedFlows) {
          // Find closest matching operating point at this inlet pressure
          ProcessOperatingPoint bestMatch = null;
          double minDiff = Double.MAX_VALUE;
          for (int j = 0; j < outletPressures.length; j++) {
            ProcessOperatingPoint pt = operatingPoints[thpIdx][j];
            if (pt != null && pt.isFeasible()) {
              double diff = Math.abs(pt.getFlowRate() - targetFlow);
              if (diff < minDiff) {
                minDiff = diff;
                bestMatch = pt;
              }
            }
          }
          if (bestMatch != null && minDiff < targetFlow * 0.1) {
            sb.append(String.format("   %.2f\n", bestMatch.getOutletPressure()));
          } else {
            sb.append("   1* \n"); // Eclipse default/undefined marker
          }
        }
        sb.append("/\n");
      }

      sb.append("\n");
      return sb.toString();
    }

    /**
     * Generates a CSV export of the capacity table for spreadsheet analysis.
     *
     * @return CSV formatted string
     */
    public String toCsv() {
      StringBuilder sb = new StringBuilder();

      // Header
      sb.append("InletPressure_").append(pressureUnit);
      sb.append(",OutletPressure_").append(pressureUnit);
      sb.append(",FlowRate_").append(flowRateUnit);
      sb.append(",TotalPower_kW");
      sb.append(",MaxUtilization");
      sb.append(",Feasible\n");

      // Data rows
      for (int i = 0; i < inletPressures.length; i++) {
        for (int j = 0; j < outletPressures.length; j++) {
          ProcessOperatingPoint pt = operatingPoints[i][j];
          sb.append(String.format("%.2f,%.2f,", inletPressures[i], outletPressures[j]));
          if (pt != null) {
            sb.append(String.format("%.2f,%.2f,%.4f,%s\n", pt.getFlowRate(), pt.getTotalPower(),
                pt.getMaxUtilization(), pt.isFeasible() ? "true" : "false"));
          } else {
            sb.append(",,,,false\n");
          }
        }
      }

      return sb.toString();
    }

    /**
     * Generates JSON representation of the capacity table.
     *
     * @return JSON string
     */
    public String toJson() {
      StringBuilder sb = new StringBuilder();
      sb.append("{\n");
      sb.append("  \"tableName\": \"").append(tableName).append("\",\n");
      sb.append("  \"pressureUnit\": \"").append(pressureUnit).append("\",\n");
      sb.append("  \"flowRateUnit\": \"").append(flowRateUnit).append("\",\n");
      sb.append("  \"maxUtilization\": ").append(maxUtilization).append(",\n");

      // Inlet pressures
      sb.append("  \"inletPressures\": [");
      for (int i = 0; i < inletPressures.length; i++) {
        sb.append(inletPressures[i]);
        if (i < inletPressures.length - 1) {
          sb.append(", ");
        }
      }
      sb.append("],\n");

      // Outlet pressures
      sb.append("  \"outletPressures\": [");
      for (int i = 0; i < outletPressures.length; i++) {
        sb.append(outletPressures[i]);
        if (i < outletPressures.length - 1) {
          sb.append(", ");
        }
      }
      sb.append("],\n");

      // Compressor names
      sb.append("  \"compressorNames\": [");
      for (int i = 0; i < compressorNames.size(); i++) {
        sb.append("\"").append(compressorNames.get(i)).append("\"");
        if (i < compressorNames.size() - 1) {
          sb.append(", ");
        }
      }
      sb.append("],\n");

      // Flow rate values
      sb.append("  \"flowRates\": [\n");
      double[][] flows = getFlowRateValues();
      for (int i = 0; i < flows.length; i++) {
        sb.append("    [");
        for (int j = 0; j < flows[i].length; j++) {
          if (Double.isNaN(flows[i][j])) {
            sb.append("null");
          } else {
            sb.append(String.format("%.1f", flows[i][j]));
          }
          if (j < flows[i].length - 1) {
            sb.append(", ");
          }
        }
        sb.append("]");
        if (i < flows.length - 1) {
          sb.append(",");
        }
        sb.append("\n");
      }
      sb.append("  ],\n");

      // Total power values
      sb.append("  \"totalPower\": [\n");
      double[][] power = getTotalPowerValues();
      for (int i = 0; i < power.length; i++) {
        sb.append("    [");
        for (int j = 0; j < power[i].length; j++) {
          if (Double.isNaN(power[i][j])) {
            sb.append("null");
          } else {
            sb.append(String.format("%.2f", power[i][j]));
          }
          if (j < power[i].length - 1) {
            sb.append(", ");
          }
        }
        sb.append("]");
        if (i < power.length - 1) {
          sb.append(",");
        }
        sb.append("\n");
      }
      sb.append("  ],\n");

      // Max utilization values
      sb.append("  \"maxUtilizationValues\": [\n");
      double[][] util = getMaxUtilizationValues();
      for (int i = 0; i < util.length; i++) {
        sb.append("    [");
        for (int j = 0; j < util[i].length; j++) {
          if (Double.isNaN(util[i][j])) {
            sb.append("null");
          } else {
            sb.append(String.format("%.3f", util[i][j]));
          }
          if (j < util[i].length - 1) {
            sb.append(", ");
          }
        }
        sb.append("]");
        if (i < util.length - 1) {
          sb.append(",");
        }
        sb.append("\n");
      }
      sb.append("  ],\n");

      // Summary statistics
      sb.append("  \"feasiblePoints\": ").append(countFeasiblePoints()).append(",\n");
      sb.append("  \"totalPoints\": ").append(inletPressures.length * outletPressures.length)
          .append(",\n");

      ProcessOperatingPoint maxFlow = findMaxFlowRatePoint();
      if (maxFlow != null) {
        sb.append("  \"maxFlowRatePoint\": {\n");
        sb.append("    \"flowRate\": ").append(maxFlow.getFlowRate()).append(",\n");
        sb.append("    \"inletPressure\": ").append(maxFlow.getInletPressure()).append(",\n");
        sb.append("    \"outletPressure\": ").append(maxFlow.getOutletPressure()).append(",\n");
        sb.append("    \"totalPower\": ").append(maxFlow.getTotalPower()).append("\n");
        sb.append("  },\n");
      } else {
        sb.append("  \"maxFlowRatePoint\": null,\n");
      }

      ProcessOperatingPoint minPower = findMinimumPowerPoint();
      if (minPower != null) {
        sb.append("  \"minimumPowerPoint\": {\n");
        sb.append("    \"flowRate\": ").append(minPower.getFlowRate()).append(",\n");
        sb.append("    \"inletPressure\": ").append(minPower.getInletPressure()).append(",\n");
        sb.append("    \"outletPressure\": ").append(minPower.getOutletPressure()).append(",\n");
        sb.append("    \"totalPower\": ").append(minPower.getTotalPower()).append("\n");
        sb.append("  }\n");
      } else {
        sb.append("  \"minimumPowerPoint\": null\n");
      }

      sb.append("}");
      return sb.toString();
    }

    // Getters and setters

    /** @return the tableName */
    public String getTableName() {
      return tableName;
    }

    /** @param tableName the tableName to set */
    public void setTableName(String tableName) {
      this.tableName = tableName;
    }

    /** @return the inletPressures */
    public double[] getInletPressures() {
      return inletPressures.clone();
    }

    /** @return the outletPressures */
    public double[] getOutletPressures() {
      return outletPressures.clone();
    }

    /** @return the compressorNames */
    public List<String> getCompressorNames() {
      return new ArrayList<String>(compressorNames);
    }

    /** @return the pressureUnit */
    public String getPressureUnit() {
      return pressureUnit;
    }

    /** @param pressureUnit the pressureUnit to set */
    public void setPressureUnit(String pressureUnit) {
      this.pressureUnit = pressureUnit;
    }

    /** @return the flowRateUnit */
    public String getFlowRateUnit() {
      return flowRateUnit;
    }

    /** @param flowRateUnit the flowRateUnit to set */
    public void setFlowRateUnit(String flowRateUnit) {
      this.flowRateUnit = flowRateUnit;
    }

    /** @return the maxUtilization */
    public double getMaxUtilization() {
      return maxUtilization;
    }

    /** @param maxUtilization the maxUtilization to set */
    public void setMaxUtilization(double maxUtilization) {
      this.maxUtilization = maxUtilization;
    }
  }
}
