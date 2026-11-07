package neqsim.process.engineering.pid;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;
import neqsim.process.util.scenario.CompressorAutomationStudy;

/**
 * Checks the actual native compressor topology, causal controls and DEXPI/P&amp;ID/PFD export together.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CompressorNativePidIntegrationTest {
  /**
   * Requires every actual unit and true control chain to survive schema-checked native export and drawing QC.
   *
   * @param temporary isolated package parent
   * @throws Exception if native model construction or export fails
   */
  @Test
  void actualRecycleTrainExportsWithoutInventedHardwareOrMissingEquipment(@TempDir Path temporary) throws Exception {
    CompressorAutomationStudy.Train train = CompressorAutomationStudy.create(new CompressorAutomationStudy.Design());
    train.initializeDynamics();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder
        .from("Synthetic compressor native export integration", train.getProcess(), "2.0.1")
        .projectId("COMPRESSOR-NATIVE-EXPORT-TEST").registerProposedInstruments(false).build();
    PidDesignModel model = PidNativeControlSystem.fromProcess(project);
    PidSimulationBindings bindings = PidNativeControlSystem.bind(project, model);
    for (ClosedLoopSafetyFunction function : train.getSafetyFunctions()) {
      model = PidSafetyFunctionImporter.fromNativeFunction(project, model, bindings, train.getProcess(), function,
          function.getName().contains("MANUAL") ? "COMPRESSOR_MANUAL_ESD" : "COMPRESSOR_ESD");
      bindings = PidNativeControlSystem.bind(project, model);
    }
    int measurements = 0;
    int controllers = 0;
    for (PidElement element : model.getElements()) {
      if (element.getType() == PidElementType.MEASUREMENT) {
        measurements++;
      } else if (element.getType() == PidElementType.CONTROLLER) {
        controllers++;
      }
    }
    assertEquals(train.getMeasurements().size(), measurements);
    java.util.Set<String> actualControllers = new java.util.LinkedHashSet<String>();
    for (neqsim.process.controllerdevice.ControllerDeviceInterface controller : train.getControllers()) {
      actualControllers.add(controller.getName());
    }
    for (ProcessEquipmentInterface equipment : train.getProcess().getUnitOperations()) {
      for (neqsim.process.controllerdevice.ControllerDeviceInterface controller : equipment.getControllers()) {
        actualControllers.add(controller.getName());
      }
    }
    assertEquals(actualControllers.size(), controllers);
    Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
        .builder("2026-11-07T00:00:00Z", "Synthetic compressor native export test", "NeqSim", "development")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE,
            project.getProjectId())
        .build();
    Path output = ProfessionalPidPackageExporter.export(project, model, bindings, metadata,
        temporary.resolve("native-package"));
    JsonObject conformance = json(output.resolve("dexpi-2.0.1-conformance.json"));
    assertTrue(conformance.get("schemaValid").getAsBoolean());
    assertEquals("CONFORMANT", conformance.get("status").getAsString());
    JsonObject readiness = json(output.resolve("professional-pid-readiness.json"));
    assertTrue(readiness.get("drawingHasNoErrors").getAsBoolean(), readiness.get("drawingDiagnostics").toString());
    assertEquals("GENERATED_FOR_REVIEW", readiness.getAsJsonObject("pfdOverview").get("generationStatus").getAsString(),
        readiness.getAsJsonObject("pfdOverview").get("diagnostics").toString());
    assertFalse(readiness.get("fitnessForConstruction").getAsBoolean());
    assertTrue(
        json(output.resolve("plant.dexpi.xml.pid-projection.json")).getAsJsonObject("unresolvedTags").size() == 0);
    String pidSymbols = "";
    java.util.Map<String, Integer> functionSymbols = new java.util.LinkedHashMap<String, Integer>();
    java.util.Set<String> directLinks = new java.util.LinkedHashSet<String>();
    for (Map.Entry<String, com.google.gson.JsonElement> sheet : readiness.getAsJsonObject("sheetFiles").entrySet()) {
      String svgPath = sheet.getValue().getAsString();
      pidSymbols += read(output.resolve(svgPath));
      JsonObject layout = json(output.resolve(svgPath.replace(".svg", ".layout.json")));
      for (com.google.gson.JsonElement node : layout.getAsJsonArray("nodes")) {
        String id = node.getAsJsonObject().get("id").getAsString();
        functionSymbols.put(id, Integer.valueOf(functionSymbols.containsKey(id) ? functionSymbols.get(id) + 1 : 1));
      }
      for (com.google.gson.JsonElement edge : layout.getAsJsonArray("edges")) {
        JsonObject route = edge.getAsJsonObject();
        directLinks.add(route.get("from").getAsString() + "->" + route.get("to").getAsString());
      }
    }
    for (PidElement element : model.getElements()) {
      if (element.getType() == PidElementType.CONTROLLER || element.getType() == PidElementType.SIGNAL
          || element.getType() == PidElementType.TRIP || element.getType() == PidElementType.SAFETY_FUNCTION
          || "CALCULATED_COMPRESSOR_MAP_MARGIN".equals(element.getAttributes().get("inputKind"))) {
        assertEquals(Integer.valueOf(1), functionSymbols.get(element.getTag()),
            "one actual function symbol in its owning sheet: " + element.getTag());
      }
    }
    assertTrue(directLinks.contains("30-PIC-101->30-K-100-SPEED-DEMAND"));
    assertTrue(directLinks.contains("30-TIC-102->30-E-100-UTILITY-DEMAND"));
    assertTrue(pidSymbols.contains("SIM FO"), "actual ASV native fail-open policy must be shown");
    assertTrue(pidSymbols.contains("SIM FC"), "actual isolation native fail-closed policy must be shown");
    String pfdSymbols = read(output.resolve("drawings/pfd-overview.svg"));
    for (ProcessEquipmentInterface equipment : train.getProcess().getUnitOperations()) {
      if (!(equipment instanceof StreamInterface)) {
        assertTrue(pidSymbols.contains(equipment.getName()), "P&ID source equipment: " + equipment.getName());
        assertTrue(pfdSymbols.contains(equipment.getName()), "PFD source equipment: " + equipment.getName());
      }
    }
    String nativeXml = read(output.resolve("plant.dexpi.xml"));
    assertTrue(nativeXml.contains("PidSignal_30_FT_100_30_XY_100"));
    assertTrue(nativeXml.contains("PidSignal_30_XY_100_30_ASC_100"));
    assertTrue(nativeXml.contains("PidSignal_30_ASC_100_30_ASV_100"));
    assertFalse(nativeXml.contains("PidSignal_30_PT_100_30_ASC_100"), "monitoring PT must not gain invented causality");
    assertFalse(nativeXml.contains("PidGenerator_native_control_30_XY_100"),
        "calculated input is not a field transmitter");
    assertFalse(nativeXml.contains("PidEquipment_native_sif_30_K_100_DRIVER_TRIP"), "driver stop is not new hardware");
  }

  /**
   * Reads one actual exported JSON document.
   *
   * @param file exported file
   * @return parsed JSON object
   * @throws Exception on file access failure
   */
  private static JsonObject json(Path file) throws Exception {
    return JsonParser.parseString(read(file)).getAsJsonObject();
  }

  /**
   * Reads a UTF-8 native exported document.
   *
   * @param file exported file
   * @return exact text
   * @throws Exception on file access failure
   */
  private static String read(Path file) throws Exception {
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }
}
