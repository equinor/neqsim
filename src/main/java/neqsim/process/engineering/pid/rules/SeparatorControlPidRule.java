package neqsim.process.engineering.pid.rules;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.pid.PidDesignContext;
import neqsim.process.engineering.pid.PidDesignRule;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;

/** Proposes pressure, level and temperature instrumentation for separators and scrubbers. */
public final class SeparatorControlPidRule implements PidDesignRule {
  public static final String RULE_ID = "PID-CONTROL-SEPARATOR";

  @Override
  public String getId() {
    return RULE_ID;
  }

  @Override
  public int getOrder() {
    return 100;
  }

  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Separator;
  }

  @Override
  public List<PidElement> propose(PidDesignContext context, ProcessEquipmentInterface equipment) {
    List<PidElement> result = new ArrayList<PidElement>();
    PidElement pt = PidRuleSupport
        .onOutlet(
            PidRuleSupport.element(context, equipment, "PT", "PRESSURE-MEASUREMENT", PidElementType.MEASUREMENT,
                RULE_ID, "Separator pressure transmitter", "Measure vessel pressure for control and protection review"),
            equipment)
        .attribute("measuredVariable", "PRESSURE").attribute("functions", "IT");
    PidElement pic = PidRuleSupport.element(context, equipment, "PIC", "PRESSURE-CONTROLLER", PidElementType.CONTROLLER,
        RULE_ID, "Separator pressure indicating controller",
        "Maintain the approved separator operating-pressure envelope");
    PidElement pcv = PidRuleSupport
        .onOutlet(
            PidRuleSupport.element(context, equipment, "PCV", "PRESSURE-CONTROL-VALVE", PidElementType.CONTROL_VALVE,
                RULE_ID, "Separator pressure control valve", "Modulate gas outlet flow to control pressure"),
            equipment)
        .attribute("failurePosition", "HAZOP_INPUT_REQUIRED").attribute("sizingStatus", "CALCULATION_REQUIRED");
    connect(pt, pic, pcv);
    PidRuleSupport.trace(pt, context, equipment, "PRESSURE");
    PidRuleSupport.trace(pic, context, equipment, "PRESSURE-CONTROL");
    PidRuleSupport.trace(pcv, context, equipment, "PRESSURE-CONTROL");

    PidElement lt = PidRuleSupport
        .element(context, equipment, "LT", "LEVEL-MEASUREMENT", PidElementType.MEASUREMENT, RULE_ID,
            "Separator level transmitter", "Measure liquid inventory for carry-over and gas-blowby prevention")
        .attribute("measuredVariable", "LEVEL").attribute("functions", "IT").attribute("sensingLocation", "VESSEL");
    PidElement lic = PidRuleSupport.element(context, equipment, "LIC", "LEVEL-CONTROLLER", PidElementType.CONTROLLER,
        RULE_ID, "Separator level indicating controller",
        "Maintain liquid level within the approved operating envelope");
    PidElement lcv = PidRuleSupport
        .element(context, equipment, "LCV", "LEVEL-CONTROL-VALVE", PidElementType.CONTROL_VALVE, RULE_ID,
            "Separator liquid level control valve", "Modulate liquid outlet flow to control level")
        .line(((Separator) equipment).getLiquidOutStream().getName())
        .attribute("failurePosition", "HAZOP_INPUT_REQUIRED").attribute("sizingStatus", "CALCULATION_REQUIRED");
    connect(lt, lic, lcv);
    PidRuleSupport.trace(lt, context, equipment, "LEVEL");
    PidRuleSupport.trace(lic, context, equipment, "LEVEL-CONTROL");
    PidRuleSupport.trace(lcv, context, equipment, "LEVEL-CONTROL");

    PidElement tt = PidRuleSupport
        .onOutlet(
            PidRuleSupport.element(context, equipment, "TT", "TEMPERATURE-MEASUREMENT", PidElementType.MEASUREMENT,
                RULE_ID, "Separator outlet temperature transmitter", "Expose process temperature for monitoring"),
            equipment)
        .attribute("measuredVariable", "TEMPERATURE").attribute("functions", "IT");
    result.add(pt);
    result.add(pic);
    result.add(pcv);
    result.add(lt);
    result.add(lic);
    result.add(lcv);
    result.add(tt);
    if (equipment instanceof ThreePhaseSeparator) {
      addInterfaceLoop(context, (ThreePhaseSeparator) equipment, result);
    }
    return result;
  }

  /**
   * Adds a distinct oil/water-interface loop whose final element belongs to the water outlet.
   *
   * @param context controlled proposal context
   * @param separator source three-phase separator
   * @param result proposal collection
   */
  private static void addInterfaceLoop(PidDesignContext context, ThreePhaseSeparator separator,
      List<PidElement> result) {
    PidElement sensor = PidRuleSupport
        .element(context, separator, "LT", "INTERFACE-LEVEL-MEASUREMENT", PidElementType.MEASUREMENT, RULE_ID,
            "Separator oil/water interface transmitter",
            "Measure the distinct oil/water interface for water-outlet control")
        .attribute("measuredVariable", "INTERFACE_LEVEL").attribute("sensingLocation", "VESSEL");
    PidElement controller = PidRuleSupport.element(context, separator, "LIC", "INTERFACE-LEVEL-CONTROLLER",
        PidElementType.CONTROLLER, RULE_ID, "Separator interface level controller",
        "Maintain the project-defined oil/water interface envelope");
    PidElement valve = PidRuleSupport
        .element(context, separator, "LCV", "INTERFACE-LEVEL-CONTROL-VALVE", PidElementType.CONTROL_VALVE, RULE_ID,
            "Separator water-outlet control valve",
            "Manipulate the water outlet separately from total liquid-level control")
        .line(separator.getWaterOutStream().getName()).attribute("failurePosition", "HAZOP_INPUT_REQUIRED")
        .attribute("sizingStatus", "CALCULATION_REQUIRED");
    connect(sensor, controller, valve);
    PidRuleSupport.trace(sensor, context, separator, "INTERFACE");
    PidRuleSupport.trace(controller, context, separator, "INTERFACE");
    PidRuleSupport.trace(valve, context, separator, "INTERFACE");
    result.add(sensor);
    result.add(controller);
    result.add(valve);
  }

  private static void connect(PidElement sensor, PidElement controller, PidElement valve) {
    sensor.connect(controller.getId());
    controller.connect(valve.getId());
  }
}
