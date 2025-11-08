package neqsim.process.logic;

import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Base interface for all process logic implementations.
 * 
 * <p>
 * Process logic represents automated control sequences that coordinate multiple pieces of equipment
 * to achieve specific operational objectives such as:
 * <ul>
 * <li>Emergency Shutdown (ESD) sequences</li>
 * <li>Startup procedures</li>
 * <li>Shutdown sequences</li>
 * <li>Mode transitions</li>
 * <li>Batch operations</li>
 * </ul>
 * 
 * <p>
 * Logic execution is typically driven by triggers (manual or automatic) and proceeds through a
 * series of steps with timing, conditions, and actions on equipment.
 *
 * @author ESOL
 * @version 1.0
 */
public interface ProcessLogic {

  /**
   * Gets the name of this logic sequence.
   *
   * @return logic name
   */
  String getName();

  /**
   * Gets the current state of the logic.
   *
   * @return current logic state
   */
  LogicState getState();

  /**
   * Activates the logic sequence, starting execution.
   * 
   * <p>
   * If the logic is already active, this may restart it or have no effect depending on
   * implementation.
   * </p>
   */
  void activate();

  /**
   * Deactivates the logic sequence, pausing or stopping execution.
   * 
   * <p>
   * The logic remains in its current state and can be reactivated later.
   * </p>
   */
  void deactivate();

  /**
   * Resets the logic sequence to its initial state.
   * 
   * <p>
   * This prepares the logic for a fresh execution. Reset may require certain permissive conditions
   * to be met.
   * </p>
   *
   * @return true if reset was successful, false if permissives not met
   */
  boolean reset();

  /**
   * Executes one time step of the logic sequence.
   * 
   * <p>
   * This method should be called repeatedly in transient simulations to advance the logic through
   * its steps.
   * </p>
   *
   * @param timeStep time increment in seconds
   */
  void execute(double timeStep);

  /**
   * Checks if the logic is currently active (running).
   *
   * @return true if logic is active
   */
  boolean isActive();

  /**
   * Checks if the logic sequence has completed successfully.
   *
   * @return true if logic has completed all steps
   */
  boolean isComplete();

  /**
   * Gets the list of equipment targeted by this logic.
   *
   * @return list of target equipment
   */
  List<ProcessEquipmentInterface> getTargetEquipment();

  /**
   * Gets a description of the current status.
   *
   * @return status description
   */
  String getStatusDescription();
}
