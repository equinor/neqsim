package neqsim.process.engineering.pid.rules;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.pid.PidDesignContext;
import neqsim.process.engineering.pid.PidDesignRule;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Heater;

/** Proposes outlet-temperature measurement and control for heaters, coolers and exchangers. */
public final class ThermalControlPidRule implements PidDesignRule {
  public static final String RULE_ID = "PID-CONTROL-THERMAL";

  @Override
  public String getId() {
    return RULE_ID;
  }

  @Override
  public int getOrder() {
    return 300;
  }

  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Heater;
  }

  @Override
  public List<PidElement> propose(PidDesignContext context, ProcessEquipmentInterface equipment) {
    List<PidElement> result = new ArrayList<PidElement>();
    PidElement tt = PidRuleSupport
        .onOutlet(PidRuleSupport.element(context, equipment, "TT", "OUTLET-TEMPERATURE", PidElementType.MEASUREMENT,
            RULE_ID, "Outlet temperature transmitter", "Measure temperature at the controlled process outlet"),
            equipment)
        .attribute("measuredVariable", "TEMPERATURE");
    PidElement tic = PidRuleSupport.element(context, equipment, "TIC", "TEMPERATURE-CONTROLLER",
        PidElementType.CONTROLLER, RULE_ID, "Temperature indicating controller",
        "Maintain the approved outlet-temperature target");
    PidElement tcv = PidRuleSupport
        .element(context, equipment, "TCV", "UTILITY-CONTROL-VALVE", PidElementType.CONTROL_VALVE, RULE_ID,
            "Heating or cooling utility control valve", "Manipulate utility duty to control process outlet temperature")
        .attribute("failurePosition", "HAZOP_INPUT_REQUIRED").attribute("utilityConnection", "PROJECT_INPUT_REQUIRED");
    tt.connect(tic.getId());
    tic.connect(tcv.getId());
    PidRuleSupport.trace(tt, context, equipment, "OUTLET-T", "TEMPERATURE");
    PidRuleSupport.trace(tic, context, equipment, "OUTLET-T", "TEMPERATURE");
    PidRuleSupport.trace(tcv, context, equipment, "OUTLET-T", "TEMPERATURE");
    result.add(tt);
    result.add(tic);
    result.add(tcv);
    return result;
  }
}
