package neqsim.process.processmodel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>
 * ProcessModel class.
 * </p>
 *
 * Manages a collection of processes that can be run in steps or continuously.
 * 
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ProcessModel implements Runnable {
  private final Map<String, ProcessSystem> processes = new LinkedHashMap<>();
  private boolean runStep = false;

  /**
   * Checks if the model is running in step mode.
   *
   * @return true if running in step mode, false otherwise.
   */
  public boolean isRunStep() {
    return runStep;
  }

  /**
   * Sets the step mode for the process.
   *
   * @param runStep true to enable step mode, false to disable.
   */
  public void setRunStep(boolean runStep) {
    this.runStep = runStep;
  }

  /**
   * Adds a process to the model.
   *
   * @param name the name of the process.
   * @param process the process to add.
   * @return true if the process was added successfully.
   */
  public boolean add(String name, ProcessSystem process) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Name cannot be null or empty");
    }
    if (process == null) {
      throw new IllegalArgumentException("Process cannot be null");
    }
    if (processes.containsKey(name)) {
      throw new IllegalArgumentException("A process with the given name already exists");
    }
    processes.put(name, process);
    return true;
  }

  /**
   * Retrieves a process by its name.
   *
   * @param name the name of the process.
   * @return the corresponding process, or null if not found.
   */
  public ProcessSystem get(String name) {
    return processes.get(name);
  }

  /**
   * Removes a process by its name.
   *
   * @param name the name of the process to remove.
   * @return true if the process was removed, false otherwise.
   */
  public boolean remove(String name) {
    return processes.remove(name) != null;
  }

  /**
   * Executes all processes, either continuously or in steps based on mode.
   */
  @Override
  public void run() {
    for (ProcessSystem process : processes.values()) {
      try {
        if (runStep) {
          process.run_step();
        } else {
          process.run();
        }
      } catch (Exception e) {
        System.err.println("Error running process: " + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  /**
   * Executes all processes in a single step.
   */
  public void runStep() {
    for (ProcessSystem process : processes.values()) {
      try {
        process.run_step();
      } catch (Exception e) {
        System.err.println("Error in runStep: " + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  /**
   * Runs the model as a separate thread.
   *
   * @return the thread running the model.
   */
  public Thread runAsThread() {
    Thread processThread = new Thread(this);
    processThread.start();
    return processThread;
  }

}
