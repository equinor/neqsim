package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** End-to-end proposal tests for the standard control and instrumentation rule profile. */
public class ControlInstrumentationPidSynthesisTest extends NeqSimTest {
  @Test
  void proposesTraceableLoopsForMajorProcessEquipment() {
    EngineeringProject project = createProject();
    PidDesignModel model = PidDesignSynthesizer.synthesize(project,
        new PidDesignBasis("NORSOK-CONTROL-AND-INSTRUMENTATION", "20"),
        NorsokPidRuleCatalog.controlAndInstrumentation());

    assertEquals(21, model.getElements().size());
    assertEquals(11, model.getElementsByType(PidElementType.MEASUREMENT).size());
    assertEquals(5, model.getElementsByType(PidElementType.CONTROLLER).size());
    assertEquals(5, model.getElementsByType(PidElementType.CONTROL_VALVE).size());
    assertEquals(7, model.getElementsForEquipment("20-VG-001").size());
    assertEquals(6, model.getElementsForEquipment("20-KA-001").size());

    Set<String> tags = new HashSet<String>();
    for (PidElement element : model.getElements()) {
      assertTrue(tags.add(element.getTag()), "duplicate tag " + element.getTag());
      assertEquals(PidProposalStatus.REVIEW_REQUIRED, element.getStatus());
      assertFalse(element.getRuleId().isEmpty());
    }

    PidElement antisurgeValve = findByFunction(model, "ASCV");
    assertNotNull(antisurgeValve);
    assertEquals("FAIL_OPEN_PROPOSAL", antisurgeValve.getAttributes().get("failurePosition"));
    assertFalse(antisurgeValve.getRequirementIds().isEmpty());

    PidElement separatorPressure = findByPurpose(model, "PRESSURE-MEASUREMENT", "20-VG-001");
    assertNotNull(separatorPressure);
    assertFalse(separatorPressure.getLineTag().isEmpty());
    assertFalse(separatorPressure.getRequirementIds().isEmpty());
    assertTrue(separatorPressure.getStandardReferences().stream()
        .anyMatch(reference -> reference.contains("IEC 62424")));
  }

  private static PidElement findByFunction(PidDesignModel model, String function) {
    for (PidElement element : model.getElements()) {
      if (element.getTag().contains("-" + function + "-")) {
        return element;
      }
    }
    return null;
  }

  private static PidElement findByPurpose(PidDesignModel model, String purpose, String equipmentTag) {
    for (PidElement element : model.getElementsForEquipment(equipmentTag)) {
      if (purpose.equals(element.getAttributes().get("proposalPurpose"))) {
        return element;
      }
    }
    return null;
  }

  private static EngineeringProject createProject() {
    SystemInterface fluid = new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("n-heptane", 0.10);
    Stream feed = new Stream("20-FEED-001", fluid);
    Separator separator = new Separator("20-VG-001", feed);
    Compressor compressor = new Compressor("20-KA-001", separator.getGasOutStream());
    Cooler cooler = new Cooler("20-HA-001", compressor.getOutletStream());
    Pump pump = new Pump("20-PA-001", separator.getLiquidOutStream());
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(cooler);
    process.add(pump);
    return NorsokOffshoreEngineeringBuilder.from("Complete control proposal", process)
        .projectId("PID-CONTROL-TEST").build();
  }
}
