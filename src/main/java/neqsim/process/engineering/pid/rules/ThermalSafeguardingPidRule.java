package neqsim.process.engineering.pid.rules;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.pid.PidDesignContext;
import neqsim.process.engineering.pid.PidDesignRule;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Heater;

/** Proposes independent high-temperature and low-flow protection for thermal equipment. */
public final class ThermalSafeguardingPidRule implements PidDesignRule {
  public static final String RULE_ID = "PID-SAFEGUARD-THERMAL";

  @Override
  public String getId() {
    return RULE_ID;
  }

  @Override
  public int getOrder() {
    return 800;
  }

  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Heater;
  }

  @Override
  public List<PidElement> propose(PidDesignContext context, ProcessEquipmentInterface equipment) {
    List<PidElement> result = new ArrayList<PidElement>();
    PidElement tt = PidRuleSupport.onOutlet(PidRuleSupport.element(context, equipment, "TT",
        "OUTLET-T-HH-SENSOR", PidElementType.MEASUREMENT, RULE_ID,
        "Independent outlet temperature transmitter", "Detect excessive process temperature"),
        equipment).attribute("system", "SIS").attribute("measuredVariable", "TEMPERATURE");
    PidElement tshh = PidRuleSupport.element(context, equipment, "TSHH", "OUTLET-T-HH-TRIP",
        PidElementType.TRIP, RULE_ID, "High-high outlet temperature trip",
        "Remove heat input before downstream or equipment temperature limits are exceeded")
        .attribute("setPoint", "SRS_INPUT_REQUIRED");
    tt.connect(tshh.getId());
    PidElement ft = PidRuleSupport.onInlet(PidRuleSupport.element(context, equipment, "FT",
        "LOW-FLOW-SENSOR", PidElementType.MEASUREMENT, RULE_ID,
        "Independent process flow transmitter", "Detect loss of process flow through thermal equipment"),
        equipment).attribute("system", "SIS").attribute("measuredVariable", "FLOW");
    PidElement fsll = PidRuleSupport.element(context, equipment, "FSLL", "LOW-FLOW-TRIP",
        PidElementType.TRIP, RULE_ID, "Low-low process flow trip",
        "Remove heat input when process flow is insufficient")
        .attribute("setPoint", "SRS_INPUT_REQUIRED");
    ft.connect(fsll.getId());
    PidElement utilitySdv = PidRuleSupport.element(context, equipment, "SDV",
        "UTILITY-SHUTDOWN", PidElementType.SHUTDOWN_VALVE, RULE_ID,
        "Heating or cooling utility shutdown valve", "Isolate the utility in the approved safe direction")
        .attribute("failurePosition", "HAZOP_INPUT_REQUIRED")
        .attribute("utilityConnection", "PROJECT_INPUT_REQUIRED");
    tshh.connect(utilitySdv.getId());
    fsll.connect(utilitySdv.getId());
    PidElement drain = PidRuleSupport.element(context, equipment, "DV", "PROCESS-DRAIN",
        PidElementType.DRAIN, RULE_ID, "Thermal equipment process drain",
        "Enable controlled draining for maintenance").attribute("destination", "DRAIN_REVIEW_REQUIRED");
    PidElement vent = PidRuleSupport.element(context, equipment, "VV", "PROCESS-VENT",
        PidElementType.VENT, RULE_ID, "Thermal equipment process vent",
        "Enable safe venting for maintenance").attribute("destination", "VENT_REVIEW_REQUIRED");
    PidRuleSupport.trace(tt, context, equipment, "OUTLET-T-HH");
    PidRuleSupport.trace(tshh, context, equipment, "OUTLET-T-HH");
    PidRuleSupport.trace(ft, context, equipment, "LOW-FLOW");
    PidRuleSupport.trace(fsll, context, equipment, "LOW-FLOW");
    result.add(tt);
    result.add(tshh);
    result.add(ft);
    result.add(fsll);
    result.add(utilitySdv);
    result.add(drain);
    result.add(vent);
    return result;
  }
}
