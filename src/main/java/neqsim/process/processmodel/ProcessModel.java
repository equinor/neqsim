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
  ArrayList<ProcessSystem> processes = new ArrayList<ProcessSystem>(0);

  public boolean add(String name, ProcessSystem process) {
    processes.add(process);
    return true;
  }

  /**
   * <p>
   * runAsThread.
   * </p>
   *
   * @return a {@link java.lang.Thread} object
   */
  public void run() {
    for (int i = 0; i < processes.size(); i++) {
      processes.get(i).run();
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
