package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Retains actual control-loop identities in governed dynamic scenario scopes.
 *
 * @author NeqSim development team
 * @version 1.0
 */
class EngineeringProjectDynamicControllerScopeTest {
  /** A standalone registered controller is an actual executable scenario target. */
  @Test
  void acceptsRegisteredControllerScope() {
    ProcessSystem process = new ProcessSystem();
    process.add(new ControllerDeviceBaseClass("PIC-101"));
    EngineeringProject project = project(process, "PIC-101");
    assertEquals(0, unknownTagCount(project));
  }

  /** Attached controllers and their exposed input sensors match P&ID binding resolution. */
  @Test
  void acceptsAttachedControllerAndItsInput() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 40.0);
    fluid.addComponent("methane", 1.0);
    Stream feed = new Stream("FEED", fluid);
    ThrottlingValve valve = new ThrottlingValve("PCV-101", feed);
    PressureTransmitter transmitter = new PressureTransmitter("PT-101", feed);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("PIC-101");
    controller.setTransmitter(transmitter);
    valve.setController(controller);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(valve);
    EngineeringProject project = project(process, "PIC-101", "PT-101");
    assertEquals(0, unknownTagCount(project));
  }

  /** Unregistered names remain blocking errors even when they resemble a real controller tag. */
  @Test
  void rejectsUnknownControllerLikeScope() {
    ProcessSystem process = new ProcessSystem();
    process.add(new ControllerDeviceBaseClass("PIC-101"));
    EngineeringProject project = project(process, "PIC-101", "PIC-UNREGISTERED");
    assertEquals(1, unknownTagCount(project));
  }

  /**
   * Builds a controlled definition without executing its placeholder response.
   *
   * @param process actual executable source
   * @param tags declared scope tags
   * @return governed project
   */
  private static EngineeringProject project(ProcessSystem process, String... tags) {
    EngineeringProject project = new EngineeringProject("CONTROL-SCOPE", "Control scope", process,
        new EngineeringDesignBasis());
    DynamicSafetyScenario.Builder builder = DynamicSafetyScenario.builder("SCOPE-CASE", "Scope case")
        .initiatingEvent("NOOP-v1", working -> {
        }).addLogic("PLACEHOLDER-v1", working -> new ESDLogic("Placeholder"))
        .addCriterion(DynamicScenarioCriterion
            .builder("observed", "Placeholder observation", "boolean", "CONSTANT-v1", working -> 1.0)
            .acceptanceRange(1.0, 1.0).build());
    for (String tag : tags) {
      builder.addEquipmentTag(tag);
    }
    project.addDynamicSafetyScenario(builder.build());
    return project;
  }

  /**
   * Counts only native errors for targets absent from the actual executable namespace.
   *
   * @param project governed source
   * @return unknown dynamic target count
   */
  private static long unknownTagCount(EngineeringProject project) {
    return project.validate().getFindings().stream().filter(finding -> "ENG-DYNAMIC-004".equals(finding.getCode()))
        .count();
  }
}
