package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.NeqSimTest;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.pid.NorsokPidRuleCatalog;
import neqsim.process.engineering.pid.PidDesignBasis;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidDesignSynthesizer;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression checks for native professional symbol semantics, pagination and explicit ISO-review gaps.
 *
 * @author NeqSim development team
 * @version 1.0
 */
public class ProfessionalPidDiagramRendererTest extends NeqSimTest {
  /** Verifies sensing touches a dished head and the straight vertical shell, rather than its bounding box. */
  @Test
  void sensingTapIntersectsActualVesselHull() {
    NativeEngineeringDiagramRenderer.Point horizontal = ProfessionalPidSymbolLibrary.vesselSensingTap(320, 280, 100, 60,
        262);
    assertEquals(364, horizontal.x, .15);
    assertEquals(262, horizontal.y, 1e-12);
    NativeEngineeringDiagramRenderer.Point vertical = ProfessionalPidSymbolLibrary.vesselSensingTap(320, 280, 46, 90,
        280);
    assertEquals(343, vertical.x, 1e-12);
    assertEquals(280, vertical.y, 1e-12);
  }

  /** Ensures source orientation is used instead of assigning all vessels a vertical generic box. */
  @Test
  void respectsVesselOrientationAndEquipmentFamily() {
    Separator separator = new Separator("20-V-101");
    assertEquals(ProfessionalPidSymbolLibrary.Family.HORIZONTAL_VESSEL, ProfessionalPidSymbolLibrary.family(separator));
    separator.setOrientation("vertical");
    assertEquals(ProfessionalPidSymbolLibrary.Family.VERTICAL_VESSEL, ProfessionalPidSymbolLibrary.family(separator));
    assertEquals(ProfessionalPidSymbolLibrary.Family.COMPRESSOR,
        ProfessionalPidSymbolLibrary.family(new Compressor("20-K-101")));
    assertEquals(ProfessionalPidSymbolLibrary.Family.CHECK_VALVE,
        ProfessionalPidSymbolLibrary.family(new neqsim.process.equipment.valve.CheckValve("NRV")));
    assertEquals(ProfessionalPidSymbolLibrary.Family.RELIEF_VALVE,
        ProfessionalPidSymbolLibrary.family(new neqsim.process.equipment.valve.SafetyReliefValve("PSV")));
    assertEquals(ProfessionalPidSymbolLibrary.Family.ISOLATION_VALVE,
        ProfessionalPidSymbolLibrary.family(new neqsim.process.equipment.valve.ESDValve("SDV")));
    assertEquals(ProfessionalPidSymbolLibrary.Family.UNMAPPED,
        ProfessionalPidSymbolLibrary.family(new neqsim.process.equipment.expander.Expander("EXP")));
  }

  /**
   * Exercises real source-connected generation and bounds every retained text envelope.
   */
  @Test
  @SuppressWarnings("unchecked")
  void rendersSourceConnectionsAndRetainsUnspecifiedDesignData() {
    EngineeringProject project = project();
    PidDesignModel pid = PidDesignSynthesizer.synthesize(project, new PidDesignBasis("COMPLETE", "20"),
        NorsokPidRuleCatalog.completeProposals());
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        project.getEngineeringProcessSystem(), project.getProjectId(), project.getRevision(), "PID-ISO-001",
        project.getName(), ContentProfile.PID);
    ProfessionalPidDiagramRenderer renderer = new ProfessionalPidDiagramRenderer(project, pid,
        PidSimulationBindings.from(project, pid), documents, new EngineeringDiagramConventionRegister());
    NativeEngineeringDiagramRenderer.Result result = renderer.render();
    assertTrue(result.isComplete(), new com.google.gson.Gson().toJson(result.getDiagnostics()));
    assertTrue(result.getSvgBySheetId().size() >= 2);
    String svg = result.getSvgBySheetId().values().iterator().next();
    assertTrue(svg.contains("width=\"841mm\""));
    assertTrue(svg.contains("data-symbol-family=\"HORIZONTAL_VESSEL\""));
    assertTrue(svg.contains("DN:HOLD"));
    assertTrue(svg.contains("FA:HOLD"));
    assertTrue(svg.contains("20-K-101 / PID-002"));
    assertFalse(svg.contains("Proposed functional links (not process piping)"));
    assertTrue(new String(result.getPdf(), java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF"));
    JsonObject report = JsonParser.parseString(renderer.getContentReportJson()).getAsJsonObject();
    assertFalse(report.get("fitnessForConstruction").getAsBoolean());
    assertEquals("REQUIRES_CONTROLLED_STANDARD_AND_DISCIPLINE_REVIEW",
        report.get("isoConformanceStatus").getAsString());
    assertEquals(pid.getElements().size(), report.getAsJsonArray("representedProposalTags").size());
    assertTrue(report.getAsJsonArray("holds").size() > 0);
    for (Map<String, Object> layout : renderer.getLayoutSpecifications().values()) {
      for (Map<String, Object> label : (java.util.List<Map<String, Object>>) layout.get("labels")) {
        double[] bounds = (double[]) label.get("bbox");
        assertTrue(bounds[0] >= 10);
        assertTrue(bounds[1] >= 10);
        assertTrue(bounds[0] + bounds[2] <= 831);
        assertTrue(bounds[1] + bounds[3] <= 584);
        assertTrue(((Number) label.get("font_size")).doubleValue() >= 2.5);
      }
    }
  }

  /** Tests that rendering output is deterministic for the same controlled source. */
  @Test
  void repeatsExactlyForSameSourceAndRevision() {
    EngineeringProject project = project();
    PidDesignModel pid = PidDesignSynthesizer.synthesize(project, new PidDesignBasis("COMPLETE", "20"),
        NorsokPidRuleCatalog.controlAndInstrumentation());
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        project.getEngineeringProcessSystem(), project.getProjectId(), project.getRevision(), "PID-ISO-001",
        project.getName(), ContentProfile.PID);
    PidSimulationBindings bindings = PidSimulationBindings.from(project, pid);
    ProfessionalPidDiagramRenderer first = new ProfessionalPidDiagramRenderer(project, pid, bindings, documents,
        new EngineeringDiagramConventionRegister());
    ProfessionalPidDiagramRenderer second = new ProfessionalPidDiagramRenderer(project, pid, bindings, documents,
        new EngineeringDiagramConventionRegister());
    assertEquals(first.render().getSvgBySheetId(), second.render().getSvgBySheetId());
    assertEquals(first.getContentReportJson(), second.getContentReportJson());
  }

  /** Verifies that the PFD shares native source symbols and omits proposed instrumentation. */
  @Test
  void rendersSourceOnlyPfdWithOperatingData() {
    EngineeringProject project = project();
    PidDesignModel pid = PidDesignSynthesizer.synthesize(project, new PidDesignBasis("COMPLETE", "20"),
        NorsokPidRuleCatalog.completeProposals());
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        project.getEngineeringProcessSystem(), project.getProjectId(), project.getRevision(), "PFD-ISO-001",
        project.getName(), ContentProfile.PFD);
    ProfessionalPidDiagramRenderer renderer = new ProfessionalPidDiagramRenderer(project, pid,
        PidSimulationBindings.from(project, pid), documents, new EngineeringDiagramConventionRegister());
    NativeEngineeringDiagramRenderer.Result result = renderer.renderProcessFlowOverview();
    assertTrue(result.isComplete(), new com.google.gson.Gson().toJson(result.getDiagnostics()));
    assertEquals(1, result.getSvgBySheetId().size());
    String svg = result.getSvgBySheetId().values().iterator().next();
    assertTrue(svg.contains("data-symbol-family=\"COMPRESSOR\""));
    assertTrue(svg.contains("shaft power"));
    assertTrue(svg.contains("STREAM DATA"));
    assertFalse(svg.contains("data-symbol-family=\"FIELD_INSTRUMENT\""));
  }

  /** Unsupported serial source equipment must be reported instead of becoming a false product boundary. */
  @Test
  void rejectsUnrepresentedSerialSourceValves() {
    ProcessSystem process = project().getEngineeringProcessSystem();
    neqsim.process.equipment.valve.ThrottlingValve first = new neqsim.process.equipment.valve.ThrottlingValve("V1",
        process.getUnit("20-K-101").getOutletStreams().get(0));
    neqsim.process.equipment.valve.ThrottlingValve second = new neqsim.process.equipment.valve.ThrottlingValve("V2",
        first.getOutletStream());
    process.add(first);
    process.add(second);
    process.run();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Serial valve regression", process, "2.0.1")
        .projectId("SERIAL-ISO").registerProposedInstruments(false).build();
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "SOURCE-COVERAGE");
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process,
        project.getProjectId(), project.getRevision(), "SOURCE-001", project.getName(), ContentProfile.PID);
    ProfessionalPidDiagramRenderer renderer = new ProfessionalPidDiagramRenderer(project, pid,
        PidSimulationBindings.from(project, pid), documents, new EngineeringDiagramConventionRegister());
    NativeEngineeringDiagramRenderer.Result result = renderer.render();
    assertFalse(result.isComplete());
    assertTrue(result.getDiagnostics().stream()
        .anyMatch(d -> "DRAWING_SOURCE_EQUIPMENT_UNREPRESENTED".equals(d.getCode()) && "V2".equals(d.getSubjectId())));
    renderer = new ProfessionalPidDiagramRenderer(project, pid, PidSimulationBindings.from(project, pid), documents,
        new EngineeringDiagramConventionRegister());
    result = renderer.renderProcessFlowOverview();
    assertFalse(result.isComplete());
    assertTrue(result.getDiagnostics().stream().anyMatch(d -> "PFD_UNREPRESENTED_EQUIPMENT".equals(d.getCode())));
  }

  /** A liquid sensing location must not be silently moved onto the gas outlet. */
  @Test
  void retainsUnsupportedLiquidOutletSensingAsExplicitHold() {
    EngineeringProject project = project();
    ProcessSystem process = project.getEngineeringProcessSystem();
    Separator separator = (Separator) process.getUnit("20-V-101");
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "SENSING-LOCATION");
    pid.add(new neqsim.process.engineering.pid.PidElement("liquid-ft", "FT-LIQUID",
        neqsim.process.engineering.pid.PidElementType.MEASUREMENT).equipment(separator.getName())
        .line(separator.getLiquidOutStream().getName()).attribute("measuredVariable", "FLOW"));
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process,
        project.getProjectId(), project.getRevision(), "SENSE-001", project.getName(), ContentProfile.PID);
    ProfessionalPidDiagramRenderer renderer = new ProfessionalPidDiagramRenderer(project, pid,
        PidSimulationBindings.from(project, pid), documents, new EngineeringDiagramConventionRegister());
    NativeEngineeringDiagramRenderer.Result result = renderer.render();
    assertTrue(renderer.getContentReportJson().contains("LIQUID_OUTLET_SENSING_LAYOUT"));
    assertFalse(result.getSvgBySheetId().values().iterator().next().contains("sense:FT-LIQUID"));
  }

  /**
   * Builds the source-connected two-equipment test case.
   *
   * @return controlled synthetic project
   */
  private EngineeringProject project() {
    SystemSrkEos fluid = new SystemSrkEos(308.15, 40.0);
    fluid.addComponent("methane", .85);
    fluid.addComponent("n-heptane", .15);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("20-FEED-001", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator separator = new Separator("20-V-101", feed);
    Compressor compressor = new Compressor("20-K-101", separator.getGasOutStream());
    compressor.setOutletPressure(80.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.run();
    return NorsokOffshoreEngineeringBuilder.from("Synthetic ISO drawing regression", process, "2.0.1")
        .projectId("PID-ISO-REFERENCE").registerProposedInstruments(false).build().setRevision("A");
  }
}
