package neqsim.process.conditionmonitor;

import java.util.ArrayList;
import neqsim.process.processmodel.ProcessSystem;

/**
 * <p>
 * ConditionMonitor class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ConditionMonitor implements java.io.Serializable, Runnable {
  /** Serialization version UID. */
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
   * @param refprocess a {@link neqsim.process.processmodel.ProcessSystem} object
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
    neqsim.process.equipment.ProcessEquipmentBaseClass refUn =
        (neqsim.process.equipment.ProcessEquipmentBaseClass) refprocess.getUnit(unitName);
    ((neqsim.process.equipment.ProcessEquipmentInterface) process.getUnit(unitName))
        .runConditionAnalysis(refUn);
    report += ((neqsim.process.equipment.ProcessEquipmentInterface) process.getUnit(unitName))
        .getConditionAnalysisMessage();
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
   * @return a {@link neqsim.process.processmodel.ProcessSystem} object
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
