package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies shared drawing/exchange attachment semantics against actual process identities.
 *
 * @author NeqSim development team
 * @version 1.0
 */
public class PidPhysicalProjectionTest extends NeqSimTest {
  /** Verifies actual measurement bindings, separate phase paths and explicit unresolved auxiliaries. */
  @Test
  void resolvesActualSensorAndValveIdentitiesWithoutInventedAuxiliaries() {
    ProcessSystem process = process();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Projection test", process, "2.0.1")
        .projectId("PID-PROJECTION").registerProposedInstruments(false).build().setRevision("A");
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "TEST");
    pid.add(new PidElement("sensor", "PT-DRAWING", PidElementType.MEASUREMENT).equipment("V-101").line("GAS")
        .attribute("measuredVariable", "PRESSURE"));
    pid.add(new PidElement("level", "LT-DRAWING", PidElementType.MEASUREMENT).equipment("V-101")
        .attribute("sensingLocation", "VESSEL").attribute("measuredVariable", "LEVEL"));
    pid.add(new PidElement("gas-valve", "PCV-DRAWING", PidElementType.CONTROL_VALVE).equipment("V-101").line("GAS"));
    pid.add(
        new PidElement("liquid-valve", "LCV-PROPOSAL", PidElementType.CONTROL_VALVE).equipment("V-101").line("LIQUID"));
    pid.add(new PidElement("bdv", "BDV-PROPOSAL", PidElementType.BLOWDOWN_VALVE).equipment("V-101"));
    PidSimulationBindings bindings = PidSimulationBindings.from(project, pid).bindMeasurement("PT-DRAWING", "PT-LIVE")
        .bindMeasurement("LT-DRAWING", "LT-LIVE").bindEquipment("PCV-DRAWING", "PCV-LIVE");
    PidPhysicalProjection result = PidPhysicalProjection.resolve(process, pid, bindings);
    assertEquals(PidPhysicalProjection.Kind.SENSING, result.getAttachments().get("PT-DRAWING").getKind());
    assertEquals("OUTLET", result.getAttachments().get("PT-DRAWING").getDirection());
    assertEquals("GAS", result.getAttachments().get("PT-DRAWING").getStreamTag());
    assertEquals("VESSEL", result.getAttachments().get("LT-DRAWING").getDirection());
    assertEquals(PidPhysicalProjection.Kind.EXISTING_EQUIPMENT, result.getAttachments().get("PCV-DRAWING").getKind());
    assertEquals("PCV-LIVE", result.getAttachments().get("PCV-DRAWING").getEquipmentTag());
    assertEquals(PidPhysicalProjection.Kind.INLINE_VALVE, result.getAttachments().get("LCV-PROPOSAL").getKind());
    assertEquals("LIQUID", result.getAttachments().get("LCV-PROPOSAL").getStreamTag());
    assertEquals(PidPhysicalProjection.Kind.UNRESOLVED, result.getAttachments().get("BDV-PROPOSAL").getKind());
    assertTrue(result.getAttachments().get("BDV-PROPOSAL").getReason().contains("EXPLICIT_AUXILIARY"));
    assertEquals(2, process.getMeasurementDevices().size());
  }

  /** Verifies nozzle links are physical paths rather than fabricated measurement signals. */
  @Test
  void retainsDirectedFunctionalLinksAndSeparatesSourceNozzlePaths() {
    ProcessSystem process = process();
    PidDesignModel pid = new PidDesignModel("PID-PROJECTION", "TEST");
    pid.add(new PidElement("in", "NZ-1", PidElementType.NOZZLE).equipment("V-101").line("FEED")
        .attribute("boundaryDirection", "INLET").connect("out"));
    pid.add(new PidElement("out", "NZ-2", PidElementType.NOZZLE).equipment("V-101").line("GAS")
        .attribute("boundaryDirection", "OUTLET"));
    pid.add(new PidElement("pt", "PT-1", PidElementType.MEASUREMENT).equipment("V-101").line("GAS").connect("pic"));
    pid.add(new PidElement("pic", "PIC-1", PidElementType.CONTROLLER).equipment("V-101"));
    pid.add(new PidElement("psv", "PSV-1", PidElementType.SAFETY_RELIEF_VALVE).equipment("V-101").connect("flare"));
    pid.add(new PidElement("flare", "FLARE-1", PidElementType.OFF_PAGE_CONNECTOR).equipment("V-101"));
    PidPhysicalProjection result = PidPhysicalProjection.resolve(process, pid, null);
    List<java.util.Map<String, String>> links = result.getSignals();
    assertEquals(1, links.size());
    assertEquals("PT-1", links.get(0).get("sourceTag"));
    assertEquals("PIC-1", links.get(0).get("targetTag"));
    assertFalse(result.toJson().isEmpty());
  }

  /** Verifies that actual piping ports and numerical helper identities do not become fake equipment nozzles. */
  @Test
  void topologyRuleDoesNotInventNozzlesOnPipingOrNumericalHelpers() {
    ProcessSystem process = process();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Nozzle test", process, "2.0.1")
        .projectId("NOZZLE-TEST").registerProposedInstruments(false).build();
    PidDesignContext context = new PidDesignContext(project, new PidDesignBasis("TEST", "20"));
    neqsim.process.engineering.pid.rules.ProcessTopologyPidRule rule = new neqsim.process.engineering.pid.rules.ProcessTopologyPidRule();
    assertTrue(rule.supports(process.getUnit("V-101")));
    assertEquals(3, rule.propose(context, process.getUnit("V-101")).size());
    for (neqsim.process.equipment.ProcessEquipmentInterface unit : new neqsim.process.equipment.ProcessEquipmentInterface[] {
        process.getUnit("FEED"), process.getUnit("PCV-LIVE"), new neqsim.process.equipment.splitter.Splitter("T-101"),
        new neqsim.process.equipment.util.Recycle("Recycle"),
        new neqsim.process.equipment.util.Calculator("Calculator"),
        new neqsim.process.equipment.util.Adjuster("Adjuster")}) {
      assertFalse(rule.supports(unit));
      assertTrue(rule.propose(context, unit).isEmpty());
    }
  }

  /** Verifies cooler utility-loss sensing never silently reads the hot process inlet. */
  @Test
  void coolerUtilityFlowNeedsExplicitUtilitySensingEvidence() {
    ProcessSystem process = process();
    neqsim.process.equipment.heatexchanger.Cooler cooler = new neqsim.process.equipment.heatexchanger.Cooler("E-101",
        ((Separator) process.getUnit("V-101")).getGasOutStream());
    cooler.setOutTemperature(303.15);
    process.add(cooler);
    process.run();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Utility FT", process, "2.0.1")
        .projectId("UTILITY-FT").registerProposedInstruments(false).build();
    PidDesignContext context = new PidDesignContext(project, new PidDesignBasis("TEST", "20"));
    neqsim.process.engineering.pid.rules.ThermalSafeguardingPidRule rule = new neqsim.process.engineering.pid.rules.ThermalSafeguardingPidRule();
    PidElement utilityFlow = null;
    for (PidElement proposal : rule.propose(context, cooler)) {
      if (proposal.getType() == PidElementType.MEASUREMENT
          && "FLOW".equals(proposal.getAttributes().get("measuredVariable"))) {
        utilityFlow = proposal;
      }
    }
    assertTrue(utilityFlow != null);
    assertEquals("", utilityFlow.getLineTag());
    assertEquals("UTILITY_SUPPLY", utilityFlow.getAttributes().get("sensingLocation"));
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "TEST");
    pid.add(utilityFlow);
    PidPhysicalProjection missing = PidPhysicalProjection.resolve(process, pid, null);
    assertEquals(PidPhysicalProjection.Kind.UNRESOLVED, missing.getAttachments().get(utilityFlow.getTag()).getKind());
    assertEquals("EXPLICIT_UTILITY_SENSING_LOCATION_REQUIRED",
        missing.getAttachments().get(utilityFlow.getTag()).getReason());
    utilityFlow.attribute("physicalSourceEquipmentTag", "E-101").attribute("physicalSourceNozzleTag", "CW-IN")
        .attribute("physicalSourcePortRole", "UTILITY").attribute("designEvidenceReference", "SYNTHETIC-CW-DESIGN");
    PidPhysicalProjection supplied = PidPhysicalProjection.resolve(process, pid, null);
    assertEquals("DESIGNED_NOZZLE", supplied.getAttachments().get(utilityFlow.getTag()).getDirection());
    assertEquals("CW-IN", supplied.getAttachments().get(utilityFlow.getTag()).getSourceNozzleTag());
    assertEquals("", supplied.getAttachments().get(utilityFlow.getTag()).getStreamTag());
  }

  /**
   * Creates a solved two-phase separator and named measurement/valve objects.
   *
   * @return actual source process
   */
  private static ProcessSystem process() {
    SystemSrkEos fluid = new SystemSrkEos(308.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator separator = new Separator("V-101", feed);
    separator.getGasOutStream().setName("GAS");
    separator.getLiquidOutStream().setName("LIQUID");
    ThrottlingValve valve = new ThrottlingValve("PCV-LIVE", separator.getGasOutStream());
    valve.setOutletPressure(30.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(valve);
    process.add(new PressureTransmitter("PT-LIVE", separator.getGasOutStream()));
    process.add(new LevelTransmitter("LT-LIVE", separator));
    process.run();
    return process;
  }
}
