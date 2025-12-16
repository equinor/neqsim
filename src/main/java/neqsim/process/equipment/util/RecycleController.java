package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import neqsim.process.util.uncertainty.SensitivityMatrix;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * RecycleController class for managing multiple recycle streams in process simulations.
 *
 * <p>
 * This class coordinates convergence of multiple recycle loops, supporting:
 * <ul>
 * <li>Priority-based sequencing of nested recycles</li>
 * <li>Individual acceleration methods per recycle (Wegstein, Broyden)</li>
 * <li>Coordinated multi-recycle Broyden acceleration for coupled systems</li>
 * </ul>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class RecycleController implements java.io.Serializable {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(RecycleController.class);
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  ArrayList<Recycle> recycleArray = new ArrayList<Recycle>();
  ArrayList<Integer> priorityArray = new ArrayList<Integer>();
  private int currentPriorityLevel = 100;
  private int minimumPriorityLevel = 100;
  private int maximumPriorityLevel = 100;

  /** Coordinated Broyden accelerator for multi-recycle systems. */
  private transient BroydenAccelerator coordinatedAccelerator = null;

  /** Whether to use coordinated acceleration across all recycles at current priority. */
  private boolean useCoordinatedAcceleration = false;

  /**
   * Constructor for RecycleController.
   */
  public RecycleController() {}

  /**
   * Initializes the controller for a new convergence cycle.
   */
  public void init() {
    for (Recycle recyc : recycleArray) {
      recyc.resetIterations();
      if (recyc.getPriority() < minimumPriorityLevel) {
        minimumPriorityLevel = recyc.getPriority();
      }
      if (recyc.getPriority() > maximumPriorityLevel) {
        maximumPriorityLevel = recyc.getPriority();
      }
    }

    // Reset coordinated accelerator
    if (coordinatedAccelerator != null) {
      coordinatedAccelerator.reset();
    }

    currentPriorityLevel = minimumPriorityLevel;
  }

  /**
   * <p>
   * resetPriorityLevel.
   * </p>
   */
  public void resetPriorityLevel() {
    currentPriorityLevel = minimumPriorityLevel;
  }

  /**
   * <p>
   * addRecycle.
   * </p>
   *
   * @param recycle a {@link neqsim.process.equipment.util.Recycle} object
   */
  public void addRecycle(Recycle recycle) {
    recycleArray.add(recycle);
    priorityArray.add(recycle.getPriority());
  }

  /**
   * <p>
   * doSolveRecycle.
   * </p>
   *
   * @param recycle a {@link neqsim.process.equipment.util.Recycle} object
   * @return a boolean
   */
  public boolean doSolveRecycle(Recycle recycle) {
    if (recycle.getPriority() == getCurrentPriorityLevel()) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * <p>
   * isHighestPriority.
   * </p>
   *
   * @param recycle a {@link neqsim.process.equipment.util.Recycle} object
   * @return a boolean
   */
  public boolean isHighestPriority(Recycle recycle) {
    if (recycle.getPriority() == maximumPriorityLevel) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * <p>
   * solvedCurrentPriorityLevel.
   * </p>
   *
   * @return a boolean
   */
  public boolean solvedCurrentPriorityLevel() {
    for (Recycle recyc : recycleArray) {
      if (recyc.getPriority() == currentPriorityLevel) {
        if (!recyc.solved()) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * <p>
   * nextPriorityLevel.
   * </p>
   */
  public void nextPriorityLevel() {
    currentPriorityLevel = maximumPriorityLevel;
  }

  /**
   * <p>
   * hasLoverPriorityLevel.
   * </p>
   *
   * @return a boolean
   */
  public boolean hasLoverPriorityLevel() {
    if (currentPriorityLevel > minimumPriorityLevel) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * <p>
   * hasHigherPriorityLevel.
   * </p>
   *
   * @return a boolean
   */
  public boolean hasHigherPriorityLevel() {
    if (currentPriorityLevel < maximumPriorityLevel) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * <p>
   * solvedAll.
   * </p>
   *
   * @return a boolean
   */
  public boolean solvedAll() {
    for (Recycle recyc : recycleArray) {
      logger.info(recyc.getName() + " solved " + recyc.solved());
      if (!recyc.solved()) {
        return false;
      }
    }
    return true;
  }

  /**
   * <p>
   * clear.
   * </p>
   */
  public void clear() {
    recycleArray.clear();
    priorityArray.clear();
  }

  /**
   * <p>
   * Getter for the field <code>currentPriorityLevel</code>.
   * </p>
   *
   * @return a int
   */
  public int getCurrentPriorityLevel() {
    return currentPriorityLevel;
  }

  /**
   * <p>
   * Setter for the field <code>currentPriorityLevel</code>.
   * </p>
   *
   * @param currentPriorityLevel a int
   */
  public void setCurrentPriorityLevel(int currentPriorityLevel) {
    this.currentPriorityLevel = currentPriorityLevel;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(currentPriorityLevel, maximumPriorityLevel, minimumPriorityLevel,
        priorityArray, recycleArray);
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    RecycleController other = (RecycleController) obj;
    return currentPriorityLevel == other.currentPriorityLevel
        && maximumPriorityLevel == other.maximumPriorityLevel
        && minimumPriorityLevel == other.minimumPriorityLevel
        && Objects.equals(priorityArray, other.priorityArray)
        && Objects.equals(recycleArray, other.recycleArray);
  }

  // ============ ACCELERATION METHOD SUPPORT ============

  /**
   * Sets the acceleration method for all recycles managed by this controller.
   *
   * @param method the acceleration method to use
   */
  public void setAccelerationMethod(AccelerationMethod method) {
    for (Recycle recycle : recycleArray) {
      recycle.setAccelerationMethod(method);
    }
  }

  /**
   * Sets the acceleration method for recycles at the specified priority level.
   *
   * @param method the acceleration method to use
   * @param priority the priority level to apply to
   */
  public void setAccelerationMethod(AccelerationMethod method, int priority) {
    for (Recycle recycle : recycleArray) {
      if (recycle.getPriority() == priority) {
        recycle.setAccelerationMethod(method);
      }
    }
  }

  /**
   * Checks if coordinated multi-recycle acceleration is enabled.
   *
   * @return true if coordinated acceleration is enabled
   */
  public boolean isUseCoordinatedAcceleration() {
    return useCoordinatedAcceleration;
  }

  /**
   * Enables or disables coordinated Broyden acceleration across all recycles at the same priority.
   *
   * <p>
   * When enabled, all recycles at the current priority level will share a single Broyden
   * accelerator, treating the combined tear stream values as a single multi-variable system. This
   * can improve convergence for tightly coupled recycle loops.
   *
   * <p>
   * When disabled (default), each recycle uses its own acceleration method independently.
   *
   * @param useCoordinatedAcceleration true to enable coordinated acceleration
   */
  public void setUseCoordinatedAcceleration(boolean useCoordinatedAcceleration) {
    this.useCoordinatedAcceleration = useCoordinatedAcceleration;
    if (useCoordinatedAcceleration && coordinatedAccelerator == null) {
      coordinatedAccelerator = new BroydenAccelerator();
    }
  }

  /**
   * Gets the coordinated Broyden accelerator.
   *
   * @return the coordinated accelerator, or null if not using coordinated acceleration
   */
  public BroydenAccelerator getCoordinatedAccelerator() {
    return coordinatedAccelerator;
  }

  /**
   * Gets all recycles at the current priority level.
   *
   * @return list of recycles at current priority
   */
  public List<Recycle> getRecyclesAtCurrentPriority() {
    List<Recycle> result = new ArrayList<>();
    for (Recycle recycle : recycleArray) {
      if (recycle.getPriority() == currentPriorityLevel) {
        result.add(recycle);
      }
    }
    return result;
  }

  /**
   * Gets the number of recycles managed by this controller.
   *
   * @return number of recycles
   */
  public int getRecycleCount() {
    return recycleArray.size();
  }

  /**
   * Gets all recycles managed by this controller.
   *
   * @return list of all recycles
   */
  public List<Recycle> getRecycles() {
    return new ArrayList<>(recycleArray);
  }

  // ============ SIMULTANEOUS MODULAR SOLVING ============

  /**
   * Performs simultaneous modular solving for all recycles at the current priority level.
   *
   * <p>
   * This method collects all tear stream variables from recycles at the current priority level into
   * a single vector and applies global Broyden acceleration. This approach can significantly
   * improve convergence for tightly coupled recycle loops compared to solving each recycle
   * independently.
   *
   * <p>
   * The algorithm:
   * <ol>
   * <li>Extracts current tear stream values from all recycles at current priority</li>
   * <li>Runs all equipment between tear streams to get updated outputs</li>
   * <li>Applies global Broyden acceleration to the combined variable vector</li>
   * <li>Updates all tear streams with accelerated values</li>
   * </ol>
   *
   * @return true if all recycles at current priority are converged
   */
  public boolean runSimultaneousAcceleration() {
    List<Recycle> currentRecycles = getRecyclesAtCurrentPriority();
    if (currentRecycles.isEmpty()) {
      return true;
    }

    // Calculate total dimension across all recycles
    int totalDimension = 0;
    int[] dimensions = new int[currentRecycles.size()];
    for (int i = 0; i < currentRecycles.size(); i++) {
      Recycle recycle = currentRecycles.get(i);
      if (recycle.getOutletStream() != null
          && recycle.getOutletStream().getThermoSystem() != null) {
        int numComponents =
            recycle.getOutletStream().getThermoSystem().getPhase(0).getNumberOfComponents();
        dimensions[i] = 3 + numComponents; // T, P, flow, compositions
        totalDimension += dimensions[i];
      }
    }

    if (totalDimension == 0) {
      return true;
    }

    // Initialize coordinated accelerator if needed
    if (coordinatedAccelerator == null) {
      coordinatedAccelerator = new BroydenAccelerator(totalDimension);
    }

    // Extract combined input vector (current tear stream values)
    double[] combinedInput = new double[totalDimension];
    int offset = 0;
    for (int i = 0; i < currentRecycles.size(); i++) {
      Recycle recycle = currentRecycles.get(i);
      double[] values = extractRecycleInputValues(recycle);
      if (values != null) {
        System.arraycopy(values, 0, combinedInput, offset, values.length);
        offset += values.length;
      }
    }

    // Extract combined output vector (after running equipment)
    double[] combinedOutput = new double[totalDimension];
    offset = 0;
    for (int i = 0; i < currentRecycles.size(); i++) {
      Recycle recycle = currentRecycles.get(i);
      double[] values = extractRecycleOutputValues(recycle);
      if (values != null) {
        System.arraycopy(values, 0, combinedOutput, offset, values.length);
        offset += values.length;
      }
    }

    // Apply global Broyden acceleration
    double[] accelerated = coordinatedAccelerator.accelerate(combinedInput, combinedOutput);

    // Apply accelerated values back to recycles
    offset = 0;
    for (int i = 0; i < currentRecycles.size(); i++) {
      Recycle recycle = currentRecycles.get(i);
      double[] values = new double[dimensions[i]];
      System.arraycopy(accelerated, offset, values, 0, dimensions[i]);
      applyAcceleratedValuesToRecycle(recycle, values);
      offset += dimensions[i];
    }

    // Check convergence
    return solvedCurrentPriorityLevel();
  }

  /**
   * Extracts input values from a recycle's last iteration stream.
   *
   * @param recycle the recycle to extract from
   * @return array of [temperature, pressure, flow, mole_fractions...]
   */
  private double[] extractRecycleInputValues(Recycle recycle) {
    if (recycle.getOutletStream() == null || recycle.getOutletStream().getThermoSystem() == null) {
      return null;
    }

    neqsim.thermo.system.SystemInterface fluid = recycle.getOutletStream().getThermoSystem();
    int numComponents = fluid.getPhase(0).getNumberOfComponents();
    double[] values = new double[3 + numComponents];

    values[0] = fluid.getTemperature();
    values[1] = fluid.getPressure();
    values[2] = fluid.getFlowRate("mole/sec");

    for (int i = 0; i < numComponents; i++) {
      values[3 + i] = fluid.getPhase(0).getComponent(i).getx();
    }
    return values;
  }

  /**
   * Extracts output values from a recycle's mixed stream (after mixing inputs).
   *
   * @param recycle the recycle to extract from
   * @return array of [temperature, pressure, flow, mole_fractions...]
   */
  private double[] extractRecycleOutputValues(Recycle recycle) {
    if (recycle.getThermoSystem() == null) {
      return null;
    }

    neqsim.thermo.system.SystemInterface fluid = recycle.getThermoSystem();
    int numComponents = fluid.getPhase(0).getNumberOfComponents();
    double[] values = new double[3 + numComponents];

    values[0] = fluid.getTemperature();
    values[1] = fluid.getPressure();
    values[2] = fluid.getFlowRate("mole/sec");

    for (int i = 0; i < numComponents; i++) {
      values[3 + i] = fluid.getPhase(0).getComponent(i).getx();
    }
    return values;
  }

  /**
   * Applies accelerated values to a recycle's outlet stream.
   *
   * @param recycle the recycle to update
   * @param values array of [temperature, pressure, flow, mole_fractions...]
   */
  private void applyAcceleratedValuesToRecycle(Recycle recycle, double[] values) {
    if (recycle.getOutletStream() == null || recycle.getOutletStream().getThermoSystem() == null) {
      return;
    }

    neqsim.thermo.system.SystemInterface fluid = recycle.getOutletStream().getThermoSystem();
    int numComponents = fluid.getPhase(0).getNumberOfComponents();

    // Apply composition changes (normalized)
    if (values.length >= 3 + numComponents) {
      double sum = 0.0;
      for (int i = 0; i < numComponents; i++) {
        values[3 + i] = Math.max(0.0, values[3 + i]); // Ensure non-negative
        sum += values[3 + i];
      }

      if (sum > 1e-15) {
        for (int i = 0; i < numComponents; i++) {
          double normalizedX = values[3 + i] / sum;
          for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
            fluid.getPhase(phase).getComponent(i).setx(normalizedX);
          }
        }
      }
    }
  }

  /**
   * Gets the total iteration count across all recycles.
   *
   * @return sum of iterations from all recycles
   */
  public int getTotalIterations() {
    int total = 0;
    for (Recycle recycle : recycleArray) {
      total += recycle.getIterations();
    }
    return total;
  }

  /**
   * Gets the maximum residual error across all recycles at current priority.
   *
   * @return maximum composition error
   */
  public double getMaxResidualError() {
    double maxError = 0.0;
    for (Recycle recycle : getRecyclesAtCurrentPriority()) {
      maxError = Math.max(maxError, recycle.getErrorComposition());
      maxError = Math.max(maxError, recycle.getErrorFlow());
    }
    return maxError;
  }

  /**
   * Resets all recycles and the coordinated accelerator for a new convergence cycle.
   */
  public void resetAll() {
    for (Recycle recycle : recycleArray) {
      recycle.resetIterations();
    }
    if (coordinatedAccelerator != null) {
      coordinatedAccelerator.reset();
    }
    init();
  }

  /**
   * Gets convergence diagnostics for the current state.
   *
   * @return diagnostic string with convergence information
   */
  public String getConvergenceDiagnostics() {
    StringBuilder sb = new StringBuilder();
    sb.append("RecycleController Diagnostics:\n");
    sb.append("  Total recycles: ").append(recycleArray.size()).append("\n");
    sb.append("  Current priority level: ").append(currentPriorityLevel).append("\n");
    sb.append("  Using coordinated acceleration: ").append(useCoordinatedAcceleration).append("\n");

    List<Recycle> current = getRecyclesAtCurrentPriority();
    sb.append("  Recycles at current priority: ").append(current.size()).append("\n");

    for (Recycle recycle : current) {
      sb.append("    - ").append(recycle.getName());
      sb.append(" [iterations=").append(recycle.getIterations());
      sb.append(", solved=").append(recycle.solved());
      sb.append(", errComp=").append(String.format("%.2e", recycle.getErrorComposition()));
      sb.append(", errFlow=").append(String.format("%.2e", recycle.getErrorFlow()));
      sb.append("]\n");
    }

    if (coordinatedAccelerator != null) {
      sb.append("  Coordinated accelerator residual norm: ")
          .append(String.format("%.2e", coordinatedAccelerator.getResidualNorm())).append("\n");
    }

    return sb.toString();
  }

  /**
   * Gets the sensitivity matrix from the Broyden convergence Jacobian.
   *
   * <p>
   * This provides sensitivities computed as a byproduct of convergence, without additional
   * simulations. The matrix represents d(output)/d(input) for tear stream variables.
   *
   * @return SensitivityMatrix from convergence, or null if not available
   */
  public SensitivityMatrix getTearStreamSensitivityMatrix() {
    if (coordinatedAccelerator == null) {
      return null;
    }

    double[][] invJ = coordinatedAccelerator.getInverseJacobian();
    if (invJ == null) {
      return null;
    }

    // Build variable names from recycles at current priority
    List<String> varNames = new ArrayList<>();
    for (Recycle recycle : getRecyclesAtCurrentPriority()) {
      String baseName = recycle.getName();
      varNames.add(baseName + ".temperature");
      varNames.add(baseName + ".pressure");
      varNames.add(baseName + ".flowRate");
    }

    String[] names = varNames.toArray(new String[0]);
    int dim = Math.min(names.length, invJ.length);

    // Create sensitivity matrix - use dim x dim submatrix
    String[] actualNames = new String[dim];
    System.arraycopy(names, 0, actualNames, 0, dim);

    SensitivityMatrix matrix = new SensitivityMatrix(actualNames, actualNames);

    // The inverse Jacobian approximates -(I - dg/dx)^{-1}
    // Sensitivity is -invJ (since B ≈ I - dg/dx, B^{-1} ≈ dx*/dg)
    for (int i = 0; i < dim; i++) {
      for (int j = 0; j < dim; j++) {
        matrix.setSensitivity(actualNames[i], actualNames[j], -invJ[i][j]);
      }
    }

    return matrix;
  }

  /**
   * Gets the raw inverse Jacobian matrix from the Broyden accelerator.
   *
   * <p>
   * This is the direct output of the Broyden update formula, useful for advanced analysis.
   *
   * @return inverse Jacobian matrix, or null if not available
   */
  public double[][] getConvergenceJacobian() {
    if (coordinatedAccelerator == null) {
      return null;
    }
    return coordinatedAccelerator.getInverseJacobian();
  }

  /**
   * Gets the names of tear stream variables in Jacobian order.
   *
   * @return list of variable names corresponding to Jacobian rows/columns
   */
  public List<String> getTearStreamVariableNames() {
    List<String> varNames = new ArrayList<>();
    for (Recycle recycle : getRecyclesAtCurrentPriority()) {
      String baseName = recycle.getName();
      varNames.add(baseName + ".temperature");
      varNames.add(baseName + ".pressure");
      varNames.add(baseName + ".flowRate");
    }
    return varNames;
  }

  /**
   * Checks if sensitivity data is available from convergence.
   *
   * @return true if Broyden Jacobian is available
   */
  public boolean hasSensitivityData() {
    return coordinatedAccelerator != null && coordinatedAccelerator.getInverseJacobian() != null
        && coordinatedAccelerator.getIterationCount() > 2;
  }
}
