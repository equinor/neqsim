package neqsim.process.engineering.pid.rules;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.pid.PidDesignContext;
import neqsim.process.engineering.pid.PidDesignRule;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;

/** Proposes compressor operating and antisurge instrumentation. */
public final class CompressorControlPidRule implements PidDesignRule {
  public static final String RULE_ID = "PID-CONTROL-COMPRESSOR";

  @Override
  public String getId() {
    return RULE_ID;
  }

  @Override
  public int getOrder() {
    return 200;
  }

  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Compressor;
  }

  @Override
  public List<PidElement> propose(PidDesignContext context, ProcessEquipmentInterface equipment) {
    List<PidElement> result = new ArrayList<PidElement>();
    PidElement suctionPt = PidRuleSupport.onInlet(PidRuleSupport.element(context, equipment, "PT",
        "SUCTION-PRESSURE", PidElementType.MEASUREMENT, RULE_ID,
        "Compressor suction pressure transmitter", "Monitor the approved suction-pressure envelope"),
        equipment).attribute("measuredVariable", "PRESSURE").attribute("location", "SUCTION");
    PidElement dischargePt = PidRuleSupport.onOutlet(PidRuleSupport.element(context, equipment, "PT",
        "DISCHARGE-PRESSURE", PidElementType.MEASUREMENT, RULE_ID,
        "Compressor discharge pressure transmitter", "Monitor discharge pressure and pressure ratio"),
        equipment).attribute("measuredVariable", "PRESSURE").attribute("location", "DISCHARGE");
    PidElement dischargeTt = PidRuleSupport.onOutlet(PidRuleSupport.element(context, equipment, "TT",
        "DISCHARGE-TEMPERATURE", PidElementType.MEASUREMENT, RULE_ID,
        "Compressor discharge temperature transmitter", "Monitor thermal operating limits"),
        equipment).attribute("measuredVariable", "TEMPERATURE").attribute("location", "DISCHARGE");
    PidElement suctionFt = PidRuleSupport.onInlet(PidRuleSupport.element(context, equipment, "FT",
        "ANTISURGE-FLOW", PidElementType.MEASUREMENT, RULE_ID,
        "Compressor antisurge flow transmitter", "Measure corrected flow for surge-margin control"),
        equipment).attribute("measuredVariable", "FLOW").attribute("service", "ANTISURGE");
    PidElement fic = PidRuleSupport.element(context, equipment, "FIC", "ANTISURGE-CONTROLLER",
        PidElementType.CONTROLLER, RULE_ID, "Compressor antisurge controller",
        "Maintain vendor-approved surge margin during steady and transient operation")
        .attribute("algorithm", "VENDOR_MAP_AND_DYNAMIC_COMPENSATION_REQUIRED");
    PidElement ascv = PidRuleSupport.element(context, equipment, "ASCV", "ANTISURGE-VALVE",
        PidElementType.CONTROL_VALVE, RULE_ID, "Compressor antisurge recycle valve",
        "Recycle discharge gas to prevent surge").attribute("failurePosition", "FAIL_OPEN_PROPOSAL")
        .attribute("strokeTimeStatus", "DYNAMIC_VERIFICATION_REQUIRED")
        .attribute("topology", "RECYCLE_TIE_IN_REVIEW_REQUIRED");
    suctionFt.connect(fic.getId());
    fic.connect(ascv.getId());
    PidRuleSupport.trace(suctionPt, context, equipment, "SUCTION-P");
    PidRuleSupport.trace(dischargePt, context, equipment, "DISCHARGE-P");
    PidRuleSupport.trace(dischargeTt, context, equipment, "DISCHARGE-T");
    PidRuleSupport.trace(suctionFt, context, equipment, "ANTI-SURGE");
    PidRuleSupport.trace(fic, context, equipment, "ANTI-SURGE");
    PidRuleSupport.trace(ascv, context, equipment, "ANTI-SURGE");
    result.add(suctionPt);
    result.add(dischargePt);
    result.add(dischargeTt);
    result.add(suctionFt);
    result.add(fic);
    result.add(ascv);
    return result;
  }
}
