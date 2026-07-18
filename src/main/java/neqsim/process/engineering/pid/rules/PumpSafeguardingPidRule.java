package neqsim.process.engineering.pid.rules;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.pid.PidDesignContext;
import neqsim.process.engineering.pid.PidDesignRule;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pump.Pump;

/** Proposes pump low-suction trip, isolation, reverse-flow protection, drain and vent. */
public final class PumpSafeguardingPidRule implements PidDesignRule {
  public static final String RULE_ID = "PID-SAFEGUARD-PUMP";

  @Override
  public String getId() {
    return RULE_ID;
  }

  @Override
  public int getOrder() {
    return 700;
  }

  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Pump;
  }

  @Override
  public List<PidElement> propose(PidDesignContext context, ProcessEquipmentInterface equipment) {
    List<PidElement> result = new ArrayList<PidElement>();
    PidElement pt = PidRuleSupport.onInlet(PidRuleSupport.element(context, equipment, "PT",
        "LOW-SUCTION-TRIP-SENSOR", PidElementType.MEASUREMENT, RULE_ID,
        "Independent pump suction pressure transmitter", "Detect loss of liquid supply"), equipment)
        .attribute("system", "SIS").attribute("measuredVariable", "PRESSURE");
    PidElement trip = PidRuleSupport.element(context, equipment, "PSLL", "LOW-SUCTION-TRIP",
        PidElementType.TRIP, RULE_ID, "Pump low-low suction pressure trip",
        "Stop the pump before cavitation or loss of prime causes damage")
        .attribute("setPoint", "NPSH_AND_VENDOR_INPUT_REQUIRED");
    pt.connect(trip.getId());
    PidElement suctionXv = PidRuleSupport.onInlet(PidRuleSupport.element(context, equipment, "XV",
        "SUCTION-ISOLATION", PidElementType.ISOLATION_VALVE, RULE_ID, "Pump suction isolation valve",
        "Provide maintainable pump isolation"), equipment);
    PidElement dischargeXv = PidRuleSupport.onOutlet(PidRuleSupport.element(context, equipment, "XV",
        "DISCHARGE-ISOLATION", PidElementType.ISOLATION_VALVE, RULE_ID,
        "Pump discharge isolation valve", "Provide maintainable pump isolation"), equipment);
    PidElement nrv = PidRuleSupport.onOutlet(PidRuleSupport.element(context, equipment, "NRV",
        "DISCHARGE-CHECK-VALVE", PidElementType.CHECK_VALVE, RULE_ID,
        "Pump discharge non-return valve", "Prevent reverse flow through a stopped pump"), equipment);
    PidElement drain = PidRuleSupport.element(context, equipment, "DV", "CASING-DRAIN",
        PidElementType.DRAIN, RULE_ID, "Pump casing drain", "Enable controlled draining for maintenance")
        .attribute("destination", "CLOSED_DRAIN_REVIEW_REQUIRED");
    PidElement vent = PidRuleSupport.element(context, equipment, "VV", "CASING-VENT",
        PidElementType.VENT, RULE_ID, "Pump casing vent", "Enable safe venting and priming")
        .attribute("destination", "SAFE_VENT_REVIEW_REQUIRED");
    PidRuleSupport.trace(pt, context, equipment, "LOW-SUCTION");
    PidRuleSupport.trace(trip, context, equipment, "LOW-SUCTION");
    PidRuleSupport.trace(nrv, context, equipment, "DEADHEAD");
    result.add(pt);
    result.add(trip);
    result.add(suctionXv);
    result.add(dischargeXv);
    result.add(nrv);
    result.add(drain);
    result.add(vent);
    return result;
  }
}
