package neqsim.process.engineering.pid.rules;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.pid.PidDesignContext;
import neqsim.process.engineering.pid.PidDesignRule;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pump.Pump;

/** Proposes pump pressure monitoring and minimum-flow recycle control. */
public final class PumpControlPidRule implements PidDesignRule {
  public static final String RULE_ID = "PID-CONTROL-PUMP";

  @Override
  public String getId() {
    return RULE_ID;
  }

  @Override
  public int getOrder() {
    return 400;
  }

  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Pump;
  }

  @Override
  public List<PidElement> propose(PidDesignContext context, ProcessEquipmentInterface equipment) {
    List<PidElement> result = new ArrayList<PidElement>();
    PidElement suctionPt = PidRuleSupport.onInlet(PidRuleSupport.element(context, equipment, "PT",
        "SUCTION-PRESSURE", PidElementType.MEASUREMENT, RULE_ID,
        "Pump suction pressure transmitter", "Monitor NPSH and liquid-supply margin"), equipment)
        .attribute("measuredVariable", "PRESSURE").attribute("location", "SUCTION");
    PidElement dischargePt = PidRuleSupport.onOutlet(PidRuleSupport.element(context, equipment, "PT",
        "DISCHARGE-PRESSURE", PidElementType.MEASUREMENT, RULE_ID,
        "Pump discharge pressure transmitter", "Monitor pump head and deadhead pressure"), equipment)
        .attribute("measuredVariable", "PRESSURE").attribute("location", "DISCHARGE");
    PidElement ft = PidRuleSupport.onOutlet(PidRuleSupport.element(context, equipment, "FT",
        "MINIMUM-FLOW", PidElementType.MEASUREMENT, RULE_ID,
        "Pump discharge flow transmitter", "Measure flow for minimum-flow protection"), equipment)
        .attribute("measuredVariable", "FLOW");
    PidElement fic = PidRuleSupport.element(context, equipment, "FIC", "MINIMUM-FLOW-CONTROLLER",
        PidElementType.CONTROLLER, RULE_ID, "Pump minimum-flow controller",
        "Maintain vendor-approved minimum continuous stable flow");
    PidElement fcv = PidRuleSupport.element(context, equipment, "FCV", "MINIMUM-FLOW-VALVE",
        PidElementType.CONTROL_VALVE, RULE_ID, "Pump minimum-flow recycle valve",
        "Recycle liquid to protect the pump at low process flow")
        .attribute("failurePosition", "FAIL_OPEN_PROPOSAL")
        .attribute("recycleDestination", "PROJECT_INPUT_REQUIRED");
    ft.connect(fic.getId());
    fic.connect(fcv.getId());
    PidRuleSupport.trace(suctionPt, context, equipment, "LOW-SUCTION");
    PidRuleSupport.trace(dischargePt, context, equipment, "DEADHEAD");
    PidRuleSupport.trace(ft, context, equipment, "DEADHEAD");
    PidRuleSupport.trace(fic, context, equipment, "DEADHEAD");
    PidRuleSupport.trace(fcv, context, equipment, "DEADHEAD");
    result.add(suctionPt);
    result.add(dischargePt);
    result.add(ft);
    result.add(fic);
    result.add(fcv);
    return result;
  }
}
