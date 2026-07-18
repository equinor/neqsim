package neqsim.process.engineering.pid.rules;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.pid.PidDesignContext;
import neqsim.process.engineering.pid.PidDesignRule;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;

/** Proposes compressor trips, shutdown isolation, reverse-flow prevention and blowdown. */
public final class CompressorSafeguardingPidRule implements PidDesignRule {
  public static final String RULE_ID = "PID-SAFEGUARD-COMPRESSOR";

  @Override
  public String getId() {
    return RULE_ID;
  }

  @Override
  public int getOrder() {
    return 600;
  }

  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Compressor;
  }

  @Override
  public List<PidElement> propose(PidDesignContext context, ProcessEquipmentInterface equipment) {
    List<PidElement> result = new ArrayList<PidElement>();
    PidElement suctionPt = sensor(context, equipment, "PT", "SUCTION-P-LL-SENSOR", "PRESSURE",
        "Independent compressor suction pressure transmitter", "SUCTION-P-LL");
    PidElement suctionTrip = trip(context, equipment, "PSLL", "SUCTION-P-LL-TRIP",
        "Low-low compressor suction pressure trip", "SUCTION-P-LL");
    suctionPt.connect(suctionTrip.getId());
    PidRuleSupport.onInlet(suctionPt, equipment);

    PidElement dischargePt = sensor(context, equipment, "PT", "DISCHARGE-P-HH-SENSOR", "PRESSURE",
        "Independent compressor discharge pressure transmitter", "DISCHARGE-P-HH");
    PidElement dischargeTrip = trip(context, equipment, "PSHH", "DISCHARGE-P-HH-TRIP",
        "High-high compressor discharge pressure trip", "DISCHARGE-P-HH");
    dischargePt.connect(dischargeTrip.getId());
    PidRuleSupport.onOutlet(dischargePt, equipment);

    PidElement dischargeTt = sensor(context, equipment, "TT", "DISCHARGE-T-HH-SENSOR",
        "TEMPERATURE", "Independent compressor discharge temperature transmitter", "DISCHARGE-T-HH");
    PidElement temperatureTrip = trip(context, equipment, "TSHH", "DISCHARGE-T-HH-TRIP",
        "High-high compressor discharge temperature trip", "DISCHARGE-T-HH");
    dischargeTt.connect(temperatureTrip.getId());
    PidRuleSupport.onOutlet(dischargeTt, equipment);

    PidElement vibrationTrip = trip(context, equipment, "VSHH", "MACHINERY-VIBRATION-TRIP",
        "Compressor machinery protection trip", "MACHINERY-PROTECTION")
        .attribute("channels", "VENDOR_API_670_INPUT_REQUIRED");
    PidElement shutdown = PidRuleSupport.element(context, equipment, "ESD", "UNIT-SHUTDOWN",
        PidElementType.SAFETY_FUNCTION, RULE_ID, "Compressor shutdown function",
        "Stop the driver and move final elements to the approved safe state")
        .attribute("sequence", "SRS_AND_SHUTDOWN_NARRATIVE_REQUIRED");
    suctionTrip.connect(shutdown.getId());
    dischargeTrip.connect(shutdown.getId());
    temperatureTrip.connect(shutdown.getId());
    vibrationTrip.connect(shutdown.getId());

    PidElement suctionEsdv = PidRuleSupport.onInlet(PidRuleSupport.element(context, equipment, "ESDV",
        "SUCTION-ISOLATION", PidElementType.SHUTDOWN_VALVE, RULE_ID,
        "Compressor suction ESD valve", "Isolate the compressor suction inventory"), equipment)
        .attribute("failurePosition", "FAIL_CLOSED_PROPOSAL");
    PidElement dischargeEsdv = PidRuleSupport.onOutlet(PidRuleSupport.element(context, equipment, "ESDV",
        "DISCHARGE-ISOLATION", PidElementType.SHUTDOWN_VALVE, RULE_ID,
        "Compressor discharge ESD valve", "Isolate the compressor discharge inventory"), equipment)
        .attribute("failurePosition", "FAIL_CLOSED_PROPOSAL");
    PidElement nrv = PidRuleSupport.onOutlet(PidRuleSupport.element(context, equipment, "NRV",
        "DISCHARGE-CHECK-VALVE", PidElementType.CHECK_VALVE, RULE_ID,
        "Compressor discharge non-return valve", "Limit reverse flow and reverse rotation"), equipment)
        .attribute("leakageCase", "SETTLE_OUT_AND_RELIEF_REVIEW_REQUIRED");
    PidElement bdv = PidRuleSupport.element(context, equipment, "BDV", "CASE-DEPRESSURISATION",
        PidElementType.BLOWDOWN_VALVE, RULE_ID, "Compressor settle-out blowdown valve",
        "Depressurise the isolated compressor circuit when required")
        .attribute("orificeSizing", "DYNAMIC_BLOWDOWN_STUDY_REQUIRED")
        .attribute("flareTieIn", "PROJECT_INPUT_REQUIRED");
    shutdown.connect(suctionEsdv.getId()).connect(dischargeEsdv.getId()).connect(bdv.getId());
    PidRuleSupport.trace(suctionEsdv, context, equipment, "ISOLATION-BLOWDOWN");
    PidRuleSupport.trace(dischargeEsdv, context, equipment, "ISOLATION-BLOWDOWN");
    PidRuleSupport.trace(nrv, context, equipment, "ISOLATION-BLOWDOWN");
    PidRuleSupport.trace(bdv, context, equipment, "ISOLATION-BLOWDOWN");

    result.add(suctionPt);
    result.add(suctionTrip);
    result.add(dischargePt);
    result.add(dischargeTrip);
    result.add(dischargeTt);
    result.add(temperatureTrip);
    result.add(vibrationTrip);
    result.add(shutdown);
    result.add(suctionEsdv);
    result.add(dischargeEsdv);
    result.add(nrv);
    result.add(bdv);
    return result;
  }

  private static PidElement sensor(PidDesignContext context, ProcessEquipmentInterface equipment,
      String function, String purpose, String variable, String description, String requirementToken) {
    PidElement element = PidRuleSupport.element(context, equipment, function, purpose,
        PidElementType.MEASUREMENT, RULE_ID, description,
        "Provide an independent initiating element for compressor protection")
        .attribute("measuredVariable", variable).attribute("system", "SIS")
        .attribute("independence", "VERIFICATION_REQUIRED");
    PidRuleSupport.trace(element, context, equipment, requirementToken);
    return element;
  }

  private static PidElement trip(PidDesignContext context, ProcessEquipmentInterface equipment,
      String function, String purpose, String description, String requirementToken) {
    PidElement element = PidRuleSupport.element(context, equipment, function, purpose,
        PidElementType.TRIP, RULE_ID, description, "Move the compressor to the approved safe state")
        .attribute("system", "SIS_OR_MACHINERY_PROTECTION")
        .attribute("setPoint", "SRS_OR_VENDOR_INPUT_REQUIRED")
        .attribute("voting", "SRS_OR_VENDOR_INPUT_REQUIRED");
    PidRuleSupport.trace(element, context, equipment, requirementToken);
    return element;
  }
}
