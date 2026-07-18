package neqsim.process.engineering.pid.rules;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.pid.PidDesignContext;
import neqsim.process.engineering.pid.PidDesignRule;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;

/** Proposes independent separator trips, isolation and depressurisation functions. */
public final class SeparatorSafeguardingPidRule implements PidDesignRule {
  public static final String RULE_ID = "PID-SAFEGUARD-SEPARATOR";

  @Override
  public String getId() {
    return RULE_ID;
  }

  @Override
  public int getOrder() {
    return 500;
  }

  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Separator;
  }

  @Override
  public List<PidElement> propose(PidDesignContext context, ProcessEquipmentInterface equipment) {
    List<PidElement> result = new ArrayList<PidElement>();
    PidElement pressureSensor = independentSensor(context, equipment, "PT", "PRESSURE-HH-SENSOR",
        "Independent high-pressure transmitter", "PRESSURE", "PRESSURE-HH");
    PidElement pressureTrip = trip(context, equipment, "PSHH", "PRESSURE-HH-TRIP",
        "High-high pressure shutdown", "Isolate credible pressure sources before the approved limit",
        "PRESSURE-HH");
    pressureSensor.connect(pressureTrip.getId());

    PidElement levelHighSensor = independentSensor(context, equipment, "LT", "LEVEL-HH-SENSOR",
        "Independent high-level transmitter", "LEVEL", "LEVEL-HH");
    PidElement levelHighTrip = trip(context, equipment, "LSHH", "LEVEL-HH-TRIP",
        "High-high level shutdown", "Prevent liquid carry-over to downstream gas equipment", "LEVEL-HH");
    levelHighSensor.connect(levelHighTrip.getId());

    PidElement levelLowSensor = independentSensor(context, equipment, "LT", "LEVEL-LL-SENSOR",
        "Independent low-level transmitter", "LEVEL", "LEVEL-LL");
    PidElement levelLowTrip = trip(context, equipment, "LSLL", "LEVEL-LL-TRIP",
        "Low-low level shutdown", "Prevent gas blow-by to a lower-pressure liquid system", "LEVEL-LL");
    levelLowSensor.connect(levelLowTrip.getId());

    PidElement inletEsdv = PidRuleSupport.onInlet(PidRuleSupport.element(context, equipment, "ESDV",
        "INLET-ISOLATION", PidElementType.SHUTDOWN_VALVE, RULE_ID, "Separator inlet ESD valve",
        "Isolate incoming hydrocarbon inventory on approved shutdown demand"), equipment)
        .attribute("failurePosition", "FAIL_CLOSED_PROPOSAL")
        .attribute("closureTime", "DYNAMIC_VERIFICATION_REQUIRED");
    PidElement gasEsdv = PidRuleSupport.onOutlet(PidRuleSupport.element(context, equipment, "ESDV",
        "GAS-OUTLET-ISOLATION", PidElementType.SHUTDOWN_VALVE, RULE_ID,
        "Separator gas outlet ESD valve", "Define the shutdown isolation boundary"), equipment)
        .attribute("failurePosition", "FAIL_CLOSED_PROPOSAL")
        .attribute("closureTime", "DYNAMIC_VERIFICATION_REQUIRED");
    PidElement bdv = PidRuleSupport.element(context, equipment, "BDV", "DEPRESSURISATION",
        PidElementType.BLOWDOWN_VALVE, RULE_ID, "Separator blowdown valve",
        "Depressurise isolated inventory to the flare system when required")
        .attribute("failurePosition", "FAIL_CLOSED_PROPOSAL")
        .attribute("orificeSizing", "DYNAMIC_BLOWDOWN_STUDY_REQUIRED")
        .attribute("flareTieIn", "PROJECT_INPUT_REQUIRED");
    pressureTrip.connect(inletEsdv.getId()).connect(gasEsdv.getId()).connect(bdv.getId());
    levelHighTrip.connect(inletEsdv.getId());
    levelLowTrip.connect(inletEsdv.getId());
    PidRuleSupport.trace(inletEsdv, context, equipment, "TRIP", "PRESSURE-HH", "LEVEL-HH", "LEVEL-LL");
    PidRuleSupport.trace(gasEsdv, context, equipment, "TRIP", "PRESSURE-HH");
    PidRuleSupport.trace(bdv, context, equipment, "RELIEF", "DEPRESSUR");

    result.add(pressureSensor);
    result.add(pressureTrip);
    result.add(levelHighSensor);
    result.add(levelHighTrip);
    result.add(levelLowSensor);
    result.add(levelLowTrip);
    result.add(inletEsdv);
    result.add(gasEsdv);
    result.add(bdv);
    return result;
  }

  private static PidElement independentSensor(PidDesignContext context,
      ProcessEquipmentInterface equipment, String function, String purpose, String description,
      String measuredVariable, String requirementToken) {
    PidElement sensor = PidRuleSupport.element(context, equipment, function, purpose,
        PidElementType.MEASUREMENT, RULE_ID, description,
        "Provide an independent initiating element for the proposed protective function")
        .attribute("measuredVariable", measuredVariable).attribute("system", "SIS")
        .attribute("independence", "VERIFICATION_REQUIRED");
    PidRuleSupport.trace(sensor, context, equipment, requirementToken);
    return sensor;
  }

  private static PidElement trip(PidDesignContext context, ProcessEquipmentInterface equipment,
      String function, String purpose, String description, String rationale, String requirementToken) {
    PidElement trip = PidRuleSupport.element(context, equipment, function, purpose,
        PidElementType.TRIP, RULE_ID, description, rationale).attribute("system", "SIS")
        .attribute("setPoint", "SRS_INPUT_REQUIRED").attribute("silTarget", "LOPA_INPUT_REQUIRED")
        .attribute("voting", "SRS_INPUT_REQUIRED");
    PidRuleSupport.trace(trip, context, equipment, requirementToken);
    return trip;
  }
}
