package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.ReliefDeviceDesignInput;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;
import neqsim.process.safety.overpressure.ProtectedItem;
import neqsim.process.safety.overpressure.ReliefCause;
import neqsim.process.safety.overpressure.ReliefPhase;
import neqsim.process.safety.overpressure.ReliefScenario;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Verifies governed safeguarding proposals and their link to overpressure-study evidence. */
public class SafeguardingPidSynthesisTest extends NeqSimTest {
  @Test
  void completeProfileAddsTripsIsolationBlowdownAndRelief() {
    EngineeringProject project = createProject();
    PidDesignModel model = PidDesignSynthesizer.synthesize(project,
        new PidDesignBasis("NORSOK-COMPLETE-PID-PROPOSALS", "20"),
        NorsokPidRuleCatalog.completeProposals());

    assertEquals(58, model.getElements().size());
    assertEquals(10, model.getElementsByType(PidElementType.TRIP).size());
    assertEquals(5, model.getElementsByType(PidElementType.SHUTDOWN_VALVE).size());
    assertEquals(2, model.getElementsByType(PidElementType.BLOWDOWN_VALVE).size());
    assertEquals(2, model.getElementsByType(PidElementType.CHECK_VALVE).size());
    assertEquals(1, model.getElementsByType(PidElementType.SAFETY_RELIEF_VALVE).size());

    PidElement psv = model.getElementsByType(PidElementType.SAFETY_RELIEF_VALVE).get(0);
    assertEquals("20-PSV-101", psv.getTag());
    assertEquals(Double.valueOf(68.0), psv.getAttributes().get("setPressureBara"));
    assertEquals(Integer.valueOf(1), psv.getAttributes().get("scenarioCount"));
    assertEquals("BLOCKED_OUTLET", psv.getAttributes().get("governingCause"));
    assertFalse(psv.getRequirementIds().isEmpty());
    assertEquals(1, psv.getConnectedElementIds().size());

    PidElement compressorShutdown = find(model, "20-KA-001", "UNIT-SHUTDOWN");
    assertNotNull(compressorShutdown);
    assertEquals(PidElementType.SAFETY_FUNCTION, compressorShutdown.getType());
    assertEquals(3, compressorShutdown.getConnectedElementIds().size());

    PidElement separatorPressureTrip = find(model, "20-VG-001", "PRESSURE-HH-TRIP");
    assertNotNull(separatorPressureTrip);
    assertEquals("SIS", separatorPressureTrip.getAttributes().get("system"));
    assertFalse(separatorPressureTrip.getRequirementIds().isEmpty());
    for (PidElement element : model.getElements()) {
      assertEquals(PidProposalStatus.REVIEW_REQUIRED, element.getStatus());
    }
    assertEquals(Boolean.FALSE, model.toMap().get("fitnessForConstruction"));
  }

  private static PidElement find(PidDesignModel model, String equipmentTag, String purpose) {
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
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Complete safeguarding proposal", process)
        .projectId("PID-SAFEGUARD-TEST").build();
    ProtectedItem protectedSeparator =
        new ProtectedItem("20-VG-001", 70.0).setReliefSetPressureBara(68.0);
    ReliefScenario blockedOutlet = new ReliefScenario.Builder("Blocked outlet", ReliefCause.BLOCKED_OUTLET)
        .phase(ReliefPhase.VAPOUR).reliefRateKgPerS(3.0).reliefTemperatureK(330.0)
        .molarMassKgPerMol(0.020).compressibility(0.95).specificHeatRatio(1.25).build();
    project.addOverpressureStudy(
        new OverpressureProtectionStudy(protectedSeparator).addScenario(blockedOutlet));
    project.addReliefDeviceDesignInput(new ReliefDeviceDesignInput("20-PSV-101", "20-VG-001")
        .setSelectedOrificeAreaIn2(4.34).setInletPiping(0.08, 2.0, 1.5)
        .setOutletPiping(0.15, 20.0, 4.0).setConcurrencyGroup("FIRE-ZONE-A")
        .setEvidenceReference("PRELIMINARY-PSV-DATASHEET"));
    return project;
  }
}
