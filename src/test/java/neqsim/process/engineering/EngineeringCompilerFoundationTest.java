package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;
import neqsim.process.engineering.deliverables.EngineeringDeliverableCompiler;
import neqsim.process.engineering.designcase.DesignCaseEngine;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.engineering.designcase.EngineeringMetric;
import neqsim.process.engineering.model.EngineeringCalculation;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringGraphBuilder;
import neqsim.process.engineering.model.EngineeringGraphDiff;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Verifies the canonical graph, design envelope and coordinated deliverable compiler. */
class EngineeringCompilerFoundationTest extends NeqSimTest {
  @TempDir Path temporaryDirectory;

  @Test
  void executesCasesSelectsEnvelopeAndCompilesCoordinatedPackage() throws Exception {
    EngineeringProject project = createProject();
    EngineeringDesignEnvelope envelope = DesignCaseEngine.run(project.getProcessSystem(),
        project.getExecutableDesignCases(), project.getEngineeringMetrics());

    assertEquals(3, envelope.getCaseResults().size());
    assertEquals("FAILED", envelope.getCaseResults().get(2).getStatus());
    EngineeringDesignEnvelope.GoverningValue pressure = envelope.getGoverningValues().get("20-VG-001.pressure");
    assertNotNull(pressure);
    assertEquals("CASE-MAX", pressure.getDesignCaseId());
    assertEquals(75.0, pressure.getValue(), 0.1);
    assertEquals(55.0, project.getProcessSystem().getUnit("20-FEED-001").getPressure("bara"), 0.1);

    EngineeringDeliverableCompiler.CompilationResult result = EngineeringDeliverableCompiler.compile(project,
        temporaryDirectory);
    assertTrue(Files.isRegularFile(result.getEngineeringGraphFile()));
    assertTrue(Files.isRegularFile(result.getDesignEnvelopeFile()));
    assertTrue(Files.isRegularFile(result.getEquipmentRegisterFile()));
    assertTrue(Files.isRegularFile(result.getLineRegisterFile()));
    assertTrue(Files.isRegularFile(result.getInstrumentRegisterFile()));
    assertTrue(Files.isRegularFile(result.getDexpiResult().getDexpi20File()));
    assertTrue(read(result.getEquipmentRegisterFile()).contains("20-VG-001"));
    assertTrue(read(result.getInstrumentRegisterFile()).contains("ENGINEERING_REQUIREMENT"));
    assertTrue(read(result.getDesignEnvelopeFile()).contains("CASE-MAX"));
    assertNotNull(result.getEngineeringGraph().getNode("calculation:envelope-20-vg-001-pressure"));
  }

  @Test
  void persistsGraphAndReportsRevisionImpact() throws Exception {
    EngineeringProject project = createProject();
    EngineeringGraph revisionA = EngineeringGraphBuilder.fromProject(project);
    Path snapshot = temporaryDirectory.resolve("revision-a.json");
    Files.write(snapshot, revisionA.toJson().getBytes(StandardCharsets.UTF_8));
    EngineeringGraph reloaded = EngineeringGraph.read(snapshot);
    assertTrue(revisionA.compareTo(reloaded).isEmpty());

    project.setRevision("B");
    String equipmentNode = EngineeringIds.nodeId(EngineeringNode.Kind.EQUIPMENT, "20-VG-001");
    project.addCalculation(new EngineeringCalculation("20-VG-001-DESIGN-P", equipmentNode,
        "Maximum design-case pressure plus project margin").setStatus(EngineeringCalculation.Status.REVIEW_REQUIRED)
            .setResult(82.5, "bara").setDesignCaseId("CASE-MAX")
            .addInput(new EngineeringCalculation.Input("maximum case pressure", equipmentNode, 75.0, "bara",
                "DESIGN-CASE-REGISTER-REV-B"))
            .addEvidenceReference("PROCESS-DESIGN-BASIS-REV-B"));
    EngineeringGraph revisionB = EngineeringGraphBuilder.fromProject(project);
    EngineeringGraphDiff diff = reloaded.compareTo(revisionB);

    assertFalse(diff.isEmpty());
    assertTrue(diff.getAddedNodeIds().contains("calculation:20-vg-001-design-p"));
    assertTrue(diff.getImpactedNodeIds().contains(equipmentNode));
  }

  private static EngineeringProject createProject() {
    SystemInterface fluid = new SystemSrkEos(298.15, 55.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("20-FEED-001", fluid);
    feed.setFlowRate(30000.0, "kg/hr");
    Separator separator = new Separator("20-VG-001", feed);
    ProcessSystem process = new ProcessSystem("Engineering compiler test process");
    process.add(feed);
    process.add(separator);
    process.run();

    EngineeringProject project = NorsokOffshoreEngineeringBuilder
        .from("Engineering compiler test", process).projectId("TEST-ENGINEERING-PROJECT")
        .registerProposedInstruments(false).build().setRevision("A");
    project.addDesignCase(caseAtPressure("CASE-NORMAL", "Normal operation", 55.0));
    project.addDesignCase(caseAtPressure("CASE-MAX", "Maximum production", 75.0));
    project.addDesignCase(new EngineeringDesignCase("CASE-INVALID", "Invalid controlled case",
        EngineeringDesignCase.Type.CUSTOM, new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem process) {
            throw new IllegalStateException("Missing controlled feed composition");
          }
        }));
    project.addEngineeringMetric(EngineeringMetric.equipmentPressure("20-VG-001"));
    project.addEngineeringMetric(EngineeringMetric.equipmentTemperature("20-VG-001"));
    project.addEngineeringMetric(EngineeringMetric.equipmentInletMassFlow("20-VG-001"));
    return project;
  }

  private static EngineeringDesignCase caseAtPressure(final String id, String name, final double pressureBara) {
    return new EngineeringDesignCase(id, name, EngineeringDesignCase.Type.CUSTOM,
        new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem process) {
            Stream feed = (Stream) process.getUnit("20-FEED-001");
            feed.setPressure(pressureBara, "bara");
          }
        }).addInput(new EngineeringDesignCase.Input("feed pressure", pressureBara, "bara",
            "PROCESS-DESIGN-BASIS"));
  }

  private static String read(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }
}
