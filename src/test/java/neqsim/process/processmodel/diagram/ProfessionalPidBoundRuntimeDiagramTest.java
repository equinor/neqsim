package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.pid.NorsokPidRuleCatalog;
import neqsim.process.engineering.pid.PidDesignBasis;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidDesignSynthesizer;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.engineering.pid.PidPhysicalProjection;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks physical placement when native P&amp;ID proposals acquire real runtime bindings.
 *
 * @author NeqSim development team
 * @version 1.0
 */
class ProfessionalPidBoundRuntimeDiagramTest {
  /** Bound inlet hardware keeps its primary owner's port context and actual equipment identity. */
  @Test
  void retainsOwnerPortForBoundInletValve() {
    Fixture fixture = fixture();
    PidPhysicalProjection.Attachment inlet = PidPhysicalProjection
        .resolve(fixture.project.getEngineeringProcessSystem(), fixture.pid, fixture.bindings).getAttachments()
        .get("20-ESDV-001");
    assertEquals(PidPhysicalProjection.Kind.EXISTING_EQUIPMENT, inlet.getKind());
    assertEquals("20-ESDV-001", inlet.getEquipmentTag());
    assertEquals("INLET", inlet.getDirection());
    assertEquals(0, inlet.getStreamIndex());
    assertEquals("20-FEED-V101", inlet.getStreamTag());
  }

  /**
   * A real inlet ESD valve and three gas-line sensors remain visible without crossing vessel sensing routes.
   */
  @Test
  @SuppressWarnings("unchecked")
  void rendersBoundInletAndIndependentGasSensorWithoutCrossing() {
    Fixture fixture = fixture();
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        fixture.project.getEngineeringProcessSystem(), fixture.project.getProjectId(), fixture.project.getRevision(),
        "PID-RUNTIME", fixture.project.getName(), ContentProfile.PID);
    ProfessionalPidDiagramRenderer renderer = new ProfessionalPidDiagramRenderer(fixture.project, fixture.pid,
        fixture.bindings, documents, new EngineeringDiagramConventionRegister());
    NativeEngineeringDiagramRenderer.Result result = renderer.render();
    assertTrue(result.isComplete(), new com.google.gson.Gson().toJson(result.getDiagnostics()));
    assertTrue(renderer.getContentReportJson().contains("REQUIRES_CONTROLLED_STANDARD_AND_DISCIPLINE_REVIEW"));
    Map<String, Object> main = renderer.getLayoutSpecifications().values().iterator().next();
    List<Map<String, Object>> nodes = (List<Map<String, Object>>) main.get("nodes");
    assertTrue(nodes.stream().anyMatch(node -> "20-ESDV-001".equals(node.get("tag"))));
    assertTrue(nodes.stream().anyMatch(node -> "20-PT-002".equals(node.get("tag"))));
    assertTrue(nodes.stream().anyMatch(node -> "20-LT-001".equals(node.get("tag"))));
    // The real gas control valve stays downstream of the separately proposed
    // vessel-outlet isolation valve, matching the native material source graph.
    double controlX = x(nodes, "20-PCV-001");
    double isolationX = x(nodes, "20-ESDV-002");
    assertTrue(controlX > isolationX);
  }

  /** The normal-operating PFD retains actual inlet hardware and its true upstream feed. */
  @Test
  @SuppressWarnings("unchecked")
  void rendersSourcePfdWithActualUpstreamValve() {
    Fixture fixture = fixture();
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        fixture.project.getEngineeringProcessSystem(), fixture.project.getProjectId(), fixture.project.getRevision(),
        "PID-RUNTIME", fixture.project.getName(), ContentProfile.PFD);
    ProfessionalPidDiagramRenderer renderer = new ProfessionalPidDiagramRenderer(fixture.project, fixture.pid,
        fixture.bindings, documents, new EngineeringDiagramConventionRegister());
    NativeEngineeringDiagramRenderer.Result result = renderer.renderProcessFlowOverview();
    assertTrue(result.isComplete(), new com.google.gson.Gson().toJson(result.getDiagnostics()));
    Map<String, Object> main = renderer.getLayoutSpecifications().values().iterator().next();
    List<Map<String, Object>> nodes = (List<Map<String, Object>>) main.get("nodes");
    assertTrue(nodes.stream().anyMatch(node -> "20-ESDV-001".equals(node.get("tag"))));
    assertTrue(nodes.stream().anyMatch(node -> "pfd-in:20-FEED-001".equals(node.get("tag"))));
    assertTrue(x(nodes, "pfd-in:20-FEED-001") < x(nodes, "20-ESDV-001"));
    assertTrue(x(nodes, "20-ESDV-001") < x(nodes, "20-V-101"));
  }

  /**
   * Returns the actual horizontal position of one represented tag.
   *
   * @param nodes actual rendered nodes
   * @param tag physical or instrument identity
   * @return left coordinate
   */
  private static double x(List<Map<String, Object>> nodes, String tag) {
    for (Map<String, Object> node : nodes) {
      if (tag.equals(node.get("tag"))) {
        return ((double[]) node.get("bbox"))[0];
      }
    }
    throw new AssertionError("Missing represented tag " + tag);
  }

  /**
   * Builds one source-connected separator with actual pressure/level sensing and physical valves.
   *
   * @return controlled fixture
   */
  private static Fixture fixture() {
    SystemSrkEos fluid = new SystemSrkEos(308.15, 45.0);
    fluid.addComponent("methane", .85);
    fluid.addComponent("n-heptane", .15);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("20-FEED-001", fluid);
    feed.setFlowRate(2000.0, "kg/hr");
    ESDValve inlet = new ESDValve("20-ESDV-001", feed);
    inlet.setOutletPressure(40.0);
    inlet.getOutletStream().setName("20-FEED-V101");
    Separator separator = new Separator("20-V-101", inlet.getOutletStream());
    separator.getGasOutStream().setName("20-S-02");
    separator.getLiquidOutStream().setName("20-S-07");
    ThrottlingValve gas = new ThrottlingValve("20-PCV-001", separator.getGasOutStream());
    gas.setOutletPressure(35.0);
    gas.getOutletStream().setName("20-S-02-TO-K101");
    ThrottlingValve liquid = new ThrottlingValve("20-LCV-001", separator.getLiquidOutStream());
    liquid.setOutletPressure(20.0);
    liquid.getOutletStream().setName("20-S-09");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(inlet);
    process.add(separator);
    process.add(gas);
    process.add(liquid);
    process.add(new PressureTransmitter("20-PT-001", separator.getGasOutStream()));
    process.add(new PressureTransmitter("20-PT-002", separator.getGasOutStream()));
    process.add(new LevelTransmitter("20-LT-001", separator));
    process.run();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Bound runtime separator", process, "2.0.1")
        .projectId("PID-RUNTIME").registerProposedInstruments(false).build().setRevision("B");
    PidDesignModel pid = PidDesignSynthesizer.synthesize(project, new PidDesignBasis("RUNTIME-PROPOSALS", "20"),
        NorsokPidRuleCatalog.completeProposals());
    for (PidElement element : pid.getElements()) {
      if (element.getType() == PidElementType.SAFETY_RELIEF_VALVE) {
        element.attribute("physicalSourceEquipmentTag", "20-V-101").attribute("physicalSourceStreamTag", "20-S-02")
            .attribute("destinationTag", "20-FLARE-001");
      } else if (element.getType() == PidElementType.BLOWDOWN_VALVE) {
        element.attribute("physicalSourceEquipmentTag", "20-V-101").attribute("physicalSourceStreamTag", "20-S-02")
            .attribute("destinationTag", "SYN-FLARE-HEADER");
      }
    }
    PidSimulationBindings bindings = PidSimulationBindings.from(project, pid)
        .bindEquipment("20-ESDV-001", "20-ESDV-001").bindEquipment("20-PCV-001", "20-PCV-001")
        .bindEquipment("20-LCV-001", "20-LCV-001").bindMeasurement("20-PT-001", "20-PT-001")
        .bindMeasurement("20-PT-002", "20-PT-002").bindMeasurement("20-LT-001", "20-LT-001");
    return new Fixture(project, pid, bindings);
  }

  /**
   * Immutable source and identity fixture.
   *
   * @author NeqSim development team
   * @version 1.0
   */
  private static final class Fixture {
    private final EngineeringProject project;
    private final PidDesignModel pid;
    private final PidSimulationBindings bindings;

    /**
     * Retains the same project/model/binding revision.
     *
     * @param project source project
     * @param pid proposal model
     * @param bindings native runtime bindings
     */
    private Fixture(EngineeringProject project, PidDesignModel pid, PidSimulationBindings bindings) {
      this.project = project;
      this.pid = pid;
      this.bindings = bindings;
    }
  }
}
