package neqsim.process.engineering.pid.rules;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.ReliefDeviceDesignInput;
import neqsim.process.engineering.pid.PidDesignContext;
import neqsim.process.engineering.pid.PidDesignRule;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;
import neqsim.process.safety.overpressure.ReliefScenario;

/** Proposes relief devices only when a governed requirement or overpressure study exists. */
public final class PressureReliefPidRule implements PidDesignRule {
  public static final String RULE_ID = "PID-SAFEGUARD-PRESSURE-RELIEF";

  @Override
  public String getId() {
    return RULE_ID;
  }

  @Override
  public int getOrder() {
    return 900;
  }

  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return true;
  }

  @Override
  public List<PidElement> propose(PidDesignContext context, ProcessEquipmentInterface equipment) {
    List<PidElement> result = new ArrayList<PidElement>();
    OverpressureProtectionStudy study = study(context, equipment.getName());
    boolean hasRequirement = !context
        .requirements(equipment.getName(), neqsim.process.engineering.EngineeringRequirement.Type.RELIEF).isEmpty();
    if (study == null && !hasRequirement) {
      return result;
    }
    ReliefDeviceDesignInput installed = installedInput(context, equipment.getName());
    String tag;
    if (installed == null) {
      tag = context.getTagAllocator().allocate("PSV", equipment.getName() + "-PRESSURE-RELIEF");
    } else {
      tag = installed.getDeviceTag();
      context.getTagAllocator().reserve(tag);
    }
    PidElement psv = new PidElement("pid:" + tag.toLowerCase().replaceAll("[^a-z0-9]+", "-"), tag,
        PidElementType.SAFETY_RELIEF_VALVE).equipment(equipment.getName()).description("Pressure safety valve")
        .provenance(RULE_ID, "Protect pressure-containing equipment against governed credible overpressure scenarios")
        .standard("API 520").standard("API 521").attribute("approvalStatus", "REVIEW_REQUIRED")
        .attribute("disposalDestination", "FLARE_OR_SAFE_LOCATION_REVIEW_REQUIRED");
    PidRuleSupport.trace(psv, context, equipment, "RELIEF", "ISOLATION-BLOWDOWN");
    if (study != null) {
      ReliefScenario governing = study.governingScenario();
      psv.attribute("mawpBara", Double.valueOf(study.getItem().getMaximumAllowableWorkingPressureBara()))
          .attribute("setPressureBara", Double.valueOf(study.getItem().getReliefSetPressureBara()))
          .attribute("scenarioCount", Integer.valueOf(study.getScenarios().size()))
          .attribute("governingScenario", governing == null ? "NO_CREDIBLE_SCENARIO" : governing.getName())
          .attribute("governingCause", governing == null ? "NOT_DEFINED" : governing.getCause().name())
          .attribute("sizingStatus",
              governing == null ? "SCENARIO_REVIEW_REQUIRED" : "CALCULATION_AND_VENDOR_REVIEW_REQUIRED");
    }
    if (installed != null) {
      psv.attribute("installedDesignInput", installed.toMap()).attribute("installedInputMissingFields",
          installed.getMissingFields());
    }
    PidElement flare = PidRuleSupport
        .element(context, equipment, "FLARE", "RELIEF-DESTINATION", PidElementType.OFF_PAGE_CONNECTOR, RULE_ID,
            "Relief and blowdown disposal connector",
            "Connect the device outlet to the qualified disposal-system model")
        .attribute("destination", "FLARE_NETWORK_MODEL_REQUIRED");
    psv.connect(flare.getId());
    result.add(psv);
    result.add(flare);
    return result;
  }

  private static OverpressureProtectionStudy study(PidDesignContext context, String equipmentTag) {
    for (OverpressureProtectionStudy candidate : context.getProject().getOverpressureStudies()) {
      if (candidate.getItem().getName().equals(equipmentTag)) {
        return candidate;
      }
    }
    return null;
  }

  private static ReliefDeviceDesignInput installedInput(PidDesignContext context, String equipmentTag) {
    for (ReliefDeviceDesignInput input : context.getProject().getReliefDeviceDesignInputs()) {
      if (input.getEquipmentTag().equals(equipmentTag)) {
        return input;
      }
    }
    return null;
  }
}
