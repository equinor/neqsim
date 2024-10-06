package neqsim.processsimulation.conditionmonitor;

import java.util.ArrayList;
import neqsim.processsimulation.processsystem.ProcessSystem;

/**
 * <p>
 * ConditionMonitor class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ConditionMonitor implements java.io.Serializable, Runnable {
  private static final long serialVersionUID = 1000;
  ProcessSystem refprocess = null;
  ProcessSystem process = null;
  String report;

  /**
   * <p>
   * Constructor for ConditionMonitor.
   * </p>
   */
  public ConditionMonitor() {}

  /**
   * <p>
   * Constructor for ConditionMonitor.
   * </p>
   *
   * @param refprocess a {@link neqsim.processsimulation.processsystem.ProcessSystem} object
   */
  public ConditionMonitor(ProcessSystem refprocess) {
    this.refprocess = refprocess;
    process = refprocess.copy();
  }

  /**
   * <p>
   * conditionAnalysis.
   * </p>
   *
   * @param unitName a {@link java.lang.String} object
   */
  public void conditionAnalysis(String unitName) {
    neqsim.processsimulation.processequipment.ProcessEquipmentBaseClass refUn =
        (neqsim.processsimulation.processequipment.ProcessEquipmentBaseClass) refprocess
            .getUnit(unitName);
    ((neqsim.processsimulation.processequipment.ProcessEquipmentInterface) process
        .getUnit(unitName)).runConditionAnalysis(refUn);
    report += ((neqsim.processsimulation.processequipment.ProcessEquipmentInterface) process
        .getUnit(unitName)).getConditionAnalysisMessage();
  }

  /**
   * <p>
   * conditionAnalysis.
   * </p>
   */
  public void conditionAnalysis() {
    ArrayList<String> names = process.getAllUnitNames();
    for (int i = 0; i < names.size(); i++) {
      conditionAnalysis(names.get(i));
    }
  }

  /**
   * <p>
   * Getter for the field <code>report</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getReport() {
    return report;
  }

  /**
   * <p>
   * Getter for the field <code>process</code>.
   * </p>
   *
   * @return a {@link neqsim.processsimulation.processsystem.ProcessSystem} object
   */
  public ProcessSystem getProcess() {
    return process;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    process = refprocess.copy();
  }
}
