package neqsim.process.processmodel;

import java.util.ArrayList;

/**
 * <p>
 * ProcessModel class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ProcessModel implements Runnable {
  ArrayList<ProcessSystem> processes = new ArrayList<ProcessSystem>();
  private boolean runStep = false;


  /**
   * <p>
   * isRunStep.
   * </p>
   * 
   *
   * @return a {@link java.lang.Boolean} runStep
   * 
   */
  public boolean isRunStep() {
    return runStep;
  }

  /**
   * <p>
   * runStep.
   * </p>
   * 
   * @param runStep run in steps
   * 
   */
  public void setRunStep(boolean runStep) {
    this.runStep = runStep;
  }


  /**
   * <p>
   * add.
   * </p>
   *
   * @param name name of process
   * @param process process to add
   * 
   * @return a {@link java.lang.Boolean} success
   */
  public boolean add(String name, ProcessSystem process) {
    processes.add(process);
    return true;
  }

  /**
   * <p>
   * run.
   * </p>
   *
   */
  public void run() {
    for (int i = 0; i < processes.size(); i++) {
      if (runStep) {
        processes.get(i).run_step();
      } else {
        processes.get(i).run();
      }
    }
  }

  /**
   * <p>
   * runStep.
   * </p>
   *
   */
  public void runStep() {
    for (int i = 0; i < processes.size(); i++) {
      processes.get(i).run_step();

    }
  }

  /**
   * <p>
   * runAsThread.
   * </p>
   *
   * @return a {@link java.lang.Thread} object
   */
  public Thread runAsThread() {
    Thread processThread = new Thread(this);
    processThread.start();
    return processThread;
  }

}
