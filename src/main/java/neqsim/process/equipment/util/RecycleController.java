package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
}
