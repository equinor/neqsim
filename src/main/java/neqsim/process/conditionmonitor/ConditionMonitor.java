package neqsim.process.conditionmonitor;

import java.util.ArrayList;
import neqsim.process.processmodel.ProcessSystem;

/**
 * ConditionMonitor class.
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
   * Constructor for ConditionMonitor.
   */
  public ConditionMonitor() {
  }

  /**
   * Constructor for ConditionMonitor.
   *
   * @param refprocess a {@link neqsim.process.processmodel.ProcessSystem} object
   */
  public ConditionMonitor(ProcessSystem refprocess) {
    this.refprocess = refprocess;
    process = refprocess.copy();
  }

  /**
   * conditionAnalysis.
   *
   * @param unitName a {@link java.lang.String} object
   */
  public void conditionAnalysis(String unitName) {
    neqsim.process.equipment.ProcessEquipmentBaseClass refUn = (neqsim.process.equipment.ProcessEquipmentBaseClass) refprocess
	.getUnit(unitName);
    process.getUnit(unitName).runConditionAnalysis(refUn);
    report += process.getUnit(unitName).getConditionAnalysisMessage();
  }

  /**
   * conditionAnalysis.
   */
  public void conditionAnalysis() {
    ArrayList<String> names = process.getAllUnitNames();
    for (int i = 0; i < names.size(); i++) {
      conditionAnalysis(names.get(i));
    }
  }

  /**
   * Getter for the field <code>report</code>.
   *
   * @return a {@link java.lang.String} object
   */
  public String getReport() {
    return report;
  }

  /**
   * Getter for the field <code>process</code>.
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
