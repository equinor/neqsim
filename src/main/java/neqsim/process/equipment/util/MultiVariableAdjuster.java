package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;
import neqsim.process.equipment.mixer.MixerInterface;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Multi-variable adjuster for simultaneous convergence of coupled process specifications.
 *
 * <p>
 * This class solves the multi-variable analog of the single-variable {@link Adjuster}: given N
 * manipulated (adjusted) variables and N target specifications, it simultaneously drives all
 * targets to their desired values using Broyden's quasi-Newton method.
 * </p>
 *
 * <p>
 * The underlying algorithm uses the inverse Jacobian approximation from {@link BroydenAccelerator},
 * which avoids expensive numerical perturbation of all variables and provides superlinear
 * convergence for well-conditioned problems.
 * </p>
 *
 * <h2>Problem Statement</h2>
 * <p>
 * Given N adjusted variables x_1, ..., x_N and N target specifications y_1, ..., y_N with target
 * values t_1, ..., t_N, find the values of x that satisfy:
 * </p>
 *
 * <pre>
 * f_i(x) = y_i(x) - t_i = 0   for i = 1, ..., N
 * </pre>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li>Simultaneous N-variable convergence (vs N independent single-variable loops)</li>
 * <li>Broyden quasi-Newton with Sherman-Morrison inverse Jacobian updates</li>
 * <li>Variable bounds enforcement with clamping</li>
 * <li>Configurable convergence tolerance and maximum iterations</li>
 * <li>Support for pressure, temperature, flow rate, and molar flow adjustments</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * MultiVariableAdjuster adj = new MultiVariableAdjuster("MV-Adj");
 *
 * // Add adjusted variables (manipulated)
 * adj.addAdjustedVariable(compressor, "pressure", "bara");
 * adj.addAdjustedVariable(heater, "temperature", "C");
 *
 * // Add target specifications (in same order)
 * adj.addTargetSpecification(separator, "pressure", 85.0, "bara");
 * adj.addTargetSpecification(cooler, "temperature", 30.0, "C");
 *
 * // Optional: set bounds
 * adj.setVariableBounds(0, 50.0, 200.0); // pressure
 * adj.setVariableBounds(1, 10.0, 100.0); // temperature
 *
 * // Add to process system and run
 * process.add(adj);
 * process.run();
 * }</pre>
 *
 * @author NeqSim
 * @version 1.0
 * @see Adjuster
 * @see BroydenAccelerator
 */
public class MultiVariableAdjuster extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(MultiVariableAdjuster.class);

  /** List of adjusted (manipulated) variable definitions. */
  private List<AdjustedVariable> adjustedVariables = new ArrayList<AdjustedVariable>();

  /** List of target specification definitions. */
  private List<TargetSpecification> targetSpecifications = new ArrayList<TargetSpecification>();

  /** Broyden accelerator for quasi-Newton convergence. */
  private transient BroydenAccelerator broyden;

  /** Maximum number of outer iterations. */
  private int maxIterations = 50;

  /** Convergence tolerance on the residual norm. */
  private double tolerance = 1e-4;

  /** Current iteration count. */
  private int iterations = 0;

  /** Current maximum residual. */
  private double maxResidual = 1e10;

  /** Whether the adjuster has converged. */
  private boolean converged = false;

  /**
   * Definition of a single adjusted (manipulated) variable.
   *
   * @author NeqSim
   * @version 1.0
   */
  private static class AdjustedVariable implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;
    /** Equipment whose variable is manipulated. */
    ProcessEquipmentInterface equipment;
    /** Variable name (pressure, temperature, flow, etc.). */
    String variable;
    /** Unit string. */
    String unit;
    /** Lower bound. */
    double lowerBound = -1e10;
    /** Upper bound. */
    double upperBound = 1e10;
  }

  /**
   * Definition of a single target specification.
   *
   * @author NeqSim
   * @version 1.0
   */
  private static class TargetSpecification implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;
    /** Equipment whose variable is observed. */
    ProcessEquipmentInterface equipment;
    /** Variable name (pressure, temperature, flow, etc.). */
    String variable;
    /** Desired target value. */
    double targetValue;
    /** Unit string. */
    String unit;
    /** Phase name (optional). */
    String phase = "";
    /** Component name (optional). */
    String component = "";
  }

  /**
   * Default constructor for MultiVariableAdjuster.
   */
  public MultiVariableAdjuster() {
    super("MultiVariableAdjuster");
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public MultiVariableAdjuster(String name) {
    super(name);
  }

  /**
   * Add an adjusted (manipulated) variable.
   *
   * @param equipment the equipment whose variable is manipulated
   * @param variable the variable name (pressure, temperature, flow)
   * @param unit the unit string (bara, C, kg/hr, etc.)
   */
  public void addAdjustedVariable(ProcessEquipmentInterface equipment, String variable,
      String unit) {
    AdjustedVariable av = new AdjustedVariable();
    av.equipment = equipment;
    av.variable = variable;
    av.unit = unit;
    adjustedVariables.add(av);
  }

  /**
   * Add a target specification.
   *
   * @param equipment the equipment whose variable is observed
   * @param variable the variable name (pressure, temperature, flow, volume)
   * @param targetValue the desired target value
   * @param unit the unit string
   */
  public void addTargetSpecification(ProcessEquipmentInterface equipment, String variable,
      double targetValue, String unit) {
    TargetSpecification ts = new TargetSpecification();
    ts.equipment = equipment;
    ts.variable = variable;
    ts.targetValue = targetValue;
    ts.unit = unit;
    targetSpecifications.add(ts);
  }

  /**
   * Add a target specification with phase and component.
   *
   * @param equipment the equipment whose variable is observed
   * @param variable the variable name
   * @param targetValue the desired target value
   * @param unit the unit string
   * @param phase the phase name (gas, oil, aqueous)
   * @param component the component name
   */
  public void addTargetSpecification(ProcessEquipmentInterface equipment, String variable,
      double targetValue, String unit, String phase, String component) {
    TargetSpecification ts = new TargetSpecification();
    ts.equipment = equipment;
    ts.variable = variable;
    ts.targetValue = targetValue;
    ts.unit = unit;
    ts.phase = phase;
    ts.component = component;
    targetSpecifications.add(ts);
  }

  /**
   * Set bounds on an adjusted variable.
   *
   * @param index zero-based index of the adjusted variable
   * @param lower lower bound
   * @param upper upper bound
   */
  public void setVariableBounds(int index, double lower, double upper) {
    if (index < 0 || index >= adjustedVariables.size()) {
      throw new IndexOutOfBoundsException(
          "Variable index " + index + " out of range [0, " + adjustedVariables.size() + ")");
    }
    AdjustedVariable av = adjustedVariables.get(index);
    av.lowerBound = lower;
    av.upperBound = upper;
  }

  /**
   * Set maximum number of iterations.
   *
   * @param maxIter maximum iterations
   */
  public void setMaxIterations(int maxIter) {
    this.maxIterations = maxIter;
  }

  /**
   * Set convergence tolerance.
   *
   * @param tol convergence tolerance on max residual
   */
  public void setTolerance(double tol) {
    this.tolerance = tol;
  }

  /**
   * Get the number of iterations performed in the last run.
   *
   * @return iteration count
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Get the maximum residual from the last run.
   *
   * @return maximum residual value
   */
  public double getMaxResidual() {
    return maxResidual;
  }

  /**
   * Check if the adjuster converged in the last run.
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Get the number of adjusted variables.
   *
   * @return number of adjusted variables
   */
  public int getNumberOfVariables() {
    return adjustedVariables.size();
  }

  /**
   * Run one step of the multi-variable adjustment.
   *
   * <p>
   * Unlike an internal iteration loop, this method performs a single Broyden step per call. The
   * {@link neqsim.process.processmodel.ProcessSystem} provides the outer iteration loop: it runs
   * all equipment, calls this method, checks {@link #solved()}, and re-runs the process if needed.
   * This ensures downstream equipment is re-evaluated between adjustment steps.
   * </p>
   *
   * @param id calculation identifier for tracking
   */
  @Override
  public void run(UUID id) {
    int n = adjustedVariables.size();
    if (n == 0) {
      logger.warn("MultiVariableAdjuster {}: no adjusted variables defined", getName());
      setCalculationIdentifier(id);
      return;
    }
    if (n != targetSpecifications.size()) {
      throw new RuntimeException("MultiVariableAdjuster " + getName()
          + ": number of adjusted variables (" + n
          + ") must equal number of target specifications (" + targetSpecifications.size() + ")");
    }

    // Initialize Broyden accelerator on first call
    if (broyden == null) {
      broyden = new BroydenAccelerator(n);
      broyden.setDelayIterations(1);
      broyden.setRelaxationFactor(0.8);
    } else if (broyden.getDimension() != n) {
      broyden.initialize(n);
    }

    // Read current adjusted variable values
    double[] x = new double[n];
    for (int i = 0; i < n; i++) {
      x[i] = readAdjustedValue(i);
    }

    // Compute residuals from current process state
    double[] residuals = computeResiduals();
    maxResidual = maxAbsValue(residuals);

    if (maxResidual <= tolerance) {
      converged = true;
      setCalculationIdentifier(id);
      return;
    }

    // Perform one adjustment step
    iterations++;

    double[] xNew;

    if (iterations <= 2) {
      // First two iterations: use damped perturbation (like single-variable Adjuster)
      // This avoids overshoot from the initial unit-Jacobian assumption and provides
      // the Broyden accelerator with good finite-difference information.
      double dampingFactor = 0.1;
      xNew = new double[n];
      for (int i = 0; i < n; i++) {
        xNew[i] = x[i] + dampingFactor * residuals[i];
      }
    } else {
      // Subsequent iterations: use Broyden quasi-Newton acceleration
      // Build fixed-point form: g(x) = x + residuals
      double[] gx = new double[n];
      for (int i = 0; i < n; i++) {
        gx[i] = x[i] + residuals[i];
      }
      xNew = broyden.accelerate(x, gx);
    }

    // Apply bounds clamping
    for (int i = 0; i < n; i++) {
      AdjustedVariable av = adjustedVariables.get(i);
      xNew[i] = Math.max(av.lowerBound, Math.min(av.upperBound, xNew[i]));
    }

    // Write new values to adjusted equipment streams
    for (int i = 0; i < n; i++) {
      writeAdjustedValue(i, xNew[i]);
    }

    if (logger.isDebugEnabled()) {
      logger.debug("MultiVariableAdjuster {} step {}: maxResidual = {}", getName(), iterations,
          maxResidual);
    }

    // converged will be checked on next call after process re-runs
    converged = false;
    setCalculationIdentifier(id);
  }

  /**
   * Compute the residual vector (target - current) for all specifications.
   *
   * @return array of residuals
   */
  private double[] computeResiduals() {
    int n = targetSpecifications.size();
    double[] res = new double[n];
    for (int i = 0; i < n; i++) {
      TargetSpecification ts = targetSpecifications.get(i);
      double current = readTargetValue(ts);
      res[i] = ts.targetValue - current;
    }
    return res;
  }

  /**
   * Read the current value of an adjusted variable from its equipment.
   *
   * @param index index of the adjusted variable
   * @return current value in the specified unit
   */
  private double readAdjustedValue(int index) {
    AdjustedVariable av = adjustedVariables.get(index);
    StreamInterface stream = getStreamFromEquipment(av.equipment);
    if (stream == null) {
      logger.error("Cannot get stream from adjusted equipment: {}", av.equipment.getName());
      return 0.0;
    }

    if ("pressure".equals(av.variable)) {
      return stream.getPressure(av.unit);
    } else if ("temperature".equals(av.variable)) {
      return stream.getTemperature(av.unit);
    } else if ("flow".equals(av.variable) || "mass flow".equals(av.variable)) {
      return stream.getFlowRate(av.unit);
    } else {
      return stream.getThermoSystem().getNumberOfMoles();
    }
  }

  /**
   * Write a new value to an adjusted variable's equipment.
   *
   * @param index index of the adjusted variable
   * @param value new value to set
   */
  private void writeAdjustedValue(int index, double value) {
    AdjustedVariable av = adjustedVariables.get(index);
    StreamInterface stream = getStreamFromEquipment(av.equipment);
    if (stream == null) {
      logger.error("Cannot get stream from adjusted equipment: {}", av.equipment.getName());
      return;
    }

    if ("pressure".equals(av.variable)) {
      stream.setPressure(value, av.unit);
    } else if ("temperature".equals(av.variable)) {
      stream.setTemperature(value, av.unit);
    } else if ("flow".equals(av.variable) || "mass flow".equals(av.variable)) {
      stream.setFlowRate(value, av.unit);
    } else {
      stream.getThermoSystem().setTotalNumberOfMoles(value);
    }
  }

  /**
   * Read the current value of a target specification from its equipment.
   *
   * @param ts target specification
   * @return current value in the specified unit
   */
  private double readTargetValue(TargetSpecification ts) {
    StreamInterface stream = getStreamFromEquipment(ts.equipment);
    if (stream == null) {
      logger.error("Cannot get stream from target equipment: {}", ts.equipment.getName());
      return 0.0;
    }

    if ("pressure".equals(ts.variable)) {
      return stream.getThermoSystem().getPressure(ts.unit);
    } else if ("temperature".equals(ts.variable)) {
      return stream.getTemperature(ts.unit);
    } else if ("flow".equals(ts.variable) || "mass flow".equals(ts.variable)
        || "gasVolumeFlow".equals(ts.variable)) {
      return stream.getThermoSystem().getFlowRate(ts.unit);
    } else if ("volume".equals(ts.variable)) {
      return stream.getThermoSystem().getVolume(ts.unit);
    } else if ("mass fraction".equals(ts.variable) && !ts.phase.isEmpty()
        && !ts.component.isEmpty()) {
      return stream.getThermoSystem().getPhase(ts.phase).getWtFrac(ts.component);
    } else {
      return stream.getThermoSystem().getVolume(ts.unit);
    }
  }

  /**
   * Get a stream interface from equipment, handling various equipment types.
   *
   * @param equipment the equipment to get the stream from
   * @return stream interface, or null if not available
   */
  private StreamInterface getStreamFromEquipment(ProcessEquipmentInterface equipment) {
    if (equipment instanceof StreamInterface) {
      return (StreamInterface) equipment;
    } else if (equipment instanceof TwoPortInterface) {
      return ((TwoPortInterface) equipment).getOutletStream();
    } else if (equipment instanceof MixerInterface) {
      return ((MixerInterface) equipment).getOutletStream();
    }
    return null;
  }

  /**
   * Compute the maximum absolute value in an array.
   *
   * @param arr the array
   * @return maximum absolute value
   */
  private double maxAbsValue(double[] arr) {
    double maxVal = 0.0;
    for (double v : arr) {
      double abs = Math.abs(v);
      if (abs > maxVal) {
        maxVal = abs;
      }
    }
    return maxVal;
  }

  /**
   * Check if the adjuster is solved (converged).
   *
   * @return true if the maximum residual is within tolerance
   */
  @Override
  public boolean solved() {
    return converged || maxResidual <= tolerance;
  }
}
