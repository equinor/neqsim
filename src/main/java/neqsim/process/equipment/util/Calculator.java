package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.splitter.Splitter;

/**
 * <p>
 * Calculator class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class Calculator extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Calculator.class);

  ArrayList<ProcessEquipmentInterface> inputVariable = new ArrayList<ProcessEquipmentInterface>();
  private ProcessEquipmentInterface outputVariable;
  String type = "sumTEG";

  /**
   * <p>
   * Constructor for Calculator.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public Calculator(String name) {
    super(name);
  }

  /**
   * <p>
   * addInputVariable.
   * </p>
   *
   * @param unit a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void addInputVariable(ProcessEquipmentInterface unit) {
    inputVariable.add(unit);
  }

  /**
   * <p>
   * Getter for the field <code>outputVariable</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public ProcessEquipmentInterface getOutputVariable() {
    return outputVariable;
  }

  /**
   * <p>
   * runAntiSurgeCalc.
   * </p>
   *
   * @param id a {@link java.util.UUID} object
   */
  public void runAntiSurgeCalc(UUID id) {
    Compressor compressor = (Compressor) inputVariable.get(0);

    Splitter anitSurgeSplitter = (Splitter) outputVariable;
    double distToSurge = compressor.getDistanceToSurge();
    double flowInAntiSurge = anitSurgeSplitter.getSplitStream(1).getFlowRate("MSm3/day");

    flowInAntiSurge = anitSurgeSplitter.getSplitStream(1).getFlowRate("MSm3/day")
        - compressor.getInletStream().getFlowRate("MSm3/day") * distToSurge * 0.5;

    if (flowInAntiSurge > compressor.getInletStream().getFlowRate("MSm3/day")) {
      flowInAntiSurge = compressor.getInletStream().getFlowRate("MSm3/day") * 0.99;
    }
    if (flowInAntiSurge < 1e-6) {
      flowInAntiSurge = 1e-6;
    }

    anitSurgeSplitter.setFlowRates(new double[] {-1, flowInAntiSurge}, "MSm3/day");
    anitSurgeSplitter.run();
    anitSurgeSplitter.setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    double sum = 0.0;
    if (name.equals("anti surge calculator")) {
      runAntiSurgeCalc(id);
      return;
    }

    if (name.equals("MEG makeup calculator")) {
      for (int i = 0; i < inputVariable.size(); i++) {
        sum += inputVariable.get(i).getFluid().getPhase(0).getComponent("MEG").getFlowRate("kg/hr");
      }
    } else {
      for (int i = 0; i < inputVariable.size(); i++) {
        sum += inputVariable.get(i).getFluid().getComponent("TEG").getFlowRate("kg/hr");
      }
    }

    // System.out.println("make up TEG " + sum);
    // ((Stream) outputVariable).setFlowRate(sum, "kg/hr");
    try {
      if (sum < 0.0) {
        sum = 0.0;
      }
      ((neqsim.process.equipment.stream.Stream) outputVariable).setFlowRate(sum, "kg/hr");
      outputVariable.run();
      outputVariable.setCalculationIdentifier(id);
    } catch (Exception ex) {
      logger.info("flow rate error " + sum);
      logger.error("error in calculator", ex);
    }
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * Setter for the field <code>outputVariable</code>.
   * </p>
   *
   * @param outputVariable a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void setOutputVariable(ProcessEquipmentInterface outputVariable) {
    this.outputVariable = outputVariable;
  }
}
