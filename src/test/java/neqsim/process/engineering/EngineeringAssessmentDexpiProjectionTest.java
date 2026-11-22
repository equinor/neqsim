package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.engineering.pid.ProfessionalPidPackageExporter;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi20ConformanceAssessment;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.thermo.system.SystemSrkEos;

/** Keeps documentary engineering assessments from becoming invented native pressure instruments and nozzles. */
class EngineeringAssessmentDexpiProjectionTest {
  @TempDir
  Path directory;

  /**
   * Assessment requirements remain in the actual manifest while only the explicit instrument intent is projected.
   *
   * @throws Exception if native export or parsing fails
   */
  @Test
  void engineeringAssessmentsDoNotInventNativePceOrSensingNozzles() throws Exception {
    EngineeringProject project = project();
    Path nativeFile = directory.resolve("assessments.dexpi.xml");
    Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
        .builder("2026-11-05T00:00:00Z", "Native assessment regression", "NeqSim", "test").plantProperty(
            Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, project.getProjectId())
        .build();
    Dexpi20ConformanceAssessment.Report assessment = DexpiEngineeringExporter.exportDexpi201(project, nativeFile,
        metadata);
    assertTrue(assessment.isSchemaAndProfileConformant(), assessment.getErrors().toString());
    Document nativeXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(nativeFile.toFile());
    NodeList objects = nativeXml.getElementsByTagName("Object");
    int functions = 0;
    int generators = 0;
    int sensingNozzles = 0;
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      assertFalse(object.getAttribute("id").contains("BASIS_A"),
          "No PCE, signal or nozzle may be invented for assessment A");
      assertFalse(object.getAttribute("id").contains("BASIS_B"),
          "No PCE, signal or nozzle may be invented for assessment B");
      functions += "Plant/Instrumentation.ProcessInstrumentationFunction".equals(object.getAttribute("type")) ? 1 : 0;
      generators += "Plant/Instrumentation.ProcessSignalGeneratingFunction".equals(object.getAttribute("type")) ? 1 : 0;
      sensingNozzles += "Plant/ProcessEquipment.InstrumentNozzle".equals(object.getAttribute("type")) ? 1 : 0;
    }
    assertEquals(1, functions, "Only the explicit instrumentation intent may create a proposed function");
    assertEquals(1, generators);
    assertEquals(1, sensingNozzles);
    DexpiEngineeringExporter.ExportResult exported = DexpiEngineeringExporter.export(project,
        directory.resolve("package"));
    JsonObject manifest = JsonParser
        .parseString(new String(Files.readAllBytes(exported.getManifestFile()), StandardCharsets.UTF_8))
        .getAsJsonObject();
    assertEquals(3, manifest.getAsJsonArray("requirements").size());
    assertTrue(manifest.toString().contains("BASIS-A"));
    assertTrue(manifest.toString().contains("BASIS-B"));
  }

  /**
   * An explicit professional P&amp;ID overlay supplies the sole native instrumentation graph.
   *
   * @throws Exception if native export or parsing fails
   */
  @Test
  void canonicalProfessionalPidSuppressesUnboundGenericRequirementPce() throws Exception {
    EngineeringProject project = project();
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "ACTUAL-CANONICAL");
    pid.add(new PidElement("pt", "PT-LIVE", PidElementType.MEASUREMENT).equipment("V-101").line("GAS-EXPORT"));
    PidSimulationBindings bindings = PidSimulationBindings.from(project, pid).bindMeasurement("PT-LIVE", "PT-LIVE");
    Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
        .builder("2026-11-05T00:00:00Z", "Canonical native PCE regression", "NeqSim", "test").plantProperty(
            Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, project.getProjectId())
        .build();
    Path exported = ProfessionalPidPackageExporter.export(project, pid, bindings, metadata,
        directory.resolve("canonical"));
    Path nativeFile = exported.resolve("plant.dexpi.xml");
    assertTrue(neqsim.process.processmodel.dexpi.Dexpi201XmlWriter.assess(nativeFile).isSchemaAndProfileConformant());
    Document nativeXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(nativeFile.toFile());
    NodeList objects = nativeXml.getElementsByTagName("Object");
    int functions = 0;
    int generators = 0;
    int sensingNozzles = 0;
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      assertFalse(object.getAttribute("id").startsWith("ProcessInstrumentationFunction_"),
          "Generic requirement functions must not be duplicated beside the canonical P&ID");
      functions += "Plant/Instrumentation.ProcessInstrumentationFunction".equals(object.getAttribute("type")) ? 1 : 0;
      generators += "Plant/Instrumentation.ProcessSignalGeneratingFunction".equals(object.getAttribute("type")) ? 1 : 0;
      sensingNozzles += "Plant/ProcessEquipment.InstrumentNozzle".equals(object.getAttribute("type")) ? 1 : 0;
    }
    assertEquals(1, functions);
    assertEquals(1, generators);
    assertEquals(0, sensingNozzles, "The actual PT senses its existing process segment");
    JsonObject evidence = JsonParser
        .parseString(new String(Files.readAllBytes(exported.resolve("plant.dexpi.xml.engineering-projection.json")),
            StandardCharsets.UTF_8))
        .getAsJsonObject();
    assertEquals("CANONICAL_PID_MODEL_IS_SOLE_REQUIREMENT_PCE_AUTHORITY",
        evidence.get("requirementProposalProjectionPolicy").getAsString());
    assertEquals(3, evidence.getAsJsonArray("requirementsRetainedWithoutGenericPce").size());
    JsonObject manifest = JsonParser
        .parseString(
            new String(Files.readAllBytes(exported.resolve("engineering-manifest.json")), StandardCharsets.UTF_8))
        .getAsJsonObject();
    assertEquals(3, manifest.getAsJsonArray("requirements").size(),
        "No documentary or unbound PCE requirement is discarded");
  }

  /** @return solved source with two documentary assessments and one explicit instrumentation proposal */
  private static EngineeringProject project() {
    SystemSrkEos fluid = new SystemSrkEos(308.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator first = new Separator("V-100", feed);
    first.getGasOutStream().setName("V100-GAS");
    first.getLiquidOutStream().setName("V100-LIQUID");
    Separator second = new Separator("V-101", first.getGasOutStream());
    second.getGasOutStream().setName("GAS-EXPORT");
    second.getLiquidOutStream().setName("LIQUID-EXPORT");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(first);
    process.add(second);
    process.add(new neqsim.process.measurementdevice.PressureTransmitter("PT-LIVE", second.getGasOutStream()));
    process.run();
    EngineeringDesignBasis basis = new EngineeringDesignBasis()
        .addStandard(new EngineeringStandard("DEXPI", "2.0.1", "Native DEXPI", "Explicit native export edition"));
    EngineeringProject project = new EngineeringProject("ASSESSMENT-NOT-PCE", "Documentary engineering basis", process,
        basis);
    project.addRequirement(new EngineeringRequirement("BASIS-A", "V-100",
        EngineeringRequirement.Type.ENGINEERING_ASSESSMENT, "Review mechanical design basis",
        "A documentary assessment has no pressure transmitter or sensing nozzle"));
    project.addRequirement(new EngineeringRequirement("BASIS-B", "V-101",
        EngineeringRequirement.Type.ENGINEERING_ASSESSMENT, "Review process design basis",
        "Retain this requirement as review evidence, without inventing a process instrument"));
    project.addRequirement(new EngineeringRequirement("PRESSURE-INTENT", "V-101",
        EngineeringRequirement.Type.INSTRUMENT, "Pressure indication proposal",
        "Positive control: an explicit instrumentation requirement remains a proposal"));
    return project;
  }
}
